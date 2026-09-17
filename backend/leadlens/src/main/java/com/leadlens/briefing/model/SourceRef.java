package com.leadlens.briefing.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A citation: which CRM record produced a briefing point, and how to open it.
 *
 * <p>This is the Source References requirement made usable. {@link #span} carries the quoted
 * substring so the agent can see the actual sentence rather than taking the summary on trust -
 * which is the single most persuasive thing in the product.
 *
 * @param evidenceId  internal id, used to resolve and verify
 * @param evidenceKey the CRM's own id, e.g. "call:88213"
 * @param label       human-readable origin, e.g. "Call note - 14 September"
 * @param occurredAt  when the record was created
 * @param deepLink    CRM URL for this single record
 * @param span        the quoted substring, where one exists
 */
public record SourceRef(
		UUID evidenceId,
		String evidenceKey,
		String label,
		Instant occurredAt,
		String deepLink,
		String span) {
}
