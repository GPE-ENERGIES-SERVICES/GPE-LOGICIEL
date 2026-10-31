package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import eu.gpefrance.tenders.document.ImageConverter.ConvertedImage;
import eu.gpefrance.tenders.document.OcrEngine.OcrPage;
import eu.gpefrance.tenders.document.PdfTable.Cell;

/**
 * Images read with the real Tesseract, as on the server: printed text, a
 * price schedule with French amounts, a TIFF, a turned scan and a poor copy.
 * Skipped where Tesseract and its French data are not installed (the usual
 * test run); run them where they are.
 */
class ImageOcrTests {

	@TempDir
	private Path folder;

	private final OcrEngine ocr = new OcrEngine(new OcrProperties("tesseract", "fra", 300, Duration.ofSeconds(90),
			40, 6));

	private final ImageConverter converter = new ImageConverter(new ImageProperties("heif-dec",
			DataSize.ofMegabytes(100), 300_000_000), ocr, new DocumentsProperties("storage", "soffice",
					Duration.ofMinutes(1), 100, DataSize.ofMegabytes(100), 2));

	@BeforeAll
	static void tesseractInstalled() {
		boolean installed;
		try {
			Process process = new ProcessBuilder("tesseract", "--list-langs").redirectErrorStream(true).start();
			installed = process.waitFor() == 0 && new String(process.getInputStream().readAllBytes()).contains("fra");
		}
		catch (IOException ex) {
			installed = false;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			installed = false;
		}
		assumeTrue(installed, "Tesseract with its French data is not installed");
	}

	@Test
	void readsThePrintedTextOfAJpeg() throws IOException {
		BufferedImage page = TestImages.page(TestFiles.pdf("Règlement de la consultation : remise des offres le "
				+ "15/11/2026 à 12h00"), 300);

		Read read = read("RC photo.jpg", TestImages.write(page, "jpg"));

		assertThat(read.page().text()).contains("Règlement de la consultation", "15/11/2026");
		assertThat(read.page().confidence()).isGreaterThan(80);
	}

	@Test
	void readsTheAmountsOfAPriceScheduleInAPngButNeverCallsItsTableReliable() throws IOException {
		Read read = read("DPGF lot 1.png", TestImages.write(TestImages.page(TestPdfTables.dpgf(), 300), "png"));

		assertThat(read.page().text()).contains("Déblais en pleine masse", "6 440,00", "13 740,00");
		List<PdfTable> tables = read.tables();
		assertThat(tables).hasSize(1);
		assertThat(tables.getFirst().reliable()).isFalse();
		assertThat(tables.getFirst().rows().stream().flatMap(row -> row.cells().stream()).map(Cell::text))
			.contains("Déblais en pleine masse", "350", "18,40", "6 440,00");
	}

	@Test
	void readsEachPageOfATiff() throws IOException {
		BufferedImage page = TestImages.page(TestPdfTables.dpgf(), 300);

		Read read = read("DPGF scan.tif", TestImages.tiff(List.of(page, page)));

		assertThat(read.converted().pages()).isEqualTo(2);
		assertThat(ocr.recognise(read.converted().pdf(), 2).text()).contains("Évacuation des déblais");
	}

	@Test
	void turnsUprightAScanTurnedAQuarterBeforeReadingIt() throws IOException {
		BufferedImage turned = ImageConverter.turn(TestImages.page(TestPdfTables.dpgf(), 300), 270);

		Read read = read("DPGF tourné.png", TestImages.write(turned, "png"));

		assertThat(read.converted().notes()).containsExactly("image redressée");
		assertThat(read.page().text()).contains("Déblais en pleine masse");
	}

	@Test
	void findsLettersInAPageWhoseNameSaysNothingButNoneInAPhoto() throws IOException {
		Path page = Files.write(folder.resolve("scan001.png"),
				TestImages.write(TestImages.page(TestPdfTables.dpgf(), 300), "png"));
		Path photo = Files.write(folder.resolve("IMG_2034.jpg"),
				TestImages.write(TestImages.marked(2000, 1500), "jpg"));

		assertThat(converter.toPdf(page, folder.resolve("page"), true).pages()).isEqualTo(1);
		org.assertj.core.api.Assertions.assertThatThrownBy(() -> converter.toPdf(photo, folder.resolve("photo"), true))
			.isInstanceOf(ImageConverter.NoTextException.class);
	}

	@Test
	void readsAnHeicPhotoDecodedByLibheif() throws IOException, InterruptedException {
		Path png = Files.write(folder.resolve("source.png"),
				TestImages.write(TestImages.page(TestPdfTables.dpgf(), 300), "png"));
		Path heic = folder.resolve("IMG_0042.HEIC");
		Process encoder;
		try {
			encoder = new ProcessBuilder("heif-enc", "-q", "90", "-o", heic.toString(), png.toString())
				.redirectErrorStream(true)
				.redirectOutput(ProcessBuilder.Redirect.DISCARD)
				.start();
		}
		catch (IOException ex) {
			encoder = null;
		}
		assumeTrue(encoder != null && encoder.waitFor() == 0 && Files.exists(heic), "libheif with an encoder is not installed");

		ConvertedImage converted = converter.toPdf(heic, folder.resolve("pdf"));

		assertThat(converted.pages()).isEqualTo(1);
		assertThat(ocr.recognise(converted.pdf(), 1).text()).contains("Déblais en pleine masse", "6 440,00");
	}

	@Test
	void aPoorCopyIsReadWithALowerConfidenceAndItsDoubtfulNumbersAreMarked() throws IOException {
		BufferedImage clean = TestImages.page(TestPdfTables.dpgf(), 300);
		Read good = read("DPGF net.png", TestImages.write(clean, "png"));

		Read poor = read("DPGF photocopie.png", TestImages.write(TestImages.degraded(clean, 0.3), "png"));

		assertThat(poor.page().confidence()).isLessThan(good.page().confidence());
		for (PdfTable table : poor.tables()) {
			assertThat(table.reliable()).isFalse();
		}
		// Any number read with doubt is marked, never given as sure.
		List<OcrEngine.OcrWord> doubtful = poor.page()
			.words()
			.stream()
			.filter(word -> word.confidence() < 80 && word.text().matches(".*\\d.*"))
			.toList();
		List<Cell> cells = poor.tables().stream().flatMap(t -> t.rows().stream()).flatMap(r -> r.cells().stream())
			.toList();
		for (OcrEngine.OcrWord word : doubtful) {
			cells.stream()
				.filter(cell -> cell.text().equals(word.text()) && cell.x0() == word.x0())
				.forEach(cell -> assertThat(cell.ambiguous()).as(cell.text()).isTrue());
		}
	}

	/**
	 * @param page the first page as read by Tesseract
	 * @param tables the tables rebuilt from the words read
	 */
	private record Read(ConvertedImage converted, OcrPage page, List<PdfTable> tables) {
	}

	private Read read(String name, byte[] image) throws IOException {
		Path file = Files.write(folder.resolve(name), image);
		ConvertedImage converted = converter.toPdf(file, folder.resolve("pdf"));
		OcrPage page = ocr.recognise(converted.pdf(), 1);
		List<PdfTable> tables = new PdfTableExtractor().extract(converted.pdf(),
				List.of(new DocumentPage(1L, 1, page.text(), OcrEngine.toJson(page.words()))));
		return new Read(converted, page, tables);
	}

}
