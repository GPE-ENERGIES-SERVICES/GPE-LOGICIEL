package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.util.unit.DataSize;

class FileStorageTests {

	@TempDir
	Path root;

	@Test
	void keepsOnlyASafeFileName() {
		assertThat(FileStorage.safeName("../../etc/passwd")).isEqualTo("passwd");
		assertThat(FileStorage.safeName("C:\\Users\\x\\RC lot 1.pdf")).isEqualTo("RC lot 1.pdf");
		assertThat(FileStorage.safeName(".hidden")).isEqualTo("hidden");
		assertThat(FileStorage.safeName("a<b>c?.pdf")).isEqualTo("a_b_c_.pdf");
		assertThat(FileStorage.safeName("")).isEqualTo("fichier");
		assertThat(FileStorage.safeName(null)).isEqualTo("fichier");
	}

	@Test
	void dropsPathSegmentsThatCouldLeaveTheDirectory() {
		assertThat(FileStorage.safeSegments("../../etc/passwd")).containsExactly("etc", "passwd");
		assertThat(FileStorage.safeSegments("/abs/./RC.pdf")).containsExactly("abs", "RC.pdf");
		assertThat(FileStorage.safeSegments("C:\\DCE\\CCTP.pdf")).containsExactly("DCE", "CCTP.pdf");
	}

	@Test
	void numbersFilesWithTheSameName() {
		FileStorage storage = storage();

		Path first = storage.store(content("a"), root.resolve("files"), "RC.pdf");
		Path second = storage.store(content("b"), root.resolve("files"), "RC.pdf");

		assertThat(first.getFileName()).hasToString("RC.pdf");
		assertThat(second.getFileName()).hasToString("RC (2).pdf");
		assertThat(Files.exists(second)).isTrue();
	}

	@Test
	void refusesAPathOutsideTheStorageDirectory() {
		FileStorage storage = storage();

		assertThat(storage.resolve("tenders/1/v1/RC.pdf").startsWith(root)).isTrue();
		assertThatThrownBy(() -> storage.resolve("../outside.pdf")).isInstanceOf(IllegalArgumentException.class);
	}

	private FileStorage storage() {
		return new FileStorage(new DocumentsProperties(root.toString(), "soffice", Duration.ofMinutes(1), 10,
				DataSize.ofMegabytes(1), 2));
	}

	private static ByteArrayInputStream content(String text) {
		return new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8));
	}

}
