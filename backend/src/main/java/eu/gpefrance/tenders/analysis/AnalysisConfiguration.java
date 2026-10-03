package eu.gpefrance.tenders.analysis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class AnalysisConfiguration {

	/**
	 * Until a provider is chosen and its client declared, analyses cannot be
	 * started and the interface says so.
	 */
	@Bean
	@ConditionalOnMissingBean
	ExtractionClient unconfiguredExtractionClient() {
		return new ExtractionClient() {

			@Override
			public String model() {
				return null;
			}

			@Override
			public ModelAnswer complete(String instructions, String documents) {
				throw new IllegalStateException("Aucun fournisseur d'IA n'est configuré");
			}

		};
	}

}
