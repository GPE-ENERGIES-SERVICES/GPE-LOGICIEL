package eu.gpefrance.tenders.supplier.rexel;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import eu.gpefrance.tenders.collect.HttpRetry;
import eu.gpefrance.tenders.supplier.SupplierConnector.Place;
import eu.gpefrance.tenders.supplier.SupplierConnector.Product;
import eu.gpefrance.tenders.supplier.SupplierConnectorException;
import eu.gpefrance.tenders.supplier.SupplierConnectorException.Kind;

import tools.jackson.databind.JsonNode;

/**
 * The calls to the API of Rexel: both security headers, one attempt more after
 * a temporary failure, a new token once after a 401, the products sent by
 * batches. Every failure ends as a {@link SupplierConnectorException}. A body
 * of Rexel never reaches a message or a log.
 */
@Component
class RexelHttpClient {

	private final RestClient restClient;

	private final RexelProperties properties;

	private final RexelTokenService tokens;

	RexelHttpClient(RestClient.Builder builder, RexelProperties properties, RexelTokenService tokens) {
		this.restClient = builder.build();
		this.properties = properties;
		this.tokens = tokens;
	}

	/**
	 * Asks for products by batches, each once: lines of the answer are given to
	 * the reader and matched later by their reference, never by their position.
	 * @param reader gives null for a line to leave out
	 */
	<T> List<T> query(String path, String requestRoot, String responseRoot, List<Product> products, Place place,
			Function<JsonNode, T> reader) {
		List<Product> unique = new ArrayList<>(products.stream()
			.collect(LinkedHashMap<String, Product>::new, (found, product) -> found.putIfAbsent(product.key(), product),
					Map::putAll)
			.values());
		List<T> results = new ArrayList<>();
		int size = Math.max(1, properties.batchSize());
		for (int start = 0; start < unique.size(); start += size) {
			List<Product> batch = unique.subList(start, Math.min(start + size, unique.size()));
			JsonNode root = post(path, Map.of(requestRoot, request(batch, place))).path("data").path(responseRoot);
			if (root.isMissingNode()) {
				throw new SupplierConnectorException(Kind.INVALID_RESPONSE);
			}
			// The documentation gives a list for the prices, and no brackets for the stocks.
			JsonNode details = root.path("productDetails");
			List<JsonNode> lines = new ArrayList<>();
			if (details.isArray()) {
				details.forEach(lines::add);
			}
			else if (details.isObject()) {
				lines.add(details);
			}
			lines.stream().map(reader).filter(line -> line != null).forEach(results::add);
		}
		return results;
	}

	private Map<String, Object> request(List<Product> batch, Place place) {
		// The order matters: Rexel turns the JSON into XML and refuses (500) fields out of their sequence.
		Map<String, Object> request = new LinkedHashMap<>();
		// Said optional by the documentation, but the gateway answers a 500 without them.
		request.put("idCodOrigin", "GPE");
		request.put("idNumVersion", "1");
		request.put("idCustomer", properties.customerId());
		request.put("productDetails", batch.stream()
			.map(product -> new ProductDetail(product.manufacturerCode(), product.manufacturerRef(), product.quantity()))
			.toList());
		putIfPresent(request, "agenceCode", place.agencyCode());
		putIfPresent(request, "zipCode", place.zipCode());
		putIfPresent(request, "city", place.city());
		return request;
	}

	/** A record, written in this order; a Map.of would change its order at each start. */
	private record ProductDetail(String supplierCode, String supplierComRef, int orderingQty) {
	}

	private static void putIfPresent(Map<String, Object> request, String name, String value) {
		if (value != null && !value.isBlank()) {
			request.put(name, value.strip());
		}
	}

	/** A reading without body: the orders. */
	JsonNode get(String path) {
		return call(path, null);
	}

	private JsonNode post(String path, Object body) {
		return call(path, body);
	}

	private JsonNode call(String path, Object body) {
		try {
			JsonNode answer = HttpRetry.once("Rexel request", properties.retryDelay(), () -> authorized(path, body));
			if (answer == null) {
				throw new SupplierConnectorException(Kind.INVALID_RESPONSE);
			}
			return answer;
		}
		catch (HttpClientErrorException.TooManyRequests ex) {
			throw new SupplierConnectorException(Kind.RATE_LIMITED);
		}
		catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden ex) {
			throw new SupplierConnectorException(Kind.AUTHENTICATION);
		}
		catch (HttpClientErrorException ex) {
			throw new SupplierConnectorException(Kind.REJECTED);
		}
		catch (HttpServerErrorException | ResourceAccessException ex) {
			throw new SupplierConnectorException(Kind.TEMPORARY);
		}
		catch (RestClientException ex) {
			throw new SupplierConnectorException(Kind.INVALID_RESPONSE);
		}
	}

	private JsonNode authorized(String path, Object body) {
		try {
			return send(path, body);
		}
		catch (HttpClientErrorException.Unauthorized ex) {
			tokens.invalidate();
			return send(path, body);
		}
	}

	private JsonNode send(String path, Object body) {
		try {
			if (body == null) {
				return restClient.get()
					.uri(URI.create(properties.environment().baseUrl() + path))
					.header("Authorization", "Bearer " + tokens.token())
					.header("Ocp-Apim-Subscription-Key", properties.subscriptionKey())
					.accept(MediaType.APPLICATION_JSON)
					.retrieve()
					.body(JsonNode.class);
			}
			return restClient.post()
				.uri(URI.create(properties.environment().baseUrl() + path))
				.header("Authorization", "Bearer " + tokens.token())
				.header("Ocp-Apim-Subscription-Key", properties.subscriptionKey())
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.body(body)
				.retrieve()
				.body(JsonNode.class);
		}
		catch (HttpServerErrorException ex) {
			// Rebuilt without its body, which HttpRetry would write in the log.
			throw new HttpServerErrorException(ex.getStatusCode());
		}
	}

}
