package eu.gpefrance.tenders.analysis;

/**
 * Why an analysis did not succeed, stored with it. Only the failures that
 * may pass by themselves are tried again; a missing configuration, a refused
 * key or a budget reached never are: retrying would change nothing.
 */
public enum FailureKind {

	/** No provider enabled, selected or ready (key missing). */
	NOT_CONFIGURED(false, "Aucun fournisseur d'IA n'est activé"),

	/** The provider refused the key. */
	AUTHENTICATION(false, "Clé du fournisseur d'IA refusée"),

	/** Too many requests: the provider asks to wait. */
	RATE_LIMITED(true, "Limite de débit du fournisseur atteinte"),

	/** Quota or credit exhausted at the provider. */
	QUOTA_EXCEEDED(false, "Quota ou crédit épuisé chez le fournisseur"),

	/** Monthly ceiling of GPE reached (local estimate). */
	BUDGET_REACHED(false, "Plafond mensuel de dépense atteint"),

	/** No answer in time. */
	TIMEOUT(true, "Délai de réponse du fournisseur dépassé"),

	/** Server error or network failure of the provider. */
	TEMPORARY(true, "Erreur temporaire du fournisseur"),

	/** The answer is not the expected JSON. */
	INVALID_RESPONSE(false, "Réponse de l'IA non conforme au format attendu"),

	/** The documents cannot be used (empty, unreadable). */
	UNUSABLE_DOCUMENT(false, "Documents inexploitables"),

	/** The provider refused to process the content. */
	REFUSED(false, "Analyse refusée par le fournisseur"),

	/** The text does not fit in the context of the model. */
	CONTEXT_TOO_LARGE(false, "Texte trop long pour le modèle"),

	/** Interrupted too many times (stops, lease expired). */
	INTERRUPTED(false, "Analyse interrompue à plusieurs reprises"),

	/** Unexpected error of the application. */
	UNEXPECTED(false, "Erreur inattendue");

	private final boolean retryable;

	private final String label;

	FailureKind(boolean retryable, String label) {
		this.retryable = retryable;
		this.label = label;
	}

	public boolean retryable() {
		return retryable;
	}

	public String label() {
		return label;
	}

}
