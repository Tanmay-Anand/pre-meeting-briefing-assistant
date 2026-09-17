package com.leadlens.briefing;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.leadlens.briefing.model.BriefingEntry;
import com.leadlens.briefing.model.EntryFlag;
import com.leadlens.briefing.model.RenderState;
import com.leadlens.briefing.model.SectionKey;
import com.leadlens.common.tenant.ActingUser;
import com.leadlens.crm.model.LeadRef;
import com.leadlens.democrm.DemoDataSeeder;
import com.leadlens.support.EnabledIfWebServerCanStart;
import com.leadlens.support.PostgresTestcontainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.EnabledIfDockerAvailable;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 2's exit criteria, end to end: CRM over HTTP, real Postgres, no model anywhere.
 *
 * <p>Runs the server on a fixed port so {@code DemoCrmAdapter} can call the Demo CRM's API over
 * real HTTP rather than reaching into its tables. That round trip is the point - an adapter
 * that cheats is an adapter that has not been tested (G.4).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.DEFINED_PORT, properties = {
		"server.port=8089",
		"leadlens.crm.demo.base-url=http://localhost:8089",
		"leadlens.crm.demo.service-user=u-priya"
})
@Import(PostgresTestcontainer.class)
@EnabledIfDockerAvailable
@EnabledIfWebServerCanStart
class DeterministicBriefingIT {

	@Autowired
	private BriefingService briefings;

	private static final ActingUser PRIYA =
			new ActingUser(DemoDataSeeder.TENANT_ACME, DemoDataSeeder.USER_PRIYA, Set.of("SALES_AGENT"));
	private static final ActingUser ARJUN =
			new ActingUser(DemoDataSeeder.TENANT_ACME, DemoDataSeeder.USER_ARJUN, Set.of("JUNIOR_AGENT"));

	private static final LeadRef RAHUL = new LeadRef("demo", DemoDataSeeder.LEAD_RAHUL);
	private static final LeadRef EMPTY = new LeadRef("demo", DemoDataSeeder.LEAD_EMPTY);

	@Test
	@DisplayName("a useful briefing is produced with zero model calls")
	void producesDeterministicBriefingForRahul() {
		Briefing briefing = briefings.generateDeterministic(RAHUL, PRIYA);
		Map<SectionKey, List<BriefingEntry>> sections = sectionsOf(briefing);

		assertThat(sections.get(SectionKey.CUSTOMER_SNAPSHOT))
				.extracting(BriefingEntry::text)
				.contains("Rahul Sharma", "Negotiation", "3BHK", "Whitefield", "Rs 1.5-1.8 Cr");

		assertThat(sections.get(SectionKey.MEETING_CONTEXT))
				.extracting(BriefingEntry::text)
				.anyMatch(text -> text.contains("SITE_VISIT"));

		// Every source reference must be openable - that is the whole point of section 10.
		assertThat(sections.get(SectionKey.SOURCE_REFERENCES))
				.isNotEmpty()
				.allSatisfy(entry -> assertThat(entry.sources())
						.singleElement()
						.satisfies(source -> assertThat(source.deepLink()).startsWith("http")));

		assertThat(sections.get(SectionKey.JOURNEY)).isNotEmpty();
	}

	@Test
	@DisplayName("the four kinds of empty are told apart, not collapsed into 'missing'")
	void distinguishesTheKindsOfEmpty() {
		Briefing briefing = briefings.generateDeterministic(RAHUL, PRIYA);
		List<BriefingEntry> missing = sectionsOf(briefing).get(SectionKey.MISSING_INFORMATION);

		// Nobody ever mentioned a decision maker: ask the customer.
		assertThat(missing)
				.filteredOn(entry -> entry.flag() == EntryFlag.NEVER_CAPTURED)
				.extracting(BriefingEntry::label)
				.contains("Decision maker");

		// A call happened and was never written up. Not the same as an uneventful call (E.5).
		assertThat(missing)
				.filteredOn(entry -> entry.flag() == EntryFlag.NOT_SUMMARISED)
				.as("the 16 Sep call has a recording but no transcript")
				.isNotEmpty();
	}

	@Test
	@DisplayName("a masked field is reported as withheld, never as missing")
	void masksRatherThanOmitsForARestrictedRole() {
		Briefing briefing = briefings.generateDeterministic(RAHUL, ARJUN);
		Map<SectionKey, List<BriefingEntry>> sections = sectionsOf(briefing);

		assertThat(sections.get(SectionKey.CUSTOMER_SNAPSHOT))
				.filteredOn(entry -> "Budget".equals(entry.label()))
				.singleElement()
				.satisfies(entry -> {
					assertThat(entry.flag()).isEqualTo(EntryFlag.MASKED);
					assertThat(entry.text())
							.as("the value this user may not see must never reach the document")
							.doesNotContain("1.5", "1.8", "Cr");
				});

		assertThat(sections.get(SectionKey.MISSING_INFORMATION))
				.filteredOn(entry -> entry.flag() == EntryFlag.MASKED)
				.singleElement()
				.satisfies(entry -> assertThat(entry.text())
						.as("telling a junior agent to ask the customer for a budget the company "
								+ "already knows, and merely hid, would be worse than saying nothing")
						.contains("internally"));
	}

	@Test
	@DisplayName("the empty lead degrades honestly instead of inventing a customer")
	void emptyLeadProducesMostlyMissingInformation() {
		// The case that makes naive implementations hallucinate hardest: a composer handed a
		// campaign name will happily write "looking for a 3BHK in Whitefield" with no citations
		// to invalidate, because it never emitted any (F.1).
		Briefing briefing = briefings.generateDeterministic(EMPTY, PRIYA);
		Map<SectionKey, List<BriefingEntry>> sections = sectionsOf(briefing);

		assertThat(sections.get(SectionKey.MISSING_INFORMATION))
				.as("for a lead with nothing on it, Missing Information IS the briefing")
				.hasSizeGreaterThanOrEqualTo(4);

		assertThat(sections.get(SectionKey.CUSTOMER_SNAPSHOT))
				.filteredOn(entry -> "Budget".equals(entry.label()))
				.singleElement()
				.satisfies(entry -> {
					assertThat(entry.flag()).isEqualTo(EntryFlag.NEVER_CAPTURED);
					assertThat(entry.text()).isEqualTo("Not recorded");
				});

		assertThat(sections.get(SectionKey.MEETING_CONTEXT))
				.as("no meeting scheduled is an ordinary state, stated plainly")
				.isNotEmpty();
	}

	@Test
	@DisplayName("a lead with no facts says so rather than showing an empty block")
	void declaresEmptyRatherThanRenderingNothing() {
		Briefing briefing = briefings.generateDeterministic(RAHUL, PRIYA);

		// No extraction has run yet, so Attention has no facts to rank. It must say that.
		assertThat(briefings.sectionsOf(briefing.getId()))
				.filteredOn(section -> section.getSectionKey() == SectionKey.ATTENTION)
				.singleElement()
				.satisfies(section -> {
					assertThat(section.getRenderState())
							.isIn(RenderState.DECLARE_EMPTY, RenderState.RENDER);
					assertThat(section.getEntries())
							.as("a blank block and 'nothing recorded' read identically to a hurried agent")
							.isNotEmpty();
				});
	}

	@Test
	@DisplayName("status is DEGRADED, not COMPLETE, while the model-backed sections are absent")
	void doesNotClaimToBeCompleteYet() {
		Briefing briefing = briefings.generateDeterministic(RAHUL, PRIYA);

		assertThat(briefing.getStatus())
				.as("a document that looks finished while sections were never attempted is the "
						+ "'existence is not generation' mistake (F.9)")
				.isEqualTo(BriefingStatus.DEGRADED);
		assertThat(briefing.getEvidenceFingerprint()).startsWith("sha256:");
	}

	private Map<SectionKey, List<BriefingEntry>> sectionsOf(Briefing briefing) {
		return briefings.sectionsOf(briefing.getId()).stream()
				.collect(java.util.stream.Collectors.toMap(
						BriefingSectionEntity::getSectionKey,
						BriefingSectionEntity::getEntries));
	}
}
