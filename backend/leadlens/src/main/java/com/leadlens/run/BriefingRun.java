package com.leadlens.run;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A generation run, persisted rather than held in memory.
 *
 * <p>An in-process progress map is correct for exactly one JVM and wrong the moment there are
 * two - one tenant's bulk refresh must not starve visibility into another's, and a restart must
 * not silently lose every run in flight (IMPLEMENTATION_PLAN.md 0.3, Appendix 1). Postgres is
 * already a dependency; this needed nothing new.
 */
@Entity
@Table(
		name = "briefing_runs",
		indexes = {
				@Index(name = "ix_run_tenant_lead", columnList = "tenant_id, crm_key, lead_ref"),
				@Index(name = "ix_run_briefing", columnList = "briefing_id")
		})
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BriefingRun {

	@Id
	@Builder.Default
	private UUID id = UUID.randomUUID();

	@Column(name = "tenant_id", nullable = false, length = 64)
	private String tenantId;

	@Column(name = "crm_key", nullable = false, length = 64)
	private String crmKey;

	@Column(name = "lead_ref", nullable = false, length = 128)
	private String leadRef;

	/** Set once the briefing row exists - which happens before the run necessarily finishes,
	 *  since deterministic sections persist first (F.8). */
	@Column(name = "briefing_id")
	private UUID briefingId;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	@Builder.Default
	private RunStatus status = RunStatus.PENDING;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "steps")
	@Builder.Default
	private List<RunStep> steps = new ArrayList<>();

	/** The evidence-item count, known up front - what makes the progress bar a real measurement. */
	@Column(name = "total_items", nullable = false)
	@Builder.Default
	private int totalItems = 0;

	@Column(name = "completed_items", nullable = false)
	@Builder.Default
	private int completedItems = 0;

	@Column(name = "error_message", columnDefinition = "text")
	private String errorMessage;

	@Column(name = "started_at", nullable = false)
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;
}
