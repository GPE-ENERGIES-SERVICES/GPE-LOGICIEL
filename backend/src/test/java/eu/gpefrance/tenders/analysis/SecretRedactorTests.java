package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SecretRedactorTests {

	@Test
	void masksKeysAndAuthorizationHeaders() {
		assertThat(SecretRedactor.redact("HTTP 401: invalid x-api-key: abc123def456"))
			.isEqualTo("HTTP 401: invalid x-api-key: [masqué]");
		assertThat(SecretRedactor.redact("Authorization: Bearer eyJhbGciOi.secret")).doesNotContain("eyJhbGciOi");
		assertThat(SecretRedactor.redact("key sk-proj-AbCdEfGh12345678 refused")).isEqualTo("key [masqué] refused");
		assertThat(SecretRedactor.redact("api_key=\"0123456789\"")).isEqualTo("api_key=\"[masqué]\"");
	}

	@Test
	void keepsAnOrdinaryMessageAndCutsALongOne() {
		assertThat(SecretRedactor.redact("HTTP 503 Service Unavailable")).isEqualTo("HTTP 503 Service Unavailable");
		assertThat(SecretRedactor.redact("mot ".repeat(400))).hasSizeLessThanOrEqualTo(501);
		assertThat(SecretRedactor.redact(null)).isNull();
	}

}
