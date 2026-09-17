package com.leadlens.facts;

import java.time.Instant;
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
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The unit of truth: one claim, extracted from exactly one evidence item.
 *
 * <p><strong>Immutable once written.</strong> Facts belong to the lead, not to a briefing, and
 * are reused by every briefing generated for it - that is what makes incremental refresh work
 * (C5): a new activity costs one extraction, not a rebuild.
 *
 * <h2>Two load-bearing properties</h2>
 * <ol>
 *   <li>{@link #evidenceId} is <em>inherited from the input item</em>, never produced by the
 *       model. Extraction sees one evidence item at a time and the backend attaches the id
 *       afterwards, so a citation cannot be wrong in the way a remembered citation can
 *       (C1, F.2).</li>
 *   <li>{@link #claim} is written once, at extraction time, and never rewritten. The composer
 *       later selects facts by id; the renderer emits this string verbatim. "Customer rejected
 *       Prestige Lakeside because of the price" and "Customer rejected Prestige Lakeside" are
 *       different claims an agent would act on differently, and a composer allowed to
 *       paraphrase will eventually produce the wrong one (F.3).</li>
 * </ol>
 */
@Entity
@Table(
		name = "briefing_facts",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_fact_evidence_ordinal",
				columnNames = {"evidence_id", "extractor_version", "ordinal"}),
		indexes = {
				@Index(name = "ix_fact_tenant_lead", columnList = "tenant_id, lead_ref"),
				@Index(name = "ix_fact_tenant_lead_kind", columnList = "tenant_id, lead_ref, kind"),
				@Index(name = "ix_fact_evidence", columnList = "evidence_id")
		})
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class AtomicFact {

	@Id
	@Builder.Default
	private UUID factId = UUID.randomUUID();

	/**
	 * The evidence item this was read from. Inherited, never generated - see the class note.
	 * A fact whose evidenceId does not resolve is dropped rather than rendered (F.1).
	 */
	@Column(name = "evidence_id", nullable = false)
	private UUID evidenceId;

	@Column(name = "tenant_id", nullable = false, length = 64)
	private String tenantId;

	@Column(name = "lead_ref", nullable = false, length = 128)
	private String leadRef;

	@Enumerated(EnumType.STRING)
	@Column(name = "kind", nullable = false, length = 32)
	private FactKind kind;

	/** The exact sentence that will be rendered. Written once; never rewritten. */
	@Column(name = "claim", nullable = false, columnDefinition = "text")
	private String claim;

	/** The quoted substring of the source, shown in the source drawer. */
	@Column(name = "span", columnDefinition = "text")
	private String span;

	@Enumerated(EnumType.STRING)
	@Column(name = "provenance", nullable = false, length = 16)
	private Provenance provenance;

	/** The option this fact is about, when it concerns a specific project or unit. */
	@Column(name = "subject_ref", length = 128)
	private String subjectRef;

	@Enumerated(EnumType.STRING)
	@Column(name = "polarity", nullable = false, length = 16)
	@Builder.Default
	private Polarity polarity = Polarity.NEUTRAL;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 16)
	@Builder.Default
	private FactStatus status = FactStatus.OPEN;

	@Column(name = "confidence", nullable = false)
	@Builder.Default
	private double confidence = 1.0d;

	/** Inherited from the evidence item, so ordering never depends on extraction time. */
	@Column(name = "occurred_at", nullable = false)
	private Instant occurredAt;

	/**
	 * Which lead-record attribute this fact speaks to - "bhk", "budget", "location",
	 * "timeline", "financing" - or null when it has no field counterpart.
	 *
	 * <p>Set only for kinds where {@link FactKind#mapsToLeadField()} is true. Exists solely so
	 * field-versus-fact comparison is a value comparison rather than a string-similarity guess
	 * (F.16).
	 */
	@Column(name = "attribute_key", length = 64)
	private String attributeKey;

	/**
	 * A machine-comparable form of the value: "1BHK", "6500000".
	 *
	 * <p>Null is a valid outcome and never a guessed value. When the extractor cannot
	 * confidently normalise free text ("one bedroom, maybe two"), it must leave this null; the
	 * fact then drops out of the contradiction check, degrading to "not detected" rather than
	 * raising a false contradiction (R21).
	 *
	 * <p>Read only by the deterministic sync-check code. Never shown to the agent - the
	 * renderer still emits {@link #claim} verbatim.
	 */
	@Column(name = "normalized_value", length = 256)
	private String normalizedValue;

	/** Enables selective re-extraction when the extractor changes, without a full rebuild. */
	@Column(name = "extractor_version", nullable = false, length = 32)
	private String extractorVersion;

	/** Position within one evidence item's extraction output; part of the cache's identity. */
	@Column(name = "ordinal", nullable = false)
	@Builder.Default
	private int ordinal = 0;

	/** Whether this fact can take part in the field-versus-fact contradiction check (F.16). */
	public boolean isComparableToLeadField() {
		return attributeKey != null && normalizedValue != null && !normalizedValue.isBlank();
	}
}
