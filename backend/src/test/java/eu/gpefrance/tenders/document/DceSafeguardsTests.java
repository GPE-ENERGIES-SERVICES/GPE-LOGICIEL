package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import eu.gpefrance.tenders.document.FileTypeValidator.FileRejectedException;
import eu.gpefrance.tenders.document.SafeHttpFetcher.ForbiddenAddressException;

/**
 * The safeguards that do not depend on a platform: the list of the allowed
 * platforms, the addresses an HTTP retriever may contact, the size limit
 * during a transfer and the waits between attempts.
 */
class DceSafeguardsTests {

	@Test
	void thePlatformsThatRefusedAnAutomatedAccessCanNeverBeAllowed() {
		assertThatThrownBy(() -> properties(Set.of("marches-publics.info")))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("marches-publics.info");
		assertThatThrownBy(() -> properties(Set.of(" Projets-Achats.Marches-Publics.Gouv.Fr ")))
			.isInstanceOf(IllegalArgumentException.class);
		assertThat(properties(Set.of(" Plateforme.Test ", "")).allowedPlatforms()).containsExactly("plateforme.test");
	}

	@Test
	void anHttpRetrieverNeverContactsAPrivateLocalOrReservedAddress() throws Exception {
		try (SafeHttpFetcher fetcher = new SafeHttpFetcher(Duration.ofSeconds(1), Duration.ofSeconds(1), 3)) {
			for (String address : new String[] { "http://127.0.0.1/dce.zip",
					"http://10.0.0.8/dce.zip", "http://192.168.1.10/dce.zip", "http://169.254.169.254/latest",
					"http://172.16.0.1/", "http://[::1]/dce.zip", "http://100.64.0.1/", "http://0.0.0.0/" }) {
				assertThatThrownBy(() -> fetcher.check(URI.create(address))).as(address)
					.isInstanceOf(ForbiddenAddressException.class);
			}
			assertThatThrownBy(() -> fetcher.check(URI.create("ftp://8.8.8.8/dce.zip")))
				.hasMessageContaining("protocole ftp");
			assertThatThrownBy(() -> fetcher.check(URI.create("http://user:secret@8.8.8.8/dce.zip")))
				.isInstanceOf(ForbiddenAddressException.class);
		}
	}

	@Test
	void publicAddressesAreTheOnlyOnesAllowedByDefault() throws Exception {
		assertThat(SafeHttpFetcher.isPublic(InetAddress.getByName("8.8.8.8"))).isTrue();
		assertThat(SafeHttpFetcher.isPublic(InetAddress.getByName("2001:4860:4860::8888"))).isTrue();
		assertThat(SafeHttpFetcher.isPublic(InetAddress.getByName("fd00::1"))).isFalse();
		assertThat(SafeHttpFetcher.isPublic(InetAddress.getByName("198.18.0.1"))).isFalse();
	}

	@Test
	void aTransferStopsAsSoonAsItExceedsTheSizeAllowed() throws Exception {
		ByteArrayOutputStream out = new ByteArrayOutputStream();

		assertThatThrownBy(() -> DceRetrievalWorker.copyWithin(new ByteArrayInputStream(new byte[200_000]), out, 100_000))
			.isInstanceOf(FileRejectedException.class);
		assertThat(out.size()).isLessThanOrEqualTo(100_000);
		assertThat(DceRetrievalWorker.copyWithin(new ByteArrayInputStream(new byte[50]), new ByteArrayOutputStream(), 50))
			.isEqualTo(50);
	}

	private static DceRetrievalProperties properties(Set<String> platforms) {
		return new DceRetrievalProperties(platforms, false, 2, Duration.ofSeconds(30), 5, Duration.ofMinutes(10),
				Duration.ofHours(6), Duration.ofMinutes(30), DataSize.ofMegabytes(500), DataSize.ofBytes(3_000_000_000L),
				Duration.ofSeconds(10), Duration.ofSeconds(60), 5);
	}

}
