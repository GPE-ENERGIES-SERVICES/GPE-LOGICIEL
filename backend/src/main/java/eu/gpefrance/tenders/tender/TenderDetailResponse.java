package eu.gpefrance.tenders.tender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * A tender with everything known about it, its lots and its notice history.
 */
public record TenderDetailResponse(Long id, String source, String externalId, String title, String description,
		String buyer, String reference, String location, List<String> departments, List<MarketType> marketTypes,
		ProcedureType procedureType, List<String> cpvCodes, BigDecimal estimatedAmount, LocalDate publishedAt,
		Instant deadline, String noticeUrl, String platformUrl, TenderStatus status, Boolean retained,
		String filterReason, FollowUpStatus followUpStatus, String followUpComment, String followUpBy,
		Instant followUpAt, List<LotResponse> lots, List<NoticeResponse> notices) {

	static TenderDetailResponse from(Tender tender) {
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
					.map(notice -> new NoticeResponse(notice.getExternalId(), notice.getKind(),
							notice.getPublishedAt(), notice.getDeadline(), notice.getDetails()))
					.toList());
	}

	public record LotResponse(String number, String title, BigDecimal estimatedAmount) {
	}

	public record NoticeResponse(String externalId, NoticeKind kind, LocalDate publishedAt, Instant deadline,
			String details) {
	}

}
