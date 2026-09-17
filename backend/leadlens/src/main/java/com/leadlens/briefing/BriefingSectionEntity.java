package com.leadlens.briefing;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.leadlens.briefing.model.BriefingEntry;
import com.leadlens.briefing.model.RenderState;
import com.leadlens.briefing.model.SectionKey;
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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * One section of a stored briefing.
 *
 * <p>Both the selected fact ids and the rendered entries are stored. That looks redundant and
 * is not: the fact ids are what the What Changed diff compares between two versions, while the
 * entries are the document as the agent actually saw it. Recomputing the entries later from
 * live facts would quietly rewrite history, and a briefing the agent walked into a meeting with
 * needs to stay what it was.
 */
@Entity
@Table(
		name = "briefing_sections",
		uniqueConstraints = @UniqueConstraint(
				name = "uk_section_briefing_key",
				columnNames = {"briefing_id", "section_key"}),
		indexes = @Index(name = "ix_section_briefing", columnList = "briefing_id"))
@Getter
@Setter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class BriefingSectionEntity {

	@Id
	@Builder.Default
	private UUID id = UUID.randomUUID();

	@Column(name = "briefing_id", nullable = false)
	private UUID briefingId;

	@Enumerated(EnumType.STRING)
	@Column(name = "section_key", nullable = false, length = 32)
	private SectionKey sectionKey;

	@Enumerated(EnumType.STRING)
	@Column(name = "render_state", nullable = false, length = 16)
	private RenderState renderState;

	/** The facts the composer chose, in its chosen order. Empty for deterministic sections. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "ordered_fact_ids")
	@Builder.Default
	private List<UUID> orderedFactIds = new ArrayList<>();

	/** The document as rendered, frozen at generation time. */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "entries")
	@Builder.Default
	private List<BriefingEntry> entries = new ArrayList<>();
}
