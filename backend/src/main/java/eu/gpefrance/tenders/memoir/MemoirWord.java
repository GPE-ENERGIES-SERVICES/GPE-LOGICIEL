package eu.gpefrance.tenders.memoir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import eu.gpefrance.tenders.memoir.MemoirStore.MemoirRow;
import eu.gpefrance.tenders.memoir.MemoirStore.SectionRow;

/**
 * A memoir as a Word document, to be read again and finished before it is
 * sent: its accepted sections in the order of the outline, with real headings
 * (Word builds a table of contents from them). What is still to write shows,
 * nothing is invented: a part without text says so, as does an image to add.
 */
public final class MemoirWord {

	private static final Pattern IMAGE = Pattern.compile("/api/memoirs/files/(\\d+)");

	/** Characters XML refuses, that a pasted text may hold. */
	private static final Pattern NOT_XML = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");

	/** The width of an A4 page within its margins. */
	private static final int MAX_WIDTH_POINTS = 450;

	/** The memoir of GPE without its text: cover page, logo in the header, fonts, styles, table of contents. */
	private static final String TEMPLATE = "/memoir/modele-gpe.docx";

	/** The headings of GPE: a part, a sub-part (both numbered by Word), then a point. */
	static final String PART = "Titre1";

	static final String SUB_PART = "SST";

	static final String POINT = "POINT";

	/** The numbering of the sub-parts in the template, whose third level is 1.1.1. */
	private static final BigInteger SUB_PART_NUMBERING = BigInteger.valueOf(43);

	private static final String TO_WRITE = "[Partie à rédiger]";

	private MemoirWord() {
	}

	/** What the cover page says of the tender; null when unknown. */
	record Cover(String buyer, String place) {
	}

	/**
	 * @param images the file of an image of this memoir, by its id
	 */
	static byte[] write(MemoirRow memoir, Cover cover, List<SectionRow> sections,
			Function<Long, Optional<Path>> images) {
		try (InputStream template = MemoirWord.class.getResourceAsStream(TEMPLATE);
				XWPFDocument word = new XWPFDocument(template);
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			word.getProperties().getCoreProperties().setTitle(clean(memoir.title()));
			cover(word, memoir, cover, MEMOIR_FOOTER);
			for (SectionRow section : sections) {
				if (section.proposed()) {
					continue;
				}
				heading(word, section.level(), section.title());
				if (section.content() == null) {
					word.createParagraph().createRun().setText(TO_WRITE);
				}
				else {
					Element body = Jsoup.parseBodyFragment(section.content()).body();
					blocks(word, body, section.level(), images);
				}
			}
			word.write(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** The name of the file, without what a file system refuses. */
	static String fileName(MemoirRow memoir) {
		String name = memoir.title().replaceAll("[\\\\/:*?\"<>|\\x00-\\x1F]", "_").strip();
		return (name.length() <= 120 ? name : name.substring(0, 120).strip()) + ".docx";
	}

	/** The cover of the template holds markers, in its text boxes: each becomes what is known of the tender. */
	/**
	 * A piece of the answer written in the application (a list of works, a
	 * guarantee, a note...), in the style of GPE: its header, footer, fonts and
	 * styles, without the cover of a memoir.
	 * @param html the text, cleaned
	 */
	public static byte[] piece(String title, String tenderTitle, String buyer, String place, String html) {
		MemoirRow row = new MemoirRow(null, null, tenderTitle, null, title, MemoirStatus.WRITING, null, null, null,
				java.time.Instant.EPOCH, null, java.time.Instant.EPOCH, 0, 0);
		try (InputStream template = MemoirWord.class.getResourceAsStream(TEMPLATE);
				XWPFDocument word = bare(template);
				ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			word.getProperties().getCoreProperties().setTitle(clean(title));
			cover(word, row, new Cover(buyer, place), clean(title) + " – ");
			XWPFRun heading = word.createParagraph().createRun();
			heading.setBold(true);
			heading.setFontSize(16);
			heading.setColor("0F4761");
			heading.setText(clean(title));
			blocks(word, Jsoup.parseBodyFragment(html == null ? "" : html).body(), 1, (id) -> Optional.empty());
			word.write(out);
			return out.toByteArray();
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	/** The template without its body: cover and table of contents left out, header, footer and styles kept. */
	private static XWPFDocument bare(InputStream template) throws IOException {
		try (XWPFDocument full = new XWPFDocument(template); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			try (XmlCursor cursor = full.getDocument().getBody().newCursor()) {
				boolean more = cursor.toFirstChild();
				while (more) {
					if ("sectPr".equals(cursor.getName().getLocalPart())) {
						more = cursor.toNextSibling();
					}
					else {
						cursor.removeXml();
						more = cursor.isStart();
					}
				}
			}
			full.write(out);
			return new XWPFDocument(new ByteArrayInputStream(out.toByteArray()));
		}
	}

	/** The first words of the footer of the template, followed by the object of the tender. */
	private static final String MEMOIR_FOOTER = "Mémoire technique – ";

	private static void cover(XWPFDocument word, MemoirRow memoir, Cover cover, String footer) {
		String lot = memoir.lotNumber() == null || memoir.lotNumber().isBlank() ? ""
				: "LOT N°" + memoir.lotNumber().strip() + " ";
		Map<String, String> values = Map.of("{{LOT}}", lot, "{{LOT_TITLE}}", memoir.title(), "{{OBJECT}}",
				Objects.requireNonNullElse(memoir.tenderTitle(), ""), "{{BUYER}}",
				Objects.requireNonNullElse(cover.buyer(), ""), "{{PLACE_LABEL}}", cover.place() == null ? "" : "Lieu : ",
				"{{PLACE}}", Objects.requireNonNullElse(cover.place(), ""), MEMOIR_FOOTER, footer);
		List<XmlObject> parts = new ArrayList<>(List.of(word.getDocument()));
		word.getFooterList().forEach((part) -> parts.add(part._getHdrFtr()));
		for (XmlObject part : parts) {
			try (XmlCursor cursor = part.newCursor()) {
				cursor.selectPath("declare namespace w='http://schemas.openxmlformats.org/wordprocessingml/2006/main' .//w:t");
				while (cursor.toNextSelection()) {
					String value = values.get(cursor.getTextValue());
					if (value != null) {
						cursor.setTextValue(clean(value));
					}
				}
			}
		}
	}

	private static void heading(XWPFDocument word, int level, String text) {
		XWPFParagraph paragraph = word.createParagraph();
		paragraph.setStyle(level <= 1 ? PART : level <= 3 ? SUB_PART : POINT);
		if (level == 3) {
			paragraph.setNumID(SUB_PART_NUMBERING);
			paragraph.setNumILvl(BigInteger.TWO);
		}
		paragraph.setKeepNext(true);
		paragraph.createRun().setText(clean(text));
	}

	private static void blocks(XWPFDocument word, Element parent, int level, Function<Long, Optional<Path>> images)
			throws IOException {
		for (Node node : parent.childNodes()) {
			if (node instanceof TextNode text) {
				if (!text.isBlank()) {
					inline(word.createParagraph(), node, false, false, false);
				}
				continue;
			}
			if (!(node instanceof Element element)) {
				continue;
			}
			switch (element.normalName()) {
				case "h3", "h4", "h5" -> heading(word, level + Integer.parseInt(element.normalName().substring(1)) - 2,
						element.text());
				case "ul" -> list(word, element, false);
				case "ol" -> list(word, element, true);
				case "blockquote" -> {
					XWPFParagraph quote = word.createParagraph();
					quote.setIndentationLeft(400);
					inlineChildren(quote, element, false, true, false);
				}
				case "table" -> table(word, element);
				case "figure" -> figure(word, element, images);
				case "img" -> image(word, element, images);
				default -> inlineChildren(word.createParagraph(), element, false, false, false);
			}
		}
	}

	// shortcut: bullets and numbers written in the text, not a Word list; a real numbering if users ask to restyle them.
	private static void list(XWPFDocument word, Element list, boolean numbered) {
		int number = 1;
		for (Element item : list.children()) {
			XWPFParagraph paragraph = word.createParagraph();
			paragraph.setStyle("Paragraphedeliste");
			paragraph.setIndentationLeft(400);
			paragraph.setIndentationHanging(250);
			paragraph.createRun().setText(numbered ? number++ + ". " : "• ");
			inlineChildren(paragraph, item, false, false, false);
		}
	}

	private static void table(XWPFDocument word, Element html) {
		List<Element> rows = html.select("tr");
		if (rows.isEmpty()) {
			return;
		}
		XWPFTable table = word.createTable();
		boolean first = true;
		for (Element row : rows) {
			XWPFTableRow target = first ? table.getRow(0) : table.createRow();
			int index = 0;
			for (Element cell : row.select("> th, > td")) {
				XWPFTableCell out = target.getCell(index) != null ? target.getCell(index) : target.addNewTableCell();
				inlineChildren(out.getParagraphs().getFirst(), cell, cell.normalName().equals("th"), false, false);
				index++;
			}
			first = false;
		}
		word.createParagraph();
	}

	private static void figure(XWPFDocument word, Element figure, Function<Long, Optional<Path>> images)
			throws IOException {
		Element image = figure.selectFirst("img");
		String caption = Optional.ofNullable(figure.selectFirst("figcaption")).map(Element::text).orElse("");
		if (image != null) {
			image(word, image, images);
		}
		else {
			// Never left out silently: an image is still to add here.
			word.createParagraph().createRun().setText("[Image à ajouter" + (caption.isBlank() ? "" : " : " + clean(caption)) + "]");
			return;
		}
		if (!caption.isBlank()) {
			XWPFRun run = word.createParagraph().createRun();
			run.setItalic(true);
			run.setText(clean(caption));
		}
	}

	private static void image(XWPFDocument word, Element image, Function<Long, Optional<Path>> images)
			throws IOException {
		Matcher matcher = IMAGE.matcher(image.attr("src"));
		Optional<Path> file = matcher.matches() ? images.apply(Long.valueOf(matcher.group(1))) : Optional.empty();
		byte[] content = file.filter(Files::isRegularFile).map(MemoirWord::read).orElse(null);
		BufferedImage read = content == null ? null : ImageIO.read(new ByteArrayInputStream(content));
		if (read == null) {
			word.createParagraph().createRun().setText("[Image introuvable : à ajouter]");
			return;
		}
		double scale = Math.min(1, (double) MAX_WIDTH_POINTS / read.getWidth());
		int type = content[0] == (byte) 0x89 ? XWPFDocument.PICTURE_TYPE_PNG : XWPFDocument.PICTURE_TYPE_JPEG;
		try {
			word.createParagraph()
				.createRun()
				.addPicture(new ByteArrayInputStream(content), type, "image", Units.toEMU(read.getWidth() * scale),
						Units.toEMU(read.getHeight() * scale));
		}
		catch (InvalidFormatException ex) {
			word.createParagraph().createRun().setText("[Image illisible : à ajouter]");
		}
	}

	private static byte[] read(Path path) {
		try {
			return Files.readAllBytes(path);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
	}

	private static void inlineChildren(XWPFParagraph paragraph, Element parent, boolean bold, boolean italic,
			boolean underline) {
		for (Node child : parent.childNodes()) {
			inline(paragraph, child, bold, italic, underline);
		}
	}

	private static void inline(XWPFParagraph paragraph, Node node, boolean bold, boolean italic, boolean underline) {
		if (node instanceof TextNode text) {
			if (!text.text().isEmpty()) {
				XWPFRun run = paragraph.createRun();
				run.setBold(bold);
				run.setItalic(italic);
				if (underline) {
					run.setUnderline(UnderlinePatterns.SINGLE);
				}
				run.setText(clean(text.text()));
			}
		}
		else if (node instanceof Element element) {
			switch (element.normalName()) {
				case "br" -> paragraph.createRun().addBreak();
				case "strong", "b" -> inlineChildren(paragraph, element, true, italic, underline);
				case "em", "i" -> inlineChildren(paragraph, element, bold, true, underline);
				case "u" -> inlineChildren(paragraph, element, bold, italic, true);
				default -> inlineChildren(paragraph, element, bold, italic, underline);
			}
		}
	}

	private static String clean(String text) {
		return NOT_XML.matcher(text).replaceAll("");
	}

}
