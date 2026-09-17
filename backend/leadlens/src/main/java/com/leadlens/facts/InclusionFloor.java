package com.leadlens.facts;

/**
 * The selection rule for a {@link FactKind}.
 *
 * <p>This is the direct mitigation for the failure described in IMPLEMENTATION_PLAN.md D.4: if
 * selection is allowed to drop the one call note from four months ago where the customer raised
 * a financing objection that is still unresolved, the Objections section renders "no objections
 * recorded" with perfect structural integrity - correct citations, valid schema, every guard
 * green. Grounding does not catch that, because grounding governs whether a claim is
 * <em>sourced</em>, not whether the right thing was <em>looked at</em>.
 *
 * <p>Encoding the rule on the kind keeps it out of a switch statement that someone will later
 * edit without reading this comment.
 */
public enum InclusionFloor {

	/** Every fact of this kind with {@link FactStatus#OPEN}, regardless of age. Never ranked
	 *  away. */
	ALL_OPEN,

	/** Every fact of this kind, one per subject. */
	ALL,

	/** The most recent fact per {@code attributeKey}. */
	LATEST_PER_ATTRIBUTE,

	/** The most recent fact, plus the one it superseded when the value changed - because the
	 *  change is more useful to the agent than either value alone (F.14). */
	LATEST_PLUS_PRIOR_IF_CHANGED,

	/** The most recent fact only. */
	LATEST,

	/** Any single fact suffices; used where the question is existence, not content. */
	ANY,

	/** Ranked by recency and capped - the only place where recency is genuinely the relevant
	 *  signal. */
	RECENCY_CAPPED
}
