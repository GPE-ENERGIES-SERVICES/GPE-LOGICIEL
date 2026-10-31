package eu.gpefrance.tenders.document;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.PDXObject;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Component;

import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the text of a scanned page with Tesseract: the page is drawn as an
 * image by PDFBox, Tesseract gives each word with its position and its
 * confidence (TSV output), positions are turned back into points of the
 * page so that tables can be rebuilt as in a digital PDF. Nothing is sent
 * outside: Tesseract runs on the server, like LibreOffice.
 */
@Component
public class OcrEngine {

	/** Pages larger than an A3 sheet are plans: never recognised. */
	private static final float MAX_AREA = PDRectangle.A3.getWidth() * PDRectangle.A3.getHeight() * 1.1f;

	private static final JsonMapper JSON = JsonMapper.builder().build();

	/** Vertical rules of a table, read as characters. */
	private static final Pattern RULES = Pattern.compile("[|¦│┃{}\\[\\]]");

	/** What the orientation detection of Tesseract says: the turn, clockwise, that puts the text upright. */
	private static final Pattern ROTATE = Pattern.compile("Rotate: (\\d+)");

	private static final Pattern ORIENTATION_CONFIDENCE = Pattern.compile("Orientation confidence: ([\\d.]+)");

	/** Bytes of drawing below which a page without text nor image is blank: a rule, a frame, a page number. */
	private static final int BLANK_CONTENT = 4096;

	/** Below this, the orientation found is a guess: the image is left as it is. */
	static final double MIN_ORIENTATION_CONFIDENCE = 2;

	/** What is read on a blank page: nothing, without running Tesseract. */
	public static final OcrPage BLANK = new OcrPage("", List.of(), 0);

	private final OcrProperties properties;

	public OcrEngine(OcrProperties properties) {
		this.properties = properties;
	}

	public int maxPages() {
		return properties.maxPages();
	}

	/**
	 * A word read on a page, in points from the top left of the page.
	 * @param y bottom of the word, as the baseline of a digital PDF
	 * @param size height of the letters of its line
	 * @param confidence what Tesseract gives, from 0 to 100
	 */
	public record OcrWord(String text, float x0, float x1, float y, float size, float confidence) {
	}

	/**
	 * @param text the text of the page, line by line
	 * @param confidence mean confidence of its words, from 0 to 100
	 */
	public record OcrPage(String text, List<OcrWord> words, double confidence) {
	}

	/**
	 * @param pageNumber the page, from 1
	 * @return the page read, {@link #BLANK} for a blank page, or null for a page too large to be anything
	 * but a plan
	 * @throws OcrUnavailableException when Tesseract cannot be run
	 * @throws IOException when the page cannot be drawn or read
	 */
	public OcrPage recognise(Path pdf, int pageNumber) throws IOException {
		Path image = null;
		Path output = null;
		try (PDDocument document = Loader.loadPDF(pdf.toFile())) {
			PDPage page = document.getPage(pageNumber - 1);
			PDRectangle box = page.getCropBox();
			if (box.getWidth() * box.getHeight() > MAX_AREA) {
				return null;
			}
			if (blank(page)) {
				return BLANK;
			}
			BufferedImage rendered = new PDFRenderer(document).renderImageWithDPI(pageNumber - 1, properties.dpi(),
					ImageType.GRAY);
			image = Files.createTempFile("ocr-page", ".png");
			output = Files.createTempFile("ocr-words", ".tsv");
			ImageIO.write(rendered, "png", image.toFile());
			run(image, output);
			return parse(Files.readString(output, StandardCharsets.UTF_8), 72f / properties.dpi());
		}
		finally {
			if (image != null) {
				Files.deleteIfExists(image);
			}
			if (output != null) {
				Files.deleteIfExists(output);
			}
		}
	}

	/**
	 * A page without text, without any image and almost nothing drawn: a
	 * blank page, nothing to read. A scanned page always holds its image.
	 */
	static boolean blank(PDPage page) throws IOException {
		if (hasImage(page.getResources(), 0)) {
			return false;
		}
		try (InputStream contents = page.getContents()) {
			return contents.readNBytes(BLANK_CONTENT).length < BLANK_CONTENT;
		}
	}

	private static boolean hasImage(PDResources resources, int depth) throws IOException {
		if (resources == null || depth > 5) {
			return false;
		}
		for (COSName name : resources.getXObjectNames()) {
			PDXObject object = resources.getXObject(name);
			if (object instanceof PDImageXObject
					|| object instanceof PDFormXObject form && hasImage(form.getResources(), depth + 1)) {
				return true;
			}
		}
		return false;
	}

	private void run(Path image, Path output) throws IOException {
		if (execute(List.of(properties.command(), image.toString(), "stdout", "-l", properties.language(), "--psm",
				String.valueOf(properties.pageSegmentation()), "-c", "preserve_interword_spaces=1", "tsv"), output) != 0) {
			throw new IOException("Reconnaissance de caractères en échec");
		}
	}

	/**
	 * What Tesseract finds of the text of an image from the shape of its
	 * letters (its orientation detection, --psm 0), quickly, without reading it.
	 * @throws OcrUnavailableException when Tesseract cannot be run
	 */
	public Orientation orientation(Path image) throws IOException {
		Path output = Files.createTempFile("ocr-orientation", ".txt");
		try {
			if (execute(List.of(properties.command(), image.toString(), "stdout", "--psm", "0"), output) != 0) {
				return new Orientation(0, false);
			}
			String osd = Files.readString(output, StandardCharsets.UTF_8);
			return new Orientation(parseTurn(osd), ROTATE.matcher(osd).find());
		}
		finally {
			Files.deleteIfExists(output);
		}
	}

	/**
	 * @param turn the quarter turn, clockwise, that puts the text upright; 0
	 * when it is upright, or too short or unclear to tell
	 * @param text whether enough letters were found to tell: an image without
	 * it is a photo or a drawing, not a page
	 */
	public record Orientation(int turn, boolean text) {
	}

	/**
	 * Reads the orientation found by Tesseract; a guess below
	 * {@link #MIN_ORIENTATION_CONFIDENCE} is ignored.
	 */
	static int parseTurn(String osd) {
		Matcher turn = ROTATE.matcher(osd);
		Matcher confidence = ORIENTATION_CONFIDENCE.matcher(osd);
		if (!turn.find() || !confidence.find() || Double.parseDouble(confidence.group(1)) < MIN_ORIENTATION_CONFIDENCE) {
			return 0;
		}
		int degrees = Integer.parseInt(turn.group(1));
		return degrees % 90 == 0 ? Math.floorMod(degrees, 360) : 0;
	}

	/**
	 * Runs Tesseract, its output written to the given file.
	 * @return its exit code
	 */
	private int execute(List<String> command, Path output) throws IOException {
		Process process;
		try {
			ProcessBuilder builder = new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD)
				.redirectOutput(output.toFile());
			// One thread each: several pages and documents are read at the same time, which is faster.
			builder.environment().put("OMP_THREAD_LIMIT", "1");
			process = builder.start();
		}
		catch (IOException ex) {
			throw new OcrUnavailableException(ex);
		}
		try {
			if (!process.waitFor(properties.timeout().toMillis(), TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				throw new IOException("Reconnaissance de caractères trop longue, abandonnée");
			}
			return process.exitValue();
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IOException("Reconnaissance de caractères interrompue", ex);
		}
	}

	/**
	 * Reads the TSV output of Tesseract: one row per word (level 5) with its
	 * box in pixels and its confidence, grouped into lines.
	 * @param scale points per pixel
	 */
	static OcrPage parse(String tsv, float scale) {
		Map<String, List<String[]>> lines = new LinkedHashMap<>();
		for (String row : tsv.split("\\R")) {
			String[] cells = row.split("\t", -1);
			if (cells.length < 12 || !cells[0].equals("5") || cells[11].isBlank()) {
				continue;
			}
			lines.computeIfAbsent(cells[2] + "/" + cells[3] + "/" + cells[4], key -> new ArrayList<>()).add(cells);
		}
		List<OcrWord> words = new ArrayList<>();
		StringBuilder text = new StringBuilder();
		double confidence = 0;
		for (List<String[]> line : lines.values()) {
			// One baseline per line read: letters going down (g, p) would otherwise move a word to the next line.
			float height = 0;
			float bottom = 0;
			for (String[] cells : line) {
				if (RULES.matcher(cells[11]).replaceAll("").isBlank()) {
					// A rule runs over the whole height of the row: it says nothing of the letters.
					continue;
				}
				height = Math.max(height, Integer.parseInt(cells[9]) * scale);
				bottom = Math.max(bottom, (Integer.parseInt(cells[7]) + Integer.parseInt(cells[9])) * scale);
			}
			List<String> texts = new ArrayList<>();
			for (String[] cells : line) {
				// The rules of a table are read as bars: they are no text, and would join the columns.
				String word = RULES.matcher(cells[11]).replaceAll("").strip();
				if (word.isEmpty()) {
					continue;
				}
				float left = Integer.parseInt(cells[6]) * scale;
				float conf = (float) Double.parseDouble(cells[10]);
				words.add(new OcrWord(word, left, left + Integer.parseInt(cells[8]) * scale, bottom,
						Math.max(height, 4f), Math.max(conf, 0f)));
				texts.add(word);
				confidence += Math.max(conf, 0f);
			}
			if (!texts.isEmpty()) {
				text.append(String.join(" ", texts)).append('\n');
			}
		}
		return new OcrPage(text.toString().strip(), List.copyOf(words), words.isEmpty() ? 0 : confidence / words.size());
	}

	/** The words of a page as stored with it. */
	public static String toJson(List<OcrWord> words) {
		return JSON.writeValueAsString(words);
	}

	/** The words stored with a page, none when they cannot be read. */
	public static List<OcrWord> fromJson(String json) {
		if (json == null) {
			return List.of();
		}
		try {
			return JSON.readValue(json, new TypeReference<List<OcrWord>>() {
			});
		}
		catch (JacksonException ex) {
			return List.of();
		}
	}

	/**
	 * Tesseract is not installed or cannot be run: the scanned pages stay
	 * unread, nothing else changes.
	 */
	public static class OcrUnavailableException extends IOException {

		OcrUnavailableException(IOException cause) {
			super("Reconnaissance de caractères indisponible (Tesseract introuvable)", cause);
		}

	}

}
