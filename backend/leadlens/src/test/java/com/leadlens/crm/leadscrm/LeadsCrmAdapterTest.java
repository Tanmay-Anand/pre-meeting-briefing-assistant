package com.leadlens.crm.leadscrm;

import java.lang.reflect.Method;
import java.net.URI;
import java.util.UUID;

import com.leadlens.crm.leadscrm.LeadsCrmDtos.LeadResponse;
import com.leadlens.crm.leadscrm.LeadsCrmDtos.NamedRef;
import com.leadlens.crm.model.FieldValue;
import com.leadlens.crm.model.LeadRef;
import com.leadlens.crm.model.LeadSnapshot;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link LeadsCrmAdapter}'s two zero-network contracts: identity resolution and field mapping.
 *
 * <p>Both are here because the class's own Javadoc calls out honest field mapping over guessed
 * data as "this adapter's whole point", and IMPLEMENTATION_PLAN.md's 2026-09-18 Build Log entry
 * records the same thing as a deliberate decision, not a gap: {@code leads-crm-frontend} has no
 * per-lead URL ({@code resolveLead} must always return empty, never guess one from the page), and
 * {@code leads-crm-backend}'s {@code Lead} has no bhk/location/budget fields ({@code fetchLead}
 * must leave those three {@link FieldValue#absent()} rather than reading them off a field that
 * does not mean the same thing, e.g. {@code propertyCategory}). A regression in either would be
 * silent and wrong rather than loud and broken - a mis-identified lead briefs the wrong customer,
 * and a guessed bhk/location/budget defeats F.16's Unsynced/Contradicted detection - so both are
 * pinned directly.
 *
 * <p>{@link #resolveLead} and {@link LeadsCrmAdapter#crmKey()} make no network call, so they are
 * exercised directly against a real adapter built from real (unauthenticated) collaborators - no
 * mocking needed, same pattern as {@code DemoCrmAdapterIdentityTest}. The
 * {@code LeadResponse}-to-{@code LeadSnapshot} mapping lives in a private method
 * ({@code toSnapshot}); reaching it through the public {@code fetchLead()} entry point would mean
 * mocking {@link RestClient.Builder}'s whole fluent chain ({@code get().uri().headers().retrieve()
 * .body()}, each step wildcard-generic) for a test that only cares about mapping logic, not HTTP -
 * calling {@code toSnapshot} directly via reflection on a hand-built {@code LeadResponse} is the
 * less brittle option of the two, and keeps this suite free of a mocking framework entirely, in
 * keeping with this codebase's preference for hand-built real objects over mocks wherever
 * practical.
 */
class LeadsCrmAdapterTest {

	private static final LeadRef REF = new LeadRef("leadscrm", "lead-123");

	private static final LeadsCrmProperties PROPERTIES = new LeadsCrmProperties(
			"http://localhost:8090/leads-crm",
			"http://localhost:5173",
			"ap-south-1",
			"client-id",
			"svc-user",
			"svc-pass",
			"t-leadscrm",
			"leadscrm-service",
			"11111111-1111-1111-1111-111111111111",
			null);

	// Real collaborators, not mocks - none of the methods under test ever call auth.accessToken()
	// or meetings.fetchDiscussions(), so nothing here ever reaches the network. Same pattern as
	// DemoCrmAdapterIdentityTest's plain RestClient.builder().
	private final LeadsCrmAdapter adapter = new LeadsCrmAdapter(
			RestClient.builder(),
			PROPERTIES,
			new CognitoServiceAuthClient(RestClient.builder(), PROPERTIES, new tools.jackson.databind.ObjectMapper()),
			new AiSdkMeetingClient(RestClient.builder(), PROPERTIES));

	// --- resolveLead: always empty, by design -------------------------------------------------

	@Test
	@DisplayName("resolveLead is unconditionally empty - leads-crm-frontend has no per-lead URL to read one from")
	void resolveLeadAlwaysReturnsEmpty() {
		assertThat(adapter.resolveLead(URI.create("http://localhost:5173/leads")))
				.as("the leads list page itself")
				.isEmpty();
		assertThat(adapter.resolveLead(URI.create("http://localhost:5173/leads/12345")))
				.as("a path that looks lead-scoped is still just the list page underneath - "
						+ "the side sheet is client state, not a route")
				.isEmpty();
		assertThat(adapter.resolveLead(URI.create("https://example.com/totally/unrelated?x=1")))
				.as("an unrelated URL")
				.isEmpty();
		assertThat(adapter.resolveLead(URI.create("not-even-a-real-host")))
				.as("garbage input")
				.isEmpty();
		assertThat(adapter.resolveLead(null))
				.as("a null pageUrl must not throw")
				.isEmpty();
	}

	// --- crmKey / supports: zero-network identity plumbing ------------------------------------

	@Test
	@DisplayName("crmKey identifies this adapter as \"leadscrm\"")
	void crmKeyIsLeadscrm() {
		assertThat(adapter.crmKey()).isEqualTo("leadscrm");
		assertThat(adapter.crmKey()).isEqualTo(LeadsCrmAdapter.CRM_KEY);
	}

	@Test
	@DisplayName("supports only claims the configured frontend origin, not every host")
	void supportsOnlyItsOwnFrontendOrigin() {
		assertThat(adapter.supports(URI.create("http://localhost:5173/leads"))).isTrue();
		assertThat(adapter.supports(URI.create("http://localhost:3000/leads"))).isFalse();
		assertThat(adapter.supports(URI.create("https://crm.example.com/leads"))).isFalse();
	}

	@Test
	@DisplayName("supports tolerates a null or host-less URL instead of throwing")
	void supportsToleratesNullAndHostlessUrls() {
		assertThat(adapter.supports(null)).isFalse();
		assertThat(adapter.supports(URI.create("not-even-a-real-host"))).isFalse();
	}

	// --- fetchLead's mapping: bhk/location/budget absent, the rest present --------------------

	@Test
	@DisplayName("bhk, location and budget are always FieldValue.absent() - this CRM's Lead has no matching fields")
	void mapsUnsupportedFieldsAsAbsentNeverGuessed() throws Exception {
		LeadSnapshot snapshot = toSnapshot(fullLead());

		assertThat(snapshot.field("bhk"))
				.as("propertyCategory (RESIDENTIAL/COMMERCIAL/PLOT/AGRICULTURE) is not the same "
						+ "thing as a room count - guessing from it would defeat F.16's "
						+ "Unsynced/Contradicted detection the moment a conversation states one")
				.isEqualTo(FieldValue.absent());
		assertThat(snapshot.field("location")).isEqualTo(FieldValue.absent());
		assertThat(snapshot.field("budget")).isEqualTo(FieldValue.absent());
	}

	@Test
	@DisplayName("name, status, source and timeline are populated from the real lead fields")
	void mapsSupportedFieldsAsPresent() throws Exception {
		LeadSnapshot snapshot = toSnapshot(fullLead());

		assertThat(snapshot.field("name").value()).isEqualTo("Rahul Sharma");
		assertThat(snapshot.field("status").value()).isEqualTo("Qualified");
		assertThat(snapshot.field("source").value()).isEqualTo("Website");
		assertThat(snapshot.field("timeline").value()).isEqualTo("Within 6 months");
	}

	@Test
	@DisplayName("a lead missing name/status/source/timeline maps those to absent too, never a guessed placeholder")
	void missingSourceFieldsMapToAbsentNotPlaceholder() throws Exception {
		LeadResponse lead = new LeadResponse(
				UUID.randomUUID(),
				null, null, // firstName, lastName - both blank
				"9999999999", "+91", null,
				"APARTMENT",
				null, // purchaseTimeline
				null, null, null, null, // status, temperature, sourceCategory, sourceType
				null, null,
				null,
				null);

		LeadSnapshot snapshot = toSnapshot(lead);

		assertThat(snapshot.field("name")).isEqualTo(FieldValue.absent());
		assertThat(snapshot.field("status")).isEqualTo(FieldValue.absent());
		assertThat(snapshot.field("source")).isEqualTo(FieldValue.absent());
		assertThat(snapshot.field("timeline")).isEqualTo(FieldValue.absent());
		// still correctly absent, same as the fully-populated case
		assertThat(snapshot.field("bhk")).isEqualTo(FieldValue.absent());
		assertThat(snapshot.field("location")).isEqualTo(FieldValue.absent());
		assertThat(snapshot.field("budget")).isEqualTo(FieldValue.absent());
	}

	@Test
	@DisplayName("an unrecognised purchase-timeline code still renders readably instead of the raw enum constant")
	void unknownTimelineCodeFallsBackToReadableText() throws Exception {
		LeadResponse lead = new LeadResponse(
				UUID.randomUUID(), "Rahul", "Sharma", "9999999999", "+91", null,
				"APARTMENT", "NEXT_QUARTER",
				null, null, null, null,
				null, null, null, null);

		LeadSnapshot snapshot = toSnapshot(lead);

		assertThat(snapshot.field("timeline").value()).isEqualTo("next quarter");
	}

	// --- helpers -------------------------------------------------------------------------------

	private static LeadResponse fullLead() {
		return new LeadResponse(
				UUID.randomUUID(),
				"Rahul", "Sharma",
				"9999999999", "+91",
				"rahul@example.com",
				"APARTMENT",
				"SIX_MONTHS",
				new NamedRef(UUID.randomUUID(), "QUALIFIED", "Qualified"),
				new NamedRef(UUID.randomUUID(), "WARM", "Warm"),
				new NamedRef(UUID.randomUUID(), "WEBSITE", "Website"),
				new NamedRef(UUID.randomUUID(), "ORGANIC", "Organic"),
				UUID.randomUUID(),
				"Agent Name",
				null,
				null);
	}

	/**
	 * Calls {@code LeadsCrmAdapter.toSnapshot(LeadRef, LeadResponse)} via reflection - it is
	 * private, and this is the pure mapping step {@code fetchLead()} delegates to after its HTTP
	 * call returns. See the class Javadoc for why this is preferred over mocking
	 * {@link RestClient.Builder}'s fluent chain to drive {@code fetchLead()} itself.
	 */
	private LeadSnapshot toSnapshot(LeadResponse lead) throws Exception {
		Method method = LeadsCrmAdapter.class.getDeclaredMethod("toSnapshot", LeadRef.class, LeadResponse.class);
		method.setAccessible(true);
		return (LeadSnapshot) method.invoke(adapter, REF, lead);
	}
}
