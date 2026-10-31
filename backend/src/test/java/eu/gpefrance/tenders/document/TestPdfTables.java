package eu.gpefrance.tenders.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;

/**
 * Digital PDF price schedules made for the tests: texts placed at given
 * positions, numbers aligned to the right as in a real DPGF. No real
 * consultation file.
 */
public final class TestPdfTables {

	private static final float SIZE = 9;

	private static final PDType1Font FONT = new PDType1Font(Standard14Fonts.FontName.HELVETICA);

	/** Right edges of the number columns and left edges of the text columns of the DPGF. */
	static final float REF = 40;

	static final float DESIGNATION = 70;

	static final float UNIT = 300;

	static final float QUANTITY = 370;

	static final float UNIT_PRICE = 440;

	static final float AMOUNT = 530;

	private TestPdfTables() {
	}

	/**
	 * A text at a place of the page.
	 * @param y from the top of the page
	 * @param right whether x is its right edge (numbers) or its left edge
	 */
	public record Text(float x, float y, String text, boolean right) {

		public static Text left(float x, float y, String text) {
			return new Text(x, y, text, false);
		}

		public static Text right(float x, float y, String text) {
			return new Text(x, y, text, true);
		}

	}

	@SafeVarargs
	public static byte[] pdf(List<Text>... pages) {
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			for (List<Text> texts : pages) {
				PDPage page = new PDPage(PDRectangle.A4);
				document.addPage(page);
				try (PDPageContentStream content = new PDPageContentStream(document, page)) {
					for (Text text : texts) {
						float width = FONT.getStringWidth(text.text()) / 1000 * SIZE;
						content.beginText();
						content.setFont(FONT, SIZE);
						content.newLineAtOffset(text.right() ? text.x() - width : text.x(),
								PDRectangle.A4.getHeight() - text.y());
						content.showText(text.text());
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

	/** The header of the DPGF on one line. */
	public static List<Text> header(float y) {
		return List.of(Text.left(REF, y, "N°"), Text.left(DESIGNATION, y, "Désignation des ouvrages"),
				Text.left(UNIT, y, "U"), Text.right(QUANTITY, y, "Qté"), Text.right(UNIT_PRICE, y, "PU HT"),
				Text.right(AMOUNT, y, "Montant HT"));
	}

	/** A line of the DPGF; an empty value leaves its cell empty. */
	public static List<Text> line(float y, String ref, String designation, String unit, String quantity,
			String unitPrice, String amount) {
		List<Text> texts = new ArrayList<>();
		if (!ref.isEmpty()) {
			texts.add(Text.left(REF, y, ref));
		}
		if (!designation.isEmpty()) {
			texts.add(Text.left(DESIGNATION, y, designation));
		}
		if (!unit.isEmpty()) {
			texts.add(Text.left(UNIT, y, unit));
		}
		if (!quantity.isEmpty()) {
			texts.add(Text.right(QUANTITY, y, quantity));
		}
		if (!unitPrice.isEmpty()) {
			texts.add(Text.right(UNIT_PRICE, y, unitPrice));
		}
		if (!amount.isEmpty()) {
			texts.add(Text.right(AMOUNT, y, amount));
		}
		return texts;
	}

	@SafeVarargs
	public static List<Text> page(List<Text>... parts) {
		List<Text> texts = new ArrayList<>();
		for (List<Text> part : parts) {
			texts.addAll(part);
		}
		return texts;
	}

	/**
	 * A DPGF of one page: a title, the header, three lines with French
	 * numbers (the third with a wrong amount), a total and a footer.
	 */
	public static byte[] dpgf() {
		return pdf(page(List.of(Text.left(REF, 60, "DPGF - Lot 1 Terrassement")), header(100),
				line(118, "1.1", "Décapage de terre végétale", "m2", "1 200", "2,50", "3 000,00"),
				line(132, "1.2", "Déblais en pleine masse", "m3", "350", "18,40", "6 440,00"),
				line(146, "1.3", "Évacuation des déblais", "m3", "350", "12,00", "4 300,00"),
				line(170, "", "Total HT", "", "", "", "13 740,00"),
				List.of(Text.left(REF, 800, "Page 1 / 1"))));
	}

}
