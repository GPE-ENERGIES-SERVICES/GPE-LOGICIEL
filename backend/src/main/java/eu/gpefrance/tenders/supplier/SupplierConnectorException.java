package eu.gpefrance.tenders.supplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.ErrorResponseException;

/**
 * A service of a supplier that could not answer. The message says what to do;
 * it holds nothing of the request nor of the answer of the supplier.
 */
public class SupplierConnectorException extends ErrorResponseException {

	public enum Kind {

		NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "La connexion à ce fournisseur n'est pas activée ou pas complète."),
		AUTHENTICATION(HttpStatus.BAD_GATEWAY, "Le fournisseur refuse nos identifiants : vérifiez la configuration."),
		REJECTED(HttpStatus.BAD_GATEWAY, "Le fournisseur refuse cette demande : vérifiez les références et le compte client."),
		RATE_LIMITED(HttpStatus.SERVICE_UNAVAILABLE, "Trop de demandes chez le fournisseur : réessayez dans quelques minutes."),
		TEMPORARY(HttpStatus.SERVICE_UNAVAILABLE, "Le fournisseur ne répond pas pour le moment : réessayez plus tard."),
		INVALID_RESPONSE(HttpStatus.BAD_GATEWAY, "Réponse du fournisseur illisible : aucun prix n'a été modifié.");

		private final HttpStatus status;

		private final String message;

		Kind(HttpStatus status, String message) {
			this.status = status;
			this.message = message;
		}

	}

	private final Kind kind;

	public SupplierConnectorException(Kind kind) {
		super(kind.status, ProblemDetail.forStatusAndDetail(kind.status, kind.message), null);
		this.kind = kind;
	}

	public Kind getKind() {
		return kind;
	}

}
