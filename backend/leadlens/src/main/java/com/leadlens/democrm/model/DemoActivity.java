package com.leadlens.democrm.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * An activity on a lead: a call, a WhatsApp message, a note, a task, a status change.
 *
 * <p>These are the raw records the adapter normalises into {@code EvidenceItem}s. Both past
 * activities and future scheduled ones live here, distinguished by {@link #scheduled} - a site
 * visit at 4pm today is the same kind of thing before and after it happens, and splitting them
 * into two tables would mean writing the "previous related interaction" lookup twice.
 *
 * <p>{@link #text} is deliberately nullable. A call with a recording but no transcript is a real
 * record with no readable content, and it must survive into the briefing as "not summarised"
 * rather than being dropped - otherwise an unread call and an uneventful call look identical
 * (E.5).
 */
@Entity
@Table(
		name = "demo_activities",
		indexes = {
				@Index(name = "ix_demo_activity_tenant_lead", columnList = "tenant_id, lead_id"),
				@Index(name = "ix_demo_activity_occurred", columnList = "tenant_id, lead_id, occurred_at")
		})
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DemoActivity {

	@Id
	@Builder.Default
	private UUID id = UUID.randomUUID();

	@Column(name = "tenant_id", nullable = false, length = 64)
	private String tenantId;

	@Column(name = "lead_id", nullable = false, length = 128)
	private String leadId;

	/** Mirrors com.leadlens.evidence.EvidenceType by name; kept as a string so the Demo CRM
	 *  does not depend on LeadLens's own enums - it is meant to look like a foreign system. */
	@Column(name = "type", nullable = false, length = 32)
	private String type;

	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	/** "AGENT", "CUSTOMER" or "SYSTEM". */
	@Column(name = "actor", nullable = false, length = 16)
	private String actor;

	/** "PHONE", "WHATSAPP", "EMAIL", "CRM" or "IN_PERSON". */
	@Column(name = "channel", nullable = false, length = 16)
	private String channel;

	/** Null when the record has no readable content - see the class note. */
	@Column(name = "text", columnDefinition = "text")
	private String text;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "structured")
	@Builder.Default
	private Map<String, Object> structured = new LinkedHashMap<>();

	/** True for a future activity the agent is preparing for. */
	@Column(name = "scheduled", nullable = false)
	@Builder.Default
	private boolean scheduled = false;

	/** Why the meeting is happening. Null is meaningful: Missing Information reports a meeting
	 *  with no stated objective rather than inventing one. */
	@Column(name = "purpose", columnDefinition = "text")
	private String purpose;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
