package com.leadlens.briefing.model;

/**
 * The verdict the grounding gate reached for a section (IMPLEMENTATION_PLAN.md F.1).
 *
 * <p>The distinction between {@link #DECLARE_EMPTY} and {@link #DEGRADED} is the whole point:
 * "this lead has no objections" and "we could not analyse this lead" are different claims, and
 * rendering them identically reassures the agent with nothing (F.9, F.10).
 */
public enum RenderState {

	/** At least one fact with a resolvable source, or a deterministic section. */
	RENDER,

	/** Genuinely nothing of this kind on record - say so explicitly, never leave a blank. */
	DECLARE_EMPTY,

	/** Not produced: no evidence at all, or the model was unavailable. Not the same as empty. */
	DEGRADED
}
