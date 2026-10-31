package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.io.StringWriter;
import java.io.Writer;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.document.PdfTable.Cell;
import eu.gpefrance.tenders.document.PdfTable.Column;
import eu.gpefrance.tenders.document.PdfTable.Row;

/**
 * Rebuilds the tables of a digital PDF (price schedules: DPGF, BPU, DQE) from
 * the position of each word, with Apache PDFBox: words are grouped into lines
 * by their height on the page, the columns come from the header (on one or
 * several lines), and their edges are set where no value lies, between the
 * header labels. Each word then goes to the column it lies in; a word across
 * an edge, or two values far apart in one number column, make the table
 * ambiguous instead of being guessed. A table goes on over the next pages,
 * with or without its header repeated. Scanned pages have no words: nothing
 * is rebuilt there. The text extraction of the document is not changed.
 */
@Component
public class PdfTableExtractor {

	private static final int MAX_PAGES = 300;

	private static final int MAX_WORDS_PER_PAGE = 20_000;

	private static final int MAX_TABLES = 50;

	private static final int MAX_PROBLEMS = 20;

	/** Below this confidence of the OCR, a number is doubtful. */
	private static final float MIN_CONFIDENCE = 80;

	/** Words of a header cell: names of the columns of a price schedule. */
	private static final Pattern HEADER_WORD = Pattern.compile("designation|libelle|description|intitule|nature|"
			+ "ouvrage|prestation|^u$|^un$|unite|quantite|\\bqte\\b|\\bquant|^q$|prix|\\bp ?u\\b|montant|total|"
			+ "^n ?°|^no$|numero|article|^art\\b|^ref|reference|^code|^poste|^item|^repere");

	/** Header cells of the values: quantities, prices, amounts. */
	private static final Pattern VALUE_HEADER = Pattern.compile("quantite|\\bqte\\b|\\bquant|^q$|prix|\\bp ?u\\b|"
			+ "montant|total");

	/** Header cells of text: designations, prices written in words. */
	private static final Pattern TEXT_HEADER = Pattern.compile("designation|libelle|description|intitule|nature|"
			+ "ouvrage|prestation|lettres");

	private static final Pattern REFERENCE_HEADER = Pattern.compile("^n\\b|^n ?°|^no$|numero|^art|article|^ref|"
			+ "reference|^code|^poste|^item|^repere");

	private static final Pattern TOTAL_LABEL = Pattern.compile("^(sous ?)?total\\b|montant total|\\btva\\b|"
			+ "\\bt ?v ?a\\b|\\bt ?t ?c\\b|net a payer|\\breport\\b");

	/** Small linking words of a sentence, never a unit. */
	private static final Pattern SMALL_WORD = Pattern.compile("le|la|les|de|du|des|et|en|à|au|aux|un|une|sur|par|"
			+ "pour|d'|l'");

	private static final Pattern AMOUNT_HEADER = Pattern.compile("montant|total");

	/** A line ending with one of these words goes on in the next one. */
	private static final Pattern LINKING_WORD = Pattern.compile("(?iu)(?:^|\\s)(de|du|des|d'|d’|sur|sous|et|ou|en|"
			+ "à|a|au|aux|avec|pour|par|le|la|les|l'|l’|un|une|y|compris|dans|entre|type)$");

	/** What may follow a number in its cell. */
	private static final Pattern UNIT = Pattern.compile("(?i)€|eur|euros|h\\.?t\\.?|t\\.?t\\.?c\\.?|%");

	/**
	 * @return the tables found, page by page; none for a PDF without words
	 * @throws IOException when the file is not a readable PDF
	 */
	public List<PdfTable> extract(Path pdf) throws IOException {
		List<List<Line>> pages = new ArrayList<>();
		try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
			WordCollector collector = new WordCollector();
			int count = Math.min(document.getNumberOfPages(), MAX_PAGES);
			for (int page = 1; page <= count; page++) {
				pages.add(lines(collector.words(document, page)));
			}
		}
		return tables(pages, Set.of());
	}

	/**
	 * The tables of a PDF whose pages without text were read by OCR: their
	 * words come from the reading stored with the page, the others from the
	 * PDF. A table on a page read by OCR is never reliable by itself.
	 * @param stored the pages of the document as stored
	 */
	public List<PdfTable> extract(Path pdf, List<DocumentPage> stored) throws IOException {
		List<List<Line>> pages = new ArrayList<>();
		Set<Integer> recognised = new HashSet<>();
		try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
			WordCollector collector = new WordCollector();
			int count = Math.min(document.getNumberOfPages(), MAX_PAGES);
			for (int page = 1; page <= count; page++) {
				int number = page;
				DocumentPage ocr = stored.stream()
					.filter(item -> item.getPageNumber() == number && item.getMethod() == PageMethod.OCR)
					.findFirst()
					.orElse(null);
				if (ocr != null && ocr.getWords() != null) {
					recognised.add(page);
					pages.add(lines(OcrEngine.fromJson(ocr.getWords())
						.stream()
						.map(word -> new Word(word.text(), word.x0(), word.x1(), word.y(), word.size(),
								word.confidence()))
						.toList()));
				}
				else {
					pages.add(lines(collector.words(document, page)));
				}
			}
		}
		return tables(pages, recognised);
	}

	/**
	 * The tables of pages already cut into lines, the first page being 1.
	 * @param recognised the pages read by OCR
	 */
	static List<PdfTable> tables(List<List<Line>> pages, Set<Integer> recognised) {
		List<PdfTable> tables = new ArrayList<>();
		Builder current = null;
		for (int index = 0; index < pages.size() && tables.size() < MAX_TABLES; index++) {
			int page = index + 1;
			List<Line> lines = pages.get(index);
			Header header = header(lines);
			if (header != null) {
				boolean repeated = current != null && current.lastPage == page - 1
						&& current.sameLabels(header.columns());
				if (!repeated) {
					if (current != null) {
						tables.add(current.build());
					}
					List<Line> data = lines.subList(header.end(), lines.size());
					// Edges from the lines holding values: a long title would put them between its own words.
					List<Line> values = data.stream()
						.filter(line -> line.words().stream().anyMatch(PdfTableExtractor::isNumber))
						.toList();
					current = new Builder(page, edges(header.columns(), values.isEmpty() ? data : values),
							header.lines(), recognised);
				}
				current.add(page, lines.subList(header.end(), lines.size()));
			}
			else if (current != null && current.lastPage == page - 1 && hasValues(lines)) {
				// The table goes on without its header.
				current.add(page, lines);
			}
		}
		if (current != null && tables.size() < MAX_TABLES) {
			tables.add(current.build());
		}
		return tables.stream().filter(table -> !table.rows().isEmpty()).toList();
	}

	// Words and lines

	/**
	 * @param y baseline, from the top of the page
	 * @param size height of the letters, about the font size
	 * @param confidence 100 for the text of a digital PDF, what the OCR gives otherwise
	 */
	record Word(String text, float x0, float x1, float y, float size, float confidence) {

		float center() {
			return (x0 + x1) / 2;
		}

	}

	record Line(float y, List<Word> words) {
	}

	/**
	 * Collects the words of a page with their position: letters are joined
	 * until a space or a gap, rotated text is left out.
	 */
	private static final class WordCollector extends PDFTextStripper {

		private List<Word> words;

		WordCollector() {
			setSortByPosition(true);
		}

		List<Word> words(PDDocument document, int page) throws IOException {
			words = new ArrayList<>();
			setStartPage(page);
			setEndPage(page);
			Writer ignored = new StringWriter();
			writeText(document, ignored);
			return words;
		}

		@Override
		protected void writeString(String text, List<TextPosition> positions) {
			StringBuilder current = new StringBuilder();
			float x0 = 0;
			float x1 = 0;
			float y = 0;
			float size = 0;
			for (TextPosition position : positions) {
				String letter = position.getUnicode();
				if (position.getDir() != 0 || letter == null || words.size() >= MAX_WORDS_PER_PAGE) {
					continue;
				}
				float left = position.getXDirAdj();
				boolean gap = !current.isEmpty()
						&& (left - x1 > Math.max(1.5f, position.getWidthOfSpace() * 0.6f)
								|| Math.abs(position.getYDirAdj() - y) > size * 0.5f);
				if (letter.isBlank() || gap) {
					add(current, x0, x1, y, size);
					current.setLength(0);
					if (letter.isBlank()) {
						continue;
					}
				}
				if (current.isEmpty()) {
					x0 = left;
					y = position.getYDirAdj();
					size = Math.max(position.getFontSizeInPt(), position.getHeightDir());
				}
				current.append(letter);
				x1 = left + position.getWidthDirAdj();
			}
			add(current, x0, x1, y, size);
		}

		private void add(StringBuilder text, float x0, float x1, float y, float size) {
			if (!text.isEmpty()) {
				words.add(new Word(text.toString(), x0, x1, y, size <= 0 ? 10 : size, 100));
			}
		}

	}

	/** Words of a page grouped into lines by their baseline, each sorted from left to right. */
	static List<Line> lines(List<Word> words) {
		List<Word> sorted = new ArrayList<>(words);
		sorted.sort(Comparator.comparingDouble(Word::y).thenComparingDouble(Word::x0));
		List<Line> lines = new ArrayList<>();
		List<Word> current = new ArrayList<>();
		float y = 0;
		for (Word word : sorted) {
			if (!current.isEmpty() && Math.abs(word.y() - y) > Math.max(2f, word.size() * 0.4f)) {
				lines.add(line(current));
				current = new ArrayList<>();
			}
			if (current.isEmpty()) {
				y = word.y();
			}
			current.add(word);
		}
		if (!current.isEmpty()) {
			lines.add(line(current));
		}
		return lines;
	}

	private static Line line(List<Word> words) {
		List<Word> sorted = new ArrayList<>(words);
		sorted.sort(Comparator.comparingDouble(Word::x0));
		return new Line((float) sorted.stream().mapToDouble(Word::y).average().orElse(0), List.copyOf(sorted));
	}

	/**
	 * Words of a line close enough to be read together: a label of several
	 * words, a number written with spaces ("1 250,50").
	 */
	static List<Word> phrases(Line line) {
		List<Word> phrases = new ArrayList<>();
		Word current = null;
		for (Word word : line.words()) {
			if (current != null && word.x0() - current.x1() <= Math.max(current.size(), word.size()) * 0.8f) {
				current = new Word(current.text() + " " + word.text(), current.x0(), word.x1(), current.y(),
						current.size(), Math.min(current.confidence(), word.confidence()));
			}
			else {
				if (current != null) {
					phrases.add(current);
				}
				current = word;
			}
		}
		if (current != null) {
			phrases.add(current);
		}
		return phrases;
	}

	// Header

	/**
	 * @param end index of the first line under the header
	 * @param lines how many lines the header takes
	 */
	private record Header(List<Column> columns, int end, int lines) {
	}

	/**
	 * The first line naming at least two columns, one of them a value
	 * (quantity, price, amount), with the lines of text just above and below
	 * that complete its labels ("Prix" over "unitaire HT").
	 */
	private static Header header(List<Line> lines) {
		for (int i = 0; i < lines.size(); i++) {
			List<Word> cells = phrases(lines.get(i));
			long named = cells.stream().filter(cell -> HEADER_WORD.matcher(normalize(cell.text())).find()).count();
			boolean values = cells.stream().anyMatch(cell -> VALUE_HEADER.matcher(normalize(cell.text())).find());
			if (named < 2 || !values) {
				continue;
			}
			List<Word> labels = new ArrayList<>(cells);
			int first = i;
			int last = i;
			float size = (float) cells.stream().mapToDouble(Word::size).max().orElse(10);
			if (i > 0 && isHeaderPart(lines.get(i - 1), lines.get(i), size)) {
				first = i - 1;
				labels = merge(phrases(lines.get(i - 1)), labels);
			}
			while (last + 1 < lines.size() && last - first < 3 && isHeaderPart(lines.get(last + 1), lines.get(last), size)) {
				last++;
				labels = merge(labels, phrases(lines.get(last)));
			}
			List<Column> columns = labels.stream()
				.sorted(Comparator.comparingDouble(Word::x0))
				.map(label -> new Column(label.text(), label.x0(), label.x1()))
				.toList();
			return new Header(columns, last + 1, last - first + 1);
		}
		return null;
	}

	/** A line of text next to the header, close to it and without any number. */
	private static boolean isHeaderPart(Line line, Line next, float size) {
		return Math.abs(next.y() - line.y()) <= size * 1.8f
				&& line.words().stream().noneMatch(word -> FrenchNumbers.parse(word.text()) != null
						&& !word.text().matches("\\(?[0-9]\\)?"));
	}

	/**
	 * Labels of two header lines: those one above the other are joined, the
	 * others are columns of their own.
	 */
	private static List<Word> merge(List<Word> above, List<Word> below) {
		List<Word> merged = new ArrayList<>();
		List<Word> remaining = new ArrayList<>(below);
		for (Word top : above) {
			Word joined = top;
			for (Word bottom : List.copyOf(remaining)) {
				if (bottom.x0() < joined.x1() && bottom.x1() > joined.x0()) {
					joined = new Word(joined.text() + " " + bottom.text(), Math.min(joined.x0(), bottom.x0()),
							Math.max(joined.x1(), bottom.x1()), joined.y(), joined.size(),
							Math.min(joined.confidence(), bottom.confidence()));
					remaining.remove(bottom);
				}
			}
			merged.add(joined);
		}
		merged.addAll(remaining);
		return merged;
	}

	/**
	 * Sets the edge between two header labels where the fewest words of the
	 * lines under it lie, in the middle of the widest empty space: text runs
	 * to the right of its label, numbers are often aligned to the right.
	 */
	static List<Column> edges(List<Column> labels, List<Line> data) {
		List<Word> words = data.stream().flatMap(line -> line.words().stream()).toList();
		List<Column> columns = new ArrayList<>();
		float left = Float.NEGATIVE_INFINITY;
		for (int i = 0; i < labels.size(); i++) {
			float right = Float.POSITIVE_INFINITY;
			if (i + 1 < labels.size()) {
				right = edge(labels.get(i).right(), labels.get(i + 1).left(), words);
			}
			columns.add(new Column(labels.get(i).label(), left, right));
			left = right;
		}
		return columns;
	}

	private static float edge(float from, float to, List<Word> words) {
		if (to <= from) {
			return (from + to) / 2;
		}
		int best = Integer.MAX_VALUE;
		float runStart = from;
		float bestStart = from;
		float bestEnd = from;
		int previous = -1;
		for (float x = from; x <= to; x += 0.5f) {
			float at = x;
			int crossing = (int) words.stream().filter(word -> word.x0() < at && word.x1() > at).count();
			if (crossing != previous) {
				runStart = x;
				previous = crossing;
			}
			if (crossing < best || crossing == best && x - runStart > bestEnd - bestStart) {
				if (crossing < best) {
					best = crossing;
				}
				bestStart = runStart;
				bestEnd = x;
			}
		}
		return (bestStart + bestEnd) / 2;
	}

	private static boolean hasValues(List<Line> lines) {
		return lines.stream()
			.anyMatch(line -> phrases(line).stream().filter(word -> FrenchNumbers.parse(word.text()) != null).count() >= 2);
	}

	static boolean isNumber(Word word) {
		return FrenchNumbers.parse(word.text()) != null;
	}

	static String normalize(String text) {
		return Normalizer.normalize(text, Normalizer.Form.NFD)
			.replaceAll("\\p{M}", "")
			.toLowerCase(Locale.ROOT)
			.replace("°", " ° ")
			.replaceAll("[^a-z0-9°]+", " ")
			.strip();
	}

	/**
	 * Puts each word of each line in its column, and notes what is ambiguous.
	 */
	private static final class Builder {

		private final List<Integer> pages = new ArrayList<>();

		private final List<Column> columns;

		private final int headerLines;

		private final List<Row> rows = new ArrayList<>();

		private final List<String> problems = new ArrayList<>();

		private int ambiguous;

		private int lastPage;

		/** Height of the letters of the table, for the distances. */
		private float size = 1;

		/** Pages read by OCR: their tables are never reliable by themselves. */
		private final Set<Integer> recognised;

		Builder(int page, List<Column> columns, int headerLines, Set<Integer> recognised) {
			this.recognised = recognised;
			this.columns = columns;
			this.headerLines = headerLines;
			this.lastPage = page;
		}

		boolean sameLabels(List<Column> other) {
			return other.size() == columns.size() && IntStream.range(0, other.size())
				.allMatch(i -> normalize(other.get(i).label()).equals(normalize(columns.get(i).label())));
		}

		void add(int page, List<Line> lines) {
			if (!pages.contains(page)) {
				pages.add(page);
			}
			lastPage = page;
			for (Line line : lines) {
				line.words().forEach(word -> size = Math.max(size, word.size()));
				Row row = row(page, line);
				if (!row.cells().isEmpty()) {
					rows.add(row);
				}
			}
		}

		private Row row(int page, Line line) {
			List<List<Word>> byColumn = new ArrayList<>();
			columns.forEach(column -> byColumn.add(new ArrayList<>()));
			for (Word word : line.words()) {
				byColumn.get(column(word.center())).add(word);
			}
			int textColumn = textColumn();
			boolean oneText = textColumn >= 0 && keepTextTogether(byColumn, line, textColumn);
			List<Boolean> crossing = new ArrayList<>();
			for (int index = 0; index < columns.size(); index++) {
				Column column = columns.get(index);
				// Only values matter: a text running over an edge holds no number to misplace.
				crossing.add(!(oneText && index == textColumn) && byColumn.get(index)
					.stream()
					.anyMatch(word -> isNumber(word) && (word.x0() < column.left() - 1 || word.x1() > column.right() + 1)));
			}
			List<Cell> cells = new ArrayList<>();
			for (int index = 0; index < columns.size(); index++) {
				List<Word> words = byColumn.get(index);
				if (words.isEmpty()) {
					continue;
				}
				String text = String.join(" ", words.stream().map(Word::text).toList());
				boolean several = isValueColumn(index) && severalValues(words);
				// A number read by OCR with a low confidence may be another number.
				Word doubtfulNumber = words.stream()
					.filter(word -> isNumber(word) && word.confidence() < MIN_CONFIDENCE)
					.findFirst()
					.orElse(null);
				boolean doubtful = crossing.get(index) || several || doubtfulNumber != null;
				if (doubtful) {
					String where = "Page " + page + ", ligne à " + Math.round(line.y()) + " pt du haut : « " + text + " » ";
					problem(where + (crossing.get(index) ? "déborde de la colonne « " + columns.get(index).label() + " »"
							: several ? "regroupe plusieurs valeurs dans « " + columns.get(index).label() + " »"
									: "lu par OCR avec une confiance de " + Math.round(doubtfulNumber.confidence()) + " %"));
				}
				cells.add(new Cell(index, text, words.getFirst().x0(), words.getLast().x1(), doubtful));
			}
			return new Row(page, line.y(), List.copyOf(cells));
		}

		/**
		 * Text that belongs to the designation stays in it: a title or a total
		 * label running over the columns ("T.V.A à 20,0 %"), and the words that
		 * go on its sentence past the edge of the column.
		 * @return whether the whole line became one text
		 */
		private boolean keepTextTogether(List<List<Word>> byColumn, Line line, int text) {
			boolean anyNumber = line.words().stream().anyMatch(PdfTableExtractor::isNumber);
			boolean crossing = false;
			for (int index = 0; index < columns.size(); index++) {
				Column column = columns.get(index);
				crossing |= byColumn.get(index)
					.stream()
					.anyMatch(word -> word.x0() < column.left() - 1 || word.x1() > column.right() + 1);
			}
			String label = normalize(String.join(" ", line.words().stream().map(Word::text).toList()));
			boolean total = TOTAL_LABEL.matcher(label).lookingAt();
			if (crossing && !anyNumber || total) {
				// One text: everything but the reference, and the amount of a total, goes to the designation.
				int last = total ? lastValueColumn(byColumn) : -1;
				List<Word> words = new ArrayList<>();
				for (int index = 0; index < columns.size(); index++) {
					boolean keep = index != text && index == last
							|| isReferenceColumn(index) && !byColumn.get(index).isEmpty() && index != text;
					if (!keep) {
						words.addAll(byColumn.get(index));
						byColumn.set(index, new ArrayList<>());
					}
				}
				words.sort(Comparator.comparingDouble(Word::x0));
				byColumn.set(text, words);
				return true;
			}
			// Words close after the designation go on its sentence, unless they are numbers.
			List<Word> designation = byColumn.get(text);
			for (int index = text + 1; index < columns.size() && !designation.isEmpty(); index++) {
				List<Word> next = byColumn.get(index);
				while (!next.isEmpty() && !isNumber(next.getFirst())
						&& goesOnSentence(designation.getLast(), next.getFirst())) {
					designation.add(next.removeFirst());
				}
				if (!next.isEmpty()) {
					break;
				}
			}
			return false;
		}

		/**
		 * A word close to the end of the designation, or a small linking word
		 * a little further in a justified text ("la", "de"), goes on its sentence.
		 */
		private static boolean goesOnSentence(Word last, Word word) {
			float gap = word.x0() - last.x1();
			return gap <= word.size() * 0.5f || gap <= word.size() * 1.5f && SMALL_WORD.matcher(word.text()).matches();
		}

		/**
		 * The amount of a total line: the last column of amounts holding a
		 * number. A rate written in the label ("T.V.A à 20,0 %") is no amount.
		 */
		private int lastValueColumn(List<List<Word>> byColumn) {
			for (int index = columns.size() - 1; index >= 0; index--) {
				if (isValueColumn(index) && AMOUNT_HEADER.matcher(normalize(columns.get(index).label())).find()
						&& byColumn.get(index).stream().anyMatch(PdfTableExtractor::isNumber)) {
					return index;
				}
			}
			return -1;
		}

		private int column(float x) {
			for (int index = 0; index < columns.size(); index++) {
				if (x < columns.get(index).right()) {
					return index;
				}
			}
			return columns.size() - 1;
		}

		/** A column of quantities, prices or amounts, never one of text ("prix en toutes lettres"). */
		private boolean isValueColumn(int index) {
			String label = normalize(columns.get(index).label());
			return VALUE_HEADER.matcher(label).find() && !TEXT_HEADER.matcher(label).find()
					&& !REFERENCE_HEADER.matcher(label).find();
		}

		private boolean isReferenceColumn(int index) {
			return REFERENCE_HEADER.matcher(normalize(columns.get(index).label())).find();
		}

		/** The column of the designations, or -1. */
		private int textColumn() {
			for (int index = 0; index < columns.size(); index++) {
				if (TEXT_HEADER.matcher(normalize(columns.get(index).label())).find()) {
					return index;
				}
			}
			return -1;
		}

		/**
		 * A designation written on several lines, its unit and quantity on the
		 * last one, as in most schedules: the lines of text before a line of
		 * values join it when the text goes on (a next line starting in lower
		 * case or with a bracket, a line ending with a comma or a linking
		 * word). A line with a reference, a title or a total never joins.
		 */
		private List<Row> joinContinuations(List<Row> lines) {
			int text = textColumn();
			if (text < 0) {
				return lines;
			}
			List<Row> joined = new ArrayList<>();
			for (Row row : lines) {
				int absorbed = 0;
				Row current = row;
				float top = row.y();
				while (hasValues(current, text) && !joined.isEmpty()) {
					Row previous = joined.getLast();
					if (!isTextOnly(previous, text) || previous.page() != current.page()
							|| top - previous.y() > size * 2.4f
							|| !goesOn(designation(previous, text), designation(current, text))) {
						break;
					}
					joined.removeLast();
					top = previous.y();
					current = withDesignation(current, text, designation(previous, text) + " "
							+ (designation(current, text) == null ? "" : designation(current, text)),
							previous.cells().getFirst().x0());
					if (++absorbed >= 6) {
						break;
					}
				}
				joined.add(current);
			}
			return joined;
		}

		private boolean hasValues(Row row, int text) {
			return row.cells().stream().anyMatch(cell -> cell.column() != text && !isReferenceColumn(cell.column()))
					&& row.cells().stream().noneMatch(cell -> isReferenceColumn(cell.column()));
		}

		private boolean isTextOnly(Row row, int text) {
			return row.cells().size() == 1 && row.cells().getFirst().column() == text
					&& !TOTAL_LABEL.matcher(normalize(row.cells().getFirst().text())).find();
		}

		private static String designation(Row row, int text) {
			return row.cells().stream().filter(cell -> cell.column() == text).map(Cell::text).findFirst().orElse(null);
		}

		private static Row withDesignation(Row row, int text, String designation, float x0) {
			List<Cell> cells = new ArrayList<>(row.cells().stream().filter(cell -> cell.column() != text).toList());
			Cell old = row.cells().stream().filter(cell -> cell.column() == text).findFirst().orElse(null);
			cells.add(new Cell(text, designation.strip(), Math.min(x0, old == null ? x0 : old.x0()),
					old == null ? x0 : old.x1(), old != null && old.ambiguous()));
			cells.sort(Comparator.comparingInt(Cell::column));
			return new Row(row.page(), row.y(), List.copyOf(cells));
		}

		/** Whether the text of a line goes on in the next one. */
		static boolean goesOn(String before, String after) {
			if (before == null) {
				return false;
			}
			String end = before.strip();
			boolean next = after != null && !after.isBlank()
					&& (Character.isLowerCase(after.strip().charAt(0)) || after.strip().startsWith("("));
			return next || end.endsWith(",") || end.endsWith("-") || LINKING_WORD.matcher(end).find();
		}

		/**
		 * Two values in one column of values: words far apart, or a second
		 * number that is not a group of thousands ("1 250,50" is one number,
		 * "12 15,5" two).
		 */
		private static boolean severalValues(List<Word> words) {
			for (int i = 1; i < words.size(); i++) {
				Word previous = words.get(i - 1);
				Word word = words.get(i);
				if (word.x0() - previous.x1() > Math.max(previous.size(), word.size()) * 1.2f) {
					return true;
				}
				boolean thousands = word.text().matches("\\d{3}([,.]\\d+)?")
						&& FrenchNumbers.parse(previous.text()) != null;
				if (!thousands && !UNIT.matcher(word.text()).matches() && FrenchNumbers.parse(word.text()) != null) {
					return true;
				}
			}
			return false;
		}

		private void problem(String text) {
			ambiguous++;
			if (problems.size() < MAX_PROBLEMS) {
				problems.add(text);
			}
		}

		/**
		 * Values of a column are aligned, to the right or to the left: one far
		 * from the others may belong to the column next to it.
		 */
		private List<Row> checkAlignment(List<Row> rows) {
			List<Row> checked = new ArrayList<>(rows);
			for (int index = 0; index < columns.size(); index++) {
				if (!isValueColumn(index)) {
					continue;
				}
				int column = index;
				List<Cell> values = rows.stream()
					.flatMap(row -> row.cells().stream())
					.filter(cell -> cell.column() == column && FrenchNumbers.parse(cell.text()) != null)
					.toList();
				if (values.size() < 3) {
					continue;
				}
				float right = median(values.stream().map(Cell::x1).toList());
				float left = median(values.stream().map(Cell::x0).toList());
				boolean rightAligned = spread(values.stream().map(Cell::x1).toList(), right) <= spread(
						values.stream().map(Cell::x0).toList(), left);
				for (int r = 0; r < checked.size(); r++) {
					Row row = checked.get(r);
					List<Cell> cells = new ArrayList<>(row.cells());
					for (int c = 0; c < cells.size(); c++) {
						Cell cell = cells.get(c);
						float offset = rightAligned ? Math.abs(cell.x1() - right) : Math.abs(cell.x0() - left);
						if (cell.column() == column && !cell.ambiguous() && FrenchNumbers.parse(cell.text()) != null
								&& offset > size * 1.5f) {
							cells.set(c, new Cell(cell.column(), cell.text(), cell.x0(), cell.x1(), true));
							problem("Page " + row.page() + ", ligne à " + Math.round(row.y()) + " pt du haut : « "
									+ cell.text() + " » n'est pas alignée avec les autres valeurs de « "
									+ columns.get(column).label() + " »");
						}
					}
					checked.set(r, new Row(row.page(), row.y(), List.copyOf(cells)));
				}
			}
			return checked;
		}

		private static float median(List<Float> values) {
			List<Float> sorted = values.stream().sorted().toList();
			return sorted.get(sorted.size() / 2);
		}

		/** Median distance to the median: small when the values are aligned on that edge. */
		private static float spread(List<Float> values, float median) {
			return median(values.stream().map(value -> Math.abs(value - median)).toList());
		}

		PdfTable build() {
			List<Row> rows = checkAlignment(joinContinuations(this.rows));
			boolean values = rows.stream()
				.anyMatch(row -> row.cells().stream().anyMatch(cell -> isValueColumn(cell.column())
						&& FrenchNumbers.parse(cell.text()) != null));
			List<String> all = new ArrayList<>(problems);
			if (ambiguous > problems.size()) {
				all.add("… et " + (ambiguous - problems.size()) + " autres valeurs ambiguës");
			}
			if (columns.size() < 3) {
				all.add("Moins de trois colonnes reconnues dans l'en-tête");
			}
			if (!values) {
				all.add("Aucune valeur chiffrée sous les colonnes de quantités, prix ou montants");
			}
			List<Integer> read = pages.stream().filter(recognised::contains).toList();
			if (!read.isEmpty()) {
				all.addFirst("Page(s) " + read + " lue(s) par reconnaissance de caractères (OCR) : valeurs à vérifier "
						+ "sur le document");
			}
			return new PdfTable(List.copyOf(pages), List.copyOf(columns), headerLines, List.copyOf(rows),
					all.isEmpty(), List.copyOf(all));
		}

	}

}
