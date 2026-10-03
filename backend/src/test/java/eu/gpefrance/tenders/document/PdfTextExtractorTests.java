package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PdfTextExtractorTests {

	@TempDir
	Path directory;

	private final PdfTextExtractor extractor = new PdfTextExtractor();

	@Test
	void extractsTheTextOfEachPage() throws IOException {
		Path pdf = Files.write(directory.resolve("RC.pdf"),
				TestFiles.pdf("Date limite : 15/11/2026 a 12h00", "", "Visite obligatoire"));

		assertThat(extractor.extractPages(pdf)).containsExactly("Date limite : 15/11/2026 a 12h00", "",
				"Visite obligatoire");
	}

	@Test
	void failsOnAFileThatIsNotAPdf() throws IOException {
		Path pdf = Files.writeString(directory.resolve("fake.pdf"), "not a pdf");

		assertThatThrownBy(() -> extractor.extractPages(pdf)).isInstanceOf(IOException.class);
	}

}
