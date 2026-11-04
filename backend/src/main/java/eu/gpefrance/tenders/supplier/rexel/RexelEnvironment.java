package eu.gpefrance.tenders.supplier.rexel;

/**
 * A host always goes with its resource, so that a test host can never be used
 * with a production token, nor the other way round. Nothing here is secret. The
 * TEST host comes from the developer portal; only PRODUCTION is in the documentation.
 */
public enum RexelEnvironment {

	TEST("https://apidev.rexel.fr", "6333c04d-110f-45f7-8f9b-d10c6f6a5781", "REXEL_TEST"),
	PRODUCTION("https://api.rexel.fr", "aee2ba94-a840-453a-9151-1355638ac04e", "REXEL_PROD");

	private final String baseUrl;

	private final String resource;

	private final String source;

	RexelEnvironment(String baseUrl, String resource, String source) {
		this.baseUrl = baseUrl;
		this.resource = resource;
		this.source = source;
	}

	String baseUrl() {
		return baseUrl;
	}

	String resource() {
		return resource;
	}

	/** Written with each price, so that a test price is never taken for a real one. */
	String source() {
		return source;
	}

}
