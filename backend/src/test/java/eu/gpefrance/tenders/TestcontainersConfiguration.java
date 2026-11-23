package eu.gpefrance.tenders;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * One database for the whole run, started once and migrated once: each test
 * empties the tables it uses. Only one application context lives at a time
 * (src/test/resources/spring.properties), so that no worker of a former
 * context reads the rows of the next tests.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

	// Closing a context must not stop the database of the next ones; it stops with the run.
	@Bean(destroyMethod = "")
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		if (!POSTGRES.isRunning()) {
			POSTGRES.start();
		}
		return POSTGRES;
	}

}
