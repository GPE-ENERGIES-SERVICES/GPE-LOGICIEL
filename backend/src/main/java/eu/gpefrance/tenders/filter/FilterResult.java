package eu.gpefrance.tenders.filter;

/**
 * @param reason why the tender was retained or excluded, shown to users
 */
record FilterResult(boolean retained, String reason) {
}
