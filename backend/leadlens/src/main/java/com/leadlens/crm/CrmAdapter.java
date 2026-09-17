package com.leadlens.crm;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.leadlens.common.tenant.ActingUser;
import com.leadlens.crm.model.LeadRef;
import com.leadlens.crm.model.LeadSnapshot;
import com.leadlens.crm.model.ScheduledActivity;
import com.leadlens.evidence.EvidenceItem;

/**
 * The entire integration surface for one CRM.
 *
 * <p>This interface is the boundary that makes the claim "the AI engine contains no CRM-specific
 * code" true rather than aspirational (IMPLEMENTATION_PLAN.md G.1). Adding a CRM is one
 * implementation of this interface, one {@code .env.<crmKey>} file (G.7) and one registry entry -
 * the extraction, grounding and briefing logic do not change.
 *
 * <p><strong>The engine must never import a concrete implementation.</strong> Packages under
 * {@code briefing}, {@code facts} and {@code evidence} may depend on this interface and on
 * {@code crm.model}, and on nothing under {@code crm.demo}, {@code crm.leadrat} or
 * {@code crm.dom}.
 *
 * <p>Every data method takes an {@link ActingUser}. There is no overload that omits it, because
 * fetching CRM data without saying on whose behalf is what C4 forbids, and an optional
 * permission argument is one that will eventually be omitted.
 */
public interface CrmAdapter {

	/** Stable key for this CRM: "demo", "leadrat", "generic-dom". */
	String crmKey();

	/** Whether this adapter handles the given page URL. */
	boolean supports(URI pageUrl);

	/**
	 * Resolves the lead from the page URL, or empty when the URL identifies no lead.
	 *
	 * <p>Empty is a real answer. The caller must say "no lead found on this page" rather than
	 * guessing from page content.
	 */
	Optional<LeadRef> resolveLead(URI pageUrl);

	/** The lead's current fields, already permission-filtered for this user. */
	LeadSnapshot fetchLead(LeadRef ref, ActingUser user);

	/**
	 * Every record for this lead, normalised, already permission-filtered.
	 *
	 * @param since optional lower bound on {@code updatedAt}, for incremental fetches; null
	 *              means everything
	 */
	List<EvidenceItem> fetchEvidence(LeadRef ref, ActingUser user, Instant since);

	/** Upcoming activities for this lead. */
	List<ScheduledActivity> fetchUpcoming(LeadRef ref, ActingUser user);

	/**
	 * The CRM URL for a single record, so the agent can open the referenced activity - the
	 * Source References requirement. Adapters whose CRM has no stable single-record route must
	 * fall back to the lead page rather than returning a link that 404s.
	 */
	String deepLinkFor(EvidenceItem item);
}
