package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.util.Map;

import org.junit.jupiter.api.Test;

import eu.gpefrance.tenders.analysis.Decision.Check;
import eu.gpefrance.tenders.analysis.Decision.ObstacleKind;
import eu.gpefrance.tenders.analysis.Decision.Point;
import eu.gpefrance.tenders.analysis.Decision.Verdict;

class DecisionReaderTests {

	private static final Map<Long, Map<Integer, String>> PAGES = Map.of(7L,
			Map.of(1, "Règlement de la consultation.", 3,
					"La visite du site est obligatoire sous peine de rejet de l'offre. Qualification Qualibat 1311 exigée."));

	@Test
	void anAnswerWithoutDecisionHasNone() {
		assertThat(DecisionReader.read("{\"fields\": []}", PAGES)).isNull();
	}

	@Test
	void confirmsAnObstacleOnlyWhenItsQuoteIsFoundInTheCitedDocument() {
		Decision decision = DecisionReader.read("""
				{"fields": [], "decision": {"verdict": "EXAMINE", "conclusion": "Intéressant, visite à prévoir.",
				 "strengths": [], "checks": [], "limits": [],
				 "obstacles": [
				  {"text": "Visite obligatoire", "kind": "CONFIRMED", "basis": "DOCUMENT",
				   "quote": "La visite du site est obligatoire sous peine de rejet", "document_id": 7, "page": 1},
				  {"text": "Chiffre d'affaires minimal", "kind": "CONFIRMED", "basis": "DOCUMENT",
				   "quote": "Chiffre d'affaires minimal de 2 000 000 €", "document_id": 7, "page": 3},
				  {"text": "Qualibat exigé", "kind": "CONFIRMED", "basis": "DEDUCTION",
				   "quote": "Qualification Qualibat 1311 exigée", "document_id": 7, "page": 3},
				  {"text": "Pièce citée inconnue", "kind": "CONFIRMED", "basis": "DOCUMENT",
				   "quote": "La visite du site est obligatoire", "document_id": 99, "page": 3},
				  {"text": "Montant non indiqué", "kind": "MISSING", "basis": "DOCUMENT",
				   "quote": null, "document_id": null, "page": null}
				 ]}}""", PAGES);

		assertThat(decision.verdict()).isEqualTo(Verdict.EXAMINE);
		assertThat(decision.obstacles())
			.extracting(Point::text, Point::kind, Point::verified, Point::quote, Point::documentId, Point::page)
			.containsExactly(
					tuple("Visite obligatoire", ObstacleKind.CONFIRMED, true,
							"La visite du site est obligatoire sous peine de rejet", 7L, 3),
					tuple("Chiffre d'affaires minimal", ObstacleKind.TO_CHECK, false, null, 7L, null),
					tuple("Qualibat exigé", ObstacleKind.TO_CHECK, true, "Qualification Qualibat 1311 exigée", 7L, 3),
					tuple("Pièce citée inconnue", ObstacleKind.TO_CHECK, false, null, null, null),
					tuple("Montant non indiqué", ObstacleKind.MISSING, false, null, null, null));
		assertThat(decision.obstacles().get(2).deduced()).isTrue();
	}

	@Test
	void fallsBackToTheMostCarefulVerdictAndCutsTheLists() {
		Decision decision = DecisionReader.read("""
				{"decision": {"verdict": "93 %", "conclusion": "  ",
				 "strengths": [{"text": "a"}, {"text": "b"}, {"text": "c"}, {"text": "d"}, {"text": ""}],
				 "obstacles": [{"text": "x", "kind": "BLOQUANT"}],
				 "checks": [{"action": "Vérifier la qualification", "why": "Exigée", "document_id": 99},
				            {"action": "Lire le CCAP", "why": "Pénalités", "document_id": 7},
				            {"action": "", "why": "vide"}],
				 "limits": ["Capacités de GPE inconnues", 3]}}""", PAGES);

		assertThat(decision.verdict()).isEqualTo(Verdict.INSUFFICIENT);
		assertThat(decision.conclusion()).isNull();
		assertThat(decision.strengths()).extracting(Point::text).containsExactly("a", "b", "c");
		assertThat(decision.obstacles()).extracting(Point::kind).containsExactly(ObstacleKind.TO_CHECK);
		assertThat(decision.checks()).extracting(Check::action, Check::documentId)
			.containsExactly(tuple("Vérifier la qualification", null), tuple("Lire le CCAP", 7L));
		assertThat(decision.limits()).containsExactly("Capacités de GPE inconnues");
	}

}
