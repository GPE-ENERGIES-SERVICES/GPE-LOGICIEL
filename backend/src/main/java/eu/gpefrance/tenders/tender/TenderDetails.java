package eu.gpefrance.tenders.tender;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Information of a tender in the common format, as read from one notice.
 * Absent information is {@code null} or an empty list.
 */
public record TenderDetails(String title, String buyer, String reference, String location, List<String> departments,
		List<MarketType> marketTypes, ProcedureType procedureType, List<String> cpvCodes, BigDecimal estimatedAmount,
		LocalDate publishedAt, Instant deadline, String noticeUrl, String platformUrl, List<LotDetails> lots) {

	public TenderDetails {
		departments = List.copyOf(departments);
		marketTypes = List.copyOf(marketTypes);
		cpvCodes = List.copyOf(cpvCodes);
		lots = List.copyOf(lots);
	}

}
