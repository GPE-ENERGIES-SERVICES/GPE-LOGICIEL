package eu.gpefrance.tenders.supplier;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param priceMaxAge how long a price read from a supplier is used without asking again
 * @param availabilityMaxAge how long a stock read from a supplier is shown without asking again
 */
@ConfigurationProperties("supplier")
public record SupplierPricingProperties(Duration priceMaxAge, Duration availabilityMaxAge) {
}
