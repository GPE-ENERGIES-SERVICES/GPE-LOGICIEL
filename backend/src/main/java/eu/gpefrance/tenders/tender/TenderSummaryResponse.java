package eu.gpefrance.tenders.tender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A tender as shown in the list.
 * @param result outcome of the consultation, null while it is open or when unknown
 * @param followed followed by the logged-in user
 */
public record TenderSummaryResponse(Long id, String title, String buyer, String reference, String location,
		List<String> departments, List<MarketType> marketTypes, ProcedureType procedureType, String source,
		BigDecimal estimatedAmount, LocalDate publishedAt, Instant deadline, int lotCount, TenderStatus status,
		FollowUpStatus followUpStatus, Boolean retained, String filterReason,
		TenderDetailResponse.ResultResponse result, boolean followed) {

	static TenderSummaryResponse from(Tender tender, boolean followed) {
		return new TenderSummaryResponse(tender.getId(), tender.getTitle(), tender.getBuyer(), tender.getReference(),
				tender.getLocation(), tender.getDepartments(), tender.getMarketTypes(), tender.getProcedureType(),
				tender.getSource(), tender.getEstimatedAmount(), tender.getPublishedAt(), tender.getDeadline(),
				tender.getLots().size(), tender.getStatus(), tender.getFollowUpStatus(), tender.getRetained(),
				tender.getFilterReason(), TenderDetailResponse.ResultResponse.of(tender), followed);
	}

}
