package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import eu.gpefrance.tenders.document.DocumentTriage.Decision;
import eu.gpefrance.tenders.document.DocumentTriage.TextStats;
import eu.gpefrance.tenders.document.DocumentTriage.Use;

/**
 * The rules deciding what goes to the AI, on the names of a real
 * consultation file (a swimming pool, 126 files) and on general cases.
 */
class DocumentTriageTests {

	private final List<Document> documents = new ArrayList<>();

	private final Map<Long, TextStats> stats = new HashMap<>();

	@Test
	void sendsTheEssentialAndUsefulPiecesFirstAndNeverPlansDrawingsOrDuplicates() {
		Document rc = text("AWS-MPI-1849325-RC-1.pdf", PieceType.RC, "rc", 28_000, 14);
		Document rcAgain = text("DCE/AWS-MPI-1849325-RC-1.pdf", PieceType.RC, "rc", 28_000, 14);
		Document ccap = text("CCAP_MAPA-2026-24.pdf", PieceType.CCAP, "ccap", 37_000, 18);
		Document cctp = text("CCTP - Couverture.pdf", PieceType.CCTP, "cctp", 59_000, 30);
		Document diagnostic = text("Diag portance charpente INEXOM.pdf", PieceType.OTHER, "diag", 72_000, 25);
		Document prevention = text("Plan de prévention.pdf", PieceType.OTHER, "prev", 9_000, 6);
		Document manual = text("Manuel technique du zinc.pdf", PieceType.OTHER, "zinc", 35_000, 40);
		Document detail = text("Det Faience.pdf", PieceType.OTHER, "faience", 860, 1);
		Document plan = text("Plan MAj Suite relevé sur site.pdf", PieceType.PLAN, "plan", 500, 1);
		Document drawing = text("Calpinage plafond.pdf", PieceType.OTHER, "calpinage", 4_000, 2);
		Document sparse = text("Tableau repartition IndA.pdf", PieceType.OTHER, "sparse", 450, 3);
		Document dpgf = text("DPGF OBLIGATOIRE - LOT N°1.xlsx", PieceType.DPGF, "dpgf", 12_000, 1);
		Document sheet = text("Planning prévisionnel.xlsx", PieceType.OTHER, "xlsx", 3_000, 1);
		Document other = text("Dossier DP - Piscine.pdf", PieceType.OTHER, "dp", 3_300, 2);

		Map<Long, Decision> decisions = DocumentTriage.decide(documents, stats);

		assertThat(List.of(rc, ccap, cctp, diagnostic, prevention)).extracting(document -> decisions.get(document.getId()).use())
			.containsOnly(Use.SUMMARY);
		assertThat(decisions.get(rc.getId()).rank()).isLessThan(decisions.get(ccap.getId()).rank());
		assertThat(decisions.get(cctp.getId()).rank()).isLessThan(decisions.get(diagnostic.getId()).rank());
		assertThat(decisions.get(rcAgain.getId())).extracting(Decision::use, Decision::reason)
			.containsExactly(Use.EXCLUDED, "Doublon de « AWS-MPI-1849325-RC-1 »");
		assertThat(List.of(detail, plan, drawing, sparse, sheet))
			.extracting(document -> decisions.get(document.getId()).use())
			.containsOnly(Use.EXCLUDED);
		assertThat(decisions.get(sparse.getId()).reason()).startsWith("Très peu de texte par page");
		assertThat(decisions.get(dpgf.getId()).use()).isEqualTo(Use.FINANCIAL);
		assertThat(List.of(manual, other)).extracting(document -> decisions.get(document.getId()))
			.extracting(Decision::use, Decision::rank)
			.containsExactly(tuple(Use.IF_ROOM, 30), tuple(Use.IF_ROOM, 20));
	}

	@Test
	void letsAUserChooseAndPutsPagesOnlyReadByOcrLast() {
		Document excluded = text("CCTP lot 9.pdf", PieceType.CCTP, "a", 20_000, 10);
		excluded.chooseForAi(AiChoice.EXCLUDE);
		Document forced = text("Det Bureau.pdf", PieceType.OTHER, "b", 600, 1);
		forced.chooseForAi(AiChoice.INCLUDE);
		Document scanned = text("Courrier du maître d'ouvrage.pdf", PieceType.OTHER, "c", 2_000, 2);
		stats.put(scanned.getId(), new TextStats(2_000, 2, true));
		Document unread = text("Annexe scannée.pdf", PieceType.OTHER, "d", 0, 0);

		Map<Long, Decision> decisions = DocumentTriage.decide(documents, stats);

		assertThat(decisions.get(excluded.getId())).extracting(Decision::use, Decision::reason)
			.containsExactly(Use.EXCLUDED, "Exclu à la main");
		assertThat(decisions.get(forced.getId())).extracting(Decision::use, Decision::reason)
			.containsExactly(Use.SUMMARY, "Ajouté à la main");
		assertThat(decisions.get(scanned.getId())).extracting(Decision::use, Decision::rank)
			.containsExactly(Use.IF_ROOM, 40);
		assertThat(decisions.get(unread.getId()).use()).isEqualTo(Use.EXCLUDED);
	}

	@Test
	void readsByOcrOnlyThePiecesThatMayBeSent() {
		Document forced = text("Det Bureau.pdf", PieceType.OTHER, "a", 0, 0);
		forced.chooseForAi(AiChoice.INCLUDE);

		assertThat(DocumentTriage.worthReading(text("RC.pdf", PieceType.RC, "b", 0, 0))).isTrue();
		assertThat(DocumentTriage.worthReading(text("BPU.pdf", PieceType.BPU, "c", 0, 0))).isTrue();
		assertThat(DocumentTriage.worthReading(text("Rapport amiante.pdf", PieceType.OTHER, "d", 0, 0))).isTrue();
		assertThat(DocumentTriage.worthReading(forced)).isTrue();
		assertThat(DocumentTriage.worthReading(text("Det Faience.pdf", PieceType.OTHER, "e", 0, 0))).isFalse();
		assertThat(DocumentTriage.worthReading(text("Carnet A3.pdf", PieceType.OTHER, "f", 0, 0))).isFalse();
		assertThat(DocumentTriage.worthReading(text("Plan masse.pdf", PieceType.PLAN, "g", 0, 0))).isFalse();
		assertThat(DocumentTriage.worthReading(text("Divers.pdf", PieceType.OTHER, "h", 0, 0))).isFalse();
	}

	/** A document read, with its text length and pages, identified by its content. */
	@Test
	void looksForLettersOnlyInImagesThatNothingNamesAsPhotosOrPlans() {
		String pool = "802- Piscine (38).zip/802- Piscine (38)/LOT N°2 - photovoltaïque/";
		Document photo = text(pool + "1 - Dossier photo/20250925_090137.heic", PieceType.OTHER, "a", 0, 0);
		Document plans = text(pool + "Plans/IMG_0042.jpg", PieceType.OTHER, "b", 0, 0);
		Document named = text(pool + "Photo façade nord.jpg", PieceType.OTHER, "c", 0, 0);
		Document scan = text(pool + "Pièces écrites/scan001.png", PieceType.OTHER, "d", 0, 0);
		Document anonymous = text("IMG_2034.jpg", PieceType.OTHER, "e", 0, 0);
		Document cctp = text(pool + "1 - Dossier photo/CCTP page 2.jpg", PieceType.CCTP, "f", 0, 0);

		assertThat(List.of(photo, plans, named)).noneMatch(DocumentTriage::worthProbing);
		assertThat(List.of(scan, anonymous)).allMatch(DocumentTriage::worthProbing);
		// A piece named as such is read whatever the folder holding it.
		assertThat(DocumentTriage.worthReading(cctp)).isTrue();
		assertThat(DocumentTriage.worthReading(anonymous)).isFalse();
	}

	@Test
	void doesNotReadTheTextOfAPlanOrADrawingByItsNameUnlessAsked() {
		Document plan = text("Plan MAj Suite relevé sur site.pdf", PieceType.PLAN, "a", 0, 0);
		Document calpinage = text("Calpinage plafond.pdf", PieceType.OTHER, "b", 0, 0);
		Document facades = text("05 Facades.pdf", PieceType.OTHER, "c", 0, 0);
		Document prevention = text("Plan de prévention.pdf", PieceType.OTHER, "d", 0, 0);
		Document cctp = text("CCTP - Plans et détails.pdf", PieceType.CCTP, "e", 0, 0);
		Document notice = text("Notice acoustique.pdf", PieceType.OTHER, "f", 0, 0);
		Document asked = text("Coupe AA.pdf", PieceType.OTHER, "g", 0, 0);
		asked.chooseForAi(AiChoice.INCLUDE);

		assertThat(List.of(plan, calpinage, facades)).allMatch(DocumentTriage::drawingByName);
		assertThat(List.of(prevention, cctp, notice, asked)).noneMatch(DocumentTriage::drawingByName);
	}

	private Document text(String fileName, PieceType type, String content, long characters, int pages) {
		Document document = new Document(null, fileName, "files/" + fileName, 1000, content, type);
		ReflectionTestUtils.setField(document, "id", (long) documents.size() + 1);
		if (characters > 0) {
			document.markRead(pages, 0, "files/" + fileName);
		}
		documents.add(document);
		stats.put(document.getId(), new TextStats(characters, pages, false));
		return document;
	}

}
