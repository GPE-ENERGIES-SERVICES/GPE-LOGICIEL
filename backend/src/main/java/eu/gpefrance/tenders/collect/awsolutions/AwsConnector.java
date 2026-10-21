package eu.gpefrance.tenders.collect.awsolutions;

import java.time.LocalDate;
import java.util.List;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import eu.gpefrance.tenders.collect.RawNoticeRepository;
import eu.gpefrance.tenders.collect.SourceAccess;
import eu.gpefrance.tenders.collect.SourceConnector;
import eu.gpefrance.tenders.collect.SourceRecord;

/**
 * marches-publics.info, the buyer profile of AW Solutions, read from its
 * public search within the limits of its terms of use; its notices are read
 * by {@link AwsNoticeParser}. Collected after the BOAMP and TED. Disabled by
 * default: its terms do not forbid a reading below their limits, but do not
 * explicitly allow it either.
 */
@Component
@Order(4)
public class AwsConnector implements SourceConnector {

	public static final String SOURCE = "AWS";

	private final AwsClient client;

	private final RawNoticeRepository rawNotices;

	public AwsConnector(AwsClient client, RawNoticeRepository rawNotices) {
		this.client = client;
		this.rawNotices = rawNotices;
	}

	@Override
	public String source() {
		return SOURCE;
	}

	@Override
	public SourceAccess access() {
		return SourceAccess.AUTOMATED;
	}

	/**
	 * Only today and yesterday can be searched; the page of a notice already
	 * stored is not read again.
	 */
	@Override
	public List<SourceRecord> fetchPublishedOn(LocalDate day) {
		return client.fetchPublishedOn(day, id -> rawNotices.existsBySourceAndExternalId(SOURCE, id));
	}

}
