package eu.gpefrance.tenders.collect.ted;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param baseUrl URL of the TED API of the Publications Office of the EU;
 * searching published notices needs no account nor key
 */
@ConfigurationProperties("ted")
public record TedProperties(String baseUrl) {
}
