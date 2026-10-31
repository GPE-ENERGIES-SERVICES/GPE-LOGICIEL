package eu.gpefrance.tenders.document;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;

/**
 * Builds PDF and ZIP files for tests.
 */
public final class TestFiles {

	private TestFiles() {
	}

	/**
	 * A PDF with one page per text; an empty text gives a page without text,
	 * like a scanned page.
	 */
	public static byte[] pdf(String... pageTexts) {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			for (String text : pageTexts) {
				PDPage page = new PDPage();
				document.addPage(page);
				if (!text.isEmpty()) {
					try (PDPageContentStream content = new PDPageContentStream(document, page)) {
						content.beginText();
						content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
						content.newLineAtOffset(72, 700);
						content.showText(text);
						content.endText();
					}
				}
			}
			document.save(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/**
	 * A PDF with one page per text, like {@link #pdf}, but an empty text gives
	 * a scanned page: an image, without text.
	 */
	public static byte[] scannedPdf(String... pageTexts) {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			for (String text : pageTexts) {
				PDPage page = new PDPage();
				document.addPage(page);
				try (PDPageContentStream content = new PDPageContentStream(document, page)) {
					if (text.isEmpty()) {
						BufferedImage scan = new BufferedImage(200, 260, BufferedImage.TYPE_BYTE_GRAY);
						content.drawImage(LosslessFactory.createFromImage(document, scan), 0, 0, 612, 792);
					}
					else {
						content.beginText();
						content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
						content.newLineAtOffset(72, 700);
						content.showText(text);
						content.endText();
					}
				}
			}
			document.save(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	static byte[] zip(Map<String, byte[]> entries) {
		return zip(entries, StandardCharsets.UTF_8);
	}

	static byte[] zip(Map<String, byte[]> entries, Charset nameCharset) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(out, nameCharset)) {
			for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
				zip.putNextEntry(new ZipEntry(entry.getKey()));
				zip.write(entry.getValue());
				zip.closeEntry();
			}
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		return out.toByteArray();
	}

	static Map<String, byte[]> entries(Object... nameAndContent) {
		Map<String, byte[]> entries = new LinkedHashMap<>();
		for (int i = 0; i < nameAndContent.length; i += 2) {
			Object content = nameAndContent[i + 1];
			entries.put((String) nameAndContent[i],
					content instanceof byte[] bytes ? bytes : content.toString().getBytes(StandardCharsets.UTF_8));
		}
		return entries;
	}

}
