package eu.gpefrance.tenders.document;

import java.net.URI;
import java.net.URISyntaxException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import eu.gpefrance.tenders.eligibility.TenderEligibilityRepository;

/**
 * Keeps the addresses where the consultation file of each tender can be
 * obtained, as the notices of every source give them. Recording a link
 * changes nothing else on the tender.
 */
@Service
public class DceLinkService {

	private final DceLinkRepository links;

	private final TenderEligibilityRepository eligibilities;

	public DceLinkService(DceLinkRepository links, TenderEligibilityRepository eligibilities) {
		this.links = links;
		this.eligibilities = eligibilities;
	}

	/**
	 * Records the address given by a notice of a source for a tender; an
	 * address already known is only marked as seen again. Anything else than
	 * an http(s) address is left out.
	 */
	@Transactional
	public void record(Long tenderId, String source, String url, Instant now) {
		String address = url == null ? null : url.strip();
		String platform = platformOf(address);
		if (platform == null) {
			return;
		}
		links.findByTenderIdAndUrl(tenderId, address).ifPresentOrElse(link -> link.seenAgain(now), () -> {
			links.save(new DceLink(tenderId, source, address, platform, now));
			// The new address follows the eligibility of its tender at the next evaluation.
			eligibilities.markOutOfDate(tenderId);
		});
	}

	@Transactional(readOnly = true)
	public List<DceLink> linksOf(Long tenderId) {
		return links.findByTenderIdOrderByFirstSeenAtAscIdAsc(tenderId);
	}

	/**
	 * @return the host of an http(s) address without "www.", null for
	 * anything else
	 */
	static String platformOf(String url) {
		if (url == null || url.isEmpty()) {
			return null;
		}
		try {
			URI uri = new URI(url);
			String scheme = uri.getScheme();
			String host = uri.getHost();
			if (scheme == null || host == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) {
				return null;
			}
			String lower = host.toLowerCase(Locale.ROOT);
			return lower.startsWith("www.") ? lower.substring(4) : lower;
		}
		catch (URISyntaxException ex) {
			return null;
		}
	}

}
