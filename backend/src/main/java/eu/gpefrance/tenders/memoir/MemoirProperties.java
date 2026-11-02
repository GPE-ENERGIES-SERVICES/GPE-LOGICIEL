package eu.gpefrance.tenders.memoir;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param async the work of the AI runs in the background (false in the tests:
 * it runs in the request)
 * @param maxDceCharacters text of the consultation file sent for its analysis
 * @param libraryExcerpts former memoirs given to the AI as references
 * @param excerptLength characters of each former memoir given
 * @param maxFileSize largest image or former memoir, in bytes
 */
@ConfigurationProperties("memoir")
public record MemoirProperties(@DefaultValue("true") boolean async, @DefaultValue("150000") int maxDceCharacters,
		@DefaultValue("3") int libraryExcerpts, @DefaultValue("6000") int excerptLength,
		@DefaultValue("52428800") long maxFileSize) {
}
