package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.assertj.core.api.Assertions.within;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.gpefrance.tenders.document.OcrEngine.OcrPage;
import eu.gpefrance.tenders.document.OcrEngine.OcrWord;

class OcrEngineTests {

	/** What Tesseract writes for two lines read at 300 dpi: one header, one line of values. */
	static final String TSV = """
			level\tpage_num\tblock_num\tpar_num\tline_num\tword_num\tleft\ttop\twidth\theight\tconf\ttext
			1\t1\t0\t0\t0\t0\t0\t0\t2480\t3508\t-1\t
			5\t1\t1\t1\t1\t1\t250\t400\t60\t38\t96.5\tDésignation
			5\t1\t1\t1\t1\t2\t1500\t400\t70\t38\t95\tQté
			5\t1\t1\t1\t2\t1\t250\t460\t200\t38\t91\tDéblais
			5\t1\t1\t1\t2\t2\t1450\t460\t120\t38\t62.4\t1 250
			""";

	@TempDir
	private Path folder;

	@Test
	void turnsTheWordsReadIntoPointsOfThePageWithTheirConfidence() {
		OcrPage page = OcrEngine.parse(TSV, 72f / 300);

		assertThat(page.text()).isEqualTo("Désignation Qté\nDéblais 1 250");
		assertThat(page.words()).extracting(OcrWord::text, OcrWord::confidence)
			.containsExactly(tuple("Désignation", 96.5f), tuple("Qté", 95f), tuple("Déblais", 91f), tuple("1 250", 62.4f));
		OcrWord number = page.words().getLast();
		assertThat(number.x0()).isCloseTo(348f, within(0.01f));
		assertThat(number.x1()).isCloseTo(376.8f, within(0.01f));
		assertThat(number.y()).isCloseTo(119.52f, within(0.01f));
		assertThat(page.confidence()).isBetween(86.0, 86.3);
	}

	@Test
	void keepsTheWordsWithThePageAndReadsThemBack() {
		List<OcrWord> words = OcrEngine.parse(TSV, 72f / 300).words();

		assertThat(OcrEngine.fromJson(OcrEngine.toJson(words))).isEqualTo(words);
		assertThat(OcrEngine.fromJson("pas du JSON")).isEmpty();
		assertThat(OcrEngine.fromJson(null)).isEmpty();
	}

	@Test
	void readsAPageWithTesseractAndSaysWhenItIsMissing() throws IOException {
		Path pdf = Files.write(folder.resolve("scan.pdf"), TestFiles.scannedPdf(""));
		Path script = fakeTesseract(folder);

		OcrPage page = new OcrEngine(new OcrProperties(script.toString(), "fra", 150, Duration.ofSeconds(20), 5, 3))
			.recognise(pdf, 1);

		assertThat(page.words()).extracting(OcrWord::text).containsExactly("Désignation", "Qté", "Déblais", "1 250");
		// Positions follow the resolution of the image given to Tesseract.
		assertThat(page.words().getLast().x0()).isCloseTo(696f, within(0.01f));
		assertThatThrownBy(() -> new OcrEngine(new OcrProperties("missing-ocr-binary", "fra", 300,
				Duration.ofSeconds(5), 5, 3)).recognise(pdf, 1))
			.isInstanceOf(OcrEngine.OcrUnavailableException.class);
	}

	@Test
	void neverReadsAPageLargerThanAnA3Sheet() throws IOException {
		Path plan;
		try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			document.addPage(new PDPage(PDRectangle.A1));
			document.save(out);
			plan = Files.write(folder.resolve("plan.pdf"), out.toByteArray());
		}

		assertThat(new OcrEngine(new OcrProperties(fakeTesseract(folder).toString(), "fra", 150,
				Duration.ofSeconds(20), 5, 3)).recognise(plan, 1)).isNull();
	}

	@Test
	void neverReadsABlankPage() throws IOException {
		Path blank = Files.write(folder.resolve("blanche.pdf"), TestFiles.pdf(""));

		// Tesseract is not even run: it is missing here, and nothing says so.
		assertThat(new OcrEngine(new OcrProperties("missing-ocr-binary", "fra", 300, Duration.ofSeconds(5), 5, 6))
			.recognise(blank, 1)).isSameAs(OcrEngine.BLANK);
	}

	/** A script answering like Tesseract, whatever the image. */
	static Path fakeTesseract(Path folder) throws IOException {
		Path script = folder.resolve("fake-tesseract.sh");
		Files.writeString(script, "#!/bin/sh\ncat <<'EOF'\n" + TSV + "EOF\n");
		assertThat(script.toFile().setExecutable(true)).isTrue();
		return script;
	}

}
