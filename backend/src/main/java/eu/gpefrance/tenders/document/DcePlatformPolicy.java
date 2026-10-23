package eu.gpefrance.tenders.document;

import java.util.List;
import java.util.Optional;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Whether the consultation file of a link may be retrieved by a program:
 * only when its platform is in {@code dce.retrieval.allowed-platforms} and a
 * {@link DceRetriever} handles it. Checked on the server, when a link is
 * queued and again just before its retrieval. There is no fallback: an
 * address that looks like a file is not an authorisation, and an unknown
 * platform is never contacted.
 */
@Component
public class DcePlatformPolicy {

	private final DceRetrievalProperties properties;

	private final List<DceRetriever> retrievers;

	public DcePlatformPolicy(DceRetrievalProperties properties, ObjectProvider<DceRetriever> retrievers) {
		this.properties = properties;
		this.retrievers = retrievers.orderedStream().toList();
	}

	public Decision decide(DceLink link) {
		String platform = link.getPlatform();
		if (platform == null) {
			return Decision.refused("Adresse sans plateforme identifiable : accès automatisé non autorisé.");
		}
		if (!properties.allowedPlatforms().contains(platform)) {
			return Decision.refused(properties.allowedPlatforms().isEmpty()
					? "Accès automatisé non autorisé : aucune plateforme n'est autorisée."
					: "Accès automatisé non autorisé pour " + platform + ".");
		}
		Optional<DceRetriever> retriever = retrievers.stream().filter(candidate -> candidate.supports(link)).findFirst();
		return retriever.map(Decision::allowed)
			.orElseGet(() -> Decision.refused("Plateforme " + platform + " autorisée mais sans connecteur."));
	}

	public boolean isAllowed(String platform) {
		return platform != null && properties.allowedPlatforms().contains(platform);
	}

	/**
	 * @param retriever the retriever to use, null when refused
	 * @param reason why it is refused, null when allowed
	 */
	public record Decision(DceRetriever retriever, String reason) {

		static Decision allowed(DceRetriever retriever) {
			return new Decision(retriever, null);
		}

		static Decision refused(String reason) {
			return new Decision(null, reason);
		}

		public boolean isAllowed() {
			return retriever != null;
		}

	}

}
