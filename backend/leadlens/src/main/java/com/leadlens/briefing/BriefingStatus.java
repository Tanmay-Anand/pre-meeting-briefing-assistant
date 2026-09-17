package com.leadlens.briefing;

/**
 * Whether a briefing has actually been produced.
 *
 * <p>Existence is not generation (IMPLEMENTATION_PLAN.md F.9). A briefing row existing must not
 * imply the briefing was generated: returning a finished-looking document while extraction is
 * still running would show the agent "No objections recorded" for a lead the system never read,
 * which reassures them with nothing. The API returns 202 for {@link #PENDING} and
 * {@link #PARTIAL}, and 200 only for the terminal states.
 */
public enum BriefingStatus {

	/** Created, nothing produced yet. */
	PENDING,

	/** Deterministic sections are in; extraction or composition still running. */
	PARTIAL,

	/** Fully generated. */
	COMPLETE,

	/**
	 * Finished, but without the model-backed sections - no evidence at all, or the model was
	 * unavailable. A real, servable answer: the deterministic half never fails, so a briefing
	 * can always ship something true (D.2).
	 */
	DEGRADED,

	/** Could not be produced. Distinct from DEGRADED, which is a usable result. */
	FAILED
}
