package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import eu.gpefrance.tenders.analysis.AnswerReader.Proposal;
import eu.gpefrance.tenders.analysis.AnswerReader.UnreadableAnswerException;

class AnswerReaderTests {

	@Test
	void readsEachAnswerWithItsSource() {
		List<Proposal> proposals = AnswerReader.read("""
				{"fields": [
				  {"key": "deadline", "status": "FOUND", "value": "2026-11-15T12:00",
				   "quote": "avant le 15 novembre 2026 à 12h00", "document_id": 7, "page": 4},
				  {"key": "site_visit", "status": "NOT_INDICATED", "value": "obligatoire", "quote": "x"},
				  {"key": "variants", "status": "AMBIGUOUS", "value": "autorisées", "quote": "variantes",
				   "document_id": "7", "page": "2"}
				]}""", Set.of(7L));

		assertThat(proposals).filteredOn(proposal -> !proposal.key().equals("lots") || proposal.value() != null)
			.extracting(Proposal::key, Proposal::state, Proposal::value, Proposal::documentId, Proposal::page)
			.contains(tuple("deadline", FieldState.UNVERIFIED, "2026-11-15T12:00", 7L, 4),
					tuple("site_visit", FieldState.NOT_INDICATED, null, null, null),
					tuple("variants", FieldState.AMBIGUOUS, "autorisées", 7L, 2));
	}

	@Test
	void keepsAGridFieldLeftWithoutAnswerAsAValueToCheck() {
		List<Proposal> proposals = AnswerReader.read("{\"fields\": []}", Set.of());

		assertThat(proposals).hasSize(ExtractionGrid.FIELDS.size())
			.allSatisfy(proposal -> assertThat(proposal.state()).isEqualTo(FieldState.UNVERIFIED))
			.allSatisfy(proposal -> assertThat(proposal.value()).isNull());
	}

	@Test
	void keepsEveryItemOfAListButOnlyTheFirstAnswerOfASingleField() {
		List<Proposal> proposals = AnswerReader.read("""
				{"fields": [
				  {"key": "lots", "status": "FOUND", "value": "Lot 1 : électricité"},
				  {"key": "lots", "status": "FOUND", "value": "Lot 2 : plomberie"},
				  {"key": "object", "status": "FOUND", "value": "Rénovation"},
				  {"key": "object", "status": "FOUND", "value": "Autre"}
				]}""", Set.of());

		assertThat(proposals).filteredOn(proposal -> proposal.key().equals("lots"))
			.extracting(Proposal::value)
			.containsExactly("Lot 1 : électricité", "Lot 2 : plomberie");
		assertThat(proposals).filteredOn(proposal -> proposal.key().equals("object"))
			.extracting(Proposal::value)
			.containsExactly("Rénovation");
	}

	@Test
	void dropsUnknownKeysAndSourcesOutsideTheDocumentsSent() {
		List<Proposal> proposals = AnswerReader.read("""
				Voici la réponse :
				```json
				{"fields": [
				  {"key": "invented", "status": "FOUND", "value": "x"},
				  {"key": "buyer", "status": "FOUND", "value": "Ville de Lyon", "quote": "Ville de Lyon",
				   "document_id": 99, "page": 1}
				]}
				```""", Set.of(7L));

		assertThat(proposals).extracting(Proposal::key).doesNotContain("invented");
		assertThat(proposals).filteredOn(proposal -> proposal.key().equals("buyer"))
			.extracting(Proposal::value, Proposal::documentId, Proposal::page)
			.containsExactly(tuple("Ville de Lyon", null, null));
	}

	@Test
	void refusesAnAnswerThatIsNotTheExpectedJson() {
		assertThatThrownBy(() -> AnswerReader.read("Je ne peux pas répondre.", Set.of()))
			.isInstanceOf(UnreadableAnswerException.class)
			.hasMessageContaining("illisible");
		assertThatThrownBy(() -> AnswerReader.read("{\"answer\": 1}", Set.of()))
			.isInstanceOf(UnreadableAnswerException.class);
	}

}
