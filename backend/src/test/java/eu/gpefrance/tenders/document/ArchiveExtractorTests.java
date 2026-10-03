package eu.gpefrance.tenders.document;

import static eu.gpefrance.tenders.document.TestFiles.entries;
import static eu.gpefrance.tenders.document.TestFiles.zip;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

import eu.gpefrance.tenders.document.ArchiveExtractor.ArchiveRejectedException;
import eu.gpefrance.tenders.document.ArchiveExtractor.ExtractedFile;

class ArchiveExtractorTests {

	@TempDir
	Path directory;

	private final ArchiveExtractor extractor = new ArchiveExtractor(new DocumentsProperties("unused", "soffice",
			Duration.ofMinutes(1), 5, DataSize.ofKilobytes(10), 2));

	@Test
	void extractsFilesKeepingTheirFolders() throws IOException {
		Path archive = write("DCE.zip",
				zip(entries("RC.pdf", "rc", "Pieces/CCTP.pdf", "cctp", "__MACOSX/._RC.pdf", "x", "Pieces/", "")));

		List<ExtractedFile> files = extractor.extract(archive, "DCE.zip", directory.resolve("out"));

		assertThat(files).extracting(ExtractedFile::displayName)
			.containsExactly("DCE.zip/RC.pdf", "DCE.zip/Pieces/CCTP.pdf");
		assertThat(Files.readString(files.get(1).path())).isEqualTo("cctp");
	}

	@Test
	void extractsNestedArchives() {
		byte[] inner = zip(entries("Lot 1/CCTP.pdf", "cctp"));
		Path archive = write("DCE.zip", zip(entries("RC.pdf", "rc", "Lots.zip", inner)));

		List<ExtractedFile> files = extractor.extract(archive, "DCE.zip", directory.resolve("out"));

		assertThat(files).extracting(ExtractedFile::displayName)
			.containsExactly("DCE.zip/RC.pdf", "DCE.zip/Lots.zip/Lot 1/CCTP.pdf");
	}

	@Test
	void neverWritesOutsideTheTargetDirectory() throws IOException {
		Path target = directory.resolve("out");
		Path archive = write("evil.zip", zip(entries("../../escaped.txt", "x", "/etc/passwd", "y")));

		List<ExtractedFile> files = extractor.extract(archive, "evil.zip", target);

		assertThat(files).extracting(file -> target.relativize(file.path()).toString().replace('\\', '/'))
			.containsExactly("escaped.txt", "etc/passwd");
		try (Stream<Path> all = Files.list(directory)) {
			assertThat(all.map(path -> path.getFileName().toString())).containsExactlyInAnyOrder("evil.zip", "out");
		}
	}

	@Test
	void refusesAnArchiveThatExpandsTooMuch() {
		Path archive = write("bomb.zip", zip(entries("big.txt", "0".repeat(20_000))));

		assertThatThrownBy(() -> extractor.extract(archive, "bomb.zip", directory.resolve("out")))
			.isInstanceOf(ArchiveRejectedException.class)
			.hasMessageContaining("taille maximale");
	}

	@Test
	void refusesTooManyFiles() {
		Path archive = write("many.zip", zip(entries("1", "a", "2", "b", "3", "c", "4", "d", "5", "e", "6", "f")));

		assertThatThrownBy(() -> extractor.extract(archive, "many.zip", directory.resolve("out")))
			.isInstanceOf(ArchiveRejectedException.class)
			.hasMessageContaining("Trop de fichiers");
	}

	@Test
	void refusesArchivesNestedTooDeeply() {
		byte[] level3 = zip(entries("RC.pdf", "rc"));
		byte[] level2 = zip(entries("c.zip", level3));
		Path archive = write("a.zip", zip(entries("b.zip", level2)));

		assertThatThrownBy(() -> extractor.extract(archive, "a.zip", directory.resolve("out")))
			.isInstanceOf(ArchiveRejectedException.class)
			.hasMessageContaining("imbriquées");
	}

	@Test
	void readsFileNamesEncodedByWindows() {
		Path archive = write("DCE.zip", zip(entries("Règlement.pdf", "rc"), Charset.forName("IBM850")));

		assertThat(extractor.extract(archive, "DCE.zip", directory.resolve("out")))
			.extracting(ExtractedFile::displayName)
			.containsExactly("DCE.zip/Règlement.pdf");
	}

	@Test
	void refusesAFileThatIsNotAnArchive() {
		Path archive = write("fake.zip", "not a zip".getBytes());

		assertThatThrownBy(() -> extractor.extract(archive, "fake.zip", directory.resolve("out")))
			.isInstanceOf(ArchiveRejectedException.class)
			.hasMessageContaining("illisible");
	}

	private Path write(String name, byte[] content) {
		try {
			return Files.write(directory.resolve(name), content);
		}
		catch (IOException ex) {
			throw new java.io.UncheckedIOException(ex);
		}
	}

}
