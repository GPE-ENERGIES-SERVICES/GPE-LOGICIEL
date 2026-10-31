package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import eu.gpefrance.tenders.document.ImageConverter.ConvertedImage;

/**
 * Images turned into a PDF to be read by OCR, without Tesseract: its
 * orientation detection is a script, or missing. The real reading is in
 * {@link ImageOcrTests}.
 */
class ImageConverterTests {

	@TempDir
	private Path folder;

	@Test
	void turnsAJpegIntoAOnePagePdfTheSizeOfTheImageAt300DpiAndKeepsTheFile() throws IOException {
		byte[] jpeg = TestImages.write(TestImages.marked(2480, 1754), "jpg");
		Path file = Files.write(folder.resolve("CCTP page 3.jpg"), jpeg);

		ConvertedImage converted = converter("missing-ocr-binary").toPdf(file, folder.resolve("pdf"));

		assertThat(converted.pdf()).hasFileName("CCTP page 3.pdf");
		assertThat(converted.pages()).isEqualTo(1);
		assertThat(converted.notes()).isEmpty();
		try (PDDocument pdf = Loader.loadPDF(converted.pdf().toFile())) {
			PDPage page = pdf.getPage(0);
			assertThat(page.getMediaBox().getWidth()).isCloseTo(595.2f, within(0.1f));
			assertThat(page.getMediaBox().getHeight()).isCloseTo(421f, within(0.1f));
		}
		assertThat(Files.readAllBytes(file)).isEqualTo(jpeg);
	}

	@Test
	void enlargesASmallCaptureForTheOcr() throws IOException {
		Path file = Files.write(folder.resolve("capture.png"), TestImages.write(TestImages.marked(800, 600), "png"));

		ConvertedImage converted = converter("missing-ocr-binary").toPdf(file, folder);

		try (PDDocument pdf = Loader.loadPDF(converted.pdf().toFile())) {
			// 150 dpi: read at 300 dpi, the capture is twice as large.
			assertThat(pdf.getPage(0).getMediaBox().getWidth()).isCloseTo(384f, within(0.1f));
		}
	}

	@Test
	void laysATransparentImageOnWhite() throws IOException {
		BufferedImage transparent = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
		Path file = Files.write(folder.resolve("logo.png"), TestImages.write(transparent, "png"));

		ConvertedImage converted = converter("missing-ocr-binary").toPdf(file, folder);

		assertThat(firstPage(converted).getRaster().getSample(100, 70, 0)).isEqualTo(255);
	}

	@Test
	void readsEachImageOfATiffUpToThePagesReadByOcr() throws IOException {
		Path file = Files.write(folder.resolve("scan.tif"), TestImages.tiff(List.of(TestImages.marked(1700, 2200),
				TestImages.marked(1700, 2200), TestImages.marked(1700, 2200))));

		ConvertedImage all = converter("missing-ocr-binary").toPdf(file, folder.resolve("all"));
		ConvertedImage limited = new ImageConverter(new ImageProperties("missing-heif-binary", DataSize.ofMegabytes(100),
				300_000_000), ocr("missing-ocr-binary", 2), documents()).toPdf(file, folder.resolve("limited"));

		assertThat(all.pages()).isEqualTo(3);
		assertThat(limited.pages()).isEqualTo(2);
		assertThat(limited.notes()).containsExactly("seules les 2 premières images sur 3 sont lues");
	}

	@Test
	void turnsAPhotoAsTheCameraSaysAndAsTesseractFindsTheText() throws IOException {
		Path photo = Files.write(folder.resolve("photo.jpg"),
				TestImages.jpegWithOrientation(TestImages.marked(2000, 1600), 6));
		Path scan = Files.write(folder.resolve("scan.png"), TestImages.write(TestImages.marked(2000, 1600), "png"));
		Path osd = script("Page number: 0\nOrientation in degrees: 270\nRotate: 90\nOrientation confidence: 7.41\n");

		ConvertedImage byCamera = converter("missing-ocr-binary").toPdf(photo, folder.resolve("camera"));
		ConvertedImage byText = converter(osd.toString()).toPdf(scan, folder.resolve("text"));

		for (ConvertedImage converted : List.of(byCamera, byText)) {
			BufferedImage page = firstPage(converted);
			assertThat(page.getHeight()).isGreaterThan(page.getWidth());
			// The dark corner, top left as taken, is top right once turned clockwise.
			assertThat(page.getRaster().getSample(page.getWidth() - 10, 10, 0)).isLessThan(60);
			assertThat(page.getRaster().getSample(10, 10, 0)).isGreaterThan(150);
			assertThat(converted.notes()).containsExactly("image redressée");
		}
	}

	@Test
	void readsAnImageWhoseNameSaysNothingOnlyWhenTesseractFindsLetters() throws IOException {
		Path file = Files.write(folder.resolve("IMG_2034.jpg"), TestImages.write(TestImages.marked(1600, 1200), "jpg"));
		Path letters = script("Page number: 0\nOrientation in degrees: 0\nRotate: 0\nOrientation confidence: 4.2\n");
		Path none = script("Too few characters. Skipping this page\n");

		assertThat(converter(letters.toString()).toPdf(file, folder.resolve("letters"), true).pages()).isEqualTo(1);
		assertThatThrownBy(() -> converter(none.toString()).toPdf(file, folder.resolve("none"), true))
			.isInstanceOf(ImageConverter.NoTextException.class)
			.hasMessage("Aucun texte détecté dans l'image (photo ou illustration) : non lue");
		// A piece named as such is read whatever Tesseract finds; without Tesseract, nothing is left out.
		assertThat(converter(none.toString()).toPdf(file, folder.resolve("piece")).pages()).isEqualTo(1);
		assertThat(converter("missing-ocr-binary").toPdf(file, folder.resolve("missing"), true).pages()).isEqualTo(1);
	}

	@Test
	void readsTheOrientationFoundByTesseractOnlyWhenItIsSure() {
		assertThat(OcrEngine.parseTurn("Rotate: 180\nOrientation confidence: 12.5\n")).isEqualTo(180);
		assertThat(OcrEngine.parseTurn("Rotate: 90\nOrientation confidence: 0.84\n")).isZero();
		assertThat(OcrEngine.parseTurn("Too few characters. Skipping this page\n")).isZero();
		assertThat(ImageConverter.cameraTurn(TestImages.write(TestImages.marked(10, 10), "jpg"))).isZero();
		assertThat(ImageConverter.cameraTurn(TestImages.jpegWithOrientation(TestImages.marked(10, 10), 3)))
			.isEqualTo(180);
		assertThat(ImageConverter.cameraTurn(TestImages.jpegWithOrientation(TestImages.marked(10, 10), 8)))
			.isEqualTo(270);
		assertThat(ImageConverter.cameraTurn(new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE1, 0, 2 }))
			.isZero();
	}

	@Test
	void reducesAnImageLargerThanAPageAndSaysSo() throws IOException {
		Path file = Files.write(folder.resolve("photo DPGF.png"), TestImages.write(TestImages.marked(6000, 4000), "png"));

		ConvertedImage converted = converter("missing-ocr-binary").toPdf(file, folder);

		assertThat(ImageConverter.subsampling(24_000_000)).isEqualTo(2);
		assertThat(ImageConverter.subsampling(16_000_000)).isEqualTo(1);
		assertThat(converted.notes()).containsExactly("image réduite pour la lecture");
		try (PDDocument pdf = Loader.loadPDF(converted.pdf().toFile())) {
			assertThat(pdf.getPage(0).getMediaBox().getWidth()).isCloseTo(720f, within(0.1f));
		}
	}

	@Test
	void refusesADamagedImageOrOneThatIsNoImage() throws IOException {
		byte[] png = TestImages.write(TestImages.marked(800, 600), "png");
		Path truncated = Files.write(folder.resolve("tronquée.png"), Arrays.copyOf(png, png.length / 2));
		Path noise = Files.write(folder.resolve("bruit.jpg"), "pas une image du tout".getBytes());

		assertThatThrownBy(() -> converter("missing-ocr-binary").toPdf(truncated, folder))
			.isInstanceOf(ImageConverter.UnreadableImageException.class)
			.hasMessage("Image illisible ou endommagée : non analysée");
		assertThatThrownBy(() -> converter("missing-ocr-binary").toPdf(noise, folder))
			.isInstanceOf(ImageConverter.UnreadableImageException.class)
			.hasMessageStartingWith("Image illisible");
	}

	@Test
	void keepsAsideAFileOrAnImageBeyondTheLimits() throws IOException {
		Path file = Files.write(folder.resolve("grande.png"), TestImages.write(TestImages.marked(3000, 2000), "png"));

		assertThatThrownBy(() -> new ImageConverter(new ImageProperties("missing-heif-binary", DataSize.ofBytes(1000),
				300_000_000), ocr("missing-ocr-binary", 40), documents()).toPdf(file, folder))
			.isInstanceOf(ImageConverter.TooLargeException.class)
			.hasMessage("Image trop volumineuse (plus de 0 Mo) : non analysée");
		assertThatThrownBy(() -> new ImageConverter(new ImageProperties("missing-heif-binary", DataSize.ofMegabytes(100),
				5_000_000), ocr("missing-ocr-binary", 40), documents()).toPdf(file, folder))
			.isInstanceOf(ImageConverter.TooLargeException.class)
			.hasMessage("Image trop grande (6 millions de pixels) : non analysée");
	}

	@Test
	void saysWhenTheHeicDecoderIsMissing() throws IOException {
		Path file = Files.write(folder.resolve("IMG_0042.HEIC"), new byte[] { 0, 0, 0, 24, 'f', 't', 'y', 'p' });

		assertThat(ImageConverter.handles("IMG_0042.HEIC")).isTrue();
		assertThat(ImageConverter.handles("plan.dwg")).isFalse();
		assertThatThrownBy(() -> converter("missing-ocr-binary").toPdf(file, folder))
			.isInstanceOf(ImageConverter.UnsupportedImageException.class)
			.hasMessage("Photo HEIC : décodeur absent du serveur (libheif), non analysée");
	}

	private ImageConverter converter(String tesseract) {
		return new ImageConverter(new ImageProperties("missing-heif-binary", DataSize.ofMegabytes(100), 300_000_000),
				ocr(tesseract, 40), documents());
	}

	private static OcrEngine ocr(String command, int maxPages) {
		return new OcrEngine(new OcrProperties(command, "fra", 300, Duration.ofSeconds(20), maxPages, 6));
	}

	private static DocumentsProperties documents() {
		return new DocumentsProperties("storage", "missing-office-binary", Duration.ofSeconds(20), 100,
				DataSize.ofMegabytes(100), 2);
	}

	private Path script(String output) throws IOException {
		Path script = folder.resolve("fake-tesseract-" + output.hashCode() + ".sh");
		Files.writeString(script, "#!/bin/sh\ncat <<'EOF'\n" + output + "EOF\n");
		assertThat(script.toFile().setExecutable(true)).isTrue();
		return script;
	}

	private static BufferedImage firstPage(ConvertedImage converted) throws IOException {
		try (PDDocument pdf = Loader.loadPDF(converted.pdf().toFile())) {
			return new PDFRenderer(pdf).renderImageWithDPI(0, 72, ImageType.GRAY);
		}
	}

}
