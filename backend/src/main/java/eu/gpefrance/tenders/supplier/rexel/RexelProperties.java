package eu.gpefrance.tenders.supplier.rexel;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The connection to Rexel: off by default, with no environment by default. The
 * secrets come from the environment of the server only.
 * @param environment no default: TEST or PRODUCTION is always a choice
 * @param tokenUrl /oauth2/v2.0/token asks with a scope, any other with a resource
 * @param customerId the account number of GPE at Rexel (idCustomer)
 * @param batchSize products per request: the limit of Rexel is not documented
 * @param retryDelay wait before the second attempt after a temporary failure
 */
@ConfigurationProperties("rexel")
public record RexelProperties(boolean enabled, RexelEnvironment environment, String tokenUrl, String clientId,
		String clientSecret, String subscriptionKey, String customerId, int batchSize, Duration retryDelay) {

	boolean configured() {
		return enabled && environment != null && present(tokenUrl) && present(clientId) && present(clientSecret)
				&& present(subscriptionKey) && present(customerId);
	}

	/** A record prints all its values: the secrets must not reach a log this way. */
	@Override
	public String toString() {
		return "RexelProperties[enabled=" + enabled + ", environment=" + environment + "]";
	}

	private static boolean present(String value) {
		return value != null && !value.isBlank();
	}

}
