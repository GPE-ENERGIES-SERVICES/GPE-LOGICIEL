package eu.gpefrance.tenders.collect.approch;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl URL of the dataset of the purchase projects of APProch on the
 * open data portal of the ministry of economy, open without account
 * @param companiesUrl URL of the public company search API of the State,
 * giving the name of a buyer from its SIREN, open without account
 */
@ConfigurationProperties("approch")
public record ApprochProperties(String baseUrl, String companiesUrl) {
}
