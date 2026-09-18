package com.leadlens.briefing;

import java.util.List;

import com.leadlens.briefing.model.RenderState;
import com.leadlens.facts.AtomicFact;

/**
 * A section may render substantive content only if at least one fact with a resolvable
 * evidenceId was selected for it. Otherwise it says so, in one of two different ways
 * (IMPLEMENTATION_PLAN.md F.1).
 *
 * <h2>Why a per-section check, not a per-claim one</h2>
 * A validator that inspects claims after they arrive can only catch a bad citation on a claim
 * that exists. It cannot catch a composer that emits confident prose from nothing, because a
 * zero-evidence lead gives it no citations to invalidate - it never emitted any, it just wrote
 * a sentence. The equivalent failure in the Praxis Chess project produced a fluent, confident
 * answer about a chess player who does not exist, and passed every claim-level guard, because a
 * loop that calls no tools terminates cleanly. So the check has to ask "did anything arrive at
 * all", before any claim-level scrutiny is even relevant.
 *
 * <h2>DECLARE_EMPTY versus DEGRADED</h2>
 * "No objections recorded" and "we could not analyse this lead" are different claims. Collapsing
 * them reassures the agent with nothing in the second case, which is worse than saying nothing
 * (F.9, F.10). This is why the verdict needs to know not just "are there facts" but "could there
 * have been."
 *
 * <p>Pure function: no repositories, no clock, no Spring. A guard nobody has watched fail is a
 * guard nobody knows is wired up - {@code GroundingPolicyTest} mutation-tests this by deleting
 * the check and confirming a specific, counted set of tests goes red.
 */
public final class GroundingPolicy {

	private GroundingPolicy() {
	}

	/**
	 * @param selectedFacts     what {@link FactSelector} chose for this section; empty is a real
	 *                          answer, not an error
	 * @param leadHasAnyEvidence whether the lead has any evidence at all - a lead with zero
	 *                          evidence can never honestly produce more than Missing Information
	 * @param extractionComplete whether every evidence item has been through the extractor -
	 *                          false while a run is still in flight, or when the model was
	 *                          unavailable and items were never marked
	 */
	public static RenderState decide(List<AtomicFact> selectedFacts, boolean leadHasAnyEvidence, boolean extractionComplete) {
		if (selectedFacts != null && !selectedFacts.isEmpty()) {
			// Rung 1: something arrived, sourced. Render it - regardless of whether extraction
			// elsewhere on the lead is still running, because this section's own answer is
			// already grounded.
			return RenderState.RENDER;
		}
		if (!leadHasAnyEvidence) {
			// Rung 4: nothing was ever there to look at. Not this section's fault specifically -
			// the whole briefing is in this state, and Missing Information carries the weight.
			return RenderState.DEGRADED;
		}
		if (!extractionComplete) {
			// Rung 5: evidence exists but has not finished being read. Zero facts here right now
			// does not mean zero facts exist - it means "ask again once extraction finishes."
			return RenderState.DEGRADED;
		}
		// Rung 3: evidence was fully read, and genuinely none of it matched this section.
		return RenderState.DECLARE_EMPTY;
	}
}
