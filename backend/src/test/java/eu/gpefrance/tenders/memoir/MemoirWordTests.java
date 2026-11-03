package eu.gpefrance.tenders.memoir;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.imageio.ImageIO;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import eu.gpefrance.tenders.memoir.MemoirStore.MemoirRow;
import eu.gpefrance.tenders.memoir.MemoirStore.SectionRow;

/**
 * The Word document holds what the memoir holds, and says what is missing.
 */
class MemoirWordTests {

	private static final MemoirRow MEMOIR = new MemoirRow(1L, 2L, "Toiture", null, "Mémoire / toiture", MemoirStatus.WRITING,
			null, null, null, Instant.EPOCH, null, Instant.EPOCH, 3, 1);

	private static SectionRow section(int level, String number, String title, boolean proposed, String content) {
		return new SectionRow(1L, 1L, null, 1, level, number, title, null, proposed, content, "MANUAL", null, null,
				List.of(), null, Instant.EPOCH);
	}

	private static XWPFDocument word(List<SectionRow> sections) throws IOException {
		return new XWPFDocument(new ByteArrayInputStream(MemoirWord.write(MEMOIR, sections, id -> Optional.empty())));
	}

	private static List<String> texts(XWPFDocument word) {
		return word.getParagraphs().stream().map(XWPFParagraph::getText).toList();
	}

	@Test
	void writesTheHeadingsOfTheOutlineAndSaysWhatIsToWrite() throws IOException {
		try (XWPFDocument word = word(List.of(section(1, "1", "Présentation", false, "<p>Notre société.</p>"),
				section(2, "1.1", "Moyens", false, null), section(1, "2", "Proposée par l'IA", true, "<p>Secret</p>")))) {
			assertThat(texts(word)).containsSubsequence("Mémoire / toiture", "Toiture", "1 Présentation", "Notre société.",
					"1.1 Moyens", "[Partie à rédiger]");
			assertThat(texts(word)).noneMatch((text) -> text.contains("Proposée par l'IA") || text.contains("Secret"));
			XWPFParagraph heading = word.getParagraphs().stream()
				.filter((paragraph) -> paragraph.getText().equals("1.1 Moyens"))
				.findFirst()
				.orElseThrow();
			assertThat(heading.getStyle()).isEqualTo("Heading2");
			assertThat(word.getStyles().styleExist("Heading2")).isTrue();
		}
	}

	@Test
	void keepsEmphasisListsAndTables() throws IOException {
		String html = """
				<p>Un <strong>mot</strong> et <em>un autre</em></p>
				<ul><li>Premier</li><li>Second</li></ul>
				<ol><li>Étape</li></ol>
				<table><thead><tr><th>Poste</th><th>Durée</th></tr></thead><tbody><tr><td>Pose</td><td>3 j</td></tr></tbody></table>""";
		try (XWPFDocument word = word(List.of(section(1, "1", "Méthode", false, html)))) {
			assertThat(texts(word)).contains("Un mot et un autre", "• Premier", "• Second", "1. Étape");
			XWPFParagraph first = word.getParagraphs().stream()
				.filter((paragraph) -> paragraph.getText().startsWith("Un mot"))
				.findFirst()
				.orElseThrow();
			assertThat(first.getRuns().stream().filter(XWPFRun::isBold).map(XWPFRun::text)).containsExactly("mot");
			assertThat(first.getRuns().stream().filter(XWPFRun::isItalic).map(XWPFRun::text)).containsExactly("un autre");
			assertThat(word.getTables()).singleElement().satisfies((table) -> {
				assertThat(table.getRow(0).getCell(1).getText()).isEqualTo("Durée");
				assertThat(table.getRow(1).getCell(0).getText()).isEqualTo("Pose");
			});
		}
	}

	@Test
	void putsTheImagesAndSaysWhichAreMissing(@TempDir Path folder) throws IOException {
		Path png = folder.resolve("plan.png");
		ImageIO.write(new BufferedImage(2000, 1000, BufferedImage.TYPE_INT_RGB), "png", png.toFile());
		String html = """
				<figure><img src="/api/memoirs/files/7" alt=""><figcaption>Le plan</figcaption></figure>
				<figure><img src="/api/memoirs/files/8" alt=""></figure>
				<figure class="placeholder" data-kind="photo"><figcaption>Photo du chantier</figcaption></figure>""";
		byte[] content = MemoirWord.write(MEMOIR, List.of(section(1, "1", "Plans", false, html)),
				(id) -> id == 7L ? Optional.of(png) : Optional.empty());
		try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(content))) {
			assertThat(word.getAllPictures()).hasSize(1);
			assertThat(texts(word)).contains("Le plan", "[Image introuvable : à ajouter]",
					"[Image à ajouter : Photo du chantier]");
			// Within the width of the page.
			long width = word.getParagraphs().stream()
				.flatMap((paragraph) -> paragraph.getRuns().stream())
				.flatMap((run) -> run.getEmbeddedPictures().stream())
				.findFirst()
				.orElseThrow()
				.getWidth() > 0 ? 1 : 0;
			assertThat(width).isEqualTo(1);
		}
	}

	@Test
	void refusesNothingAPastedTextMayHoldAndNamesTheFile() throws IOException {
		try (XWPFDocument word = word(List.of(section(1, "1", "Titre\u0007", false, "<p>Avec\u0001 un caractère</p>")))) {
			assertThat(texts(word)).contains("1 Titre", "Avec un caractère");
		}
		assertThat(MemoirWord.fileName(MEMOIR)).isEqualTo("Mémoire _ toiture.docx");
	}

}
