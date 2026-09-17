package com.leadlens.briefing;

import java.util.List;
import java.util.Optional;

import com.leadlens.common.tenant.ActingUser;
import com.leadlens.crm.model.LeadRef;
import com.leadlens.crm.model.LeadSnapshot;
import com.leadlens.crm.model.ScheduledActivity;
import com.leadlens.evidence.EvidenceItem;
import com.leadlens.facts.AtomicFact;

/**
 * Everything a briefing is built from, already permission-filtered.
 *
 * <p>Assembled once, at the boundary, before anything reaches a model. Passing this single
 * object around means no later stage can reach back into the CRM for "just one more field" and
 * bypass the filter that made it safe (IMPLEMENTATION_PLAN.md F.7).
 *
 * @param facts may be empty. Phase 2's deterministic sections are built without any, which is
 *              the point: they still render when extraction has not run, has failed, or is
 *              still in flight (D.2).
 */
public record BriefingContext(
		LeadRef ref,
		ActingUser user,
		LeadSnapshot lead,
		List<EvidenceItem> evidence,
		List<ScheduledActivity> upcoming,
		List<AtomicFact> facts) {

	public BriefingContext {
		evidence = evidence == null ? List.of() : List.copyOf(evidence);
		upcoming = upcoming == null ? List.of() : List.copyOf(upcoming);
		facts = facts == null ? List.of() : List.copyOf(facts);
	}

	/**
	 * The meeting this briefing is preparing for, if any.
	 *
	 * <p>Empty is ordinary - an agent can prepare for a lead without a scheduled activity - and
	 * must not be confused with "a meeting exists but we could not read it".
	 */
	public Optional<ScheduledActivity> nextActivity() {
		return upcoming.stream().findFirst();
	}

	/** True when there is nothing on record at all. Drives the degraded verdict (F.1 rung 4). */
	public boolean hasNoEvidence() {
		return evidence.isEmpty();
	}
}
