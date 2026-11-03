package eu.gpefrance.tenders.analysis;

import java.time.Clock;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

/**
 * Human validation of the summary sheet: each value can be validated,
 * corrected or rejected, then the whole sheet validated. The decision of a
 * user prevails over the model (rule RM10).
 */
@Service
public class ValidationService {

	private final ExtractedFieldRepository fields;

	private final AnalysisRepository analyses;

	private final Clock clock;

	public ValidationService(ExtractedFieldRepository fields, AnalysisRepository analyses, Clock clock) {
		this.fields = fields;
		this.analyses = analyses;
		this.clock = clock;
	}

	/**
	 * @return the tender of the field
	 */
	@Transactional
	public Long decide(Long fieldId, FieldDecision decision, String correctedValue, String user) {
		ExtractedField field = fields.findById(fieldId).orElseThrow(() -> new AnalysisNotFoundException(fieldId));
		String value = correctedValue == null ? null : correctedValue.strip();
		if (decision == FieldDecision.CORRECTED && (value == null || value.isEmpty())) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Saisissez la valeur corrigée.");
		}
		field.decide(decision, value, user, clock.instant());
		return field.getAnalysis().getTenderId();
	}

	/**
	 * @return the tender of the analysis
	 */
	@Transactional
	public Long validateSheet(Long analysisId, String user) {
		Analysis analysis = analyses.findById(analysisId).orElseThrow(() -> new AnalysisNotFoundException(analysisId));
		analysis.validate(user, clock.instant());
		return analysis.getTenderId();
	}

}
