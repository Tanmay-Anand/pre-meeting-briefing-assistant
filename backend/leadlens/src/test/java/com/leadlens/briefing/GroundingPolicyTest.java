package com.leadlens.briefing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.leadlens.briefing.model.RenderState;
import com.leadlens.facts.AtomicFact;
import com.leadlens.facts.FactKind;
import com.leadlens.facts.Provenance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The grounding gate (IMPLEMENTATION_PLAN.md F.1): a section may render substantive content only
 * if at least one fact with a resolvable source was actually selected for it.
 *
 * <p>Every branch below exists because a real failure mode maps to it. This suite is meant to be
 * mutation-tested by hand: delete the check in {@link GroundingPolicy#decide} and confirm these
 * go red. A guard nobody has watched fail is a guard nobody knows is wired up, and demonstrating
 * that is worth more in judging than another feature (Phase 4 step 5).
 */
class GroundingPolicyTest {

	@Test
	@DisplayName("rung 1: any selected fact renders, regardless of anything else")
	void factsPresentAlwaysRender() {
		RenderState verdict = GroundingPolicy.decide(List.of(objection()), true, false);

		assertThat(verdict)
				.as("a section with a sourced fact must render even while other extraction is still in flight")
				.isEqualTo(RenderState.RENDER);
	}

	@Test
	@DisplayName("rung 3: zero facts, but the lead has evidence and extraction finished -> declare empty")
	void zeroFactsWithCompleteExtractionDeclaresEmpty() {
		RenderState verdict = GroundingPolicy.decide(List.of(), true, true);

		assertThat(verdict)
				.as("\"no objections recorded\" is a real, checked claim - only reachable once "
						+ "extraction has actually finished reading everything")
				.isEqualTo(RenderState.DECLARE_EMPTY);
	}

	@Test
	@DisplayName("rung 4: the lead has no evidence at all -> degraded, never declare-empty")
	void noEvidenceAtAllDegrades() {
		RenderState verdict = GroundingPolicy.decide(List.of(), false, true);

		assertThat(verdict)
				.as("a lead nobody has contacted yet is not the same claim as \"no objections were "
						+ "raised\" - collapsing the two reassures the agent with nothing (F.9, F.10)")
				.isEqualTo(RenderState.DEGRADED);
	}

	@Test
	@DisplayName("rung 5: evidence exists but extraction has not finished -> degraded, not declare-empty")
	void incompleteExtractionDegradesEvenWithEvidence() {
		RenderState verdict = GroundingPolicy.decide(List.of(), true, false);

		assertThat(verdict)
				.as("zero facts right now does not mean zero facts exist - it means ask again once "
						+ "extraction finishes. Declaring empty here would be exactly the zero-evidence "
						+ "hallucination risk F.1 exists to close off")
				.isEqualTo(RenderState.DEGRADED);
	}

	@Test
	@DisplayName("a null fact list is treated the same as an empty one")
	void nullFactsTreatedAsEmpty() {
		assertThat(GroundingPolicy.decide(null, true, true)).isEqualTo(RenderState.DECLARE_EMPTY);
		assertThat(GroundingPolicy.decide(null, false, true)).isEqualTo(RenderState.DEGRADED);
	}

	private static AtomicFact objection() {
		return AtomicFact.builder()
				.evidenceId(UUID.randomUUID())
				.tenantId("t-acme")
				.leadRef("12345")
				.kind(FactKind.OBJECTION)
				.claim("Customer feels the pricing is above their budget")
				.provenance(Provenance.INFERRED)
				.occurredAt(Instant.parse("2026-09-14T11:40:00Z"))
				.extractorVersion("v1")
				.build();
	}
}
