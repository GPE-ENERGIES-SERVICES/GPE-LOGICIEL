package eu.gpefrance.tenders.response;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;

/**
 * The places of a form found in its text, the rest left as the buyer wrote it,
 * and what engages the candidate never filled.
 */
class FormDocumentTests {

	/** A small form, as the real ones write it. */
	static byte[] form() throws IOException {
		try (XWPFDocument word = new XWPFDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
			line(word, "Objet : réfection de la toiture, écrit par l'acheteur");
			line(word, "1. Objet du marché\t4");
			line(word, "SIRET : ..............................");
			line(word, "Adresse de l'établissement :");
			line(word, "……………………………………………");
			line(word, "……………………………………………");
			line(word, "Adresse électronique :");
			line(word, "");
			line(word, "");
			line(word, "Montant HT (en chiffres) (€) :");
			line(word, "Lot suivant écrit ensuite");
			line(word, "☐ Refuse de percevoir l'avance");
			line(word, "pour le lot n°……. ou les lots n°…………… de la procédure");
			XWPFTable table = word.createTable(2, 2);
			table.getRow(0).getCell(0).setText("Exercice");
			table.getRow(0).getCell(1).setText("Chiffre d'affaires");
			table.getRow(1).getCell(0).setText("2025");
			line(word, "Le candidat déclare sur l'honneur :");
			line(word, "☐ ne pas entrer dans un cas d'exclusion");
			line(word, "Nom du signataire : ..........");
			line(word, "A - Identification de l'acheteur");
			line(word, "Téléphone : ..........");
			line(word, "Fait à ............, le ............");
			line(word, "7. Signature du candidat");
			line(word, "Signature : ..........");
			word.write(out);
			return out.toByteArray();
		}
	}

	private static void line(XWPFDocument word, String text) {
		word.createParagraph().createRun().setText(text);
	}

	private static FormDocument open() throws IOException {
		return FormDocument.open(new ByteArrayInputStream(form()));
	}

	private static FormDocument.Slot slot(List<FormDocument.Slot> slots, String words, FormDocument.Kind kind) {
		return slots.stream()
			.filter((slot) -> slot.kind() == kind && slot.label().endsWith(words))
			.findFirst()
			.orElseThrow(() -> new AssertionError(words + " not in " + slots));
	}

	@Test
	void findsThePlacesToFillAndNothingInTheTableOfContents() throws IOException {
		try (FormDocument form = open()) {
			List<FormDocument.Slot> slots = form.slots();
			assertThat(slot(slots, "SIRET : …", FormDocument.Kind.TEXT).locked()).isFalse();
			assertThat(slot(slots, "Adresse de l'établissement : …", FormDocument.Kind.TEXT).locked()).isFalse();
			assertThat(slot(slots, "Adresse électronique :", FormDocument.Kind.TEXT).locked()).isFalse();
			assertThat(slot(slots, "Montant HT (en chiffres) (€) :", FormDocument.Kind.TEXT).locked()).isFalse();
			assertThat(slot(slots, "Refuse de percevoir l'avance", FormDocument.Kind.CHECK).locked()).isFalse();
			assertThat(slots).noneMatch((slot) -> slot.label().contains("écrit par l'acheteur") && slot.label().endsWith("acheteur"));
			assertThat(slots).noneMatch((slot) -> slot.label().contains("Objet du marché"));
			// One answer for the three lines of dots, one for the two blank lines.
			assertThat(slots.stream().filter((slot) -> slot.label().contains("Adresse de l'établissement") && slot.label().endsWith("…"))).hasSize(1);
			assertThat(slots.stream().filter((slot) -> slot.label().endsWith("Adresse électronique :"))).hasSize(1);
			// Short dots, and an empty cell with its row and column.
			assertThat(slots.stream().filter((slot) -> slot.label().contains("pour le lot n°"))
				.filter((slot) -> slot.kind() == FormDocument.Kind.TEXT)).hasSize(2);
			assertThat(slots).anyMatch((slot) -> slot.label().equals("Tableau : 2025 / Chiffre d'affaires"));
		}
	}

	@Test
	void locksWhatEngagesTheCandidateAndUnlocksAtTheNextPart() throws IOException {
		try (FormDocument form = open()) {
			List<FormDocument.Slot> slots = form.slots();
			assertThat(slot(slots, "ne pas entrer dans un cas d'exclusion", FormDocument.Kind.CHECK).locked()).isTrue();
			assertThat(slot(slots, "Nom du signataire : …", FormDocument.Kind.TEXT).locked()).isTrue();
			assertThat(slot(slots, "Téléphone : …", FormDocument.Kind.TEXT).locked()).isFalse();
			assertThat(slots.stream().filter((slot) -> slot.label().contains("Fait à")).allMatch(FormDocument.Slot::locked)).isTrue();
			assertThat(slot(slots, "Signature : …", FormDocument.Kind.TEXT).locked()).isTrue();
		}
	}

	@Test
	void writesTheValuesAndNeverALockedPlace() throws IOException {
		Map<String, String> texts = new HashMap<>();
		Map<String, Boolean> boxes = new HashMap<>();
		byte[] filled;
		try (FormDocument form = open()) {
			for (FormDocument.Slot slot : form.slots()) {
				if (slot.kind() == FormDocument.Kind.CHECK) {
					boxes.put(slot.id(), true);
				}
				else {
					texts.put(slot.id(), "VALEUR");
				}
			}
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			form.fill(texts, boxes);
			form.write(out);
			filled = out.toByteArray();
		}
		try (XWPFDocument word = new XWPFDocument(new ByteArrayInputStream(filled))) {
			List<String> lines = word.getParagraphs().stream().map(XWPFParagraph::getText).toList();
			assertThat(lines).contains("SIRET : VALEUR", "☒ Refuse de percevoir l'avance", "pour le lot n°VALEUR ou les lots n°VALEUR de la procédure",
					"Objet : réfection de la toiture, écrit par l'acheteur");
			assertThat(lines).contains("☐ ne pas entrer dans un cas d'exclusion", "Nom du signataire : ..........",
					"Fait à ............, le ............", "Signature : ..........");
			assertThat(word.getTables().getFirst().getRow(1).getCell(1).getText()).isEqualTo("VALEUR");
			assertThat(lines).doesNotContain("……………………………………………");
		}
	}

	@Test
	void keepsTheSamePlacesForTheSameFile() throws IOException {
		try (FormDocument first = open(); FormDocument second = open()) {
			assertThat(first.slots()).isEqualTo(second.slots());
		}
	}

}
