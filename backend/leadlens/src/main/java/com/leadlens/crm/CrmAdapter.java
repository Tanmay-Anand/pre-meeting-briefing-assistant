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
	 * A JS/Java-compatible regex with a named {@code leadId} group, served to the extension via
	 * {@code GET /api/crm/adapters} so a CRM's URL shape can change without reinstalling it
	 * (G.3's selector-configuration idea, generalised to every adapter rather than only DOM
	 * ones). Null means this adapter's shape cannot be expressed as one pattern - the content
	 * script then has nothing to match against for this CRM and simply does not offer it.
	 */
	default String urlPattern() {
		return null;
	}

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

	/**
	 * Every lead this user can see, for {@code UpcomingActivityWorker} (Phase 9) to scan for
	 * activities starting soon.
	 *
	 * <p>A default of empty rather than an abstract method: enumerating "all leads" is not part
	 * of the core briefing contract (G.1's interface is about one lead at a time), and requiring
	 * every adapter to implement it would force a CRM with no bulk-listing API into one. An
	 * adapter that cannot support scheduled pre-warming simply opts out; the worker skips it.
	 */
	default List<LeadRef> listActiveLeads(ActingUser user) {
		return List.of();
	}

	/**
	 * Which users {@code UpcomingActivityWorker} should act as when scanning this CRM.
	 *
	 * <p>A background job has no human session to derive identity from, and every adapter method
	 * requires an {@link ActingUser} by design (C4) - so something has to say who the worker acts
	 * as. Real deployments would resolve this from a service-account registry per tenant; that
	 * registry does not exist yet (Appendix 2), so the default is empty and an adapter opts in
	 * only once it knows what identity is safe to use.
	 */
	default List<ActingUser> serviceIdentities() {
		return List.of();
	}
}
