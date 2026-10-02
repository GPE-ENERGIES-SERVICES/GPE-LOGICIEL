package eu.gpefrance.tenders;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ClockConfiguration {

	public static final ZoneId ZONE = ZoneId.of("Europe/Paris");

	@Bean
	Clock clock() {
		return Clock.system(ZONE);
	}

}
