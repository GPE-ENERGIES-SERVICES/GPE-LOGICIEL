package eu.gpefrance.tenders.document;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Images of the consultation files (photos, scans, captures), read by OCR
 * once turned into a PDF.
 * @param heifCommand the libheif decoder of the HEIC photos; when it cannot be
 * run, these photos are kept but not read
 * @param maxFileSize a larger image file is kept but not read
 * @param maxPixels an image with more pixels is kept but not read: beyond, it
 * is no document, or a file made to exhaust the memory
 */
@ConfigurationProperties("documents.images")
public record ImageProperties(@DefaultValue("heif-dec") String heifCommand,
		@DefaultValue("100MB") DataSize maxFileSize, @DefaultValue("300000000") long maxPixels) {

	public ImageProperties {
		if (maxPixels < 1) {
			throw new IllegalArgumentException("documents.images.max-pixels must be positive");
		}
	}

}
