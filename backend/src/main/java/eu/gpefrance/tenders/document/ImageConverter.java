package eu.gpefrance.tenders.document;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.document.OcrEngine.Orientation;

/**
 * Turns an image of a consultation file (photo, scan, capture) into a PDF,
 * one page per image, read then like a scanned PDF: its pages by OCR, its
 * tables rebuilt from the words read, never reliable by themselves, and it
 * opens at a page. The image is first turned upright (orientation saved by
 * the camera, then the one Tesseract finds from the letters), laid on white,
 * and reduced when larger than a page read at 300 dpi. The file uploaded is
 * never changed. HEIC photos are decoded by libheif, on the server, like
 * LibreOffice and Tesseract: nothing is sent outside.
 */
@Component
public class ImageConverter {

	public static final Set<String> FORMATS = Set.of("jpg", "jpeg", "png", "tif", "tiff", "bmp", "gif", "heic",
			"heif");

	private static final Set<String> HEIF = Set.of("heic", "heif");

	/** Photos: their pixels stay compressed as JPEG in the PDF. */
	private static final Set<String> PHOTO = Set.of("jpg", "jpeg", "heic", "heif");

	private static final Set<String> TIFF = Set.of("tif", "tiff");

	/** About an A3 sheet at 300 dpi: a larger image is reduced, it would be no better read. */
	static final long TARGET_PIXELS = 16_000_000;

	/** Below this many pixels on its longest side, an image is enlarged twice for the OCR: small letters. */
	static final int SMALL = 1600;

	/** Where the orientation saved by a camera is: the beginning of a JPEG. */
	private static final int EXIF_HEAD = 256 * 1024;

	private final ImageProperties properties;

	private final OcrEngine ocr;

	private final DocumentsProperties documents;

	public ImageConverter(ImageProperties properties, OcrEngine ocr, DocumentsProperties documents) {
		this.properties = properties;
		this.ocr = ocr;
		this.documents = documents;
	}

	public static boolean handles(String fileName) {
		return FORMATS.contains(DocumentProcessor.extension(fileName));
	}

	/**
	 * @param pdf the PDF created, one page per image read
	 * @param notes what was done to the image, for the user
	 */
	public record ConvertedImage(Path pdf, int pages, List<String> notes) {
	}

	/**
	 * @return the PDF created in the output directory
	 * @throws TooLargeException when the file or the image is beyond the limits
	 * @throws UnsupportedImageException when the HEIC decoder is missing
	 * @throws UnreadableImageException when the image cannot be decoded
	 */
	public ConvertedImage toPdf(Path image, Path outputDirectory) throws IOException {
		return toPdf(image, outputDirectory, false);
	}

	/**
	 * @param probe whether an image without letters is left out: one whose name
	 * says nothing of what it is, read only if it holds some text
	 * @throws NoTextException when probed and Tesseract finds no letters in it
	 */
	public ConvertedImage toPdf(Path image, Path outputDirectory, boolean probe) throws IOException {
		String extension = DocumentProcessor.extension(image.getFileName().toString());
		if (Files.size(image) > properties.maxFileSize().toBytes()) {
			throw new TooLargeException("Image trop volumineuse (plus de " + properties.maxFileSize().toMegabytes()
					+ " Mo) : non analysée");
		}
		Path decoded = HEIF.contains(extension) ? decodeHeif(image) : null;
		try {
			Path source = decoded == null ? image : decoded.resolve("photo.png");
			int cameraTurn = extension.equals("jpg") || extension.equals("jpeg") ? cameraTurn(head(source)) : 0;
			List<String> notes = new ArrayList<>();
			Files.createDirectories(outputDirectory);
			Path pdf = outputDirectory.resolve(baseName(image) + ".pdf");
			int pages;
			try (PDDocument document = new PDDocument()) {
				pages = addImages(document, source, TIFF.contains(extension), PHOTO.contains(extension), cameraTurn,
						probe, notes);
				document.save(pdf.toFile());
			}
			return new ConvertedImage(pdf, pages, List.copyOf(notes));
		}
		finally {
			if (decoded != null) {
				deleteQuietly(decoded);
			}
		}
	}

	/**
	 * Adds a page for each image of the file: all those of a TIFF, up to the
	 * pages read by OCR, the first one otherwise.
	 */
	private int addImages(PDDocument document, Path source, boolean tiff, boolean photo, int cameraTurn,
			boolean probe, List<String> notes) throws IOException {
		try (ImageInputStream input = ImageIO.createImageInputStream(source.toFile())) {
			Iterator<ImageReader> readers = input == null ? null : ImageIO.getImageReaders(input);
			if (readers == null || !readers.hasNext()) {
				throw new UnreadableImageException("Image illisible : format non reconnu ou fichier endommagé");
			}
			ImageReader reader = readers.next();
			try {
				reader.setInput(input);
				int count = tiff ? reader.getNumImages(true) : 1;
				int read = Math.min(count, Math.max(ocr.maxPages(), 1));
				if (read < count) {
					notes.add("seules les " + read + " premières images sur " + count + " sont lues");
				}
				boolean reduced = false;
				boolean turned = cameraTurn != 0;
				boolean text = false;
				for (int index = 0; index < read; index++) {
					long pixels = (long) reader.getWidth(index) * reader.getHeight(index);
					if (pixels > properties.maxPixels()) {
						throw new TooLargeException("Image trop grande (" + pixels / 1_000_000 + " millions de pixels) "
								+ ": non analysée");
					}
					int step = subsampling(pixels);
					reduced |= step > 1;
					ImageReadParam param = reader.getDefaultReadParam();
					param.setSourceSubsampling(step, step, 0, 0);
					BufferedImage image = turn(onWhite(reader.read(index, param)), cameraTurn);
					Orientation orientation = orientation(image);
					text |= orientation.text();
					turned |= orientation.turn() != 0;
					addPage(document, turn(image, orientation.turn()), photo);
				}
				if (probe && !text) {
					throw new NoTextException("Aucun texte détecté dans l'image (photo ou illustration) : non lue");
				}
				if (turned) {
					notes.add("image redressée");
				}
				if (reduced) {
					notes.add("image réduite pour la lecture");
				}
				return read;
			}
			catch (IOException | IndexOutOfBoundsException | IllegalArgumentException | IllegalStateException ex) {
				throw new UnreadableImageException("Image illisible ou endommagée : non analysée");
			}
			finally {
				reader.dispose();
			}
		}
	}

	/**
	 * Lays an image on a white page in grey levels or colours: a transparent
	 * background would otherwise be read black.
	 */
	static BufferedImage onWhite(BufferedImage image) {
		boolean grey = image.getColorModel().getNumColorComponents() == 1;
		BufferedImage page = new BufferedImage(image.getWidth(), image.getHeight(),
				grey ? BufferedImage.TYPE_BYTE_GRAY : BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = page.createGraphics();
		try {
			graphics.setColor(Color.WHITE);
			graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
			graphics.drawImage(image, 0, 0, null);
		}
		finally {
			graphics.dispose();
		}
		return page;
	}

	/**
	 * @param degrees clockwise, a multiple of 90
	 */
	static BufferedImage turn(BufferedImage image, int degrees) {
		int quarters = Math.floorMod(degrees / 90, 4);
		if (quarters == 0) {
			return image;
		}
		int width = image.getWidth();
		int height = image.getHeight();
		boolean across = quarters % 2 == 1;
		BufferedImage turned = new BufferedImage(across ? height : width, across ? width : height, image.getType());
		AffineTransform transform = new AffineTransform();
		switch (quarters) {
			case 1 -> transform.translate(height, 0);
			case 2 -> transform.translate(width, height);
			default -> transform.translate(0, width);
		}
		transform.quadrantRotate(quarters);
		Graphics2D graphics = turned.createGraphics();
		try {
			graphics.drawImage(image, transform, null);
		}
		finally {
			graphics.dispose();
		}
		return turned;
	}

	/**
	 * What Tesseract finds from the letters; when it is missing or fails, the
	 * image is taken as it is, with text: the OCR says then what it reads.
	 */
	private Orientation orientation(BufferedImage image) {
		Path file = null;
		try {
			file = Files.createTempFile("image-orientation", ".png");
			ImageIO.write(image, "png", file.toFile());
			return ocr.orientation(file);
		}
		catch (IOException ex) {
			return new Orientation(0, true);
		}
		finally {
			if (file != null) {
				try {
					Files.deleteIfExists(file);
				}
				catch (IOException ex) {
					// a leftover temporary file is harmless
				}
			}
		}
	}

	/**
	 * A page the size of the image read at 300 dpi, or 150 dpi for a small
	 * one, which the OCR then reads enlarged.
	 */
	private static void addPage(PDDocument document, BufferedImage image, boolean photo) throws IOException {
		float dpi = Math.max(image.getWidth(), image.getHeight()) < SMALL ? 150 : 300;
		float width = image.getWidth() * 72f / dpi;
		float height = image.getHeight() * 72f / dpi;
		PDPage page = new PDPage(new PDRectangle(width, height));
		document.addPage(page);
		PDImageXObject pixels = photo ? JPEGFactory.createFromImage(document, image, 0.9f)
				: LosslessFactory.createFromImage(document, image);
		try (PDPageContentStream content = new PDPageContentStream(document, page)) {
			content.drawImage(pixels, 0, 0, width, height);
		}
	}

	/** One pixel kept out of so many in each direction, for the image to be about a page at 300 dpi. */
	static int subsampling(long pixels) {
		return pixels <= TARGET_PIXELS ? 1 : (int) Math.ceil(Math.sqrt((double) pixels / TARGET_PIXELS));
	}

	/**
	 * The orientation saved by the camera in the EXIF data of a JPEG, as the
	 * turn, clockwise, that shows the photo upright; mirrored orientations are
	 * left as they are.
	 */
	static int cameraTurn(byte[] jpeg) {
		if (jpeg.length < 4 || (jpeg[0] & 0xFF) != 0xFF || (jpeg[1] & 0xFF) != 0xD8) {
			return 0;
		}
		int at = 2;
		while (at + 4 <= jpeg.length && (jpeg[at] & 0xFF) == 0xFF) {
			int marker = jpeg[at + 1] & 0xFF;
			int length = unsigned16(jpeg, at + 2, false);
			if (marker == 0xDA || length < 2) {
				break;
			}
			if (marker == 0xE1 && at + 10 <= jpeg.length
					&& new String(jpeg, at + 4, 6, StandardCharsets.ISO_8859_1).equals("Exif\0\0")) {
				return exifTurn(jpeg, at + 10, Math.min(jpeg.length, at + 2 + length));
			}
			at += 2 + length;
		}
		return 0;
	}

	/**
	 * @param tiff where the TIFF structure of the EXIF data starts
	 * @param end where the EXIF data ends
	 */
	private static int exifTurn(byte[] data, int tiff, int end) {
		if (tiff + 8 > end) {
			return 0;
		}
		boolean little = data[tiff] == 'I';
		long first = unsigned32(data, tiff + 4, little);
		if (first < 8 || tiff + first + 2 > end) {
			return 0;
		}
		int directory = (int) (tiff + first);
		int entries = unsigned16(data, directory, little);
		for (int entry = 0; entry < entries; entry++) {
			int at = directory + 2 + 12 * entry;
			if (at + 12 > end) {
				return 0;
			}
			if (unsigned16(data, at, little) == 0x0112) {
				return switch (unsigned16(data, at + 8, little)) {
					case 3 -> 180;
					case 6 -> 90;
					case 8 -> 270;
					default -> 0;
				};
			}
		}
		return 0;
	}

	private static int unsigned16(byte[] data, int at, boolean little) {
		int a = data[at] & 0xFF;
		int b = data[at + 1] & 0xFF;
		return little ? a | b << 8 : a << 8 | b;
	}

	private static long unsigned32(byte[] data, int at, boolean little) {
		long high = unsigned16(data, at + (little ? 2 : 0), little);
		long low = unsigned16(data, at + (little ? 0 : 2), little);
		return high << 16 | low;
	}

	private static byte[] head(Path file) throws IOException {
		try (InputStream in = Files.newInputStream(file)) {
			return in.readNBytes(EXIF_HEAD);
		}
	}

	/**
	 * Decodes a HEIC photo with libheif into a folder of its own: the decoder
	 * may write, besides the photo, its depth or auxiliary images.
	 * @return the folder holding the photo as PNG
	 */
	private Path decodeHeif(Path image) throws IOException {
		Path folder = Files.createTempDirectory("heif");
		Process process;
		try {
			process = new ProcessBuilder(List.of(properties.heifCommand(), image.toString(),
					folder.resolve("photo.png").toString()))
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start();
		}
		catch (IOException ex) {
			deleteQuietly(folder);
			throw new UnsupportedImageException("Photo HEIC : décodeur absent du serveur (libheif), non analysée");
		}
		try {
			if (!process.waitFor(documents.conversionTimeout().toMillis(), TimeUnit.MILLISECONDS)) {
				process.destroyForcibly();
				deleteQuietly(folder);
				throw new UnreadableImageException("Photo HEIC trop longue à décoder, abandonnée");
			}
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			deleteQuietly(folder);
			throw new UnreadableImageException("Décodage de la photo HEIC interrompu");
		}
		if (process.exitValue() != 0 || !Files.exists(folder.resolve("photo.png"))) {
			deleteQuietly(folder);
			throw new UnreadableImageException("Photo HEIC illisible ou endommagée : non analysée");
		}
		return folder;
	}

	private static String baseName(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot > 0 ? name.substring(0, dot) : name;
	}

	private static void deleteQuietly(Path folder) {
		try (Stream<Path> paths = Files.walk(folder)) {
			paths.sorted(Comparator.reverseOrder()).forEach(path -> path.toFile().delete());
		}
		catch (IOException ex) {
			// a leftover temporary folder is harmless
		}
	}

	/** The file or the image is beyond the limits: kept, not read. */
	public static class TooLargeException extends RuntimeException {

		TooLargeException(String message) {
			super(message);
		}

	}

	/** The format cannot be decoded on this server: kept, not read. */
	public static class UnsupportedImageException extends RuntimeException {

		UnsupportedImageException(String message) {
			super(message);
		}

	}

	/** An image whose name says nothing holds no letters: a photo or an illustration. */
	public static class NoTextException extends RuntimeException {

		NoTextException(String message) {
			super(message);
		}

	}

	/** The image is damaged, truncated or not an image. */
	public static class UnreadableImageException extends RuntimeException {

		UnreadableImageException(String message) {
			super(message);
		}

	}

}
