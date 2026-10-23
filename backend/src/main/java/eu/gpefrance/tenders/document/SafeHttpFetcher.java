package eu.gpefrance.tenders.document;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Locale;
import java.util.function.IntPredicate;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.SystemDefaultDnsResolver;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.DefaultSchemePortResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.routing.DefaultRoutePlanner;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.util.Timeout;

/**
 * HTTP access for the retrievers of the allowed platforms, never for any
 * address given by a source. It does not authorise anything by itself: the
 * platform must be allowed ({@link DcePlatformPolicy}), and a page asking for
 * an account, an identification or a CAPTCHA is never bypassed.
 * <p>
 * Guarantees, for the address asked and for every redirection (never followed
 * automatically, each one checked like the first address):
 * <ul>
 * <li>http(s) only, no user information, only the ports of the policy (80 and
 * 443 by default), no ambiguous numeric host (2130706433, 0177.0.0.1,
 * 0x7f.1...);</li>
 * <li>every address the name resolves to must be allowed (public Internet by
 * default, {@link #isPublic}); one forbidden address refuses the whole
 * answer;</li>
 * <li>the name is resolved once per connection, by the validating resolver
 * given to Apache HttpClient, which connects to exactly the addresses it
 * returned: no second, independent resolution between the check and the
 * connection (DNS rebinding);</li>
 * <li>never a proxy, even one configured for the system;</li>
 * <li>timeouts to connect and between two reads, no automatic retry, no
 * cookies.</li>
 * </ul>
 */
public class SafeHttpFetcher implements Closeable {

	/** A host made only of numbers (decimal, octal, hexadecimal), whatever its form. */
	private static final Pattern NUMERIC_HOST = Pattern
		.compile("^(0x[0-9a-f]*|\\d+)(\\.(0x[0-9a-f]*|\\d+))*\\.?$", Pattern.CASE_INSENSITIVE);

	/** The only numeric form accepted: four decimal numbers 0 to 255, without leading zeros. */
	private static final Pattern DOTTED_QUAD = Pattern
		.compile("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");

	private final CloseableHttpClient client;

	private final int maxRedirects;

	private final Policy policy;

	public SafeHttpFetcher(Duration connectTimeout, Duration readTimeout, int maxRedirects) {
		this(connectTimeout, readTimeout, maxRedirects, Policy.publicInternet(), SystemDefaultDnsResolver.INSTANCE);
	}

	/**
	 * @param policy the addresses and ports that may be contacted; only tests
	 * allow more than the public Internet, the address of their own server
	 * @param dns how names are resolved; the system resolver outside tests
	 */
	public SafeHttpFetcher(Duration connectTimeout, Duration readTimeout, int maxRedirects, Policy policy,
			DnsResolver dns) {
		this.maxRedirects = maxRedirects;
		this.policy = policy;
		this.client = HttpClients.custom()
			.setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
				.setDnsResolver(new ValidatingDnsResolver(dns, policy))
				.setDefaultConnectionConfig(ConnectionConfig.custom()
					.setConnectTimeout(Timeout.of(connectTimeout))
					.setSocketTimeout(Timeout.of(readTimeout))
					.build())
				.build())
			// Direct routes only: neither a proxy of the system nor of the environment.
			.setRoutePlanner(new DefaultRoutePlanner(DefaultSchemePortResolver.INSTANCE))
			.disableRedirectHandling()
			.disableAutomaticRetries()
			.disableCookieManagement()
			.disableAuthCaching()
			.build();
	}

	/**
	 * @return the answer of the last address, its body to be read and closed
	 * by the caller
	 * @throws ForbiddenAddressException for an address, or a redirection,
	 * that may not be contacted; nothing is sent to it
	 */
	public Response get(URI uri) throws IOException {
		URI current = uri;
		for (int redirects = 0;; redirects++) {
			check(current);
			ClassicHttpResponse response = client.executeOpen(null, new HttpGet(current), null);
			int status = response.getCode();
			Header location = response.getFirstHeader("Location");
			if (status >= 300 && status < 400 && status != 304 && location != null) {
				response.close();
				if (redirects >= maxRedirects) {
					throw new ForbiddenAddressException("Trop de redirections depuis " + uri.getHost());
				}
				current = current.resolve(location.getValue());
				continue;
			}
			return new Response(status, response);
		}
	}

	/**
	 * Checks an address before any request: protocol, user information,
	 * port and form of the host; a numeric address is checked at once, a
	 * name when the connection resolves it.
	 * @throws ForbiddenAddressException when it may not be contacted
	 */
	public void check(URI uri) {
		String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
		if (!scheme.equals("http") && !scheme.equals("https")) {
			throw new ForbiddenAddressException("Adresse refusée (protocole " + scheme + ")");
		}
		String host = uri.getHost();
		if (host == null || host.isBlank() || uri.getRawUserInfo() != null) {
			throw new ForbiddenAddressException("Adresse refusée : " + uri);
		}
		if (!policy.ports().test(uri.getPort())) {
			throw new ForbiddenAddressException("Port refusé : " + uri.getPort());
		}
		if (host.startsWith("[")) {
			checkAddress(host, literal(host.substring(1, host.length() - 1)));
		}
		else if (NUMERIC_HOST.matcher(host).matches()) {
			if (!DOTTED_QUAD.matcher(host).matches()) {
				throw new ForbiddenAddressException("Adresse numérique ambiguë refusée : " + host);
			}
			checkAddress(host, literal(host));
		}
	}

	private void checkAddress(String host, InetAddress address) {
		if (!policy.addresses().test(address)) {
			throw forbidden(host, address);
		}
	}

	@Override
	public void close() throws IOException {
		client.close();
	}

	/**
	 * An address of the public Internet: none of the machine, of a private,
	 * shared, link-local, multicast, documentation, benchmarking or reserved
	 * range, in IPv4 or IPv6, including an IPv4 address carried by an IPv6
	 * one (mapped, compatible, NAT64, 6to4) and Teredo. IPv6 is accepted in
	 * global unicast (2000::/3) only.
	 */
	public static boolean isPublic(InetAddress address) {
		byte[] bytes = address.getAddress();
		if (address instanceof Inet4Address) {
			return isPublicV4(bytes);
		}
		if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
				|| address.isSiteLocalAddress() || address.isMulticastAddress()) {
			return false;
		}
		if (allZero(bytes, 0, 10)) {
			// ::ffff:a.b.c.d (mapped): the IPv4 address decides; ::a.b.c.d and :: are refused.
			return (bytes[10] & 0xFF) == 0xFF && (bytes[11] & 0xFF) == 0xFF
					&& isPublicV4(Arrays.copyOfRange(bytes, 12, 16));
		}
		int first = ((bytes[0] & 0xFF) << 8) | (bytes[1] & 0xFF);
		int second = ((bytes[2] & 0xFF) << 8) | (bytes[3] & 0xFF);
		if (first == 0x0064 && second == 0xFF9B) {
			// NAT64 64:ff9b::/96 carries an IPv4 address; 64:ff9b:1::/48 is local.
			return allZero(bytes, 4, 12) && isPublicV4(Arrays.copyOfRange(bytes, 12, 16));
		}
		if (first == 0x2002) {
			// 6to4 2002::/16 carries an IPv4 address.
			return isPublicV4(Arrays.copyOfRange(bytes, 2, 6));
		}
		if (first == 0x2001 && (second == 0x0000 || second == 0x0db8 || second == 0x0002
				|| second >= 0x0010 && second <= 0x002F)) {
			// Teredo, documentation, benchmarking, ORCHID.
			return false;
		}
		return (bytes[0] & 0xE0) == 0x20;
	}

	private static boolean isPublicV4(byte[] bytes) {
		int a = bytes[0] & 0xFF;
		int b = bytes[1] & 0xFF;
		int c = bytes[2] & 0xFF;
		return !(a == 0 || a == 10 || a == 127 || a >= 224 || a == 100 && b >= 64 && b <= 127
				|| a == 169 && b == 254 || a == 172 && b >= 16 && b <= 31 || a == 192 && b == 168
				|| a == 192 && b == 0 && (c == 0 || c == 2) || a == 192 && b == 88 && c == 99
				|| a == 198 && (b == 18 || b == 19) || a == 198 && b == 51 && c == 100 || a == 203 && b == 0 && c == 113);
	}

	private static boolean allZero(byte[] bytes, int from, int to) {
		for (int i = from; i < to; i++) {
			if (bytes[i] != 0) {
				return false;
			}
		}
		return true;
	}

	private static InetAddress literal(String text) {
		try {
			// A literal address: parsed, never resolved.
			return InetAddress.getByName(text);
		}
		catch (UnknownHostException ex) {
			throw new ForbiddenAddressException("Adresse illisible : " + text);
		}
	}

	private static ForbiddenAddressException forbidden(String host, InetAddress address) {
		return new ForbiddenAddressException(
				"Adresse privée, locale ou réservée refusée : " + host + " (" + address.getHostAddress() + ")");
	}

	/**
	 * What may be contacted.
	 * @param addresses the addresses allowed
	 * @param ports the ports allowed, -1 for the default port of the protocol
	 */
	public record Policy(Predicate<InetAddress> addresses, IntPredicate ports) {

		/** The public Internet, on the default ports of http and https. */
		public static Policy publicInternet() {
			return new Policy(SafeHttpFetcher::isPublic, port -> port == -1 || port == 80 || port == 443);
		}

	}

	/**
	 * The answer of a server; closing it releases the connection.
	 */
	public static final class Response implements Closeable {

		private final int status;

		private final ClassicHttpResponse response;

		Response(int status, ClassicHttpResponse response) {
			this.status = status;
			this.response = response;
		}

		public int status() {
			return status;
		}

		/** The body, empty when the server sent none. */
		public InputStream body() throws IOException {
			return response.getEntity() == null ? InputStream.nullInputStream() : response.getEntity().getContent();
		}

		@Override
		public void close() throws IOException {
			response.close();
		}

	}

	/**
	 * Resolves a name for a connection and refuses the whole answer when one
	 * address is not allowed: Apache HttpClient connects to the addresses it
	 * returns, so the address connected is always one that was checked.
	 */
	static final class ValidatingDnsResolver implements DnsResolver {

		private final DnsResolver delegate;

		private final Policy policy;

		ValidatingDnsResolver(DnsResolver delegate, Policy policy) {
			this.delegate = delegate;
			this.policy = policy;
		}

		@Override
		public InetAddress[] resolve(String host) throws UnknownHostException {
			InetAddress[] addresses = delegate.resolve(host);
			if (addresses == null || addresses.length == 0) {
				throw new UnknownHostException(host);
			}
			for (InetAddress address : addresses) {
				if (!policy.addresses().test(address)) {
					throw forbidden(host, address);
				}
			}
			return addresses;
		}

		@Override
		public String resolveCanonicalHostname(String host) {
			// Never a reverse lookup: the name asked is the one used.
			return host;
		}

	}

	public static class ForbiddenAddressException extends RuntimeException {

		public ForbiddenAddressException(String message) {
			super(message);
		}

	}

}
