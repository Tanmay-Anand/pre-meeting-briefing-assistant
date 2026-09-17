package com.leadlens.evidence;

/**
 * How the raw record reached LeadLens - the merge point for the three acquisition modes in
 * IMPLEMENTATION_PLAN.md G.3.
 *
 * <p>This is <strong>not</strong> the same as provenance. {@code SourceMode} records how we
 * obtained the record; provenance records how a claim was derived from it. Both are rendered,
 * because they answer different questions: "can this be trusted as a CRM record?" versus
 * "did a human write this down or did a model read it into the text?"
 */
public enum SourceMode {

	/** Fetched from a CRM's own API. Fully trusted: may populate the snapshot and carries a
	 *  working deep link. */
	CRM_API,

	/** Scraped from the rendered CRM page. May produce facts, but must never be rendered as an
	 *  authoritative CRM field, and its deep link points at the page rather than a record. */
	DOM_SCRAPE,

	/** Text the agent highlighted and submitted themselves. Same constraints as
	 *  {@link #DOM_SCRAPE}, and the panel says plainly that the agent supplied it. */
	USER_SUPPLIED;

	/**
	 * Whether evidence from this mode may back an authoritative Customer Snapshot field.
	 * Only records that came from a CRM's API qualify - anything read off a page could have
	 * been rendered from something other than the record of truth.
	 */
	public boolean isAuthoritative() {
		return this == CRM_API;
	}
}
