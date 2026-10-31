package eu.gpefrance.tenders.company;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param expiryWarningDays an item ending within these days is shown as
 * expiring
 * @param maxFileSize largest file of the company, in bytes
 */
@ConfigurationProperties("company")
public record CompanyProperties(@DefaultValue("60") int expiryWarningDays,
		@DefaultValue("52428800") long maxFileSize) {
}
