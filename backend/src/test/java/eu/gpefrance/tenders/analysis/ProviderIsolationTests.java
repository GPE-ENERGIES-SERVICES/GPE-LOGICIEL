package eu.gpefrance.tenders.analysis;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * The provider stays behind its contract: the SDK of Anthropic in its own
 * package only and no other provider SDK anywhere, the contract used by the
 * analysis only, and Anthropic as the only real provider of the application.
 * No test can call it: it needs a key, which no test configuration has, and
 * the analysis stays off. Read from the sources.
 */
class ProviderIsolationTests {

	private static final Path SOURCES = Path.of("src/main/java");

	private static final Path ANTHROPIC = SOURCES.resolve("eu/gpefrance/tenders/analysis/anthropic");

	private static final List<String> OTHER_SDK_PACKAGES = List.of("com.openai", "dev.langchain4j",
			"org.springframework.ai", "com.azure.ai", "software.amazon.awssdk.services.bedrock", "com.google.cloud.vertexai",
			"com.google.genai", "ai.mistral", "io.github.sashirestela");

	@Test
	void onlyTheAnthropicClientUsesTheSdkOfAProvider() throws IOException {
		assertThat(sources()).allSatisfy((file, text) -> {
			OTHER_SDK_PACKAGES.forEach(sdk -> assertThat(text).as(file.toString()).doesNotContain("import " + sdk));
			if (!file.startsWith(ANTHROPIC)) {
				assertThat(text).as(file.toString()).doesNotContain("import com.anthropic");
			}
		});
		String pom = Files.readString(Path.of("pom.xml"));
		assertThat(pom).doesNotContainIgnoringCase("openai")
			.doesNotContainIgnoringCase("langchain4j")
			.doesNotContainIgnoringCase("spring-ai")
			.doesNotContainIgnoringCase("mistral");
	}

	@Test
	void onlyTheAnalysisKnowsTheProvider() throws IOException {
		assertThat(sources()).allSatisfy((file, text) -> {
			if (!file.startsWith(SOURCES.resolve("eu/gpefrance/tenders/analysis"))) {
				assertThat(text).as(file.toString()).doesNotContain("ExtractionClient");
			}
		});
	}

	@Test
	void anthropicIsTheOnlyRealProvider() throws IOException {
		assertThat(sources()).allSatisfy((file, text) -> {
			if (!file.startsWith(ANTHROPIC)) {
				assertThat(text).as(file.toString())
					.doesNotContainPattern("implements\\s+(\\w+\\s*,\\s*)*ExtractionClient");
			}
		});
	}

	private static Map<Path, String> sources() throws IOException {
		try (Stream<Path> files = Files.walk(SOURCES)) {
			return files.filter(file -> file.toString().endsWith(".java"))
				.collect(Collectors.toMap(file -> file, file -> {
					try {
						return Files.readString(file);
					}
					catch (IOException ex) {
						throw new UncheckedIOException(ex);
					}
				}));
		}
	}

}
