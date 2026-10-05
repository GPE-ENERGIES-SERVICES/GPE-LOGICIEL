package eu.gpefrance.tenders.collect;

/**
 * How the notices of a source may be obtained. Only the sources that a
 * program may read are collected; the others are never requested, whatever
 * protection they use.
 */
public enum SourceAccess {

	/** Public API or open data: collected. */
	AUTOMATED,

	/** Access with an account or a key of the company, given by the environment: collected. */
	AUTHENTICATED,

	/** A person downloads the notices and hands them to the application: never requested. */
	MANUAL,

	/** Protected against programs (CAPTCHA, anti-bot): consulted by hand, never requested. */
	NOT_AUTOMATABLE;

	public boolean isCollected() {
		return this == AUTOMATED || this == AUTHENTICATED;
	}

}
