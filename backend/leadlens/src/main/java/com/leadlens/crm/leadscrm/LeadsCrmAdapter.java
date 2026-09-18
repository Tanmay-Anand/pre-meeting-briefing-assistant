package com.leadlens.crm.leadscrm;

import java.net.URI;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.leadlens.common.tenant.ActingUser;
import com.leadlens.crm.CrmAdapter;
import com.leadlens.crm.leadscrm.LeadsCrmDtos.DiscussionResponse;
import com.leadlens.crm.leadscrm.LeadsCrmDtos.LeadResponse;
import com.leadlens.crm.leadscrm.LeadsCrmDtos.LeadsPage;
import com.leadlens.crm.leadscrm.LeadsCrmDtos.NoteResponse;
import com.leadlens.crm.leadscrm.LeadsCrmDtos.NotesPage;
import com.leadlens.crm.model.FieldValue;
import com.leadlens.crm.model.LeadRef;
import com.leadlens.crm.model.LeadSnapshot;
import com.leadlens.crm.model.ScheduledActivity;
import com.leadlens.evidence.Actor;
import com.leadlens.evidence.Channel;
import com.leadlens.evidence.EvidenceItem;
import com.leadlens.evidence.EvidenceType;
import com.leadlens.evidence.SourceMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Adapter for {@code leads-crm-backend} - the team's second CRM target, replacing the deleted
 * {@code demo-crm} (IMPLEMENTATION_PLAN.md G.3's Demo CRM no longer exists in this repo; this
 * adapter is what fills that role now).
 *
 * <h2>Two things this CRM's data model does not have, and what that means</h2>
 * <ol>
 *   <li><strong>No per-lead URL.</strong> {@code leads-crm-frontend} opens a lead in a React
 *       state-driven side sheet, not a route change - the browser URL stays at {@code /leads}
 *       regardless of which lead is open. {@link #resolveLead} therefore always returns empty;
 *       ledger #4's own fallback (identify from a DOM signal instead, never guess) is the only
 *       path here, via the extension's existing {@code data-lead-id} click detection.</li>
 *   <li><strong>No budget, BHK/room-count, or preferred-location fields on {@code Lead}.</strong>
 *       This CRM's lead record is a generic sales lead (name, mobile, property category,
 *       purchase timeline, assignment), not real-estate-buyer-preference-shaped the way the
 *       plan's Rahul Sharma fixture was. Those three {@link LeadSnapshot} fields are left
 *       genuinely absent rather than guessed at from a field that does not mean the same thing
 *       (R21's discipline, applied to field mapping instead of text normalisation) - and
 *       leaving them absent is what lets {@code Unsynced}/{@code Contradicted} detection (F.16)
 *       fire correctly if a conversation later states one of them.</li>
 * </ol>
 *
 * <h2>Meeting evidence</h2>
 * {@link #fetchEvidence} also pulls this lead's meeting transcripts from the {@code ai-query-sdk}
 * instance mounted inside the same backend ({@link AiSdkMeetingClient}), so Google
 * Calendar/Recall.ai capture becomes ordinary {@code MEETING}-type evidence the existing
 * extraction pipeline already knows how to read - no new extraction logic needed.
 */
@Component
public class LeadsCrmAdapter implements CrmAdapter {

	private static final Logger log = LoggerFactory.getLogger(LeadsCrmAdapter.class);

	public static final String CRM_KEY = "leadscrm";

	private static final int LIST_PAGE_SIZE = 100;

	private final RestClient.Builder builder;
	private final LeadsCrmProperties properties;
	private final CognitoServiceAuthClient auth;
	private final AiSdkMeetingClient meetings;
	private volatile RestClient http;

	public LeadsCrmAdapter(
			RestClient.Builder builder,
			LeadsCrmProperties properties,
			CognitoServiceAuthClient auth,
			AiSdkMeetingClient meetings) {
		this.builder = builder;
		this.properties = properties;
		this.auth = auth;
		this.meetings = meetings;
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
		URI frontend = URI.create(properties.frontendOrigin());
		return frontend.getHost().equals(pageUrl.getHost()) && frontend.getPort() == pageUrl.getPort();
	}

	/**
	 * Always empty - see the class-level note. This is a correct, deliberate answer, not a gap:
	 * guessing a lead id from a URL that never carries one is exactly what ledger #4 forbids.
	 * Identity comes from the extension's {@code data-lead-id} click detection instead.
	 */
	@Override
	public Optional<LeadRef> resolveLead(URI pageUrl) {
		return Optional.empty();
	}

	@Override
	public LeadSnapshot fetchLead(LeadRef ref, ActingUser user) {
		LeadResponse lead = http().get()
				.uri("/leads/{id}", ref.leadRef())
				.headers(this::applyIdentity)
				.retrieve()
				.body(LeadResponse.class);

		if (lead == null) {
			throw new IllegalStateException("leads-crm-backend returned no body for lead " + ref.leadRef());
		}

		return toSnapshot(ref, lead);
	}

	@Override
	public List<EvidenceItem> fetchEvidence(LeadRef ref, ActingUser user, Instant since) {
		List<EvidenceItem> items = new ArrayList<>();
		items.addAll(fetchNotesAsEvidence(ref, user, since));
		items.addAll(fetchMeetingDiscussionsAsEvidence(ref, user, since));
		return items;
	}

	private List<EvidenceItem> fetchNotesAsEvidence(LeadRef ref, ActingUser user, Instant since) {
		NotesPage response = http().get()
				.uri(uriBuilder -> uriBuilder.path("/leads/{leadId}/notes")
						.queryParam("size", 200)
						.build(ref.leadRef()))
				.headers(this::applyIdentity)
				.retrieve()
				.body(NotesPage.class);

		if (response == null) {
			return List.of();
		}

		List<EvidenceItem> items = new ArrayList<>();
		for (NoteResponse note : response.content()) {
			Instant occurredAt = toInstant(note.createdOn());
			if (since != null && occurredAt != null && occurredAt.isBefore(since)) {
				continue;
			}
			items.add(toEvidence(ref, user, note, occurredAt));
		}
		return items;
	}

	/**
	 * The meeting-transcripts-as-evidence integration: transcripts captured by the ai-query-sdk
	 * instance mounted inside this same backend become MEETING-type evidence, exactly like a
	 * call note would. Never throws - {@link AiSdkMeetingClient} already isolates failures.
	 */
	private List<EvidenceItem> fetchMeetingDiscussionsAsEvidence(LeadRef ref, ActingUser user, Instant since) {
		List<DiscussionResponse> discussions = meetings.fetchDiscussions(ref.leadRef());

		List<EvidenceItem> items = new ArrayList<>();
		for (DiscussionResponse discussion : discussions) {
			if (since != null && discussion.receivedAt() != null && discussion.receivedAt().isBefore(since)) {
				continue;
			}
			items.add(toEvidence(ref, user, discussion));
		}
		return items;
	}

	@Override
	public List<ScheduledActivity> fetchUpcoming(LeadRef ref, ActingUser user) {
		LeadResponse lead = http().get()
				.uri("/leads/{id}", ref.leadRef())
				.headers(this::applyIdentity)
				.retrieve()
				.body(LeadResponse.class);

		if (lead == null || lead.scheduleDate() == null) {
			return List.of();
		}

		Instant scheduledAt = lead.scheduleDate().atZone(ZoneOffset.UTC).toInstant();
		return List.of(new ScheduledActivity(
				ref.leadRef() + "-schedule",
				EvidenceType.MEETING,
				scheduledAt,
				// Not captured on this lead record - surfaces correctly as "no stated
				// objective" in Missing Information (MissingInfoRules) rather than inventing one.
				null,
				List.of(),
				leadsPageUrl()));
	}

	@Override
	public String deepLinkFor(EvidenceItem item) {
		return item.getDeepLink() != null ? item.getDeepLink() : leadsPageUrl();
	}

	@Override
	public List<LeadRef> listActiveLeads(ActingUser user) {
		LeadsPage response = http().get()
				.uri(uriBuilder -> uriBuilder.path("/leads")
						.queryParam("size", LIST_PAGE_SIZE)
						.build())
				.headers(this::applyIdentity)
				.retrieve()
				.body(LeadsPage.class);

		if (response == null) {
			return List.of();
		}
		return response.content().stream()
				.map(lead -> new LeadRef(CRM_KEY, lead.id().toString()))
				.toList();
	}

	@Override
	public List<ActingUser> serviceIdentities() {
		return List.of(new ActingUser(properties.tenantId(), properties.serviceUserId(), Set.of()));
	}

	// --- mapping -------------------------------------------------------------------------

	private LeadSnapshot toSnapshot(LeadRef ref, LeadResponse lead) {
		LeadSnapshot.Builder snapshot = LeadSnapshot.builder(ref);

		snapshot.field("name", present(lead.displayName()));
		snapshot.field("status", present(lead.status() == null ? null : lead.status().label()));
		snapshot.field("source", present(lead.sourceCategory() == null ? null : lead.sourceCategory().label()));
		snapshot.field("timeline", present(readableTimeline(lead.purchaseTimeline())));

		// Deliberately absent - see the class doc's second note. Never guessed from
		// propertyCategory/address, which are not the same thing as these three.
		snapshot.field("bhk", FieldValue.absent());
		snapshot.field("location", FieldValue.absent());
		snapshot.field("budget", FieldValue.absent());

		return snapshot.build();
	}

	private static FieldValue present(String value) {
		return (value == null || value.isBlank()) ? FieldValue.absent() : FieldValue.of(value, null);
	}

	private static String readableTimeline(String purchaseTimeline) {
		if (purchaseTimeline == null) {
			return null;
		}
		return switch (purchaseTimeline) {
			case "IMMEDIATE" -> "Immediate";
			case "ONE_MONTH" -> "Within 1 month";
			case "THREE_MONTHS" -> "Within 3 months";
			case "SIX_MONTHS" -> "Within 6 months";
			case "ONE_YEAR" -> "Within 1 year";
			case "EXPLORING" -> "Just exploring";
			default -> purchaseTimeline.toLowerCase(Locale.ROOT).replace('_', ' ');
		};
	}

	private EvidenceItem toEvidence(LeadRef ref, ActingUser user, NoteResponse note, Instant occurredAt) {
		return EvidenceItem.builder()
				.tenantId(user.tenantId())
				.crmKey(CRM_KEY)
				.leadRef(ref.leadRef())
				.evidenceKey("note:" + note.id())
				.type(noteEvidenceType(note.type()))
				.occurredAt(occurredAt)
				.actor(Actor.AGENT)
				.channel(noteChannel(note.type()))
				.text(note.body())
				.structured(Map.of())
				.deepLink(leadsPageUrl())
				.sourceMode(SourceMode.CRM_API)
				.updatedAt(occurredAt)
				.build();
	}

	private EvidenceItem toEvidence(LeadRef ref, ActingUser user, DiscussionResponse discussion) {
		Map<String, Object> structured = new LinkedHashMap<>();
		structured.put("meetingId", discussion.meetingId());
		structured.put("meetingTitle", discussion.meetingTitle());
		if (discussion.participants() != null) {
			structured.put("participants", discussion.participants());
		}

		Instant occurredAt = discussion.occurredAt() != null ? discussion.occurredAt() : discussion.receivedAt();
		Instant updatedAt = discussion.receivedAt() != null ? discussion.receivedAt() : occurredAt;

		return EvidenceItem.builder()
				.tenantId(user.tenantId())
				.crmKey(CRM_KEY)
				.leadRef(ref.leadRef())
				.evidenceKey("meeting-discussion:" + discussion.id())
				.type(EvidenceType.MEETING)
				// A transcript is multi-party; no single AGENT/CUSTOMER attribution is honest at
				// the evidence level. FactExtractor reads the full text and attributes
				// individual claims correctly regardless (matches how CALL evidence already works).
				.actor(Actor.SYSTEM)
				.channel(Channel.VIDEO_CALL)
				.occurredAt(occurredAt)
				.text(discussion.discussion())
				.structured(structured)
				.deepLink(discussion.meetingUrl())
				.sourceMode(SourceMode.CRM_API)
				.updatedAt(updatedAt)
				.build();
	}

	private static EvidenceType noteEvidenceType(String type) {
		if (type == null) {
			return EvidenceType.NOTE;
		}
		return switch (type) {
			case "CALL" -> EvidenceType.CALL;
			case "WHATSAPP", "EMAIL" -> EvidenceType.MESSAGE;
			case "SITE_VISIT" -> EvidenceType.SITE_VISIT;
			case "DOCUMENT_LINK" -> EvidenceType.DOCUMENT;
			default -> EvidenceType.NOTE;
		};
	}

	private static Channel noteChannel(String type) {
		if (type == null) {
			return Channel.CRM;
		}
		return switch (type) {
			case "CALL" -> Channel.PHONE;
			case "WHATSAPP" -> Channel.WHATSAPP;
			case "EMAIL" -> Channel.EMAIL;
			case "SITE_VISIT" -> Channel.IN_PERSON;
			default -> Channel.CRM;
		};
	}

	private static Instant toInstant(java.time.LocalDateTime localDateTime) {
		return localDateTime == null ? null : localDateTime.atZone(ZoneOffset.UTC).toInstant();
	}

	private String leadsPageUrl() {
		return properties.frontendOrigin() + "/leads";
	}

	private void applyIdentity(HttpHeaders headers) {
		headers.setBearerAuth(auth.accessToken());
		// [CORRECTED 2026-09-18, twice] First pass sent properties.tenantId() here - LeadLens's
		// own internal identity ("leadscrm-default"), not even a UUID, which TenantFilter hard-
		// rejects with 400. Second pass removed the header entirely, on the assumption the bearer
		// token's own custom:tenantId claim would cover it - true for the ID token, false for the
		// access token, which is what CognitoServiceAuthClient actually issues as the bearer and
		// which Cognito never populates with custom attributes by default. So the header is
		// genuinely required; it just needs the CRM's real tenant UUID, not LeadLens's.
		if (properties.crmTenantId() != null && !properties.crmTenantId().isBlank()) {
			headers.set("x-tenant-id", properties.crmTenantId());
		}
	}
}
