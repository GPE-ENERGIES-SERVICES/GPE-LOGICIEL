package eu.gpefrance.tenders.tender;

import java.util.List;
import java.util.Map;

/**
 * Departments of each region of France, by the region's name as the
 * interface sends it.
 */
final class Regions {

	private static final Map<String, List<String>> DEPARTMENTS = Map.ofEntries(
			Map.entry("Auvergne-Rhône-Alpes",
					List.of("01", "03", "07", "15", "26", "38", "42", "43", "63", "69", "73", "74")),
			Map.entry("Bourgogne-Franche-Comté", List.of("21", "25", "39", "58", "70", "71", "89", "90")),
			Map.entry("Bretagne", List.of("22", "29", "35", "56")),
			Map.entry("Centre-Val de Loire", List.of("18", "28", "36", "37", "41", "45")),
			Map.entry("Corse", List.of("2A", "2B")),
			Map.entry("Grand Est", List.of("08", "10", "51", "52", "54", "55", "57", "67", "68", "88")),
			Map.entry("Hauts-de-France", List.of("02", "59", "60", "62", "80")),
			Map.entry("Île-de-France", List.of("75", "77", "78", "91", "92", "93", "94", "95")),
			Map.entry("Normandie", List.of("14", "27", "50", "61", "76")),
			Map.entry("Nouvelle-Aquitaine",
					List.of("16", "17", "19", "23", "24", "33", "40", "47", "64", "79", "86", "87")),
			Map.entry("Occitanie",
					List.of("09", "11", "12", "30", "31", "32", "34", "46", "48", "65", "66", "81", "82")),
			Map.entry("Pays de la Loire", List.of("44", "49", "53", "72", "85")),
			Map.entry("Provence-Alpes-Côte d'Azur", List.of("04", "05", "06", "13", "83", "84")),
			Map.entry("Outre-mer", List.of("971", "972", "973", "974", "976")));

	private Regions() {
	}

	/**
	 * @return the departments of the region, empty for an unknown region
	 */
	static List<String> departmentsOf(String region) {
		return DEPARTMENTS.getOrDefault(region, List.of());
	}

}
