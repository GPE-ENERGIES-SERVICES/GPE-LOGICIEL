package eu.gpefrance.tenders.analysis;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.gpefrance.tenders.analysis.AnalysisReport.FieldQuality;
import eu.gpefrance.tenders.analysis.AnalysisReport.JournalEntry;
import eu.gpefrance.tenders.analysis.AnalysisReport.Quality;
import eu.gpefrance.tenders.analysis.AnalysisRepository.TokenTotals;
import eu.gpefrance.tenders.analysis.ExtractedFieldRepository.FieldCount;
import eu.gpefrance.tenders.document.Dossier;
import eu.gpefrance.tenders.document.DossierRepository;
import eu.gpefrance.tenders.tender.Tender;
import eu.gpefrance.tenders.tender.TenderRepository;

/**
 * Journal of the analyses and measure of their quality, for the
 * administrator.
 */
@Service
public class AnalysisReports {

	private final AnalysisRepository analyses;

	private final ExtractedFieldRepository fields;

	private final DossierRepository dossiers;

	private final TenderRepository tenders;

	private final AnalysisProperties properties;

	public AnalysisReports(AnalysisRepository analyses, ExtractedFieldRepository fields, DossierRepository dossiers,
			TenderRepository tenders, AnalysisProperties properties) {
		this.analyses = analyses;
		this.fields = fields;
		this.dossiers = dossiers;
		this.tenders = tenders;
		this.properties = properties;
	}

	@Transactional(readOnly = true)
	public List<JournalEntry> journal(int limit) {
		List<Analysis> latest = analyses.findAllByOrderByIdDesc(PageRequest.of(0, limit));
		Map<Long, Tender> tendersById = byId(tenders.findAllById(ids(latest, Analysis::getTenderId)), Tender::getId);
		Map<Long, Dossier> dossiersById = byId(dossiers.findAllById(ids(latest, Analysis::getDossierId)),
				Dossier::getId);
		return latest.stream()
			.map(analysis -> new JournalEntry(analysis.getId(), analysis.getTenderId(),
					tendersById.containsKey(analysis.getTenderId()) ? tendersById.get(analysis.getTenderId()).getTitle()
							: null,
					dossiersById.containsKey(analysis.getDossierId())
							? dossiersById.get(analysis.getDossierId()).getVersion() : 0,
					analysis.getStatus(), analysis.getModel(), analysis.getPromptVersion(), analysis.getCreatedAt(),
					analysis.getFinishedAt(), analysis.getErrorMessage(), analysis.getInputTokens(),
					analysis.getOutputTokens(), analysis.getInputTokens() == null ? null
							: properties.cost(analysis.getInputTokens(), analysis.getOutputTokens())))
			.toList();
	}

	@Transactional(readOnly = true)
	public Quality quality() {
		long done = analyses.countByStatus(AnalysisStatus.DONE);
		TokenTotals tokens = analyses.sumTokens();
		BigDecimal totalCost = properties.cost(tokens.getInput(), tokens.getOutput());
		BigDecimal averageCost = totalCost == null || done == 0 ? null
				: totalCost.divide(BigDecimal.valueOf(done), 4, RoundingMode.HALF_UP);
		Map<String, List<FieldCount>> counts = fields.countAnswers()
			.stream()
			.collect(Collectors.groupingBy(FieldCount::getKey));
		List<FieldQuality> byField = ExtractionGrid.FIELDS.stream().map(field -> {
			List<FieldCount> rows = counts.getOrDefault(field.key(), List.of());
			return new FieldQuality(field.key(), field.label(), sum(rows, row -> true),
					sum(rows, row -> row.getState() == FieldState.VERIFIED),
					sum(rows, row -> row.getState() == FieldState.NOT_INDICATED),
					sum(rows, row -> row.getDecision() == FieldDecision.VALIDATED),
					sum(rows, row -> row.getDecision() == FieldDecision.CORRECTED),
					sum(rows, row -> row.getDecision() == FieldDecision.REJECTED));
		}).toList();
		return new Quality(done, analyses.countByStatus(AnalysisStatus.FAILED), totalCost, averageCost, byField);
	}

	private static long sum(List<FieldCount> rows, Predicate<FieldCount> filter) {
		return rows.stream().filter(filter).mapToLong(FieldCount::getTotal).sum();
	}

	private static <T> List<Long> ids(Collection<T> items, Function<T, Long> id) {
		return items.stream().map(id).distinct().toList();
	}

	private static <T> Map<Long, T> byId(List<T> items, Function<T, Long> id) {
		return items.stream().collect(Collectors.toMap(id, Function.identity()));
	}

}
