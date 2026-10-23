package eu.gpefrance.tenders.document;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A retriever for the tests only, of the synthetic platform 127.0.0.1: it
 * reads the address of the link with {@link SafeHttpFetcher}, allowed to
 * contact the loopback address of the test server only, and turns the
 * answers into the errors of the contract.
 */
class SyntheticDceRetriever implements DceRetriever {

	static final String PLATFORM = "127.0.0.1";

	/** Threads the retrievals ran on. */
	final Set<String> threads = ConcurrentHashMap.newKeySet();

	private final SafeHttpFetcher fetcher = new SafeHttpFetcher(Duration.ofSeconds(5), Duration.ofSeconds(30), 3,
			new SafeHttpFetcher.Policy(address -> address.isLoopbackAddress() || SafeHttpFetcher.isPublic(address),
					port -> true),
			org.apache.hc.client5.http.SystemDefaultDnsResolver.INSTANCE);

	@Override
	public boolean supports(DceLink link) {
		return PLATFORM.equals(link.getPlatform());
	}

	@Override
	public List<RetrievedFile> retrieve(DceLink link) {
		threads.add(Thread.currentThread().getName());
		URI uri = URI.create(link.getUrl());
		SafeHttpFetcher.Response response;
		InputStream body;
		try {
			response = fetcher.get(uri);
			body = response.body();
		}
		catch (IOException ex) {
			throw new TemporaryRetrievalException("Connexion impossible", ex);
		}
		int status = response.status();
		if (status == 401 || status == 403) {
			close(response);
			throw new AccessRequiredException("la plateforme demande une identification");
		}
		if (status == 404 || status == 410) {
			close(response);
			throw new LinkUnavailableException("HTTP " + status);
		}
		if (status >= 400) {
			close(response);
			throw new TemporaryRetrievalException("HTTP " + status, null);
		}
		String path = uri.getPath();
		return List.of(new RetrievedFile(path.substring(path.lastIndexOf('/') + 1), link.getUrl(), body));
	}

	private static void close(SafeHttpFetcher.Response response) {
		try {
			response.close();
		}
		catch (IOException ex) {
			// nothing to keep
		}
	}

}
