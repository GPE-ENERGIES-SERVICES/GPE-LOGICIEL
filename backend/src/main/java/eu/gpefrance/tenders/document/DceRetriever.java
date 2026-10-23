package eu.gpefrance.tenders.document;

import java.io.InputStream;
import java.util.List;

/**
 * Retrieves the consultation file from the platform of a {@link DceLink},
 * for one platform whose terms allow it (one implementation per platform: no
 * platform works like another). None exists yet: every link is
 * {@link DceLinkStatus#AVAILABLE} and the file is obtained by hand, then
 * uploaded.
 * <p>
 * Contract for a future implementation:
 * <ul>
 * <li>the retrieved files become a new version of the dossier of the tender,
 * {@link DossierOrigin#RETRIEVED}, linked to the {@link DceLink}, kept in
 * the {@link FileStorage} and read like an upload: the database only keeps
 * their metadata;</li>
 * <li>a platform that asks for an account, an identification or a CAPTCHA is
 * never bypassed: the link becomes {@link DceLinkStatus#ACCESS_REQUIRED};
 * company credentials, if a platform allows their use by a program, come
 * from the environment;</li>
 * <li>a failure that may pass throws {@link TemporaryRetrievalException}: the
 * link is tried again later a limited number of times; a file that no longer
 * exists throws {@link LinkUnavailableException};</li>
 * <li>an HTTP retriever uses {@link SafeHttpFetcher};</li>
 * <li>a file identical to one of the previous version (same SHA-256) does not
 * make a new version.</li>
 * </ul>
 */
public interface DceRetriever {

	/** Whether this retriever handles the platform of the link, such as marches-publics.info. */
	boolean supports(DceLink link);

	/**
	 * @return the files of the consultation file, as published on the platform
	 * @throws AccessRequiredException when the platform asks for an access the
	 * application may not give
	 */
	List<RetrievedFile> retrieve(DceLink link);

	/**
	 * @param url the address of the file on the platform, if it has one
	 */
	record RetrievedFile(String fileName, String url, InputStream content) {
	}

	class AccessRequiredException extends RuntimeException {

		public AccessRequiredException(String reason) {
			super(reason);
		}

	}

	/** The address answers that the file does not exist, or no longer: not tried again. */
	class LinkUnavailableException extends RuntimeException {

		public LinkUnavailableException(String reason) {
			super(reason);
		}

	}

	/** A failure that may pass (server error, timeout, connection lost): tried again later. */
	class TemporaryRetrievalException extends RuntimeException {

		public TemporaryRetrievalException(String reason, Throwable cause) {
			super(reason, cause);
		}

	}

}
