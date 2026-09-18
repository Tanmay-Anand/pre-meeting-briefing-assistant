package com.leadlens.schedule;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

import com.leadlens.briefing.Briefing;
import com.leadlens.briefing.BriefingContext;
import com.leadlens.briefing.BriefingService;
import com.leadlens.briefing.BriefingStatus;
import com.leadlens.common.clock.BriefingClock;
import com.leadlens.common.tenant.ActingUser;
import com.leadlens.crm.CrmAdapter;
import com.leadlens.crm.CrmAdapterRegistry;
import com.leadlens.crm.model.LeadRef;
import com.leadlens.crm.model.LeadSnapshot;
import com.leadlens.crm.model.ScheduledActivity;
import com.leadlens.evidence.EvidenceItem;
import com.leadlens.evidence.EvidenceType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The idempotency guarantee {@link UpcomingActivityWorker}'s own Javadoc promises
 * (IMPLEMENTATION_PLAN.md Phase 9 step 5: "two worker ticks must not generate two briefings for
 * the same activity") and the two failure modes a five-minute cron job cannot afford: a
 * lead the worker should not have touched getting a briefing anyway, and one bad CRM response
 * taking the whole scan down with it.
 *
 * <p>{@link BriefingService} is mocked rather than hand-built: it transitively wires a JPA
 * repository and an LLM client, neither of which this test wants to stand up, and the worker only
 * ever calls four of its methods ({@code findLatest}, {@code buildContext}, {@code isStale},
 * {@code generateFull}), which is exactly what a mock lets this test pin down precisely.
 * {@link CrmAdapter}, by contrast, is a narrow interface with no heavy dependencies, so the fake
 * adapters below are hand-rolled real objects (house style, see {@code GroundingPolicyTest}) that
 * implement only what {@link UpcomingActivityWorker#scanForUpcomingActivities()} actually calls -
 * {@code crmKey}, {@code serviceIdentities}, {@code listActiveLeads}, {@code fetchUpcoming} - and
 * throw {@link UnsupportedOperationException} from the rest so an accidental call is a loud
 * failure, not a silent null.
 */
class UpcomingActivityWorkerTest {

	private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
	private static final ActingUser USER = new ActingUser("t-acme", "u-priya", Set.of());

	private final BriefingService briefings = mock(BriefingService.class);
	private final BriefingClock clock = BriefingClock.fixedAt(NOW);

	@Test
	@DisplayName("a lead with an activity due inside the T-30 window and no prior briefing generates exactly once")
	void dueSoonWithNoExistingBriefingGeneratesOnce() {
		LeadRef ref = new LeadRef("demo", "lead-1");
		CrmAdapter adapter = fakeAdapter("demo", List.of(USER), () -> List.of(ref),
				r -> List.of(activityAt(NOW.plus(Duration.ofMinutes(15)))));
		when(briefings.findLatest(ref, USER)).thenReturn(Optional.empty());

		newWorker(adapter).scanForUpcomingActivities();

		verify(briefings, times(1)).generateFull(ref, USER);
		// Nothing complete exists yet, so the freshness check has nothing to compare - the
		// worker must not call buildContext/isStale on the strength of an absent briefing.
		verify(briefings, never()).buildContext(any(), any());
		verify(briefings, never()).isStale(any(), any());
	}

	@Test
	@DisplayName("a lead whose latest briefing is already complete and not stale is left alone")
	void completeAndFreshBriefingIsNotRegenerated() {
		LeadRef ref = new LeadRef("demo", "lead-2");
		CrmAdapter adapter = fakeAdapter("demo", List.of(USER), () -> List.of(ref),
				r -> List.of(activityAt(NOW.plus(Duration.ofMinutes(10)))));

		Briefing existing = completeBriefing(ref);
		BriefingContext context = new BriefingContext(ref, USER, new LeadSnapshot(ref, Map.of()),
				List.of(), List.of(), List.of());
		when(briefings.findLatest(ref, USER)).thenReturn(Optional.of(existing));
		when(briefings.buildContext(ref, USER)).thenReturn(context);
		when(briefings.isStale(eq(existing), eq(context.evidence()))).thenReturn(false);

		newWorker(adapter).scanForUpcomingActivities();

		// This is the actual idempotency property: not "generateFull was never called for this
		// lead" in general, but "a briefing that is already complete and still fresh by
		// fingerprint is not regenerated on this tick".
		verify(briefings, never()).generateFull(any(), any());
	}

	@Test
	@DisplayName("a lead with no activity due within the window is never passed to generateFull")
	void noActivityDueSoonNeverReachesGenerateFull() {
		LeadRef ref = new LeadRef("demo", "lead-3");
		CrmAdapter adapter = fakeAdapter("demo", List.of(USER), () -> List.of(ref),
				r -> List.of(activityAt(NOW.plus(Duration.ofHours(4)))));

		newWorker(adapter).scanForUpcomingActivities();

		// Not due soon means the worker returns before it ever asks BriefingService anything -
		// findLatest, buildContext, isStale and generateFull are all equally off the table.
		verifyNoInteractions(briefings);
	}

	@Test
	@DisplayName("one adapter's listActiveLeads() throwing does not stop a second, working adapter's due lead")
	void listActiveLeadsFailureOnOneAdapterDoesNotStopAnother() {
		CrmAdapter broken = fakeAdapter("broken", List.of(USER),
				() -> {
					throw new RuntimeException("CRM outage");
				},
				r -> {
					throw new AssertionError("fetchUpcoming must not be reached for an adapter whose "
							+ "listActiveLeads() already failed");
				});

		LeadRef workingRef = new LeadRef("working", "lead-4");
		CrmAdapter working = fakeAdapter("working", List.of(USER), () -> List.of(workingRef),
				r -> List.of(activityAt(NOW.plus(Duration.ofMinutes(5)))));
		when(briefings.findLatest(workingRef, USER)).thenReturn(Optional.empty());

		CrmAdapterRegistry registry = new CrmAdapterRegistry(List.of(broken, working));
		UpcomingActivityWorker worker = new UpcomingActivityWorker(registry, briefings, clock);

		assertThatCode(worker::scanForUpcomingActivities).doesNotThrowAnyException();
		verify(briefings, times(1)).generateFull(workingRef, USER);
	}

	@Test
	@DisplayName("fetchUpcoming() throwing for one lead does not stop a second lead in the same scan")
	void fetchUpcomingFailureOnOneLeadDoesNotStopAnother() {
		LeadRef failingRef = new LeadRef("demo", "lead-fails");
		LeadRef workingRef = new LeadRef("demo", "lead-works");
		CrmAdapter adapter = fakeAdapter("demo", List.of(USER), () -> List.of(failingRef, workingRef),
				ref -> {
					if (ref.equals(failingRef)) {
						throw new RuntimeException("CRM timeout fetching upcoming activities");
					}
					return List.of(activityAt(NOW.plus(Duration.ofMinutes(20))));
				});
		when(briefings.findLatest(workingRef, USER)).thenReturn(Optional.empty());

		newWorker(adapter).scanForUpcomingActivities();

		verify(briefings, times(1)).generateFull(workingRef, USER);
		verify(briefings, never()).generateFull(eq(failingRef), any());
	}

	private UpcomingActivityWorker newWorker(CrmAdapter adapter) {
		return new UpcomingActivityWorker(new CrmAdapterRegistry(List.of(adapter)), briefings, clock);
	}

	private static Briefing completeBriefing(LeadRef ref) {
		return Briefing.builder()
				.tenantId(USER.tenantId())
				.crmKey(ref.crmKey())
				.leadRef(ref.leadRef())
				.generatedFor(USER.userId())
				.evidenceFingerprint("fp-1")
				.status(BriefingStatus.COMPLETE)
				.createdAt(NOW.minus(Duration.ofMinutes(5)))
				.build();
	}

	private static ScheduledActivity activityAt(Instant scheduledAt) {
		return new ScheduledActivity("act-1", EvidenceType.MEETING, scheduledAt, "Site visit", List.of(), null);
	}

	private static CrmAdapter fakeAdapter(
			String crmKey,
			List<ActingUser> identities,
			Supplier<List<LeadRef>> listActiveLeads,
			Function<LeadRef, List<ScheduledActivity>> fetchUpcoming) {
		return new FakeCrmAdapter(crmKey, identities, listActiveLeads, fetchUpcoming);
	}

	/**
	 * The narrowest real implementation of {@link CrmAdapter} the worker's own code path needs -
	 * every method the worker does not call throws rather than returning a silently wrong value,
	 * so a change to {@code scanForUpcomingActivities} that starts depending on one of them fails
	 * this test loudly instead of passing on a stub that happened to return null.
	 */
	private static final class FakeCrmAdapter implements CrmAdapter {
		private final String crmKey;
		private final List<ActingUser> identities;
		private final Supplier<List<LeadRef>> listActiveLeads;
		private final Function<LeadRef, List<ScheduledActivity>> fetchUpcoming;

		FakeCrmAdapter(
				String crmKey,
				List<ActingUser> identities,
				Supplier<List<LeadRef>> listActiveLeads,
				Function<LeadRef, List<ScheduledActivity>> fetchUpcoming) {
			this.crmKey = crmKey;
			this.identities = identities;
			this.listActiveLeads = listActiveLeads;
			this.fetchUpcoming = fetchUpcoming;
		}

		@Override
		public String crmKey() {
			return crmKey;
		}

		@Override
		public boolean supports(URI pageUrl) {
			throw new UnsupportedOperationException("not used by UpcomingActivityWorker");
		}

		@Override
		public Optional<LeadRef> resolveLead(URI pageUrl) {
			throw new UnsupportedOperationException("not used by UpcomingActivityWorker");
		}

		@Override
		public LeadSnapshot fetchLead(LeadRef ref, ActingUser user) {
			throw new UnsupportedOperationException("not used by UpcomingActivityWorker");
		}

		@Override
		public List<EvidenceItem> fetchEvidence(LeadRef ref, ActingUser user, Instant since) {
			throw new UnsupportedOperationException("not used by UpcomingActivityWorker");
		}

		@Override
		public List<ScheduledActivity> fetchUpcoming(LeadRef ref, ActingUser user) {
			return fetchUpcoming.apply(ref);
		}

		@Override
		public String deepLinkFor(EvidenceItem item) {
			throw new UnsupportedOperationException("not used by UpcomingActivityWorker");
		}

		@Override
		public List<LeadRef> listActiveLeads(ActingUser user) {
			return listActiveLeads.get();
		}

		@Override
		public List<ActingUser> serviceIdentities() {
			return identities;
		}
	}
}
