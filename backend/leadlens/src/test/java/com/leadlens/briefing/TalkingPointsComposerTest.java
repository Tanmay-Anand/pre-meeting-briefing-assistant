package com.leadlens.briefing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.leadlens.ai.LlmClient;
import com.leadlens.ai.LlmClient.LlmException;
import com.leadlens.ai.LlmProperties;
import com.leadlens.ai.LlmProperties.ModelConfig;
import com.leadlens.briefing.model.ProjectedSection;
import com.leadlens.briefing.model.RenderState;
import com.leadlens.evidence.EvidenceItem;
import com.leadlens.facts.AtomicFact;
import com.leadlens.facts.FactKind;
import com.leadlens.facts.FactStatus;
import com.leadlens.facts.Provenance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Section 8, Recommended Talking Points, is IMPLEMENTATION_PLAN.md F.3's one stated exception:
 * the single section that is genuinely model-authored prose rather than a projection of facts
 * already on record. Letting a model write free text is only safe because of two guarantees
 * {@link TalkingPointsComposer} is supposed to hold regardless of what the model does, and this
 * suite exists to prove both rather than assume them.
 *
 * <h2>Guarantee 1: a bad model turn degrades, it never bubbles up</h2>
 * A missing key, a dropped connection, a model that answers with prose instead of JSON, or a
 * lead with nothing on record yet must all land on
 * {@link ProjectedSection#degraded(com.leadlens.briefing.model.SectionKey, String)}, never on an
 * uncaught exception escaping into the briefing request. F.9/F.10 both treat "we could not
 * analyse this" as a claim the system must be able to make in words - an unhandled
 * {@code RuntimeException} half-way through composing section 8 would instead fail the whole
 * briefing over one section that is allowed to be unavailable.
 *
 * <h2>Guarantee 2: every rendered point is stamped as a suggestion, never a fact</h2>
 * The brief's own rule (E.4) is that a recommendation must render under an explicit AI
 * SUGGESTION treatment, never like a recorded fact. The data-model mechanism for that is
 * {@link Provenance#INFERRED} on every entry this composer emits - see the source's own comment
 * on why {@code INFERRED} carries that weight here even though there is no dedicated
 * "AI_SUGGESTION" provenance value. A composer that forgot to stamp one entry, or that reused a
 * grounding fact's original provenance instead of overwriting it, would let a model-authored
 * sentence render with the same visual authority as an agent-logged one - exactly what F.6
 * exists to prevent for every other section, extended here at one remove.
 *
 * <p>{@link LlmClient} does real network I/O ({@code RestClient}) and cannot be constructed
 * cheaply for a unit test, so it is mocked with Mockito rather than hand-built, per this
 * codebase's stated preference for real objects wherever practical (see
 * {@code GroundingPolicyTest}, which needs none). Every other collaborator here -
 * {@link BriefingContext}, {@link AtomicFact}, {@link LlmProperties}, {@link ObjectMapper} - is a
 * real, hand-built object.
 */
class TalkingPointsComposerTest {

	private static final ObjectMapper MAPPER = new ObjectMapper();

	@Test
	@DisplayName("no evidence at all degrades, and never even asks the model")
	void noEvidenceDegradesWithoutCallingTheModel() {
		LlmClient llmClient = mock(LlmClient.class);
		TalkingPointsComposer composer = new TalkingPointsComposer(llmClient, configuredProperties(), MAPPER);

		ProjectedSection section = composer.compose(contextWith(List.of(), List.of()), true);

		assertThat(section.renderState())
				.as("a lead nobody has contacted yet must degrade (F.1 rung 4), the same verdict "
						+ "every other section reaches - section 8 is not a special case for this rung")
				.isEqualTo(RenderState.DEGRADED);
		verifyNoInteractions(llmClient);
	}

	@Test
	@DisplayName("the model being unconfigured degrades, and never even asks the model")
	void modelNotConfiguredDegradesWithoutCallingTheModel() {
		LlmClient llmClient = mock(LlmClient.class);
		LlmProperties unconfigured = new LlmProperties(null, null,
				new ModelConfig("test-extractor", null), new ModelConfig("test-composer", null));
		TalkingPointsComposer composer = new TalkingPointsComposer(llmClient, unconfigured, MAPPER);

		ProjectedSection section = composer.compose(
				contextWith(List.of(objection()), List.of(evidenceItem())), true);

		assertThat(section.renderState())
				.as("a missing OPENROUTER_API_KEY must fail closed as degraded before any network "
						+ "call is even attempted, not surface as a runtime error")
				.isEqualTo(RenderState.DEGRADED);
		verifyNoInteractions(llmClient);
	}

	@Test
	@DisplayName("the LLM call throwing degrades instead of propagating the exception")
	void llmCallThrowingDegradesInsteadOfPropagating() {
		LlmClient llmClient = mock(LlmClient.class);
		when(llmClient.complete(any(), any(), any(), anyBoolean()))
				.thenThrow(new LlmException("simulated network failure"));
		TalkingPointsComposer composer = new TalkingPointsComposer(llmClient, configuredProperties(), MAPPER);

		ProjectedSection section = composer.compose(
				contextWith(List.of(objection()), List.of(evidenceItem())), true);

		assertThat(section.renderState())
				.as("a dropped connection or timeout mid-composition must not throw out of "
						+ "compose() - the rest of the briefing still has to render (D.2)")
				.isEqualTo(RenderState.DEGRADED);
	}

	@Test
	@DisplayName("the LLM returning unparseable JSON degrades instead of propagating the exception")
	void llmReturningUnparseableJsonDegradesInsteadOfPropagating() {
		LlmClient llmClient = mock(LlmClient.class);
		when(llmClient.complete(any(), any(), any(), anyBoolean()))
				.thenReturn("Sure! Here are a few things you could bring up in the meeting.");
		TalkingPointsComposer composer = new TalkingPointsComposer(llmClient, configuredProperties(), MAPPER);

		ProjectedSection section = composer.compose(
				contextWith(List.of(objection()), List.of(evidenceItem())), true);

		assertThat(section.renderState())
				.as("response_format: json_object is a request, not a guarantee (see LlmClient's "
						+ "own Javadoc) - a model that answers in prose instead of the agreed JSON shape "
						+ "must degrade the section, not throw a parse exception into the request")
				.isEqualTo(RenderState.DEGRADED);
	}

	@Test
	@DisplayName("every rendered talking point carries Provenance.INFERRED and no field label")
	void renderedTalkingPointsCarryInferredProvenanceAndNoLabel() {
		LlmClient llmClient = mock(LlmClient.class);
		when(llmClient.complete(any(), any(), any(), anyBoolean())).thenReturn("""
				{"talkingPoints": [\
				"Consider asking whether the financing pre-approval has come through yet", \
				"Worth confirming their continued interest in Prestige Lakeside", \
				"Address the maintenance-charge objection before it stalls the decision"]}
				""");
		TalkingPointsComposer composer = new TalkingPointsComposer(llmClient, configuredProperties(), MAPPER);

		ProjectedSection section = composer.compose(
				contextWith(List.of(objection()), List.of(evidenceItem())), true);

		assertThat(section.renderState()).isEqualTo(RenderState.RENDER);
		assertThat(section.entries()).hasSize(3);
		assertThat(section.entries()).allSatisfy(entry -> {
			assertThat(entry.provenance())
					.as("BriefingEntry.field(null, point, Provenance.INFERRED) is the only shape "
							+ "this composer is allowed to emit - INFERRED is what the renderer keys its "
							+ "AI SUGGESTION badge off of (E.4). CRM_FIELD or CRM_ACTIVITY here would let "
							+ "model-authored prose borrow a recorded fact's authority")
					.isEqualTo(Provenance.INFERRED);
			assertThat(entry.label())
					.as("section 8 renders as a plain list (RenderStyle.LIST), not a field table - a "
							+ "non-null label here is meaningless for this section and BriefingEntry.field's "
							+ "first argument is always literally null at the call site")
					.isNull();
		});
	}

	private static LlmProperties configuredProperties() {
		return new LlmProperties(null, "test-api-key",
				new ModelConfig("test-extractor", null), new ModelConfig("test-composer", null));
	}

	private static BriefingContext contextWith(List<AtomicFact> facts, List<EvidenceItem> evidence) {
		// ref/user/lead are never read by compose() - only facts() and hasNoEvidence() (which
		// reads evidence()) are, so they are left null rather than hand-built for no purpose.
		return new BriefingContext(null, null, null, evidence, List.of(), facts);
	}

	private static AtomicFact objection() {
		return AtomicFact.builder()
				.evidenceId(UUID.randomUUID())
				.tenantId("t-acme")
				.leadRef("12345")
				.kind(FactKind.OBJECTION)
				.claim("Customer feels the pricing is above their budget")
				.provenance(Provenance.INFERRED)
				.status(FactStatus.OPEN)
				.occurredAt(Instant.parse("2026-09-14T11:40:00Z"))
				.extractorVersion("v1")
				.build();
	}

	private static EvidenceItem evidenceItem() {
		return EvidenceItem.builder().build();
	}
}
