package com.leadlens.briefing;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One generated briefing.
 *
 * <p>Every version is retained rather than overwritten. That is what makes the What Changed
 * panel a deterministic diff of two stored documents instead of a second thing to trust, and it
 * is also what the optional post-meeting-comparison scope would need later (D.5).
 */
@Entity
@Table(
		name = "briefings",
		indexes = {
				@Index(name = "ix_briefing_tenant_lead", columnList = "tenant_id, crm_key, lead_ref"),
				@Index(name = "ix_briefing_created", columnList = "tenant_id, created_at")
		})
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class Briefing {

	@Id
	@Builder.Default
	private UUID id = UUID.randomUUID();

	@Column(name = "tenant_id", nullable = false, length = 64)
	private String tenantId;

	@Column(name = "crm_key", nullable = false, length = 64)
	private String crmKey;

	@Column(name = "lead_ref", nullable = false, length = 128)
	private String leadRef;

	/** The upcoming activity this briefing prepares for, when there is one. */
	@Column(name = "activity_id", length = 128)
	private String activityId;

	/**
	 * Project context supplied alongside the lead, when the CRM has one and the caller sent it -
	 * carried here purely so {@code /refresh} can hand the same context back to the narrative
	 * source without the caller having to remember and resend it.
	 */
	@Column(name = "project_ref", length = 128)
	private String projectRef;

	/**
	 * Who it was generated for. Briefings are per-user because field masking is per-role: two
	 * agents looking at the same lead may legitimately be owed different documents (F.7).
	 */
	@Column(name = "generated_for", nullable = false, length = 128)
	private String generatedFor;

	/**
	 * Hash of the evidence this was built from. A mismatch against the lead's current evidence
	 * is what turns the freshness pill yellow - it proves the system knows what changed rather
	 * than silently regenerating (D.5).
	 */
	@Column(name = "evidence_fingerprint", nullable = false, length = 128)
	private String evidenceFingerprint;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	@Builder.Default
	private BriefingStatus status = BriefingStatus.PENDING;

	/** Which composer model produced the inferential sections, for reproducibility. */
	@Column(name = "model", length = 128)
	private String model;

	@Column(name = "prompt_version", length = 32)
	private String promptVersion;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;

	/** Set when a newer briefing replaces this one. The old one is kept, never deleted. */
	@Column(name = "superseded_by")
	private UUID supersededBy;

	/** Whether this document is finished. See {@link BriefingStatus} for why it matters. */
	public boolean isComplete() {
		return status == BriefingStatus.COMPLETE || status == BriefingStatus.DEGRADED;
	}
}
