package eu.gpefrance.tenders.tender;

import java.math.BigDecimal;
import java.text.Normalizer;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Predicate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.ErrorResponseException;

import eu.gpefrance.tenders.ClockConfiguration;
import eu.gpefrance.tenders.company.CompanyDirectory;

/**
 * The result of a tender: who won it, for how much, and what it changes on
 * the follow-up. A contract won goes to AWARDED, then to « Marchés
 * attribués »; a contract lost after an answer goes to REJECTED.
 */
@Service
public class AwardService {

	private final TenderRepository tenders;

	private final TenderAwardRepository awards;

	/** The winner written when the company has no name in its identity yet. */
	private static final String COMPANY = "GPE Énergies & Services";

	private final FollowUpHistory history;

	private final CompanyDirectory company;

	private final Clock clock;

	public AwardService(TenderRepository tenders, TenderAwardRepository awards, FollowUpHistory history,
			CompanyDirectory company, Clock clock) {
		this.tenders = tenders;
		this.awards = awards;
		this.history = history;
		this.company = company;
		this.clock = clock;
	}

	/**
	 * A tender set to AWARDED by its status alone joins « Marchés attribués »:
	 * won by the company today, the amount unknown. Nothing changes when it
	 * already has an award won.
	 */
	void recordWonByStatus(Tender tender, String user, Instant now) {
		boolean won = awards.findByTenderIdOrderByLotNumberAscIdAsc(tender.getId()).stream().anyMatch(TenderAward::isWon);
		if (!won) {
			TenderAward award = awards.findOne(tender.getId(), null).orElseGet(() -> new TenderAward(tender, null));
			award.record(companyName(), null, LocalDate.ofInstant(now, ClockConfiguration.ZONE),
					TenderAward.Source.MANUAL, true, user, now);
			awards.save(award);
		}
	}

	private String companyName() {
		String legalName = company.profile().getLegalName();
		return legalName == null || legalName.isBlank() ? COMPANY : legalName;
	}

	/**
	 * @param won the company won it; otherwise its offer was rejected, or it
	 * did not answer and only who won is kept
	 * @param lotNumber the lot, null for the whole contract
	 * @param reason why, kept in the history of the statuses
	 */
	public record AwardRequest(@NotNull Boolean won,
			@NotBlank(message = "Le titulaire est obligatoire.") @Size(max = 300) String winner,
			@PositiveOrZero(message = "Le montant ne peut pas être négatif.") BigDecimal amount,
			@NotNull(message = "La date d'attribution est obligatoire.") @PastOrPresent(
					message = "La date d'attribution ne peut pas être à venir.") LocalDate awardedAt,
			@NotNull TenderAward.Source source, @Size(max = 50) String lotNumber, @Size(max = 2000) String reason) {
	}

	/** An award as shown in « Marchés attribués » and on the tender. */
	public record AwardResponse(Long id, Long tenderId, String title, String buyer, String location,
			String postalCode, String department, MarketType marketType, List<String> activities, String lotNumber,
			String lot, String winner, String winnerCity, BigDecimal amount, LocalDate awardedAt,
			Integer offersReceived, TenderAward.Source source, boolean won) {

		static AwardResponse from(TenderAward award) {
			Tender tender = award.getTender();
			String lot = award.getLotNumber() == null ? null
					: tender.getLots()
						.stream()
						.filter(candidate -> award.getLotNumber().equals(candidate.getNumber()))
						.findFirst()
						.map(candidate -> "Lot " + candidate.getNumber() + " – "
								+ Objects.requireNonNullElse(candidate.getTitle(), "sans intitulé"))
						.orElse("Lot " + award.getLotNumber());
			return new AwardResponse(award.getId(), tender.getId(), tender.getTitle(),
					Objects.requireNonNullElse(tender.getBuyer(), "Acheteur non indiqué"), tender.getLocation(), null,
					tender.getDepartments().stream().findFirst().orElse(null),
					tender.getMarketTypes().stream().findFirst().orElse(MarketType.WORKS), tender.getActivities(),
					award.getLotNumber(), lot, award.getWinner(), null, award.getAmount(), award.getAwardedAt(), null,
					award.getSource(), award.isWon());
		}

	}

	/** A page of awards, with figures on all those found. */
	public record AwardSearchResponse(List<AwardResponse> content, int page, int size, long totalElements,
			int totalPages, long winnerCount, BigDecimal totalAmount) {
	}

	/**
	 * Records who won the tender or one of its lots; recording the same lot
	 * again updates it. A contract won makes the tender AWARDED (a signed one
	 * stays SIGNED); lost after an answer, REJECTED.
	 */
	@Transactional
	public void record(Long tenderId, AwardRequest request, String user) {
		Tender tender = tenders.findById(tenderId).orElseThrow(() -> new TenderNotFoundException(tenderId));
		String lotNumber = request.lotNumber() == null || request.lotNumber().isBlank() ? null
				: request.lotNumber().strip();
		if (lotNumber != null && tender.getLots().stream().noneMatch(lot -> lot.getNumber().equals(lotNumber))) {
			throw new ErrorResponseException(HttpStatus.BAD_REQUEST,
					ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Lot inconnu : " + lotNumber), null);
		}
		Instant now = clock.instant();
		FollowUpStatus current = tender.getFollowUpStatus();
		FollowUpStatus next = null;
		if (request.won() && current != FollowUpStatus.AWARDED && current != FollowUpStatus.SIGNED) {
			next = FollowUpStatus.AWARDED;
		}
		else if (!request.won() && current == FollowUpStatus.SUBMITTED) {
			next = FollowUpStatus.REJECTED;
		}
		if (next != null) {
			TenderStatusChange.Origin origin = request.source() == TenderAward.Source.NOTICE
					? TenderStatusChange.Origin.NOTICE : TenderStatusChange.Origin.USER;
			String reason = request.reason() == null || request.reason().isBlank() ? null : request.reason().strip();
			history.change(tender, next, origin, tender.getFollowUpComment(), reason, user, now);
		}
		TenderAward award = awards.findOne(tenderId, lotNumber).orElseGet(() -> new TenderAward(tender, lotNumber));
		award.record(request.winner().strip(), request.amount(), request.awardedAt(), request.source(),
				request.won(), user, now);
		awards.save(award);
	}

	@Transactional(readOnly = true)
	public List<AwardResponse> ofTender(Long tenderId) {
		return awards.findByTenderIdOrderByLotNumberAscIdAsc(tenderId).stream().map(AwardResponse::from).toList();
	}

	/**
	 * The awards matching the filters of « Marchés attribués » (q, winner,
	 * buyer, year, activity, marketType, region, department, city, minAmount,
	 * maxAmount), the latest first by default; an unknown amount never hides
	 * one.
	 * shortcut: filtered in memory, fine for the few thousand awards recorded by hand; a query once award notices are read in bulk.
	 */
	@Transactional(readOnly = true)
	public AwardSearchResponse search(AwardFilters filters, String sort, int page, int size) {
		List<AwardResponse> found = awards.findAllWithTender()
			.stream()
			.filter(award -> award.getTender().getMergedIntoId() == null)
			.map(AwardResponse::from)
			.filter(filters.predicate())
			.sorted(order(sort))
			.toList();
		int from = Math.min(page * size, found.size());
		return new AwardSearchResponse(found.subList(from, Math.min(from + size, found.size())), page, size,
				found.size(), (found.size() + size - 1) / size,
				found.stream().map(award -> normalize(award.winner())).distinct().count(),
				found.stream().map(AwardResponse::amount).filter(Objects::nonNull).reduce(BigDecimal.ZERO,
						BigDecimal::add));
	}

	private static Comparator<AwardResponse> order(String sort) {
		String key = sort == null || sort.isBlank() ? "-awardedAt" : sort;
		boolean descending = key.startsWith("-");
		Comparator<AwardResponse> order = key.replaceFirst("^-", "").equals("amount")
				? Comparator.comparing(AwardResponse::amount,
						descending ? Comparator.nullsFirst(Comparator.<BigDecimal>naturalOrder())
								: Comparator.nullsLast(Comparator.<BigDecimal>naturalOrder()))
				: Comparator.comparing(AwardResponse::awardedAt);
		// Unknown amounts last in both orders.
		return (descending ? order.reversed() : order).thenComparing(AwardResponse::id, Comparator.reverseOrder());
	}

	static String normalize(String text) {
		return text == null ? ""
				: Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}+", "").toLowerCase(Locale.ROOT);
	}

	/** Filters of GET /api/awards, all combinable, as sent in the request parameters. */
	public record AwardFilters(String q, String winner, String buyer, Integer year, List<String> activity,
			List<MarketType> marketType, String region, List<String> department, String city, BigDecimal minAmount,
			BigDecimal maxAmount) {

		Predicate<AwardResponse> predicate() {
			List<String> departments = department == null ? List.of()
					: department.stream().map(String::strip).toList();
			List<String> regionDepartments = region == null || region.isBlank() ? null
					: Regions.departmentsOf(region);
			return award -> {
				String searched = normalize(String.join(" ", award.title(), Objects.toString(award.lot(), ""),
						award.buyer(), award.winner(), Objects.toString(award.location(), "")));
				return (q == null || List.of(normalize(q).split("\\s+")).stream().allMatch(searched::contains))
						&& contains(award.winner(), winner) && contains(award.buyer(), buyer)
						&& (year == null || award.awardedAt().getYear() == year)
						&& (activity == null || activity.isEmpty()
								|| award.activities().stream().anyMatch(new HashSet<>(activity)::contains))
						&& (marketType == null || marketType.isEmpty() || marketType.contains(award.marketType()))
						&& (departments.isEmpty() || departments.contains(award.department()))
						&& (regionDepartments == null || regionDepartments.contains(award.department()))
						&& contains(award.location(), city)
						&& (minAmount == null || award.amount() == null || award.amount().compareTo(minAmount) >= 0)
						&& (maxAmount == null || award.amount() == null || award.amount().compareTo(maxAmount) <= 0);
			};
		}

		private static boolean contains(String value, String searched) {
			return searched == null || searched.isBlank() || normalize(value).contains(normalize(searched.strip()));
		}

	}

}
