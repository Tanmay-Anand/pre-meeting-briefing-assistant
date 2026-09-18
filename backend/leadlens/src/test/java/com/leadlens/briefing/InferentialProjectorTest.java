package com.leadlens.briefing;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.leadlens.briefing.model.BriefingEntry;
import com.leadlens.briefing.model.ProjectedSection;
import com.leadlens.briefing.model.RenderState;
import com.leadlens.briefing.model.SectionKey;
import com.leadlens.common.tenant.ActingUser;
import com.leadlens.crm.model.LeadRef;
import com.leadlens.crm.model.LeadSnapshot;
import com.leadlens.evidence.Actor;
import com.leadlens.evidence.Channel;
import com.leadlens.evidence.EvidenceItem;
import com.leadlens.evidence.EvidenceType;
import com.leadlens.evidence.SourceMode;
import com.leadlens.facts.AtomicFact;
import com.leadlens.facts.FactKind;
import com.leadlens.facts.FactStatus;
import com.leadlens.facts.Provenance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sections 3-7 (IMPLEMENTATION_PLAN.md D.4, F.2): {@link InferentialProjector} renders directly
 * from {@link FactSelector}'s already-exhaustive selection, with {@link GroundingPolicy} deciding
 * whether there is anything to render at all. Its own class Javadoc explains why there is no
 * composer call in between - selection's inclusion floors leave nothing left for a model to
 * decide, and that is exactly what the tests below have to keep true.
 *
 * <p>Every case here maps to a real failure mode:
 * <ul>
 *   <li>Objections dropping an old-but-open one is the exact D.4 scenario: a four-month-old
 *       financing objection that is still unresolved must not quietly age out just because it is
 *       old - doing so renders "no objections recorded" with perfect structural integrity while
 *       being false.</li>
 *   <li>The REQUIREMENTS label mapping is the one place {@link InferentialProjector} does
 *       genuinely translate a fact into agent-facing words (the {@code ATTRIBUTE_LABELS} map) -
 *       worth pinning down explicitly rather than trusting a switch expression by inspection.</li>
 *   <li>DECLARE_EMPTY versus DEGRADED reuses {@link GroundingPolicy}'s rungs; the point of testing
 *       them here too is that this is the call site actually wiring {@code context.hasNoEvidence()}
 *       and {@code extractionComplete} through, and a wiring mistake there would not be caught by
 *       {@code GroundingPolicyTest} alone.</li>
 *   <li>The reflection check is the test-level version of the class Javadoc's central claim: this
 *       class does not call an LLM. Pure static classes for these sections is what makes D.2's "a
 *       briefing can always ship something true" hold even when the model is unavailable.</li>
 * </ul>
 *
 * <p>No mocking, per house style: {@link InferentialProjector#project} is a pure static function
 * over hand-built {@link BriefingContext} and {@link AtomicFact} objects, the same pattern
 * {@code GroundingPolicyTest} and {@code FactSelectorTest} use in this package.
 */
class InferentialProjectorTest {

	private static final Instant JUN = Instant.parse("2026-06-01T10:00:00Z");
	private static final Instant SEP = Instant.parse("2026-09-14T11:40:00Z");

	private static final LeadRef REF = new LeadRef("demo", "12345");
	private static final ActingUser USER = new ActingUser("t-acme", "u-1", Set.of("AGENT"));

	// --- OBJECTIONS: renders every OPEN objection regardless of age, newest first ---

	@Test
	@DisplayName("OBJECTIONS: every OPEN objection renders regardless of age, ordered newest first")
	void objectionsRenderAllOpenRegardlessOfAgeNewestFirst() {
		AtomicFact oldOpen = objection("Old financing objection, still open", JUN, FactStatus.OPEN);
		AtomicFact resolved = objection("A resolved objection", JUN, FactStatus.RESOLVED);
		AtomicFact recentOpen = objection("Recent pricing objection", SEP, FactStatus.OPEN);

		BriefingContext context = context(List.of(oldOpen, resolved, recentOpen), List.of(evidenceItem()));

		// extractionComplete=false on purpose: rung 1 of GroundingPolicy says a section with a
		// sourced fact must render even while other extraction on the lead is still in flight.
		ProjectedSection section = objectionsSection(context, false);

		assertThat(section.renderState())
				.as("a sourced fact renders regardless of whether extraction elsewhere has finished")
				.isEqualTo(RenderState.RENDER);
		assertThat(section.entries())
				.as("newest first, and the resolved objection must not appear at all - this is the "
						+ "D.4 failure mode: dropping the old-but-open one would read as \"no objections\"")
				.extracting(BriefingEntry::text)
				.containsExactly("Recent pricing objection", "Old financing objection, still open");
		assertThat(section.entries())
				.as("every objection entry carries the same label, from DeterministicProjector.label")
				.extracting(BriefingEntry::label)
				.containsOnly("Unresolved objection");
	}

	@Test
	@DisplayName("OBJECTIONS: none open, but the lead has evidence and extraction finished -> DECLARE_EMPTY")
	void objectionsDeclareEmptyWhenNoneOpenButEvidenceExists() {
		BriefingContext context = context(List.of(), List.of(evidenceItem()));

		ProjectedSection section = objectionsSection(context, true);

		assertThat(section.renderState()).isEqualTo(RenderState.DECLARE_EMPTY);
		assertThat(section.entries())
				.as("\"no objections recorded\" is only a claim the system can stand behind once "
						+ "extraction has actually finished reading everything (F.1)")
				.extracting(BriefingEntry::text)
				.containsExactly("No objections recorded.");
	}

	@Test
	@DisplayName("OBJECTIONS: lead has zero evidence at all -> DEGRADED, never DECLARE_EMPTY")
	void objectionsDegradeWhenLeadHasNoEvidenceAtAll() {
		BriefingContext context = context(List.of(), List.of());

		ProjectedSection section = objectionsSection(context, true);

		assertThat(section.renderState())
				.as("a lead with no evidence at all can never honestly produce more than Missing "
						+ "Information - collapsing this into DECLARE_EMPTY would reassure the agent with "
						+ "nothing (F.9, F.10)")
				.isEqualTo(RenderState.DEGRADED);
		assertThat(section.entries())
				.extracting(BriefingEntry::text)
				.containsExactly("Nothing on record for this lead yet - see Missing Information.");
	}

	// --- REQUIREMENTS: labelFor maps attributeKey through ATTRIBUTE_LABELS ---

	@Test
	@DisplayName("REQUIREMENTS: attributeKey \"bhk\" renders with label \"Property requirement\"")
	void requirementsLabelsBhkAsPropertyRequirement() {
		AtomicFact bhkRequirement = requirement("3BHK requested", SEP, "bhk");
		BriefingContext context = context(List.of(bhkRequirement), List.of(evidenceItem()));

		ProjectedSection section = sectionFor(InferentialProjector.project(context, true), SectionKey.REQUIREMENTS);

		assertThat(section.renderState()).isEqualTo(RenderState.RENDER);
		assertThat(section.entries())
				.as("labelFor resolves \"bhk\" through InferentialProjector's ATTRIBUTE_LABELS map, "
						+ "not the raw attribute key")
				.extracting(BriefingEntry::label, BriefingEntry::text)
				.containsExactly(org.assertj.core.groups.Tuple.tuple("Property requirement", "3BHK requested"));
	}

	// --- project() is structurally incapable of an LLM call ---

	@Test
	@DisplayName("project() takes no LLM client and the class holds no collaborator to mock")
	void projectNeverTouchesAnLlmClient() throws NoSuchMethodException {
		Method project = InferentialProjector.class.getDeclaredMethod("project", BriefingContext.class, boolean.class);

		assertThat(project.getParameterTypes())
				.as("the only inputs are the already-assembled context and the extraction-complete "
						+ "flag - there is no third parameter for a client of any kind")
				.containsExactly(BriefingContext.class, boolean.class);

		Field[] fields = InferentialProjector.class.getDeclaredFields();
		assertThat(fields)
				.as("every field is static (the ATTRIBUTE_LABELS constant); nothing is instance state "
						+ "an LLM client could be injected into")
				.allSatisfy(field -> assertThat(Modifier.isStatic(field.getModifiers())).isTrue());

		Constructor<?>[] constructors = InferentialProjector.class.getDeclaredConstructors();
		assertThat(constructors)
				.as("the sole constructor is the private no-arg one noted in the class Javadoc - "
						+ "nothing can be constructed with a collaborator, let alone an LlmClient")
				.hasSize(1);
		assertThat(constructors[0].getParameterCount()).isZero();
		assertThat(Modifier.isPrivate(constructors[0].getModifiers())).isTrue();
	}

	// --- fixtures ---

	private static ProjectedSection objectionsSection(BriefingContext context, boolean extractionComplete) {
		return sectionFor(InferentialProjector.project(context, extractionComplete), SectionKey.OBJECTIONS);
	}

	private static ProjectedSection sectionFor(List<ProjectedSection> sections, SectionKey key) {
		return sections.stream()
				.filter(section -> section.key() == key)
				.findFirst()
				.orElseThrow(() -> new AssertionError("InferentialProjector.project() produced no " + key + " section"));
	}

	private static BriefingContext context(List<AtomicFact> facts, List<EvidenceItem> evidence) {
		LeadSnapshot lead = LeadSnapshot.builder(REF).build();
		return new BriefingContext(REF, USER, lead, evidence, List.of(), facts);
	}

	private static AtomicFact objection(String claim, Instant occurredAt, FactStatus status) {
		return AtomicFact.builder()
				.evidenceId(UUID.randomUUID())
				.tenantId("t-acme")
				.leadRef("12345")
				.kind(FactKind.OBJECTION)
				.claim(claim)
				.provenance(Provenance.INFERRED)
				.status(status)
				.occurredAt(occurredAt)
				.extractorVersion("v1")
				.build();
	}

	private static AtomicFact requirement(String claim, Instant occurredAt, String attributeKey) {
		return AtomicFact.builder()
				.evidenceId(UUID.randomUUID())
				.tenantId("t-acme")
				.leadRef("12345")
				.kind(FactKind.REQUIREMENT)
				.claim(claim)
				.provenance(Provenance.INFERRED)
				.status(FactStatus.OPEN)
				.occurredAt(occurredAt)
				.attributeKey(attributeKey)
				.extractorVersion("v1")
				.build();
	}

	private static EvidenceItem evidenceItem() {
		return EvidenceItem.builder()
				.tenantId("t-acme")
				.crmKey("demo")
				.leadRef("12345")
				.evidenceKey("call:1")
				.type(EvidenceType.CALL)
				.occurredAt(SEP)
				.actor(Actor.AGENT)
				.channel(Channel.PHONE)
				.text("Customer discussed requirements.")
				.deepLink("http://localhost:5174/leads/12345/activities/call:1")
				.sourceMode(SourceMode.CRM_API)
				.updatedAt(SEP)
				.build();
	}
}
