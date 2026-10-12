package eu.gpefrance.tenders.collect;

import java.time.Duration;
import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Calls a source a second time, after a pause, when it failed for a reason
 * that may pass: server error (5xx), timeout, connection lost. Never more:
 * a source still failing makes its run fail, and the next scheduled run
 * fetches the same days again. A refusal (4xx) is not repeated.
 */
public final class HttpRetry {

	private static final Logger log = LoggerFactory.getLogger(HttpRetry.class);

	private HttpRetry() {
	}

	public static <T> T once(String what, Duration delay, Supplier<T> call) {
		try {
			return call.get();
		}
		catch (HttpServerErrorException | ResourceAccessException ex) {
			log.warn("{} failed ({}), tried again in {} s", what, ex.getMessage(), delay.toSeconds());
			pause(delay, ex);
			return call.get();
		}
	}

	private static void pause(Duration delay, RuntimeException cause) {
		try {
			Thread.sleep(delay);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw cause;
		}
	}

}
