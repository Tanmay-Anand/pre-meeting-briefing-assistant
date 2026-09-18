package com.leadlens.evidence;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The central abstraction: one normalised CRM record.
 *
 * <p>Every heterogeneous CRM record - a call, a WhatsApp thread, a status change, a shared
 * brochure - collapses into this one shape. Everything downstream operates on this and nothing
 * else, which is what makes the briefing engine testable and the CRM sources swappable. This is
 * the "Common CRM Model" of IMPLEMENTATION_PLAN.md E.1 and the architecture slide.
 *
 * <h2>Deviation from the plan, and why</h2>
 * The plan describes {@code id} as the stable namespaced string ({@code "call:88213"}). That
 * string is only unique <em>within</em> one CRM in one tenant, so using it as the primary key
 * would let two tenants collide on the same row - the exact cross-tenant leak C4 and R14 exist
 * to prevent. So the primary key is a surrogate {@link UUID}, and the namespaced string is kept
 * as {@link #evidenceKey}, unique per (tenant, crm). Callers that need "the stable id from the
 * CRM" want {@code evidenceKey}; callers that need "the thing an AtomicFact cites" want
 * {@code id}.
 */
@Entity
@Table(
		name = "evidence_items",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_evidence_tenant_crm_key",
				columnNames = {"tenant_id", "crm_key", "evidence_key"}),
		indexes = {
				@Index(name = "ix_evidence_tenant_lead", columnList = "tenant_id, lead_ref"),
				@Index(name = "ix_evidence_occurred_at", columnList = "tenant_id, lead_ref, occurred_at")
		})
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@lombok.AllArgsConstructor(access = AccessLevel.PRIVATE)
public class EvidenceItem {

	@Id
	@Builder.Default
	private UUID id = UUID.randomUUID();

	@Column(name = "tenant_id", nullable = false, length = 64)
	private String tenantId;

	/** Which adapter produced this - "demo", "leadrat", "generic-dom". */
	@Column(name = "crm_key", nullable = false, length = 64)
	private String crmKey;

	/** The lead's identifier in that CRM. */
	@Column(name = "lead_ref", nullable = false, length = 128)
	private String leadRef;

	/** The CRM's own stable, namespaced id for this record: {@code "call:88213"}. */
	@Column(name = "evidence_key", nullable = false, length = 256)
	private String evidenceKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "type", nullable = false, length = 32)
	private EvidenceType type;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	@Enumerated(EnumType.STRING)
	@Column(name = "actor", nullable = false, length = 16)
	private Actor actor;

	@Column(name = "actor_id", length = 128)
	private String actorId;

	@Enumerated(EnumType.STRING)
	@Column(name = "channel", nullable = false, length = 16)
	private Channel channel;

	/**
	 * Normalised content, or null.
	 *
	 * <p>A null here is meaningful and must not be treated as "nothing happened" (E.5). A call
	 * with a recording but no transcript is a real gap the agent should know about: it surfaces
	 * in Missing Information as "call on 14 Sep is not summarised". Dropping such items would
	 * make an unread call and an uneventful call indistinguishable.
	 */
	@Column(name = "text", columnDefinition = "text")
	private String text;

	/** Type-specific payload: propertyId, oldStatus/newStatus, dueAt, and so on. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "structured")
	@Builder.Default
	private Map<String, Object> structured = new LinkedHashMap<>();

	/** CRM URL for this single record. Required by the Source References section. */
	@Column(name = "deep_link", length = 1024)
	private String deepLink;

	@Enumerated(EnumType.STRING)
	@Column(name = "source_mode", nullable = false, length = 16)
	private SourceMode sourceMode;

	/** Feeds the briefing's evidence fingerprint, which is how staleness is detected (D.5). */
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	/**
	 * The extractor version that has run on this item, or null if none has.
	 *
	 * <p>Exists solely to make the extraction cache correct. {@code FactRepository}'s cache
	 * lookup is "facts with this evidenceId and extractorVersion" - which is indistinguishable
	 * between "never extracted" and "extracted, and genuinely produced nothing" when the result
	 * is an empty list. A quiet call that yields zero facts would otherwise be re-sent to the
	 * model on every single generation forever, silently breaking the "adding one activity costs
	 * exactly one extraction call" guarantee (F.4). This field is what makes zero a cacheable
	 * answer.
	 */
	@Column(name = "facts_extracted_version", length = 32)
	private String factsExtractedVersion;

	/** True when this record carries no readable content - see {@link #text}. */
	public boolean hasNoText() {
		return text == null || text.isBlank();
	}
}
