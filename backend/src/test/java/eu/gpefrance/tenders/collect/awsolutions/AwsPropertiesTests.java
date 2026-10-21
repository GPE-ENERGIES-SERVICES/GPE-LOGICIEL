package eu.gpefrance.tenders.collect.awsolutions;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * The variables of docker-compose.yml reach the limits of the reading and the
 * list of disabled sources.
 */
class AwsPropertiesTests {

	@Test
	void theVariablesOfTheComposeFileSetTheLimitsAndTheDisabledSources() {
		Binder binder = binder(Map.of("AWSOLUTIONS_BASEURL", "https://www.marches-publics.info", "AWSOLUTIONS_NATURES",
				"T,S", "AWSOLUTIONS_PAUSE", "60s", "AWSOLUTIONS_MAXPAGESPERHOUR", "50",
				"AWSOLUTIONS_MAXPAGESPERDAY", "200", "COLLECT_DISABLED_SOURCES", "APPROCH"));

		AwsProperties aws = binder.bind("aw-solutions", Bindable.of(AwsProperties.class)).get();

		assertThat(aws.natures()).containsExactly("T", "S");
		assertThat(aws.pause()).isEqualTo(Duration.ofSeconds(60));
		assertThat(aws.maxPagesPerHour()).isEqualTo(50);
		assertThat(aws.maxPagesPerDay()).isEqualTo(200);
		assertThat(binder.bind("collect.disabled-sources", Bindable.setOf(String.class)).get())
			.isEqualTo(Set.of("APPROCH"));
	}

	private static Binder binder(Map<String, Object> variables) {
		StandardEnvironment environment = new StandardEnvironment();
		MutablePropertySources sources = environment.getPropertySources();
		sources.replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
				new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME, variables));
		return new Binder(ConfigurationPropertySources.from(sources));
	}

}
