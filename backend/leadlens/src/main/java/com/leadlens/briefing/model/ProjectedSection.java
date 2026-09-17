package com.leadlens.briefing.model;

import java.util.List;
import java.util.UUID;

/**
 * One section, produced but not yet persisted.
 *
 * <p>Deliberately separate from the JPA entity so the projector and the composer stay pure
 * functions over their inputs - easy to test, and impossible to accidentally couple to a
 * database session.
 */
public record ProjectedSection(
		SectionKey key,
		RenderState renderState,
		List<BriefingEntry> entries,
		List<UUID> orderedFactIds) {

	public ProjectedSection {
		entries = entries == null ? List.of() : List.copyOf(entries);
		orderedFactIds = orderedFactIds == null ? List.of() : List.copyOf(orderedFactIds);
	}

	public static ProjectedSection rendered(SectionKey key, List<BriefingEntry> entries) {
		return new ProjectedSection(key, RenderState.RENDER, entries, List.of());
	}

	/**
	 * Nothing of this kind is on record - and the section says so, in words.
	 *
	 * <p>Never an empty block: "no objections recorded" and a blank space read identically to a
	 * hurried agent, and only one of them is a claim the system can stand behind (F.1).
	 */
	public static ProjectedSection declareEmpty(SectionKey key, String statement) {
		return new ProjectedSection(
				key,
				RenderState.DECLARE_EMPTY,
				List.of(new BriefingEntry(null, statement, com.leadlens.facts.Provenance.DERIVED,
						EntryFlag.NONE, List.of())),
				List.of());
	}

	/** Could not be produced: no evidence, or the model was unavailable. Not the same as empty. */
	public static ProjectedSection degraded(SectionKey key, String statement) {
		return new ProjectedSection(
				key,
				RenderState.DEGRADED,
				List.of(new BriefingEntry(null, statement, com.leadlens.facts.Provenance.DERIVED,
						EntryFlag.NONE, List.of())),
				List.of());
	}
}
