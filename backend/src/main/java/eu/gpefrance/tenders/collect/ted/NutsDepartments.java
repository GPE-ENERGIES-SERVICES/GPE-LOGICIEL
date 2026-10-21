package eu.gpefrance.tenders.collect.ted;

import java.util.Locale;
import java.util.Map;

/**
 * Department of each French NUTS 3 region (NUTS 2024, Eurostat), as TED
 * gives the place of performance. Each NUTS 3 region of France is one
 * department; the table was made by matching the Eurostat names with the
 * official geographic code of INSEE (101 regions, 101 departments).
 */
public final class NutsDepartments {

	private static final Map<String, String> DEPARTMENTS = Map.ofEntries(
			Map.entry("FR101", "75"),
			Map.entry("FR102", "77"),
			Map.entry("FR103", "78"),
			Map.entry("FR104", "91"),
			Map.entry("FR105", "92"),
			Map.entry("FR106", "93"),
			Map.entry("FR107", "94"),
			Map.entry("FR108", "95"),
			Map.entry("FRB01", "18"),
			Map.entry("FRB02", "28"),
			Map.entry("FRB03", "36"),
			Map.entry("FRB04", "37"),
			Map.entry("FRB05", "41"),
			Map.entry("FRB06", "45"),
			Map.entry("FRC11", "21"),
			Map.entry("FRC12", "58"),
			Map.entry("FRC13", "71"),
			Map.entry("FRC14", "89"),
			Map.entry("FRC21", "25"),
			Map.entry("FRC22", "39"),
			Map.entry("FRC23", "70"),
			Map.entry("FRC24", "90"),
			Map.entry("FRD11", "14"),
			Map.entry("FRD12", "50"),
			Map.entry("FRD13", "61"),
			Map.entry("FRD21", "27"),
			Map.entry("FRD22", "76"),
			Map.entry("FRE11", "59"),
			Map.entry("FRE12", "62"),
			Map.entry("FRE21", "02"),
			Map.entry("FRE22", "60"),
			Map.entry("FRE23", "80"),
			Map.entry("FRF11", "67"),
			Map.entry("FRF12", "68"),
			Map.entry("FRF21", "08"),
			Map.entry("FRF22", "10"),
			Map.entry("FRF23", "51"),
			Map.entry("FRF24", "52"),
			Map.entry("FRF31", "54"),
			Map.entry("FRF32", "55"),
			Map.entry("FRF33", "57"),
			Map.entry("FRF34", "88"),
			Map.entry("FRG01", "44"),
			Map.entry("FRG02", "49"),
			Map.entry("FRG03", "53"),
			Map.entry("FRG04", "72"),
			Map.entry("FRG05", "85"),
			Map.entry("FRH01", "22"),
			Map.entry("FRH02", "29"),
			Map.entry("FRH03", "35"),
			Map.entry("FRH04", "56"),
			Map.entry("FRI11", "24"),
			Map.entry("FRI12", "33"),
			Map.entry("FRI13", "40"),
			Map.entry("FRI14", "47"),
			Map.entry("FRI15", "64"),
			Map.entry("FRI21", "19"),
			Map.entry("FRI22", "23"),
			Map.entry("FRI23", "87"),
			Map.entry("FRI31", "16"),
			Map.entry("FRI32", "17"),
			Map.entry("FRI33", "79"),
			Map.entry("FRI34", "86"),
			Map.entry("FRJ11", "11"),
			Map.entry("FRJ12", "30"),
			Map.entry("FRJ13", "34"),
			Map.entry("FRJ14", "48"),
			Map.entry("FRJ15", "66"),
			Map.entry("FRJ21", "09"),
			Map.entry("FRJ22", "12"),
			Map.entry("FRJ23", "31"),
			Map.entry("FRJ24", "32"),
			Map.entry("FRJ25", "46"),
			Map.entry("FRJ26", "65"),
			Map.entry("FRJ27", "81"),
			Map.entry("FRJ28", "82"),
			Map.entry("FRK11", "03"),
			Map.entry("FRK12", "15"),
			Map.entry("FRK13", "43"),
			Map.entry("FRK14", "63"),
			Map.entry("FRK21", "01"),
			Map.entry("FRK22", "07"),
			Map.entry("FRK23", "26"),
			Map.entry("FRK24", "38"),
			Map.entry("FRK25", "42"),
			Map.entry("FRK26", "69"),
			Map.entry("FRK27", "73"),
			Map.entry("FRK28", "74"),
			Map.entry("FRL01", "04"),
			Map.entry("FRL02", "05"),
			Map.entry("FRL03", "06"),
			Map.entry("FRL04", "13"),
			Map.entry("FRL05", "83"),
			Map.entry("FRL06", "84"),
			Map.entry("FRM01", "2A"),
			Map.entry("FRM02", "2B"),
			Map.entry("FRY10", "971"),
			Map.entry("FRY20", "972"),
			Map.entry("FRY30", "973"),
			Map.entry("FRY40", "974"),
			Map.entry("FRY50", "976"));

	private NutsDepartments() {
	}

	/**
	 * @return the department of a French NUTS 3 code, null for any other code
	 */
	public static String departmentOf(String nuts) {
		return nuts == null ? null : DEPARTMENTS.get(nuts.strip().toUpperCase(Locale.ROOT));
	}

}
