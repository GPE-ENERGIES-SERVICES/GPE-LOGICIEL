package eu.gpefrance.tenders.document;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Optical character recognition of the pages without text, with Tesseract.
 * @param command the Tesseract executable; when it cannot be run, scanned
 * pages stay unread, as before
 * @param language Tesseract language data, French by default
 * @param dpi resolution of the page images given to Tesseract
 * @param timeout longest recognition of one page
 * @param maxPages pages recognised in one document, at most
 * @param pageSegmentation how Tesseract cuts the page (its --psm option): 6, one
 * block of text, gave the most lines of a real scanned DPGF (34 of 65, against 11
 * with 3 and none with 11 or 12, measured on 28/10/2026)
 */
@ConfigurationProperties("documents.ocr")
public record OcrProperties(@DefaultValue("tesseract") String command, @DefaultValue("fra") String language,
		@DefaultValue("300") int dpi, @DefaultValue("90s") Duration timeout, @DefaultValue("40") int maxPages,
		@DefaultValue("6") int pageSegmentation) {

	public OcrProperties {
		if (dpi < 72 || dpi > 600) {
			throw new IllegalArgumentException("documents.ocr.dpi must be between 72 and 600");
		}
		if (maxPages < 0) {
			throw new IllegalArgumentException("documents.ocr.max-pages must not be negative");
		}
	}

}
