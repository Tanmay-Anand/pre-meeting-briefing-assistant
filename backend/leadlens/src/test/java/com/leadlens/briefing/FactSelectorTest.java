package com.leadlens.briefing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.leadlens.facts.AtomicFact;
import com.leadlens.facts.FactKind;
import com.leadlens.facts.FactStatus;
import com.leadlens.facts.Polarity;
import com.leadlens.facts.Provenance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Inclusion floors (IMPLEMENTATION_PLAN.md E.3, D.4) - the mechanism that keeps selection from
 * quietly narrowing what a briefing can say, independent of anything a composer might do later.
 */
class FactSelectorTest {

	private static final Instant JUN = Instant.parse("2026-06-01T10:00:00Z");
	private static final Instant JUL = Instant.parse("2026-07-01T10:00:00Z");
	private static final Instant AUG = Instant.parse("2026-08-01T10:00:00Z");
	private static final Instant SEP = Instant.parse("2026-09-14T11:40:00Z");

	@Test
	@DisplayName("ALL_OPEN: a four-month-old open objection survives alongside a recent one")
	void allOpenSurvivesRegardlessOfAge() {
		AtomicFact old = fact(FactKind.OBJECTION, "Old pricing objection, still open", JUN, FactStatus.OPEN);
		AtomicFact resolved = fact(FactKind.OBJECTION, "A resolved objection", JUL, FactStatus.RESOLVED);
		AtomicFact recent = fact(FactKind.OBJECTION, "Recent objection", SEP, FactStatus.OPEN);

		List<AtomicFact> selected = FactSelector.select(FactKind.OBJECTION, List.of(old, resolved, recent));

		assertThat(selected)
				.as("this is the exact D.4 failure mode: dropping the old one would render "
						+ "\"no objections recorded\" with perfect structural integrity")
				.containsExactly(recent, old);
		assertThat(selected).doesNotContain(resolved);
	}

	@Test
	@DisplayName("LATEST_PER_ATTRIBUTE: only the newest requirement is selected")
	void latestPerAttributeKeepsOnlyTheNewest() {
		AtomicFact first = fact(FactKind.REQUIREMENT, "2BHK", JUN, FactStatus.SUPERSEDED, "bhk");
		AtomicFact second = fact(FactKind.REQUIREMENT, "3BHK", JUL, FactStatus.OPEN, "bhk");

		List<AtomicFact> selected = FactSelector.select(FactKind.REQUIREMENT, List.of(first, second));

		assertThat(selected).containsExactly(second);
	}

	@Test
	@DisplayName("LATEST_PLUS_PRIOR_IF_CHANGED: a genuine change keeps both values")
	void latestPlusPriorWhenValueChanged() {
		AtomicFact prior = factWithValue(FactKind.BUDGET_STATEMENT, "40L budget", JUN, "4000000");
		AtomicFact latest = factWithValue(FactKind.BUDGET_STATEMENT, "65L budget", SEP, "6500000");

		List<AtomicFact> selected = FactSelector.select(FactKind.BUDGET_STATEMENT, List.of(prior, latest));

		assertThat(selected)
				.as("the change itself is more useful to the agent than either number alone (F.14)")
				.containsExactly(latest, prior);
	}

	@Test
	@DisplayName("LATEST_PLUS_PRIOR_IF_CHANGED: restating the same value keeps only the latest")
	void latestOnlyWhenValueUnchanged() {
		AtomicFact prior = factWithValue(FactKind.BUDGET_STATEMENT, "Budget confirmed at 65L", JUN, "6500000");
		AtomicFact latest = factWithValue(FactKind.BUDGET_STATEMENT, "Still targeting 65L", SEP, "6500000");

		List<AtomicFact> selected = FactSelector.select(FactKind.BUDGET_STATEMENT, List.of(prior, latest));

		assertThat(selected).containsExactly(latest);
	}

	@Test
	@DisplayName("ALL: one entry per subject, the latest for each")
	void allKeepsLatestPerSubject() {
		AtomicFact projectAOld = withSubject(FactKind.PROPERTY_RESPONSE, "Shared Prestige Lakeside", JUN, "proj-a");
		AtomicFact projectARejected = withSubject(FactKind.PROPERTY_RESPONSE, "Rejected Prestige Lakeside on price", AUG, "proj-a");
		AtomicFact projectB = withSubject(FactKind.PROPERTY_RESPONSE, "Shared Brigade Cornerstone, awaiting feedback", JUL, "proj-b");

		List<AtomicFact> selected = FactSelector.select(FactKind.PROPERTY_RESPONSE,
				List.of(projectAOld, projectARejected, projectB));

		assertThat(selected).containsExactlyInAnyOrder(projectARejected, projectB);
	}

	@Test
	@DisplayName("no facts of a kind selects nothing, not an error")
	void emptyInputSelectsNothing() {
		assertThat(FactSelector.select(FactKind.OBJECTION, List.of())).isEmpty();
	}

	private static AtomicFact fact(FactKind kind, String claim, Instant at, FactStatus status) {
		return fact(kind, claim, at, status, null);
	}

	private static AtomicFact fact(FactKind kind, String claim, Instant at, FactStatus status, String attributeKey) {
		return AtomicFact.builder()
				.evidenceId(UUID.randomUUID())
				.tenantId("t-acme")
				.leadRef("12345")
				.kind(kind)
				.claim(claim)
				.provenance(Provenance.INFERRED)
				.status(status)
				.polarity(Polarity.NEUTRAL)
				.occurredAt(at)
				.attributeKey(attributeKey)
				.extractorVersion("v1")
				.build();
	}

	private static AtomicFact factWithValue(FactKind kind, String claim, Instant at, String normalizedValue) {
		return AtomicFact.builder()
				.evidenceId(UUID.randomUUID())
				.tenantId("t-acme")
				.leadRef("12345")
				.kind(kind)
				.claim(claim)
				.provenance(Provenance.INFERRED)
				.status(FactStatus.OPEN)
				.occurredAt(at)
				.attributeKey(kind.leadFieldAttribute())
				.normalizedValue(normalizedValue)
				.extractorVersion("v1")
				.build();
	}

	private static AtomicFact withSubject(FactKind kind, String claim, Instant at, String subjectRef) {
		return AtomicFact.builder()
				.evidenceId(UUID.randomUUID())
				.tenantId("t-acme")
				.leadRef("12345")
				.kind(kind)
				.claim(claim)
				.provenance(Provenance.CRM_ACTIVITY)
				.status(FactStatus.OPEN)
				.subjectRef(subjectRef)
				.occurredAt(at)
				.extractorVersion("v1")
				.build();
	}
}
