package com.leadlens.crm.demo;

import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.leadlens.common.tenant.ActingUser;
import com.leadlens.crm.CrmAdapter;
import com.leadlens.crm.model.FieldValue;
import com.leadlens.crm.model.LeadRef;
import com.leadlens.crm.model.LeadSnapshot;
import com.leadlens.crm.model.ScheduledActivity;
import com.leadlens.democrm.DemoCrmDtos.ActivityListResponse;
import com.leadlens.democrm.DemoCrmDtos.ActivityResponse;
import com.leadlens.democrm.DemoCrmDtos.FieldResponse;
import com.leadlens.democrm.DemoCrmDtos.LeadResponse;
import com.leadlens.evidence.Actor;
import com.leadlens.evidence.Channel;
import com.leadlens.evidence.EvidenceItem;
import com.leadlens.evidence.EvidenceType;
import com.leadlens.evidence.SourceMode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adapter for the Demo CRM - the build's primary integration target (G.3).
 *
 * <p>It talks to the Demo CRM over HTTP even though both happen to run in the same JVM. Reading
 * those tables directly would be faster and would also quietly destroy the thing this layer
 * exists to prove: that the briefing engine works against a CRM it does not own. An adapter
 * that cheats is an adapter that has not been tested (G.4).
 *
 * <p>Permission filtering happens here, at the boundary, before any data reaches an extractor
 * or a composer. The Demo CRM masks fields server-side per role, so a value this user may not
 * see never crosses the wire - LeadLens cannot leak what it never held (C4, F.7).
 */
@Component
public class DemoCrmAdapter implements CrmAdapter {

	public static final String CRM_KEY = "demo";

	/**
	 * Identity comes from the URL, never the DOM (ledger #4). A DOM selector breaks on every
	 * CRM release; a mis-identified lead means briefing the agent about the wrong customer.
	 */
	private static final Pattern LEAD_URL = Pattern.compile("^/leads/(?<leadId>[^/?#]+)");

	private final RestClient.Builder builder;
	private final DemoCrmProperties properties;

	/**
	 * Built on first use rather than in the constructor.
	 *
	 * <p>Constructing an HTTP client is not free - it opens an NIO selector - and doing it
	 * eagerly would mean {@link #resolveLead} and {@link #supports}, which are pure string
	 * logic and the most safety-critical things here, could not be tested without a working
	 * network stack. Identity resolution deserves tests that depend on nothing.
	 */
	private volatile RestClient http;

	public DemoCrmAdapter(RestClient.Builder builder, DemoCrmProperties properties) {
		this.builder = builder;
		this.properties = properties;
	}

	private RestClient http() {
		RestClient local = http;
		if (local == null) {
			synchronized (this) {
				local = http;
				if (local == null) {
					local = builder.baseUrl(properties.baseUrl()).build();
					http = local;
				}
			}
		}
		return local;
	}

	@Override
	public String crmKey() {
		return CRM_KEY;
	}

	@Override
	public boolean supports(URI pageUrl) {
		if (pageUrl == null || pageUrl.getHost() == null) {
			return false;
		}
		// The Demo CRM's dev server. A real adapter would match its vendor's domains.
		return "localhost".equals(pageUrl.getHost()) && pageUrl.getPort() == 5174;
	}

	@Override
	public String urlPattern() {
		// Kept identical to the extension's own BUILT_IN_PATTERNS fallback (url-parser.ts) -
		// this endpoint is meant to become the single source of truth for it, not a second one.
		return "^https?://[^/]+/leads/(?<leadId>[^/?#]+)";
	}

	@Override
	public Optional<LeadRef> resolveLead(URI pageUrl) {
		if (pageUrl == null || pageUrl.getPath() == null) {
			return Optional.empty();
		}
		Matcher matcher = LEAD_URL.matcher(pageUrl.getPath());
		if (!matcher.find()) {
			// Not a failure to try. A CRM page that is not a lead page is the common case, and
			// guessing a lead from page content is exactly what ledger #4 forbids.
			return Optional.empty();
		}
		return Optional.of(new LeadRef(CRM_KEY, matcher.group("leadId")));
	}

	@Override
	public LeadSnapshot fetchLead(LeadRef ref, ActingUser user) {
		LeadResponse response = http().get()
				.uri("/api/democrm/leads/{leadId}", ref.leadRef())
				.headers(headers -> applyIdentity(headers, user))
				.retrieve()
				.body(LeadResponse.class);

		if (response == null) {
			throw new IllegalStateException("Demo CRM returned no body for lead " + ref.leadRef());
		}

		LeadSnapshot.Builder snapshot = LeadSnapshot.builder(ref);
		response.fields().forEach((attributeKey, field) ->
				snapshot.field(attributeKey, toFieldValue(field)));
		return snapshot.build();
	}

	@Override
	public List<EvidenceItem> fetchEvidence(LeadRef ref, ActingUser user, Instant since) {
		ActivityListResponse response = http().get()
				.uri("/api/democrm/leads/{leadId}/activities", ref.leadRef())
				.headers(headers -> applyIdentity(headers, user))
				.retrieve()
				.body(ActivityListResponse.class);

		if (response == null) {
			return List.of();
		}

		List<EvidenceItem> items = new ArrayList<>();
		for (ActivityResponse activity : response.activities()) {
			if (since != null && activity.updatedAt() != null && activity.updatedAt().isBefore(since)) {
				continue;
			}
			items.add(toEvidence(ref, user, activity));
		}
		return items;
	}

	@Override
	public List<ScheduledActivity> fetchUpcoming(LeadRef ref, ActingUser user) {
		ActivityListResponse response = http().get()
				.uri("/api/democrm/leads/{leadId}/scheduled", ref.leadRef())
				.headers(headers -> applyIdentity(headers, user))
				.retrieve()
				.body(ActivityListResponse.class);

		if (response == null) {
			return List.of();
		}

		return response.activities().stream()
				.map(activity -> new ScheduledActivity(
						activity.id(),
						parseType(activity.type()),
						activity.occurredAt(),
						activity.purpose(),
						List.of(),
						deepLink(ref.leadRef(), activity.id())))
				.toList();
	}

	@Override
	public String deepLinkFor(EvidenceItem item) {
		return deepLink(item.getLeadRef(), item.getEvidenceKey());
	}

	@Override
	public List<LeadRef> listActiveLeads(ActingUser user) {
		List<com.leadlens.democrm.DemoCrmDtos.LeadSummaryResponse> response = http().get()
				.uri("/api/democrm/leads")
				.headers(headers -> applyIdentity(headers, user))
				.retrieve()
				.body(new org.springframework.core.ParameterizedTypeReference<
						List<com.leadlens.democrm.DemoCrmDtos.LeadSummaryResponse>>() {
				});

		if (response == null) {
			return List.of();
		}
		return response.stream().map(lead -> new LeadRef(CRM_KEY, lead.id())).toList();
	}

	@Override
	public List<ActingUser> serviceIdentities() {
		// The Demo CRM's own seeded tenants (DemoDataSeeder). Hardcoded here rather than in the
		// generic scheduler: which identities are safe to scan as is Demo-CRM-specific knowledge,
		// not something the worker should need to know about any particular CRM.
		return List.of(
				new ActingUser("t-acme", "u-priya", java.util.Set.of("SALES_AGENT")),
				new ActingUser("t-globex", "u-meera", java.util.Set.of("SALES_AGENT")));
	}

	private EvidenceItem toEvidence(LeadRef ref, ActingUser user, ActivityResponse activity) {
		Instant occurredAt = activity.occurredAt();
		Map<String, Object> structured = activity.structured() == null
				? new LinkedHashMap<>()
				: new LinkedHashMap<>(activity.structured());

		return EvidenceItem.builder()
				.tenantId(user.tenantId())
				.crmKey(CRM_KEY)
				.leadRef(ref.leadRef())
				.evidenceKey(activity.id())
				.type(parseType(activity.type()))
				.occurredAt(occurredAt)
				.actor(parseActor(activity.actor()))
				.channel(parseChannel(activity.channel()))
				// Null text is preserved, not dropped: a call with a recording and no transcript
				// must surface as "not summarised" rather than vanishing (E.5).
				.text(activity.text())
				.structured(structured)
				.deepLink(deepLink(ref.leadRef(), activity.id()))
				.sourceMode(SourceMode.CRM_API)
				.updatedAt(activity.updatedAt() == null ? occurredAt : activity.updatedAt())
				.build();
	}

	private static FieldValue toFieldValue(FieldResponse field) {
		if (field.masked()) {
			return FieldValue.maskedField();
		}
		if (field.value() == null || field.value().isBlank()) {
			return FieldValue.absent();
		}
		return FieldValue.of(field.value(), field.updatedAt());
	}

	private void applyIdentity(org.springframework.http.HttpHeaders headers, ActingUser user) {
		headers.set("X-Demo-Tenant", user.tenantId());
		// The acting user, not a service identity: permission checks must reflect who is
		// actually asking, or the masked case is meaningless.
		headers.set("X-Demo-User", user.userId());
	}

	private String deepLink(String leadId, String activityId) {
		return "http://localhost:5174/leads/%s/activities/%s".formatted(leadId, activityId);
	}

	private static EvidenceType parseType(String raw) {
		try {
			return EvidenceType.valueOf(raw);
		} catch (IllegalArgumentException | NullPointerException e) {
			// An unrecognised record type is still a record. Dropping it would make the lead
			// look quieter than it is; NOTE is the least-claiming bucket available.
			return EvidenceType.NOTE;
		}
	}

	private static Actor parseActor(String raw) {
		try {
			return Actor.valueOf(raw);
		} catch (IllegalArgumentException | NullPointerException e) {
			return Actor.SYSTEM;
		}
	}

	private static Channel parseChannel(String raw) {
		try {
			return Channel.valueOf(raw);
		} catch (IllegalArgumentException | NullPointerException e) {
			return Channel.CRM;
		}
	}

	/** Exposed for diagnostics; the service user is only used where no end user is in play. */
	public String serviceUser() {
		return properties.serviceUser();
	}
}
