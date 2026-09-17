package com.leadlens.democrm.model;

import java.time.Instant;

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

/**
 * A lead in the Demo CRM.
 *
 * <p>The Demo CRM is a separate application that happens to share a database process. LeadLens
 * reaches it over HTTP like any other CRM, so the adapter boundary stays honest rather than
 * quietly reading these tables directly (IMPLEMENTATION_PLAN.md G.4).
 *
 * <p>Deliberately thin: the lead's attribute values live in {@link DemoLeadField} instead of
 * columns here, because the field-versus-fact synchronisation check needs to know <em>when each
 * field was last set</em>, not just when the row was last touched (F.16). A single
 * {@code updatedAt} on the lead cannot answer "was the bhk field set before or after that site
 * visit?", which is the entire question.
 */
@Entity
@Table(name = "demo_leads", indexes = @Index(name = "ix_demo_lead_tenant", columnList = "tenant_id"))
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class DemoLead {

	/** The CRM's own lead id, as it appears in the URL: /leads/12345. */
	@Id
	@Column(name = "id", length = 128)
	private String id;

	@Column(name = "tenant_id", nullable = false, length = 64)
	private String tenantId;

	@Column(name = "assigned_user_id", length = 128)
	private String assignedUserId;

	@Column(name = "created_at", nullable = false)
	private Instant createdAt;
}
