package com.leadlens.common.clock;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import org.springframework.stereotype.Component;

/**
 * The server owns "today".
 *
 * <p>"Overdue task", "no contact in 14 days" and "meeting in 30 minutes" are all day- and
 * time-boundary claims. A browser in another timezone, or one left open past midnight, will
 * disagree with the database about what day it is - so the extension renders whatever the
 * server sends and never computes a relative date itself (IMPLEMENTATION_PLAN.md F.11).
 *
 * <p>Injectable so day boundaries and the T-30 trigger are testable, rather than depending on
 * when the test suite happens to run. Resolution is against the <em>tenant's</em> timezone, not
 * the server's and not the browser's.
 */
@Component
public class BriefingClock {

	private final Clock clock;

	public BriefingClock() {
		this(Clock.systemUTC());
	}

	public BriefingClock(Clock clock) {
		this.clock = clock;
	}

	public Instant now() {
		return clock.instant();
	}

	/** The current moment in a tenant's own timezone, for day-boundary arithmetic. */
	public ZonedDateTime nowFor(ZoneId tenantZone) {
		return ZonedDateTime.ofInstant(clock.instant(), tenantZone);
	}

	/** A clock pinned to a fixed instant, for tests. */
	public static BriefingClock fixedAt(Instant instant) {
		return new BriefingClock(Clock.fixed(instant, ZoneId.of("UTC")));
	}
}
