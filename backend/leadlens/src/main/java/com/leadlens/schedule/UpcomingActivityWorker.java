package com.leadlens.schedule;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.leadlens.briefing.Briefing;
import com.leadlens.briefing.BriefingContext;
import com.leadlens.briefing.BriefingService;
import com.leadlens.common.clock.BriefingClock;
import com.leadlens.common.tenant.ActingUser;
import com.leadlens.crm.CrmAdapter;
import com.leadlens.crm.CrmAdapterRegistry;
import com.leadlens.crm.model.LeadRef;
import com.leadlens.crm.model.ScheduledActivity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Generates briefings ahead of a meeting, with no user interaction - MVP checklist item 3, which
 * the extension structurally cannot satisfy on its own: it cannot run while the browser is
 * closed (IMPLEMENTATION_PLAN.md I.5).
 *
 * <p>Scans every adapter's known leads for activities starting within {@link #WINDOW}. This is
 * pre-warming, not a separate generation path: it calls the exact same
 * {@link BriefingService#generateFull} the extension's Prepare Me button does, so there is only
 * one way a briefing gets made, and only one set of trust properties to reason about.
 *
 * <h2>Idempotency</h2>
 * Two ticks must not generate two briefings for the same activity (Phase 9 step 5). Rather than
 * a separate lock, this reuses the mechanism that already exists for the manual refresh path: a
 * briefing whose {@code evidenceFingerprint} still matches the lead's current evidence is left
 * alone. The first tick that finds a lead due soon generates one; every tick after that, until
 * new evidence actually arrives, sees a fresh fingerprint and does nothing.
 */
@Component
public class UpcomingActivityWorker {

	private static final Logger log = LoggerFactory.getLogger(UpcomingActivityWorker.class);

	/** How far ahead to look. Matches the plan's T-30-minutes trigger. */
	private static final Duration WINDOW = Duration.ofMinutes(30);

	private final CrmAdapterRegistry adapters;
	private final BriefingService briefings;
	private final BriefingClock clock;

	public UpcomingActivityWorker(CrmAdapterRegistry adapters, BriefingService briefings, BriefingClock clock) {
		this.adapters = adapters;
		this.briefings = briefings;
		this.clock = clock;
	}

	@Scheduled(fixedDelayString = "PT5M", initialDelayString = "PT1M")
	public void scanForUpcomingActivities() {
		Instant now = clock.now();
		Instant horizon = now.plus(WINDOW);

		for (CrmAdapter adapter : adapters.all()) {
			for (ActingUser identity : adapter.serviceIdentities()) {
				for (LeadRef ref : safely(() -> adapter.listActiveLeads(identity), adapter, "listActiveLeads")) {
					prepareIfDueSoon(adapter, ref, identity, now, horizon);
				}
			}
		}
	}

	private void prepareIfDueSoon(CrmAdapter adapter, LeadRef ref, ActingUser identity, Instant now, Instant horizon) {
		List<ScheduledActivity> upcoming = safely(() -> adapter.fetchUpcoming(ref, identity), adapter, "fetchUpcoming");
		boolean dueSoon = upcoming.stream()
				.anyMatch(activity -> !activity.scheduledAt().isBefore(now) && !activity.scheduledAt().isAfter(horizon));
		if (!dueSoon) {
			return;
		}

		Optional<Briefing> latest = briefings.findLatest(ref, identity);
		if (latest.isPresent() && latest.get().isComplete()) {
			BriefingContext context = briefings.buildContext(ref, identity);
			if (!briefings.isStale(latest.get(), context.evidence())) {
				// Already prepared and still fresh - the point of idempotency, not a missed case.
				return;
			}
		}

		try {
			briefings.generateFull(ref, identity);
			log.info("Pre-warmed briefing for {}/{} ahead of a scheduled activity", ref.crmKey(), ref.leadRef());
		} catch (RuntimeException e) {
			// One lead failing must not stop the scan from reaching the rest (F.5's spirit,
			// applied to a batch job instead of a single extraction).
			log.warn("Pre-warm failed for {}/{}: {}", ref.crmKey(), ref.leadRef(), e.toString());
		}
	}

	private <T> List<T> safely(java.util.function.Supplier<List<T>> call, CrmAdapter adapter, String what) {
		try {
			return call.get();
		} catch (RuntimeException e) {
			log.warn("{} failed for adapter {}: {}", what, adapter.crmKey(), e.toString());
			return List.of();
		}
	}
}
