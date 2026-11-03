package eu.gpefrance.tenders.memoir;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.poi.openxml4j.exceptions.InvalidFormatException;
import org.apache.poi.util.Units;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.UnderlinePatterns;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFStyle;
import org.apache.poi.xwpf.usermodel.XWPFStyles;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTStyle;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.STStyleType;

import eu.gpefrance.tenders.memoir.MemoirStore.MemoirRow;
import eu.gpefrance.tenders.memoir.MemoirStore.SectionRow;

/**
 * A memoir as a Word document, to be read again and finished before it is
 * sent: its accepted sections in the order of the outline, with real headings
 * (Word builds a table of contents from them). What is still to write shows,
 * nothing is invented: a part without text says so, as does an image to add.
 */
final class MemoirWord {

	private static final Pattern IMAGE = Pattern.compile("/api/memoirs/files/(\\d+)");

	/** Characters XML refuses, that a pasted text may hold. */
	private static final Pattern NOT_XML = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F]");

	/** The width of an A4 page within its margins. */
	private static final int MAX_WIDTH_POINTS = 450;

	private static final String[] HEADING_SIZES = { "16", "13", "12", "11", "11", "11" };

	private static final String TO_WRITE = "[Partie à rédiger]";

	private MemoirWord() {
	}

	/**
	 * @param images the file of an image of this memoir, by its id
	 */
	static byte[] write(MemoirRow memoir, List<SectionRow> sections, Function<Long, Optional<Path>> images) {
		try (XWPFDocument word = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			headingStyles(word);
			word.getProperties().getCoreProperties().setTitle(clean(memoir.title()));
			XWPFParagraph title = word.createParagraph();
			title.setAlignment(ParagraphAlignment.LEFT);
			XWPFRun titleRun = title.createRun();
			titleRun.setBold(true);
			titleRun.setFontSize(20);
			titleRun.setText(clean(memoir.title()));
			if (memoir.tenderTitle() != null) {
				word.createParagraph().createRun().setText(clean(memoir.tenderTitle()));
			}
			for (SectionRow section : sections) {
				if (section.proposed()) {
					continue;
				}
				heading(word, section.level(), section.number() + " " + section.title());
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

	private static void headingStyles(XWPFDocument word) {
		XWPFStyles styles = word.createStyles();
		for (int level = 1; level <= HEADING_SIZES.length; level++) {
			CTStyle style = CTStyle.Factory.newInstance();
			style.setStyleId(headingId(level));
			style.addNewName().setVal("heading " + level);
			style.setType(STStyleType.PARAGRAPH);
			style.addNewBasedOn().setVal("Normal");
			style.addNewQFormat();
			style.addNewPPr().addNewOutlineLvl().setVal(java.math.BigInteger.valueOf(level - 1L));
			var run = style.addNewRPr();
			run.addNewB();
			run.addNewSz().setVal(java.math.BigInteger.valueOf(Long.parseLong(HEADING_SIZES[level - 1]) * 2));
			styles.addStyle(new XWPFStyle(style));
		}
	}

	private static String headingId(int level) {
		return "Heading" + level;
	}

	private static void heading(XWPFDocument word, int level, String text) {
		XWPFParagraph paragraph = word.createParagraph();
		paragraph.setStyle(headingId(Math.min(Math.max(level, 1), HEADING_SIZES.length)));
		paragraph.setSpacingBefore(240);
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
