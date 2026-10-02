package eu.gpefrance.tenders.tender;

import java.math.BigDecimal;

public record LotDetails(String number, String title, BigDecimal estimatedAmount) {
}
