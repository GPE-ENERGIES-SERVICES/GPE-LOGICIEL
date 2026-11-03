package eu.gpefrance.tenders.response;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.xml.namespace.QName;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.apache.xmlbeans.XmlObject;

/**
 * A form of a consultation file, as Word: the places a candidate fills, found
 * in its text, and the same document with them filled. The rest of the document
 * is left exactly as the buyer wrote it.
 * <p>
 * A place is dots to fill in, a blank line or the end of a line after a label,
 * an empty cell of a table, or a box to tick. What engages the candidate (the
 * declarations on honour, the signature) and what belongs to the buyer (the
 * acceptance of the offer) is locked: never filled here, whoever asks.
 */
final class FormDocument implements AutoCloseable {

	enum Kind {

		TEXT, CHECK

	}

	/**
	 * @param id its place in the document, the same for the same file
	 * @param label what surrounds it, to know what it asks
	 * @param locked to fill by hand, on the document itself
	 */
	record Slot(String id, Kind kind, String label, boolean locked) {
	}

	private static final String W = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

	private static final String CHECK_BOXES = "declare namespace w='" + W + "'; .//w:fldChar/w:ffData/w:checkBox";

	/** Dots or underscores to fill in. */
	private static final Pattern DOTS = Pattern.compile("(?:[.…_]{4,}|…{2,}\\.*)(?:\\s*[.…_]+)*");

	/** The box of the forms written with the Wingdings font, and the same box ticked. */
	private static final char BOX = '¨';

	private static final char TICKED_BOX = 'þ';

	/** The box written as a character of the text, and the same box ticked. */
	private static final char TEXT_BOX = '☐';

	private static final char TICKED_TEXT_BOX = '☒';

	/** A line of a table of contents. */
	private static final Pattern CONTENTS_LINE = Pattern.compile("\\t\\s*\\d+\\s*$");

	/** A label waiting for its value. */
	private static final Pattern LABEL_END = Pattern.compile("(:|n°|N°)\\s*$");

	private static final Pattern PART = Pattern.compile("^[A-Z]\\d?\\s*[-–]\\s.*");

	/** Where the candidate engages himself, and what belongs to the buyer. */
	private static final Pattern LOCKING = Pattern.compile(
			"sur l.honneur|engagements du candidat|signature|acceptation de l.offre|date d.effet|cadre reserve|reserve a l.administration|reserve a l.acheteur");

	/** The place and date of the signature. */
	private static final Pattern SIGNING_PLACE = Pattern.compile("^(fait\\s+)?a\\s.*,?\\s*le\\b.*");

	private static final Pattern UNLOCKING = Pattern.compile("^(identification|objet|presentation|designation du mandataire|prix|avance|prestations similaires)\\b.*");

	private static final int MAX_LABEL = 400;

	private final XWPFDocument word;

	private final List<Place> places = new ArrayList<>();

	private final List<Slot> slots = new ArrayList<>();

	/**
	 * @param paragraph where to write
	 * @param occurrence which dots or which box of the paragraph
	 * @param following lines of dots after the first one, emptied once it is filled
	 */
	private record Place(Slot slot, XWPFParagraph paragraph, Way way, int occurrence, List<XWPFParagraph> following) {
	}

	private enum Way {

		DOTS, BLANK, AFTER_LABEL, FIELD_BOX, CHARACTER_BOX

	}

	private FormDocument(XWPFDocument word) {
		this.word = word;
		read();
	}

	static FormDocument open(InputStream docx) throws IOException {
		return new FormDocument(new XWPFDocument(docx));
	}

	List<Slot> slots() {
		return List.copyOf(slots);
	}

	/**
	 * Writes the values into their places; a locked place is never written.
	 * @param texts the text of each place, by its id
	 * @param boxes the boxes to tick or untick, by their id
	 */
	void fill(Map<String, String> texts, Map<String, Boolean> boxes) {
		// From the end of each paragraph: the positions of the first ones stay right.
		for (int index = places.size() - 1; index >= 0; index--) {
			Place place = places.get(index);
			if (place.slot().locked()) {
				continue;
			}
			switch (place.way()) {
				case FIELD_BOX, CHARACTER_BOX -> {
					Boolean ticked = boxes.get(place.slot().id());
					if (ticked != null) {
						tick(place, ticked);
					}
				}
				default -> {
					String text = texts.get(place.slot().id());
					if (text != null && !text.isBlank()) {
						write(place, text.strip());
					}
				}
			}
		}
	}

	void write(OutputStream out) throws IOException {
		word.write(out);
	}

	@Override
	public void close() throws IOException {
		word.close();
	}

	// Reading

	private void read() {
		List<Line> lines = new ArrayList<>();
		collect(word.getBodyElements(), null, lines);
		boolean locked = false;
		String lastText = "";
		String beforeLast = "";
		int number = 0;
		for (int index = 0; index < lines.size(); index++) {
			Line line = lines.get(index);
			String text = line.paragraph().getText().strip();
			number++;
			String plain = plain(text);
			boolean contents = CONTENTS_LINE.matcher(line.paragraph().getText()).find();
			if (!contents && !text.isEmpty()) {
				if (LOCKING.matcher(plain).find()) {
					locked = true;
				}
				else if (locked && (PART.matcher(text).matches() && !plain.startsWith("f") || UNLOCKING.matcher(plain).matches())) {
					locked = false;
					// What surrounds a place to fill never carries the words of a locked part.
					lastText = "";
					beforeLast = "";
				}
			}
			if (contents) {
				continue;
			}
			boolean signing = SIGNING_PLACE.matcher(plain).matches();
			String around = around(beforeLast, lastText, line);
			readBoxes(line.paragraph(), number, text, around, locked);
			boolean filled = false;
			if (!text.isEmpty()) {
				Matcher dots = DOTS.matcher(line.paragraph().getText());
				int occurrence = 0;
				List<XWPFParagraph> following = occurrence(line.paragraph()) == 1 ? followingDots(lines, index) : List.of();
				while (dots.find()) {
					add(new Slot("t" + number + "." + occurrence, Kind.TEXT, label(around + " " + text), locked || signing),
							line.paragraph(), Way.DOTS, occurrence, following);
					occurrence++;
					filled = true;
				}
				index += following.size();
				number += following.size();
			}
			if (!filled && text.isEmpty() && !hasBox(line.paragraph())) {
				if (line.cellLabel() != null) {
					add(new Slot("t" + number + ".0", Kind.TEXT, label(line.cellLabel()), locked), line.paragraph(), Way.BLANK, 0,
							List.of());
				}
				// Only the first blank line after a label: the others are spacing.
				else if (LABEL_END.matcher(lastText).find() && index > 0 && !lines.get(index - 1).paragraph().getText().isBlank()) {
					add(new Slot("t" + number + ".0", Kind.TEXT, label(beforeLast + " " + lastText), locked), line.paragraph(),
							Way.BLANK, 0, List.of());
				}
			}
			else if (!filled && LABEL_END.matcher(text).find() && text.length() <= 160 && !hasBox(line.paragraph())
					&& !nextIsBlank(lines, index) && !nextIsDots(lines, index)) {
				add(new Slot("t" + number + ".0", Kind.TEXT, label(lastText + " " + text), locked), line.paragraph(),
						Way.AFTER_LABEL, 0, List.of());
			}
			if (!text.isEmpty()) {
				beforeLast = lastText;
				lastText = text;
			}
		}
	}

	private static int occurrence(XWPFParagraph paragraph) {
		Matcher dots = DOTS.matcher(paragraph.getText());
		int count = 0;
		while (dots.find()) {
			count++;
		}
		return count;
	}

	/** Lines holding nothing but dots, that continue the line before them: one answer for all. */
	private static List<XWPFParagraph> followingDots(List<Line> lines, int index) {
		List<XWPFParagraph> following = new ArrayList<>();
		if (!onlyDots(lines.get(index).paragraph())) {
			return following;
		}
		for (int next = index + 1; next < lines.size() && onlyDots(lines.get(next).paragraph()); next++) {
			following.add(lines.get(next).paragraph());
		}
		return following;
	}

	private static boolean onlyDots(XWPFParagraph paragraph) {
		String text = paragraph.getText().strip();
		return !text.isEmpty() && DOTS.matcher(text).replaceAll("").isBlank();
	}

	private static boolean nextIsBlank(List<Line> lines, int index) {
		return index + 1 < lines.size() && lines.get(index + 1).paragraph().getText().isBlank()
				&& lines.get(index + 1).cellLabel() == null;
	}

	private static boolean nextIsDots(List<Line> lines, int index) {
		return index + 1 < lines.size() && onlyDots(lines.get(index + 1).paragraph());
	}

	private void readBoxes(XWPFParagraph paragraph, int number, String text, String around, boolean locked) {
		int fieldBoxes = paragraph.getCTP().selectPath(CHECK_BOXES).length;
		for (int occurrence = 0; occurrence < fieldBoxes; occurrence++) {
			add(new Slot("c" + number + "." + occurrence, Kind.CHECK,
					label(boxLabel(text, occurrence, fieldBoxes, around)), locked), paragraph, Way.FIELD_BOX, occurrence, List.of());
		}
		int characters = 0;
		for (char character : boxCharacters(paragraph)) {
			if (character == BOX || character == TEXT_BOX) {
				add(new Slot("c" + number + "." + (fieldBoxes + characters), Kind.CHECK, label(around + " " + text), locked),
						paragraph, Way.CHARACTER_BOX, characters, List.of());
				characters++;
			}
		}
	}

	private static String boxLabel(String text, int occurrence, int count, String around) {
		if (count > 1) {
			return around + " [case " + (occurrence + 1) + " sur " + count + "] " + text;
		}
		return around + " " + text;
	}

	private static boolean hasBox(XWPFParagraph paragraph) {
		return paragraph.getCTP().selectPath(CHECK_BOXES).length > 0 || !boxCharacters(paragraph).isEmpty();
	}

	private static List<Character> boxCharacters(XWPFParagraph paragraph) {
		List<Character> boxes = new ArrayList<>();
		for (XWPFRun run : paragraph.getRuns()) {
			String font = run.getFontFamily();
			String text = run.text();
			text.chars().filter((c) -> c == TEXT_BOX).forEach((c) -> boxes.add(TEXT_BOX));
			if (font != null && font.toLowerCase().contains("wingdings") || text.strip().equals(String.valueOf(BOX))
					|| text.stripLeading().startsWith(BOX + " ")) {
				text.chars().filter((c) -> c == BOX).forEach((c) -> boxes.add(BOX));
			}
		}
		return boxes;
	}

	private void add(Slot slot, XWPFParagraph paragraph, Way way, int occurrence, List<XWPFParagraph> following) {
		slots.add(slot);
		places.add(new Place(slot, paragraph, way, occurrence, following));
	}

	private static String around(String beforeLast, String last, Line line) {
		return line.cellLabel() != null ? line.cellLabel() : (beforeLast + " " + last).strip();
	}

	private static String label(String text) {
		String clean = text.replaceAll("[.…_]{3,}", "…").replaceAll("\\s+", " ").strip();
		return clean.length() <= MAX_LABEL ? clean : "…" + clean.substring(clean.length() - MAX_LABEL);
	}

	/** In lower case, without accents: what the words are compared on. */
	private static String plain(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase();
	}

	/**
	 * @param cellLabel for an empty cell of a table, its row and column
	 */
	private record Line(XWPFParagraph paragraph, String cellLabel) {
	}

	private static void collect(List<IBodyElement> elements, String label, List<Line> lines) {
		// One place per empty cell, however many blank lines it holds; a cell with text asks nothing more.
		boolean asked = label == null || elements.stream()
			.anyMatch((element) -> element instanceof XWPFParagraph paragraph && !paragraph.getText().isBlank());
		for (IBodyElement element : elements) {
			if (element instanceof XWPFParagraph paragraph) {
				lines.add(new Line(paragraph, !asked && paragraph.getText().isBlank() ? label : null));
				asked = asked || paragraph.getText().isBlank();
			}
			else if (element instanceof XWPFTable table) {
				collect(table, lines);
			}
		}
	}

	private static void collect(XWPFTable table, List<Line> lines) {
		List<XWPFTableRow> rows = table.getRows();
		for (int rowIndex = 0; rowIndex < rows.size(); rowIndex++) {
			List<XWPFTableCell> cells = rows.get(rowIndex).getTableCells();
			for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
				XWPFTableCell cell = cells.get(cellIndex);
				String first = cellIndex == 0 ? "" : cells.getFirst().getText().strip();
				List<XWPFTableCell> header = rowIndex == 0 ? List.of() : rows.getFirst().getTableCells();
				String column = cellIndex < header.size() ? header.get(cellIndex).getText().strip() : "";
				String label = (first + " / " + column).replaceAll("^ / | / $", "");
				// A table of one cell is a frame around text, and a blank without any header asks nothing.
				collect(cell.getBodyElements(), cells.size() == 1 || label.isBlank() ? null : "Tableau : " + label, lines);
			}
		}
	}

	// Writing

	private void write(Place place, String text) {
		XWPFParagraph paragraph = place.paragraph();
		switch (place.way()) {
			case DOTS -> {
				replaceDots(paragraph, place.occurrence(), text);
				place.following().forEach(FormDocument::empty);
			}
			case BLANK -> paragraph.createRun().setText(text);
			case AFTER_LABEL -> {
				XWPFRun run = paragraph.createRun();
				run.setBold(false);
				run.setText(" " + text);
			}
			default -> throw new IllegalStateException(place.way().name());
		}
	}

	private static void empty(XWPFParagraph paragraph) {
		for (int index = paragraph.getRuns().size() - 1; index >= 0; index--) {
			paragraph.removeRun(index);
		}
	}

	/** Puts the text in place of the dots, in the first run they touch; the runs around keep their format. */
	private static void replaceDots(XWPFParagraph paragraph, int occurrence, String text) {
		List<XWPFRun> runs = paragraph.getRuns();
		StringBuilder all = new StringBuilder();
		runs.forEach((run) -> all.append(run.text()));
		Matcher dots = DOTS.matcher(all);
		for (int found = 0; found <= occurrence; found++) {
			if (!dots.find()) {
				return;
			}
		}
		int start = dots.start();
		int end = dots.end();
		int offset = 0;
		boolean written = false;
		for (XWPFRun run : runs) {
			String content = run.text();
			int from = Math.max(start - offset, 0);
			int to = Math.min(end - offset, content.length());
			if (from < to) {
				String replaced = content.substring(0, from) + (written ? "" : text) + content.substring(to);
				setText(run, replaced);
				written = true;
			}
			offset += content.length();
		}
	}

	private static void setText(XWPFRun run, String text) {
		while (run.getCTR().sizeOfTArray() > 0) {
			run.getCTR().removeT(0);
		}
		run.setText(text);
	}

	private static void tick(Place place, boolean ticked) {
		if (place.way() == Way.FIELD_BOX) {
			XmlObject box = place.paragraph().getCTP().selectPath(CHECK_BOXES)[place.occurrence()];
			try (XmlCursor cursor = box.newCursor()) {
				QName checked = new QName(W, "checked");
				if (cursor.toChild(checked)) {
					cursor.removeXml();
				}
			}
			try (XmlCursor cursor = box.newCursor()) {
				cursor.toEndToken();
				cursor.beginElement(checked());
				cursor.insertAttributeWithValue(new QName(W, "val"), ticked ? "1" : "0");
			}
			return;
		}
		int seen = 0;
		for (XWPFRun run : place.paragraph().getRuns()) {
			String text = run.text();
			for (int index = 0; index < text.length(); index++) {
				char character = text.charAt(index);
				if (character == BOX || character == TICKED_BOX || character == TEXT_BOX || character == TICKED_TEXT_BOX) {
					if (seen == place.occurrence()) {
						boolean textBox = character == TEXT_BOX || character == TICKED_TEXT_BOX;
						char mark = textBox ? (ticked ? TICKED_TEXT_BOX : TEXT_BOX) : ticked ? TICKED_BOX : BOX;
						setText(run, text.substring(0, index) + mark + text.substring(index + 1));
						return;
					}
					seen++;
				}
			}
		}
	}

	private static QName checked() {
		return new QName(W, "checked");
	}

}
