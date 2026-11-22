package eu.gpefrance.tenders.filter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import eu.gpefrance.tenders.tender.Tender;

/**
 * The domains of GPE a tender belongs to, by rules: a CPV code of the domain,
 * or one of its words in the title, the description or the lots. Free and
 * immediate on every collected tender; the AI analysis of the consultation
 * file confirms or corrects it afterwards.
 */
public final class DomainRules {

	public static final String ELECTRICITY = "ELECTRICITY";

	public static final String PHOTOVOLTAIC = "PHOTOVOLTAIC";

	// shortcut: first lists, to complete with the words met in the files of GPE.
	private static final Map<String, List<String>> CPV_PREFIXES = Map.of(
			// Electrical installations, alarms, telecoms, other installations, lighting, cabling
			ELECTRICITY, List.of("4531", "45312", "45314", "45315", "45316", "45317", "3152", "3153", "31682",
					"32424", "35121", "35125", "50711", "50712"),
			// Solar modules and installations, solar panel roofing, solar energy
			PHOTOVOLTAIC, List.of("09331", "09332", "45261215", "31712331"));

	private static final Map<String, List<String>> KEYWORDS = Map.of(
			ELECTRICITY, List.of("courants forts", "courants faibles", "courant fort", "courant faible", "electricite",
					"electrique", "cfo", "cfa", "tgbt", "ssi", "systeme de securite incendie", "alarme incendie",
					"videosurveillance", "video protection", "videoprotection", "controle d acces", "vdi",
					"precablage", "cablage", "eclairage", "luminaire", "intrusion", "interphonie", "sonorisation",
					"reseau informatique", "fibre optique", "borne de recharge", "irve", "poste de transformation"),
			PHOTOVOLTAIC, List.of("photovolt", "ombriere", "centrale solaire", "panneaux solaires",
					"panneau solaire", "autoconsommation", "solaire pv", "modules pv", "installation pv"));

	private DomainRules() {
	}

	/** @return the domains found, in a fixed order; empty when none */
	public static List<String> of(Tender tender) {
		TextMatcher text = new TextMatcher(TenderFilter.searchableText(tender));
		List<String> domains = new ArrayList<>();
		for (String domain : List.of(ELECTRICITY, PHOTOVOLTAIC)) {
			boolean byCpv = tender.getCpvCodes()
				.stream()
				.anyMatch(code -> CPV_PREFIXES.get(domain).stream().anyMatch(code::startsWith));
			if (byCpv || KEYWORDS.get(domain).stream().anyMatch(text::contains)) {
				domains.add(domain);
			}
		}
		return domains;
	}

}
