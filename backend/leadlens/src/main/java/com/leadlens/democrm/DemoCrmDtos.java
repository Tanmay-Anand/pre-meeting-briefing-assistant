package com.leadlens.democrm;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import jakarta.validation.constraints.NotBlank;

/**
 * Wire types for the Demo CRM's own HTTP API.
 *
 * <p>These are deliberately <em>not</em> LeadLens's domain types. The Demo CRM is standing in
 * for a foreign system, and if its API spoke in {@code EvidenceItem}s the adapter would have
 * nothing to translate - which would make the whole adapter layer look like ceremony rather
 * than the real boundary it is (G.1, G.4).
 */
public final class DemoCrmDtos {

	private DemoCrmDtos() {
	}

	/**
	 * A lead's fields as the CRM reports them.
	 *
	 * @param fields attributeKey -> value/updatedAt/masked. Masked entries carry no value, so
	 *               a value this user may not see never crosses the wire at all.
	 */
	public record LeadResponse(
			String id,
			String assignedUserId,
			Instant createdAt,
			Map<String, FieldResponse> fields) {
	}

	public record FieldResponse(String value, Instant updatedAt, boolean masked) {
	}

	public record ActivityResponse(
			String id,
			String type,
			Instant occurredAt,
			String actor,
			String channel,
			String text,
			Map<String, Object> structured,
			boolean scheduled,
			String purpose,
			Instant updatedAt) {
	}

	public record LeadSummaryResponse(String id, String name, String status, Instant createdAt) {
	}

	/**
	 * Request to log a new activity. This endpoint is what powers the refresh demo: the agent
	 * adds a WhatsApp message in the CRM, and the briefing's freshness pill turns yellow.
	 */
	public record CreateActivityRequest(
			@NotBlank String type,
			@NotBlank String actor,
			@NotBlank String channel,
			String text,
			Instant occurredAt,
			Map<String, Object> structured,
			String purpose) {
	}

	/** Request to set one field on the lead - the write the agent themselves confirms (A.6). */
	public record UpdateFieldRequest(@NotBlank String value) {
	}

	public record ActivityListResponse(List<ActivityResponse> activities) {
	}
}
