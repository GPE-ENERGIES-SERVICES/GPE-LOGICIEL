package eu.gpefrance.tenders.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * A local HTTP server serving synthetic consultation files, for the tests
 * of the retrieval only: no real platform is ever contacted.
 */
final class SyntheticDceServer {

	static final byte[] PDF = "%PDF-1.7\n1 0 obj << >> endobj\ntrailer << >>\n%%EOF\n"
		.getBytes(StandardCharsets.ISO_8859_1);

	private final HttpServer server;

	private final Map<String, AtomicInteger> requests = new ConcurrentHashMap<>();

	/** Holds the answers of /slow.pdf until released. */
	volatile CountDownLatch slow = new CountDownLatch(0);

	SyntheticDceServer() {
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		}
		catch (IOException ex) {
			throw new UncheckedIOException(ex);
		}
		server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
		server.createContext("/", this::handle);
		server.start();
	}

	String url(String path) {
		return "http://127.0.0.1:" + server.getAddress().getPort() + path;
	}

	int port() {
		return server.getAddress().getPort();
	}

	int requests(String path) {
		return requests.getOrDefault(path, new AtomicInteger()).get();
	}

	int totalRequests() {
		return requests.values().stream().mapToInt(AtomicInteger::get).sum();
	}

	void reset() {
		requests.clear();
		slow = new CountDownLatch(0);
	}

	void stop() {
		server.stop(0);
	}

	private void handle(HttpExchange exchange) throws IOException {
		String path = exchange.getRequestURI().getPath();
		requests.computeIfAbsent(path, key -> new AtomicInteger()).incrementAndGet();
		switch (path) {
			case "/ok.pdf", "/same.pdf" -> send(exchange, 200, PDF);
			case "/dce.zip" -> send(exchange, 200, zip());
			case "/login.pdf" -> send(exchange, 200,
					"<!DOCTYPE html><html><body><form>Connexion</form></body></html>".getBytes(StandardCharsets.UTF_8));
			case "/denied" -> send(exchange, 403, new byte[0]);
			case "/missing" -> send(exchange, 404, new byte[0]);
			case "/flaky" -> send(exchange, 503, new byte[0]);
			case "/big.pdf" -> send(exchange, 200, big());
			case "/redirect-private" -> {
				exchange.getResponseHeaders().add("Location", "http://10.1.2.3/dce.zip");
				send(exchange, 302, new byte[0]);
			}
			case "/slow.pdf" -> {
				try {
					slow.await(30, TimeUnit.SECONDS);
				}
				catch (InterruptedException ex) {
					Thread.currentThread().interrupt();
				}
				send(exchange, 200, PDF);
			}
			default -> send(exchange, 404, new byte[0]);
		}
	}

	private static void send(HttpExchange exchange, int status, byte[] body) throws IOException {
		exchange.sendResponseHeaders(status, body.length == 0 ? -1 : body.length);
		try (OutputStream out = exchange.getResponseBody()) {
			out.write(body);
		}
	}

	/** A PDF of 2 MB, above the size allowed by the tests. */
	private static byte[] big() {
		byte[] content = new byte[2 * 1024 * 1024];
		System.arraycopy(PDF, 0, content, 0, 8);
		byte[] end = "%%EOF\n".getBytes(StandardCharsets.ISO_8859_1);
		System.arraycopy(end, 0, content, content.length - end.length, end.length);
		return content;
	}

	private static byte[] zip() throws IOException {
		ByteArrayOutputStream bytes = new ByteArrayOutputStream();
		try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
			for (String name : new String[] { "RC.pdf", "CCTP.pdf" }) {
				zip.putNextEntry(new ZipEntry(name));
				zip.write(PDF);
				zip.closeEntry();
			}
		}
		return bytes.toByteArray();
	}

}
