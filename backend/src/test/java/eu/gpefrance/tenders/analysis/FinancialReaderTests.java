package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import eu.gpefrance.tenders.analysis.FinancialResult.Point;

class FinancialReaderTests {

	private static final Map<Long, Map<Integer, String>> PAGES = Map.of(
			7L, Map.of(1, """
					Feuille « DPGF Lot 1 », lignes 3 à 5
					L3 | A3 "N°" | B3 "Désignation" | D3 "Qté"
					L4 | A4 "1.1" | B4 "Décapage de terre végétale" | D4 1200
					L5 | A5 "1.2" | B5 "Déblais en pleine masse" | D5 350"""),
			8L, Map.of(2, "Article 3 - Le DQE est fourni à titre indicatif et n'a pas valeur contractuelle."));

	@Test
	void verifiesEachCitedTextInTheRowOrOnThePageGiven() {
		FinancialResult result = FinancialReader.read("""
				{"overview": "Un DPGF de terrassement.", "limits": ["Prix non remplis"],
				 "items": [
				  {"text": "Décapage 1200 m2", "document_id": 7, "page": 1, "row": 4, "quote": "Décapage de terre végétale"},
				  {"text": "Déblais 350 m3", "document_id": 7, "page": 1, "row": 4, "quote": "Déblais en pleine masse"},
				  {"text": "Poste inventé", "document_id": 7, "page": 1, "row": 99, "quote": "Fondations spéciales"}],
				 "points": [
				  {"text": "DQE indicatif", "document_id": 8, "page": 2, "row": null, "quote": "fourni à titre indicatif"},
				  {"text": "Pièce inconnue", "document_id": 99, "page": 1, "row": 1, "quote": "x"},
				  {"text": "Sans source", "document_id": null, "page": null, "row": null, "quote": null}]}""",
				PAGES, List.of(), List.of());

		assertThat(result.overview()).isEqualTo("Un DPGF de terrassement.");
		assertThat(result.items())
			.extracting(Point::text, Point::sheet, Point::row, Point::verified, Point::quote)
			.containsExactly(tuple("Décapage 1200 m2", "DPGF Lot 1", 4, true, "Décapage de terre végétale"),
					tuple("Déblais 350 m3", "DPGF Lot 1", 4, false, null),
					tuple("Poste inventé", "DPGF Lot 1", 99, false, null));
		assertThat(result.points())
			.extracting(Point::documentId, Point::page, Point::sheet, Point::verified)
			.containsExactly(tuple(8L, 2, null, true), tuple(null, null, null, false), tuple(null, null, null, false));
		assertThat(result.limits()).containsExactly("Prix non remplis");
	}

	@Test
	void cutsTheListsAndRefusesAnAnswerWithoutThem() {
		String many = "{\"text\": \"x\", \"document_id\": null, \"page\": null, \"row\": null, \"quote\": null},"
			.repeat(20);
		FinancialResult result = FinancialReader.read("{\"overview\": \"o\", \"items\": [" + many + "{\"text\": \"y\"}],"
				+ " \"points\": [" + many + "{\"text\": \"y\"}], \"limits\": [\"a\", \"b\", \"c\", \"d\", \"e\"]}", PAGES,
				List.of(), List.of());

		assertThat(result.items()).hasSize(FinancialReader.MAX_ITEMS);
		assertThat(result.points()).hasSize(FinancialReader.MAX_POINTS);
		assertThat(result.limits()).hasSize(FinancialReader.MAX_LIMITS);
		assertThatThrownBy(() -> FinancialReader.read("{\"fields\": []}", PAGES, List.of(), List.of()))
			.isInstanceOf(AnswerReader.UnreadableAnswerException.class);
	}

}
