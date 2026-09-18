package com.leadlens.briefing;

import java.util.List;
import java.util.Optional;

import com.leadlens.briefing.model.BriefingEntry;
import com.leadlens.briefing.model.CrmNarrative;
import com.leadlens.briefing.model.EntryFlag;
import com.leadlens.briefing.model.ProjectedSection;
import com.leadlens.briefing.model.RenderState;
import com.leadlens.briefing.model.SectionKey;
import com.leadlens.briefing.model.SourceRef;
import com.leadlens.crm.CrmNarrativeSource;
import com.leadlens.facts.Provenance;
import org.springframework.stereotype.Component;

/**
 * Section 0, the CRM AI narrative - a rendering of whatever a {@link CrmNarrativeSource}
 * returned, never a fact source itself.
 *
 * <p>Absent is the ordinary case for a CRM with no such source, or when the source is
 * unconfigured, unreachable, or refused the request - none of those are failures worth
 * degrading the rest of the briefing over, so this always renders {@link RenderState#DEGRADED}
 * with an explicit statement rather than inventing text, matching every other section's rule
 * that "we could not analyse this" and "there is nothing to say" are different claims (F.9,
 * F.10) - the difference here is that "nothing to say" never applies, because a CRM either has
 * a narrative source or it does not.
 */
@Component
public class NarrativeProjector {

	public ProjectedSection project(Optional<CrmNarrative> narrative) {
		if (narrative.isEmpty()) {
			return ProjectedSection.degraded(SectionKey.AI_NARRATIVE,
					"CRM AI summary unavailable - the sections below are unaffected.");
		}

		CrmNarrative n = narrative.get();
		String label = n.model() == null ? n.sourceLabel() : n.sourceLabel() + " · " + n.model();
		SourceRef source = new SourceRef(null, null, label, n.generatedAt(), null, null);
		BriefingEntry entry = new BriefingEntry(
				"Before you walk in", n.text(), Provenance.INFERRED, EntryFlag.NONE, List.of(source));

		return ProjectedSection.rendered(SectionKey.AI_NARRATIVE, List.of(entry));
	}
}
