package com.leadlens.crm;

import java.util.Optional;

import com.leadlens.briefing.model.CrmNarrative;
import com.leadlens.common.tenant.ActingUser;
import com.leadlens.crm.model.LeadRef;

/**
 * Optional second capability a {@link CrmAdapter} may implement: a CRM-wide AI narrative for a
 * lead, produced by whatever query/summarisation engine that CRM already has mounted (e.g. an
 * {@code ai-query-sdk} instance), rather than by LeadLens's own extraction pipeline.
 *
 * <p>Deliberately not part of {@link CrmAdapter} itself - most CRMs have no such engine, and
 * {@code instanceof} on this interface is how {@code BriefingService} finds out whether one
 * exists without every adapter having to implement a no-op. A CRM with no narrative source
 * simply omits the {@link com.leadlens.briefing.model.SectionKey#AI_NARRATIVE} section, exactly
 * as {@link CrmAdapter#listActiveLeads} opts out of pre-warming today.
 *
 * <p>What this returns is never treated as evidence: it is a rendering of records the CRM's own
 * engine already read, not a new fact source for {@code FactExtractor} to mine. Feeding a
 * summary of facts back into extraction would launder inference into grounding.
 */
public interface CrmNarrativeSource {

	/**
	 * A short narrative for this lead, or empty when the source is unconfigured, unreachable, or
	 * refused the request. Empty is a real, expected answer - a briefing must render in full
	 * without this section.
	 *
	 * @param ref     the lead this briefing is for
	 * @param user    who is asking
	 * @param project optional project context, when the CRM's own model has no way to traverse
	 *                from the lead to it on its own
	 */
	Optional<CrmNarrative> narrate(LeadRef ref, ActingUser user, Optional<String> project);
}
