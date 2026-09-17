package com.leadlens.crm.model;

import java.time.Instant;
import java.util.List;

import com.leadlens.evidence.EvidenceType;

/**
 * An upcoming activity the agent is preparing for - the thing the Meeting Context section
 * describes, and what the scheduled worker scans for at T-30 minutes.
 *
 * @param activityId  the CRM's id for this activity
 * @param type        site visit, call, meeting
 * @param scheduledAt when it starts
 * @param purpose     the stated objective, or null - absent means Missing Information says so,
 *                    rather than the system inventing one
 * @param participants who is expected, where the CRM records it
 * @param deepLink    the CRM URL for this activity
 */
public record ScheduledActivity(
		String activityId,
		EvidenceType type,
		Instant scheduledAt,
		String purpose,
		List<String> participants,
		String deepLink) {

	public ScheduledActivity {
		participants = participants == null ? List.of() : List.copyOf(participants);
	}

	/** True when the CRM never recorded why this meeting is happening. */
	public boolean hasNoStatedPurpose() {
		return purpose == null || purpose.isBlank();
	}
}
