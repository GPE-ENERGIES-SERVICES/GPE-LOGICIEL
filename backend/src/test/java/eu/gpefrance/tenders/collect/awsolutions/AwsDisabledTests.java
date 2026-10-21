package eu.gpefrance.tenders.collect.awsolutions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.ErrorResponseException;

import eu.gpefrance.tenders.TestcontainersConfiguration;
import eu.gpefrance.tenders.collect.CollectService;
import eu.gpefrance.tenders.collect.CollectTrigger;
import eu.gpefrance.tenders.collect.boamp.BoampClient;

/**
 * Disabled, AWS is never read: neither by the schedule nor on demand.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class AwsDisabledTests {

	@Autowired
	private CollectService service;

	@MockitoBean
	private AwsClient awsClient;

	@MockitoBean
	private BoampClient boampClient;

	@Test
	void aDisabledSiteIsNeitherScheduledNorCollectedOnDemand() {
		assertThat(service.activeSources()).doesNotContain("AWS");
		assertThatThrownBy(() -> service.collect("AWS", CollectTrigger.MANUAL, null))
			.isInstanceOf(ErrorResponseException.class)
			.hasMessageContaining("AWS");

		service.collectActive(CollectTrigger.SCHEDULED);

		then(awsClient).should(never()).fetchPublishedOn(any(), any());
		assertThat(service.sources()).anySatisfy(source -> {
			assertThat(source.source()).isEqualTo("AWS");
			assertThat(source.active()).isFalse();
		});
	}

	@Test
	void isDisabledByTheDefaultConfigurationWithItsLimits() throws IOException {
		String properties = new ClassPathResource("application.properties").getContentAsString(StandardCharsets.UTF_8);

		assertThat(properties).containsPattern("(?m)^collect\\.disabled-sources=.*\\bAWS\\b");
		assertThat(properties).containsPattern("(?m)^aw-solutions\\.base-url=https://www\\.marches-publics\\.info$");
	}

}
