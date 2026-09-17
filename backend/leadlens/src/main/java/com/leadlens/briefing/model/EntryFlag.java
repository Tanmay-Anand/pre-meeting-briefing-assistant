package com.leadlens.briefing.model;

/**
 * Why an entry is notable beyond its text.
 *
 * <p>The five kinds of empty in IMPLEMENTATION_PLAN.md E.6, plus the ordinary case. They exist
 * as distinct values because each implies a different action, and a single "missing" would
 * hand that judgement to the agent with no information to make it on (F.10).
 */
public enum EntryFlag {

	/** Nothing special; an ordinary rendered entry. */
	NONE,

	/** Field null, no fact of that kind exists. <em>Ask the customer.</em> */
	NEVER_CAPTURED,

	/** The field exists but this user's role hides it. <em>Do not ask - escalate internally.</em> */
	MASKED,

	/**
	 * The field is empty but a conversation already told us the answer.
	 * <em>Update the CRM - the information is already known.</em>
	 *
	 * <p>The highest-value output in the system: not missing information, unsynced information.
	 */
	UNSYNCED,

	/**
	 * The field holds a value and a differently-valued statement exists for the same attribute.
	 * <em>Show both with their dates; the agent decides.</em>
	 *
	 * <p>Distinct from {@link #UNSYNCED} because the field is not empty, it is outdated - or
	 * the field is the newest event but disagrees with the most recent conversation, which is
	 * <em>more</em> worth flagging, not less (F.16).
	 */
	CONTRADICTED,

	/** Populated, but long before several later interactions. <em>Re-confirm it.</em> */
	STALE,

	/** A record exists but carries no readable content, e.g. a call with no transcript (E.5). */
	NOT_SUMMARISED
}
