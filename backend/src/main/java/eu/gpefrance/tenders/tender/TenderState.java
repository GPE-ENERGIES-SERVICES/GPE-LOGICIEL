package eu.gpefrance.tenders.tender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Everything a merge may change on a tender: the published information, its
 * status, its filtering and its lots. Saved before a merge so that undoing
 * it gives back exactly the same tender. The decision of the team and the
 * follows are not part of it: a merge never changes them.
 * @param noticeIds the notices attached to the tender at that time
 */
public record TenderState(String title, String description, String buyer, String reference, String location,
		List<String> departments, List<String> marketTypes, ProcedureType procedureType, List<String> cpvCodes,
		BigDecimal estimatedAmount, LocalDate publishedAt, Instant deadline, String noticeUrl, String platformUrl,
		String procedureId, TenderStatus status, Boolean retained, String filterReason, Instant filteredAt,
		Instant updatedAt, List<LotDetails> lots, List<Long> noticeIds) {

	public static TenderState of(Tender tender) {
		return new TenderState(tender.getTitle(), tender.getDescription(), tender.getBuyer(), tender.getReference(),
				tender.getLocation(), List.copyOf(tender.getDepartments()),
				tender.getMarketTypes().stream().map(Enum::name).toList(), tender.getProcedureType(),
				List.copyOf(tender.getCpvCodes()), tender.getEstimatedAmount(), tender.getPublishedAt(),
				tender.getDeadline(), tender.getNoticeUrl(), tender.getPlatformUrl(), tender.getProcedureId(),
				tender.getStatus(), tender.getRetained(), tender.getFilterReason(), tender.getFilteredAt(),
				tender.getUpdatedAt(),
				tender.getLots()
					.stream()
					.map(lot -> new LotDetails(lot.getNumber(), lot.getTitle(), lot.getEstimatedAmount()))
					.toList(),
				tender.getNotices().stream().map(TenderNotice::getId).toList());
	}

}
