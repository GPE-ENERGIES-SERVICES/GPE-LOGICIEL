package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class PieceClassifierTests {

	@ParameterizedTest
	@CsvSource({
			"RC.pdf, RC",
			"2026-014_RC_signé.pdf, RC",
			"Règlement de la consultation.pdf, RC",
			"Reglement_du_consultation.docx, RC",
			"CCAP lot 2.pdf, CCAP",
			"Cahier des clauses administratives particulieres.pdf, CCAP",
			"DCE.zip/Pièces techniques/CCTP_Electricite.pdf, CCTP",
			"Annexe 1 au CCTP.pdf, CCTP",
			"AE_lot1.docx, AE",
			"Acte d'engagement.pdf, AE",
			"ATTRI1.docx, AE",
			"DPGF lot 05.xlsx, DPGF",
			"BPU.xls, BPU",
			"Bordereau des prix unitaires.xlsx, BPU",
			"DQE.xlsx, DQE",
			"Plan RDC.pdf, PLAN",
			"facade.dwg, PLAN",
			"AAPC.pdf, NOTICE",
			"Avis de publicité.pdf, NOTICE",
			"Planning previsionnel.pdf, OTHER",
			"Arcade.pdf, OTHER",
			"mémoire technique cadre.docx, OTHER" })
	void guessesThePieceTypeFromTheFileName(String fileName, PieceType expected) {
		assertThat(PieceClassifier.classify(fileName)).isEqualTo(expected);
	}

}
