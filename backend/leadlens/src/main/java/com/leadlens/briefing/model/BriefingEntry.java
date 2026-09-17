package com.leadlens.briefing.model;

import java.util.List;

import com.leadlens.facts.Provenance;

/**
 * One rendered line of a briefing.
 *
 * <p>The same shape serves every section, deterministic or not, which is what makes provenance
 * impossible to lose in translation: a field projection and a model-selected fact both arrive
 * here carrying {@link #provenance}, and the renderer styles them differently on that basis
 * alone (E.4). An objection a model read into a WhatsApp message can never accidentally render
 * like one an agent logged.
 *
 * @param label       optional field name, for table-style sections
 * @param text        the text to render, emitted verbatim - never re-worded downstream (F.3)
 * @param provenance  how this was derived; decides the CRM FACT / COMPUTED / AI READING badge
 * @param flag        why it is notable, if it is
 * @param sources     citations; empty only for entries that are their own evidence
 */
public record BriefingEntry(
		String label,
		String text,
		Provenance provenance,
		EntryFlag flag,
		List<SourceRef> sources) {

	public BriefingEntry {
		sources = sources == null ? List.of() : List.copyOf(sources);
		flag = flag == null ? EntryFlag.NONE : flag;
	}

	public static BriefingEntry field(String label, String text, Provenance provenance) {
		return new BriefingEntry(label, text, provenance, EntryFlag.NONE, List.of());
	}

	public static BriefingEntry flagged(String label, String text, Provenance provenance, EntryFlag flag) {
		return new BriefingEntry(label, text, provenance, flag, List.of());
	}

	public boolean isCited() {
		return !sources.isEmpty();
	}
}
