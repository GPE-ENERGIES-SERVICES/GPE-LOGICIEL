package eu.gpefrance.tenders.collect.ted;

import java.time.LocalDate;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.collect.SourceAccess;
import eu.gpefrance.tenders.collect.SourceConnector;
import eu.gpefrance.tenders.collect.SourceRecord;

/**
 * TED (Tenders Electronic Daily), the European public procurement journal,
 * read from its public search API for French buyers; its notices are read by
 * {@link TedNoticeParser}. Collected after the BOAMP, so that its notices of
 * consultations also on the BOAMP join their BOAMP tender.
 */
@Component
@Order(2)
public class TedConnector implements SourceConnector {

	public static final String SOURCE = "TED";

	private final TedClient client;

	public TedConnector(TedClient client) {
		this.client = client;
	}

	@Override
	public String source() {
		return SOURCE;
	}

	@Override
	public SourceAccess access() {
		return SourceAccess.AUTOMATED;
	}

	@Override
	public List<SourceRecord> fetchPublishedOn(LocalDate day) {
		return client.fetchPublishedOn(day);
	}

}
