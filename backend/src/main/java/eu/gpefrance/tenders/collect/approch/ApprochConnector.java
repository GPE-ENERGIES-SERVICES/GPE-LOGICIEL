package eu.gpefrance.tenders.collect.approch;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.collect.SourceAccess;
import eu.gpefrance.tenders.collect.SourceConnector;
import eu.gpefrance.tenders.collect.SourceRecord;

/**
 * APProch, the portal where public buyers announce their purchase projects
 * before any call for tenders, read from its open data; its projects are read
 * by {@link ApprochNoticeParser}. The dataset only lists the projects open
 * today, without publication date: they are all read once per collection, on
 * its last day.
 */
@Component
@Order(3)
public class ApprochConnector implements SourceConnector {

	public static final String SOURCE = "APPROCH";

	private final ApprochClient client;

	private final Clock clock;

	public ApprochConnector(ApprochClient client, Clock clock) {
		this.client = client;
		this.clock = clock;
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
		LocalDate today = LocalDate.now(clock);
		return day.equals(today) ? client.fetchCurrent(today) : List.of();
	}

}
