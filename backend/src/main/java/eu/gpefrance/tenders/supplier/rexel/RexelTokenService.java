package eu.gpefrance.tenders.supplier.rexel;

import java.net.URI;
import java.time.Clock;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import eu.gpefrance.tenders.supplier.SupplierConnectorException;
import eu.gpefrance.tenders.supplier.SupplierConnectorException.Kind;

import tools.jackson.databind.JsonNode;

/**
 * The token of the OAuth2 client credentials flow, kept in memory only and
 * asked again 60 seconds before it ends, as told by each answer.
 */
@Component
class RexelTokenService {

	private static final long MARGIN_SECONDS = 60;

	private final RestClient restClient;

	private final RexelProperties properties;

	private final Clock clock;

	private String token;

	private Instant renewAt = Instant.MIN;

	RexelTokenService(RestClient.Builder builder, RexelProperties properties, Clock clock) {
		this.restClient = builder.build();
		this.properties = properties;
		this.clock = clock;
	}

	synchronized String token() {
		if (token == null || !clock.instant().isBefore(renewAt)) {
			fetch();
		}
		return token;
	}

	/** After a refusal of the token by the API. */
	synchronized void invalidate() {
		token = null;
	}

	private void fetch() {
		JsonNode answer;
		try {
			answer = restClient.post()
				.uri(URI.create(properties.tokenUrl()))
				.contentType(MediaType.APPLICATION_FORM_URLENCODED)
				.body(form())
				.retrieve()
				.body(JsonNode.class);
		}
		catch (HttpClientErrorException ex) {
			throw new SupplierConnectorException(Kind.AUTHENTICATION);
		}
		catch (RestClientException ex) {
			throw new SupplierConnectorException(Kind.TEMPORARY);
		}
		String received = answer == null ? null : answer.path("access_token").asString(null);
		if (received == null || received.isBlank()) {
			throw new SupplierConnectorException(Kind.INVALID_RESPONSE);
		}
		token = received;
		// Without a usable duration the token is simply asked again at the next call.
		renewAt = clock.instant().plusSeconds(Math.max(0, answer.path("expires_in").asLong(0) - MARGIN_SECONDS));
	}

	/** The documentation asks /oauth2/token with a resource, the access sheet /oauth2/v2.0/token with a scope. */
	private MultiValueMap<String, String> form() {
		MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
		form.add("grant_type", "client_credentials");
		form.add("client_id", properties.clientId());
		form.add("client_secret", properties.clientSecret());
		if (properties.tokenUrl().contains("/v2.0/")) {
			form.add("scope", properties.environment().resource() + "/.default");
		}
		else {
			form.add("resource", properties.environment().resource());
		}
		return form;
	}

}
