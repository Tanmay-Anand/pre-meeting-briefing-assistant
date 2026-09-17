package com.leadlens.facts;

/**
 * Lifecycle of a fact.
 *
 * <p>{@link #OPEN} is what drives the inclusion floors: every open objection, commitment and
 * unanswered question is included in a briefing regardless of age, because those are precisely
 * the facts that do not expire (IMPLEMENTATION_PLAN.md E.3, D.4).
 */
public enum FactStatus {

	/** Still unresolved. Survives selection at any age. */
	OPEN,

	/** Explicitly resolved by a later record. */
	RESOLVED,

	/** Contradicted by a later fact of the same kind. Retained, never deleted - the change
	 *  itself is more useful to the agent than either value alone (F.14). */
	SUPERSEDED
}
