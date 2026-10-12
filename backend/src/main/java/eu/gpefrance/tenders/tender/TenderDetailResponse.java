package eu.gpefrance.tenders.tender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A tender with everything known about it, its lots and its notice history.
 * @param result outcome of a past tender, {@code null} while it is open or
 * when unknown
 * @param followed followed by the logged-in user
 * @param mergedIntoId the tender this duplicate was merged into, null otherwise
 */
public record TenderDetailResponse(Long id, String source, String externalId, String title, String description,
		String buyer, String reference, String location, List<String> departments, List<MarketType> marketTypes,
		ProcedureType procedureType, List<String> cpvCodes, BigDecimal estimatedAmount, LocalDate publishedAt,
		Instant deadline, String noticeUrl, String platformUrl, TenderStatus status, Boolean retained,
		String filterReason, FollowUpStatus followUpStatus, String followUpComment, String followUpBy,
		Instant followUpAt, List<LotResponse> lots, List<NoticeResponse> notices, ResultResponse result,
		boolean followed, List<MatchResponse> matches, Long mergedIntoId) {

	/**
	 * @param matches the tenders of other sources proposed or confirmed as the
	 * same consultation
	 */
	static TenderDetailResponse from(Tender tender, boolean followed, List<MatchResponse> matches) {
		return new TenderDetailResponse(tender.getId(), tender.getSource(), tender.getExternalId(), tender.getTitle(),
				tender.getDescription(), tender.getBuyer(), tender.getReference(), tender.getLocation(),
				tender.getDepartments(), tender.getMarketTypes(), tender.getProcedureType(), tender.getCpvCodes(),
				tender.getEstimatedAmount(), tender.getPublishedAt(), tender.getDeadline(), tender.getNoticeUrl(),
				tender.getPlatformUrl(), tender.getStatus(), tender.getRetained(), tender.getFilterReason(),
				tender.getFollowUpStatus(), tender.getFollowUpComment(), tender.getFollowUpBy(),
				tender.getFollowUpAt(),
				tender.getLots()
					.stream()
					.map(lot -> new LotResponse(lot.getNumber(), lot.getTitle(), lot.getEstimatedAmount()))
					.toList(),
				tender.getNotices()
					.stream()
					.map(notice -> new NoticeResponse(notice.getSource(), notice.getExternalId(), notice.getKind(),
							notice.getPublishedAt(), notice.getDeadline(), notice.getDetails()))
					.toList(),
				ResultResponse.of(tender), followed, matches, tender.getMergedIntoId());
	}

	/**
	 * Outcome read from the notices and the status: AWARDED once an award
	 * notice of the source of the tender is published (not one of another
	 * source kept in its history), CANCELLED or UNSUCCESSFUL from the status.
	 */
	public record ResultResponse(String kind) {

		static ResultResponse of(Tender tender) {
			if (tender.getNotices()
				.stream()
				.anyMatch(notice -> notice.getKind() == NoticeKind.AWARD && notice.getSource().equals(tender.getSource()))) {
				return new ResultResponse("AWARDED");
			}
			return switch (tender.getStatus()) {
				case CANCELLED, UNSUCCESSFUL -> new ResultResponse(tender.getStatus().name());
				case ACTIVE, CLOSED -> null;
			};
		}

	}

	public record LotResponse(String number, String title, BigDecimal estimatedAmount) {
	}

	/**
	 * @param source the source that published the notice: the one of the
	 * tender, or another one publishing the same consultation
	 */
	public record NoticeResponse(String source, String externalId, NoticeKind kind, LocalDate publishedAt,
			Instant deadline, String details) {
	}

	/**
	 * A tender of another source that is probably the same consultation.
	 * @param tenderId the other tender
	 * @param rule why they were matched, such as SAME_PROCEDURE
	 */
	public record MatchResponse(Long id, Long tenderId, String source, String externalId, String title,
			String rule, TenderMatch.MatchStatus status) {
	}

}
