package eu.gpefrance.tenders.collect.boamp;

import java.time.LocalDate;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.collect.SourceAccess;
import eu.gpefrance.tenders.collect.SourceConnector;
import eu.gpefrance.tenders.collect.SourceRecord;

/**
 * The BOAMP, read from its open data API; its notices are read by
 * {@link BoampNoticeParser}.
 */
@Component
@Order(1)
public class BoampConnector implements SourceConnector {

	public static final String SOURCE = "BOAMP";

	private final BoampClient client;

	public BoampConnector(BoampClient client) {
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
