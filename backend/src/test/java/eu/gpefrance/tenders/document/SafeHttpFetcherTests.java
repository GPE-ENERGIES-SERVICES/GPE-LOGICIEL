package eu.gpefrance.tenders.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.hc.client5.http.DnsResolver;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import eu.gpefrance.tenders.document.SafeHttpFetcher.ForbiddenAddressException;

/**
 * The network safeguards of {@link SafeHttpFetcher}, against a local server
 * and a simulated DNS: no request ever leaves the machine. The policy of
 * these tests allows exactly one address, 127.0.0.1 (the test server), and
 * nothing else of the machine or of a private network.
 */
class SafeHttpFetcherTests {

	private static final InetAddress SERVER = address("127.0.0.1");

	private static final InetAddress PRIVATE = address("10.0.0.5");

	private HttpServer server;

	private final AtomicInteger served = new AtomicInteger();

	private final StubDns dns = new StubDns();

	private SafeHttpFetcher fetcher;

	@BeforeEach
	void start() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.createContext("/", exchange -> {
			served.incrementAndGet();
			String path = exchange.getRequestURI().getPath();
			if (path.startsWith("/redirect/")) {
				exchange.getResponseHeaders().add("Location", "http://" + path.substring("/redirect/".length()) + "/x");
				exchange.sendResponseHeaders(302, -1);
				exchange.close();
				return;
			}
			byte[] body = "DCE".getBytes(StandardCharsets.UTF_8);
			// Each answer closes its connection: the next request connects, and resolves, again.
			exchange.getResponseHeaders().add("Connection", "close");
			exchange.sendResponseHeaders(200, body.length);
			try (OutputStream out = exchange.getResponseBody()) {
				out.write(body);
			}
		});
		server.start();
		fetcher = new SafeHttpFetcher(Duration.ofSeconds(2), Duration.ofSeconds(5), 3,
				new SafeHttpFetcher.Policy(SERVER::equals, port -> true), dns);
	}

	@AfterEach
	void stop() throws IOException {
		fetcher.close();
		server.stop(0);
	}

	@Test
	void connectsToTheAddressItValidatedWithASingleResolution() throws IOException {
		dns.answer("platform.test", SERVER);

		try (SafeHttpFetcher.Response response = fetcher.get(url("platform.test", "/dce.zip"))) {
			assertThat(response.status()).isEqualTo(200);
			assertThat(new String(response.body().readAllBytes(), StandardCharsets.UTF_8)).isEqualTo("DCE");
		}

		assertThat(dns.calls("platform.test")).isEqualTo(1);
		assertThat(served).hasValue(1);
	}

	@Test
	void aNameResolvingToAPrivateAddressIsNeverContacted() {
		dns.answer("internal.test", PRIVATE);

		assertThatThrownBy(() -> fetcher.get(url("internal.test", "/"))).isInstanceOf(ForbiddenAddressException.class)
			.hasMessageContaining("10.0.0.5");
		assertThat(served).hasValue(0);
	}

	@Test
	void oneForbiddenAddressInTheAnswerRefusesItAll() {
		dns.answer("mixed.test", SERVER, PRIVATE);

		assertThatThrownBy(() -> fetcher.get(url("mixed.test", "/"))).isInstanceOf(ForbiddenAddressException.class);
		assertThat(served).hasValue(0);
	}

	@Test
	void aNameChangingToAPrivateAddressIsRefusedAtTheNextConnection() throws IOException {
		// DNS rebinding: allowed first, then private. Each connection uses its own validated resolution.
		dns.answer("rebind.test", SERVER);
		dns.answer("rebind.test", PRIVATE);

		try (SafeHttpFetcher.Response first = fetcher.get(url("rebind.test", "/1"))) {
			assertThat(first.status()).isEqualTo(200);
			first.body().readAllBytes();
		}
		assertThatThrownBy(() -> fetcher.get(url("rebind.test", "/2"))).isInstanceOf(ForbiddenAddressException.class);

		assertThat(dns.calls("rebind.test")).isEqualTo(2);
		assertThat(served).as("the second request never reached anything").hasValue(1);
	}

	@Test
	void aRedirectionToAForbiddenDestinationIsNeverFollowed() {
		dns.answer("platform.test", SERVER);
		dns.answer("platform.test", SERVER);
		dns.answer("internal.test", PRIVATE);

		for (String target : List.of("internal.test", "169.254.169.254", "2130706433", "[::1]")) {
			served.set(0);
			assertThatThrownBy(() -> fetcher.get(url("platform.test", "/redirect/" + target.replace("[", "%5B").replace("]", "%5D"))))
				.as(target)
				.isInstanceOf(ForbiddenAddressException.class);
			assertThat(served).as(target).hasValue(1);
			dns.answer("platform.test", SERVER);
		}
	}

	@Test
	void ambiguousNumericHostsAreRefusedWithoutAnyResolution() {
		SafeHttpFetcher strict = new SafeHttpFetcher(Duration.ofSeconds(1), Duration.ofSeconds(1), 3);
		for (String host : List.of("2130706433", "0177.0.0.1", "0x7f.0.0.1", "0x7f000001", "127.1", "017700000001")) {
			assertThatThrownBy(() -> strict.check(URI.create("http://" + host + "/"))).as(host)
				.isInstanceOf(ForbiddenAddressException.class)
				.satisfies(ex -> assertThat(ex.getMessage()).containsAnyOf("ambiguë", "Adresse refusée"));
		}
		assertThat(dns.totalCalls()).isZero();
	}

	@Test
	void privateLocalAndReservedLiteralsAreRefusedInIpv4AndIpv6() {
		SafeHttpFetcher strict = new SafeHttpFetcher(Duration.ofSeconds(1), Duration.ofSeconds(1), 3);
		for (String host : List.of("127.0.0.1", "10.1.2.3", "172.31.0.1", "192.168.0.1", "169.254.169.254",
				"100.64.0.1", "0.0.0.0", "224.0.0.1", "255.255.255.255", "192.0.2.1", "198.51.100.1", "203.0.113.1",
				"[::1]", "[::]", "[::ffff:127.0.0.1]", "[::ffff:10.0.0.1]", "[::127.0.0.1]", "[fe80::1]", "[fc00::1]",
				"[fd12:3456::1]", "[fec0::1]", "[ff02::1]", "[64:ff9b::a00:1]", "[2002:a00:1::]", "[2001:db8::1]",
				"[2001::1]", "[100::1]")) {
			assertThatThrownBy(() -> strict.check(URI.create("http://" + host + "/"))).as(host)
				.isInstanceOf(ForbiddenAddressException.class);
		}
		// Public addresses, and IPv4 ones carried by IPv6, pass the check (no connection is made here).
		strict.check(URI.create("https://8.8.8.8/"));
		strict.check(URI.create("https://[2001:4860:4860::8888]/"));
		strict.check(URI.create("https://[64:ff9b::808:808]/"));
	}

	@Test
	void onlyHttpWithoutUserInformationOnTheDefaultPortsByDefault() {
		SafeHttpFetcher strict = new SafeHttpFetcher(Duration.ofSeconds(1), Duration.ofSeconds(1), 3);

		assertThatThrownBy(() -> strict.check(URI.create("ftp://8.8.8.8/dce.zip"))).hasMessageContaining("protocole");
		assertThatThrownBy(() -> strict.check(URI.create("file:///etc/passwd"))).hasMessageContaining("protocole");
		assertThatThrownBy(() -> strict.check(URI.create("https://user:secret@8.8.8.8/")))
			.isInstanceOf(ForbiddenAddressException.class);
		assertThatThrownBy(() -> strict.check(URI.create("http://8.8.8.8:8080/"))).hasMessageContaining("Port refusé");
		strict.check(URI.create("http://8.8.8.8:80/"));
		strict.check(URI.create("https://8.8.8.8:443/"));
	}

	@Test
	void aProxyOfTheSystemIsNeverUsed() throws IOException {
		dns.answer("platform.test", SERVER);
		String host = System.getProperty("http.proxyHost");
		String port = System.getProperty("http.proxyPort");
		// A proxy that does not exist: going through it would fail.
		System.setProperty("http.proxyHost", "192.0.2.1");
		System.setProperty("http.proxyPort", "9");
		try (SafeHttpFetcher direct = new SafeHttpFetcher(Duration.ofSeconds(2), Duration.ofSeconds(5), 3,
				new SafeHttpFetcher.Policy(SERVER::equals, p -> true), dns);
				SafeHttpFetcher.Response response = direct.get(url("platform.test", "/"))) {
			assertThat(response.status()).isEqualTo(200);
		}
		finally {
			restore("http.proxyHost", host);
			restore("http.proxyPort", port);
		}
		assertThat(served).hasValue(1);
	}

	private URI url(String host, String path) {
		return URI.create("http://" + host + ":" + server.getAddress().getPort() + path);
	}

	private static void restore(String key, String value) {
		if (value == null) {
			System.clearProperty(key);
		}
		else {
			System.setProperty(key, value);
		}
	}

	private static InetAddress address(String literal) {
		try {
			return InetAddress.getByName(literal);
		}
		catch (UnknownHostException ex) {
			throw new IllegalStateException(ex);
		}
	}

	/** A DNS giving, for each name, the answers queued for it, one per resolution; the last one is kept. */
	private static final class StubDns implements DnsResolver {

		private final Map<String, Deque<InetAddress[]>> answers = new ConcurrentHashMap<>();

		private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

		void answer(String host, InetAddress... addresses) {
			answers.computeIfAbsent(host, key -> new ArrayDeque<>()).addLast(addresses);
		}

		int calls(String host) {
			return calls.getOrDefault(host, new AtomicInteger()).get();
		}

		int totalCalls() {
			return calls.values().stream().mapToInt(AtomicInteger::get).sum();
		}

		@Override
		public InetAddress[] resolve(String host) throws UnknownHostException {
			calls.computeIfAbsent(host, key -> new AtomicInteger()).incrementAndGet();
			Deque<InetAddress[]> queued = answers.get(host);
			if (queued == null || queued.isEmpty()) {
				// A literal address is parsed, never looked up.
				return new InetAddress[] { InetAddress.getByName(host) };
			}
			return queued.size() > 1 ? queued.pollFirst() : queued.peekFirst();
		}

		@Override
		public String resolveCanonicalHostname(String host) {
			return host;
		}

	}

}
