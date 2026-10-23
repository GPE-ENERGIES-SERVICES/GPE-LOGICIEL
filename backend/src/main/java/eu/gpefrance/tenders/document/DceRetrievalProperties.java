package eu.gpefrance.tenders.document;

import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.unit.DataSize;

/**
 * Retrieval of the consultation files (DCE) of the eligible tenders.
 * @param allowedPlatforms platforms (host without "www.", exactly as in
 * {@code dce_link.platform}) whose terms allow an automated retrieval, after
 * their written agreement; empty by default: nothing is retrieved. The
 * platforms that refused it can never be listed.
 * @param workerEnabled whether the worker takes the queued links; false by
 * default
 * @param concurrency retrievals at most at the same time
 * @param pollInterval wait between two looks at the queue
 * @param maxAttempts attempts at most of a link before a permanent error
 * @param retryDelay wait after the first temporary error, doubled at each
 * following one
 * @param maxRetryDelay longest wait between two attempts
 * @param lease time after which a link left in progress (worker stopped) is
 * released
 * @param maxFileSize largest file, and largest retrieval, accepted
 * @param quota space at most of all the retrieved files on the storage
 * directory, in bytes (3 GB = 3,000,000,000 bytes): received files, archives
 * and extracted files of the retrieved dossiers, and the retrievals in
 * progress. Not a limit of the disk, nor of the uploaded dossiers.
 * @param connectTimeout timeout to connect, for the HTTP retrievals
 * @param readTimeout timeout between two reads, for the HTTP retrievals
 * @param maxRedirects redirections followed at most, each one checked
 */
@ConfigurationProperties("dce.retrieval")
public record DceRetrievalProperties(@DefaultValue Set<String> allowedPlatforms,
		@DefaultValue("false") boolean workerEnabled, @DefaultValue("2") int concurrency,
		@DefaultValue("30s") Duration pollInterval, @DefaultValue("5") int maxAttempts,
		@DefaultValue("10m") Duration retryDelay, @DefaultValue("6h") Duration maxRetryDelay,
		@DefaultValue("30m") Duration lease, @DefaultValue("500MB") DataSize maxFileSize,
		@DefaultValue("3000000000B") DataSize quota, @DefaultValue("10s") Duration connectTimeout,
		@DefaultValue("60s") Duration readTimeout, @DefaultValue("5") int maxRedirects) {

	/**
	 * Platforms whose owner refused an automated access (AW Solutions,
	 * 21/10/2026) or which publish no consultation file (APProch).
	 */
	static final Set<String> REFUSED_PLATFORMS = Set.of("marches-publics.info", "aws-achat.info",
			"dl.aws-achat.info", "projets-achats.marches-publics.gouv.fr");

	public DceRetrievalProperties {
		allowedPlatforms = allowedPlatforms.stream()
			.map(platform -> platform.strip().toLowerCase(Locale.ROOT))
			.filter(platform -> !platform.isEmpty())
			.collect(Collectors.toUnmodifiableSet());
		Set<String> refused = allowedPlatforms.stream().filter(REFUSED_PLATFORMS::contains).collect(Collectors.toSet());
		if (!refused.isEmpty()) {
			throw new IllegalArgumentException("dce.retrieval.allowed-platforms may not contain " + refused
					+ ": their automated access is refused or they publish no consultation file");
		}
		if (concurrency < 1 || maxAttempts < 1 || maxRedirects < 0) {
			throw new IllegalArgumentException("dce.retrieval: concurrency and max-attempts must be at least 1");
		}
		if (maxFileSize.toBytes() <= 0 || quota.toBytes() <= 0) {
			throw new IllegalArgumentException("dce.retrieval: max-file-size and quota must be positive");
		}
	}

}
