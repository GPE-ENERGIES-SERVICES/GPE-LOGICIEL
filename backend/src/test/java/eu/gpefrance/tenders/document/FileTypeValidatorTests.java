package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.gpefrance.tenders.document.FileTypeValidator.FileRejectedException;
import eu.gpefrance.tenders.document.FileTypeValidator.Kind;

/**
 * The type of a retrieved file comes from its content: synthetic files.
 */
class FileTypeValidatorTests {

	private static final byte[] PDF = "%PDF-1.7\n1 0 obj << >> endobj\ntrailer << >>\n%%EOF\n"
		.getBytes(StandardCharsets.ISO_8859_1);

	@TempDir
	Path directory;

	@Test
	void acceptsAPdfAndAnArchiveOfDocuments() throws IOException {
		assertThat(validate(write("rc.pdf", PDF), "RC.pdf")).extracting(FileTypeValidator.Validated::kind)
			.isEqualTo(Kind.PDF);
		Path zip = zip("dce.zip", "CCTP.pdf", "lots/BPU.xlsx");
		assertThat(validate(zip, "DCE.zip").kind()).isEqualTo(Kind.ZIP);
		assertThat(validate(zip, "CCAP.docx").kind()).isEqualTo(Kind.ZIP);
	}

	@Test
	void namesAFileWithoutExtensionByItsContent() throws IOException {
		assertThat(validate(write("download", PDF), "telechargement").fileName()).isEqualTo("telechargement.pdf");
	}

	@Test
	void refusesAWebPageWhateverItsName() throws IOException {
		Path login = write("login", """

				<!DOCTYPE html><html><head><title>Connexion</title></head><body><form>...</form></body></html>""");

		assertThatThrownBy(() -> validate(login, "DCE.pdf")).isInstanceOf(FileRejectedException.class)
			.hasMessageContaining("Page web");
		assertThatThrownBy(() -> validate(login, "DCE.zip")).isInstanceOf(FileRejectedException.class);
		assertThatThrownBy(() -> validate(write("form", "<html lang=\"fr\">retrait</html>"), "dossier"))
			.isInstanceOf(FileRejectedException.class);
	}

	@Test
	void refusesAnEmptyAnUnknownOrATruncatedFile() throws IOException {
		assertThatThrownBy(() -> validate(write("empty", new byte[0]), "RC.pdf")).hasMessage("Fichier vide");
		assertThatThrownBy(() -> validate(write("text", "Bonjour"), "RC.txt")).hasMessage("Format non reconnu");
		assertThatThrownBy(() -> validate(write("cut.pdf", "%PDF-1.7\n1 0 obj << >>"), "RC.pdf"))
			.hasMessage("PDF tronqué ou incomplet");
	}

	@Test
	void refusesAContentThatDoesNotAgreeWithItsExtension() throws IOException {
		assertThatThrownBy(() -> validate(write("rc.pdf", PDF), "CCAP.docx"))
			.isInstanceOf(FileRejectedException.class)
			.hasMessageContaining("ne correspond pas à l'extension .docx");
	}

	@Test
	void refusesAMalformedArchiveOrOneLeavingItsFolderOrTooLarge() throws IOException {
		byte[] broken = new byte[64];
		broken[0] = 'P';
		broken[1] = 'K';
		broken[2] = 3;
		broken[3] = 4;
		assertThatThrownBy(() -> validate(write("broken.zip", broken), "DCE.zip"))
			.hasMessage("Archive illisible ou malformée");
		assertThatThrownBy(() -> validate(zip("evil.zip", "../../etc/passwd"), "DCE.zip"))
			.hasMessageContaining("chemin interdit");
		assertThatThrownBy(() -> FileTypeValidator.validate(zip("many.zip", "a.pdf", "b.pdf", "c.pdf"), "DCE.zip", 2,
				1_000_000)).hasMessageContaining("plus de 2 fichiers");
		assertThatThrownBy(() -> FileTypeValidator.validate(zip("big.zip", "a.pdf"), "DCE.zip", 10, 10))
			.hasMessageContaining("taille maximale décompressée");
	}

	private FileTypeValidator.Validated validate(Path file, String name) throws IOException {
		return FileTypeValidator.validate(file, name, 2000, 2_000_000_000L);
	}

	private Path write(String name, String content) throws IOException {
		return write(name, content.getBytes(StandardCharsets.UTF_8));
	}

	private Path write(String name, byte[] content) throws IOException {
		return Files.write(directory.resolve(name), content);
	}

	private Path zip(String name, String... entries) throws IOException {
		Path file = directory.resolve(name);
		try (OutputStream out = Files.newOutputStream(file); ZipOutputStream zip = new ZipOutputStream(out)) {
			for (String entry : entries) {
				zip.putNextEntry(new ZipEntry(entry));
				zip.write(PDF);
				zip.closeEntry();
			}
		}
		return file;
	}

}
