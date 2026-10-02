package eu.gpefrance.tenders.tender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A tender as shown in the list.
 */
public record TenderSummaryResponse(Long id, String title, String buyer, String location, List<String> departments,
		List<MarketType> marketTypes, BigDecimal estimatedAmount, LocalDate publishedAt, Instant deadline,
		TenderStatus status, FollowUpStatus followUpStatus, Boolean retained, String filterReason) {

	static TenderSummaryResponse from(Tender tender) {
		return new TenderSummaryResponse(tender.getId(), tender.getTitle(), tender.getBuyer(), tender.getLocation(),
				tender.getDepartments(), tender.getMarketTypes(), tender.getEstimatedAmount(), tender.getPublishedAt(),
				tender.getDeadline(), tender.getStatus(), tender.getFollowUpStatus(), tender.getRetained(),
				tender.getFilterReason());
	}

}
