package com.leadlens.crm;

import java.util.List;
import java.util.Set;

import com.leadlens.briefing.BriefingContext;
import com.leadlens.briefing.BriefingService;
import com.leadlens.common.tenant.ActingUser;
import com.leadlens.crm.model.LeadRef;
import com.leadlens.crm.model.LeadSnapshot;
import com.leadlens.crm.model.ScheduledActivity;
import com.leadlens.evidence.EvidenceItem;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two endpoints the content script needs before it can even ask for a briefing
 * (IMPLEMENTATION_PLAN.md H.2).
 */
@RestController
@RequestMapping("/api/crm")
public class CrmController {

	private final CrmAdapterRegistry adapters;
	private final BriefingService briefings;

	public CrmController(CrmAdapterRegistry adapters, BriefingService briefings) {
		this.adapters = adapters;
		this.briefings = briefings;
	}

	/**
	 * Fetched once at content-script load, so URL detection is server-controlled (G.3) - a
	 * selector or route change ships as a backend deploy, not an extension reinstall.
	 */
	@GetMapping("/adapters")
	public List<AdapterInfo> adapters() {
		return adapters.all().stream()
				.map(adapter -> new AdapterInfo(adapter.crmKey(), adapter.urlPattern()))
				.filter(info -> info.urlPattern() != null)
				.toList();
	}

	/**
	 * "The system understands the page you are on" - a permission-filtered snapshot plus every
	 * normalised record for this lead, the same inputs a briefing is built from (C4: filtering
	 * already happened inside {@link BriefingService#buildContext}, before this ever assembles
	 * a response).
	 */
	@GetMapping("/{crmKey}/leads/{leadRef}/context")
	public ContextResponse context(
			@RequestHeader("X-LeadLens-Tenant") String tenantId,
			@RequestHeader("X-LeadLens-User") String userId,
			@PathVariable String crmKey,
			@PathVariable String leadRef) {

		ActingUser user = new ActingUser(tenantId, userId, Set.of());
		BriefingContext context = briefings.buildContext(new LeadRef(crmKey, leadRef), user);

		return new ContextResponse(context.lead(), context.evidence(), context.upcoming());
	}

	public record AdapterInfo(String crmKey, String urlPattern) {
	}

	public record ContextResponse(
			LeadSnapshot lead,
			List<EvidenceItem> evidence,
			List<ScheduledActivity> upcoming) {
	}
}
