package com.leadlens.democrm.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One attribute on a lead, with its own last-updated timestamp.
 *
 * <p>The per-field timestamp is the point of this table. The synchronisation check builds a
 * timeline per attribute mixing field values and extracted facts, and flags a contradiction
 * when the two most recent entries disagree - <em>regardless of which side is which</em>
 * (F.16). Without a per-field timestamp there is no timeline, only a guess.
 */
@Entity
@Table(
		name = "demo_lead_fields",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_demo_field_lead_attribute",
				columnNames = {"lead_id", "attribute_key"}),
		indexes = @Index(name = "ix_demo_field_tenant_lead", columnList = "tenant_id, lead_id"))
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DemoLeadField {

	@Id
	@Builder.Default
	private UUID id = UUID.randomUUID();

	@Column(name = "tenant_id", nullable = false, length = 64)
	private String tenantId;

	@Column(name = "lead_id", nullable = false, length = 128)
	private String leadId;

	/** Matches FactKind.leadFieldAttribute(): "bhk", "budget", "location", "timeline", ... */
	@Column(name = "attribute_key", nullable = false, length = 64)
	private String attributeKey;

	@Column(name = "value", columnDefinition = "text")
	private String value;

	/** When this specific field was last set - not when the lead row was last touched. */
	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;
}
