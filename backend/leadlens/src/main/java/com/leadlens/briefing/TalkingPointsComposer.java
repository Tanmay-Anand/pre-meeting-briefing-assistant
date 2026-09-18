package com.leadlens.briefing;

import java.util.List;

import com.leadlens.ai.JsonExtraction;
import com.leadlens.ai.LlmClient;
import com.leadlens.ai.LlmProperties;
import com.leadlens.briefing.model.BriefingEntry;
import com.leadlens.briefing.model.ProjectedSection;
import com.leadlens.briefing.model.RenderState;
import com.leadlens.briefing.model.SectionKey;
import com.leadlens.facts.AtomicFact;
import com.leadlens.facts.FactKind;
import com.leadlens.facts.Provenance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

/**
 * Section 8, Recommended Talking Points - the one section that is genuinely model-authored
 * prose (IMPLEMENTATION_PLAN.md F.3's stated exception).
 *
 * <p>Grounded the same way everything else is, just at one remove: the model never sees raw
 * evidence, only the same already-verified {@link AtomicFact} claims every other section
 * selects from (F.6). It cannot suggest discussing a budget, project or promise that is not
 * already a fact on record, because it is never shown anything else.
 *
 * <p>Rendered under an explicit <strong>AI SUGGESTION</strong> treatment
 * ({@link Provenance#INFERRED}), per the brief's own rule that recommendations must read as
 * suggestions, not confirmed facts.
 */
@Service
public class TalkingPointsComposer {

	private static final Logger log = LoggerFactory.getLogger(TalkingPointsComposer.class);

	/** Every kind whose facts are worth a talking point - effectively everything except the
	 *  purely descriptive summary kind. */
	private static final List<FactKind> GROUNDING_KINDS = List.of(
			FactKind.OBJECTION, FactKind.COMMITMENT_AGENT, FactKind.COMMITMENT_CUSTOMER,
			FactKind.PENDING_DOCUMENT, FactKind.UNANSWERED_QUESTION, FactKind.PROPERTY_RESPONSE,
			FactKind.REQUIREMENT, FactKind.PREFERENCE, FactKind.BUDGET_STATEMENT,
			FactKind.TIMELINE_STATEMENT, FactKind.FINANCING_NEED);

	private final LlmClient llmClient;
	private final LlmProperties llmProperties;
	private final ObjectMapper objectMapper;

	public TalkingPointsComposer(LlmClient llmClient, LlmProperties llmProperties, ObjectMapper objectMapper) {
		this.llmClient = llmClient;
		this.llmProperties = llmProperties;
		this.objectMapper = objectMapper;
	}

	public ProjectedSection compose(BriefingContext context, boolean extractionComplete) {
		List<AtomicFact> grounding = FactSelector.selectAll(GROUNDING_KINDS, context.facts());
		RenderState verdict = GroundingPolicy.decide(grounding, !context.hasNoEvidence(), extractionComplete);

		if (verdict != RenderState.RENDER) {
			String statement = verdict == RenderState.DEGRADED
					? (context.hasNoEvidence()
							? "Nothing on record for this lead yet."
							: "Still analysing this lead's history.")
					: "Nothing specific to raise beyond the usual check-in.";
			return verdict == RenderState.DEGRADED
					? ProjectedSection.degraded(SectionKey.TALKING_POINTS, statement)
					: ProjectedSection.declareEmpty(SectionKey.TALKING_POINTS, statement);
		}

		if (!llmProperties.isConfigured()) {
			return ProjectedSection.degraded(SectionKey.TALKING_POINTS,
					"The model was unavailable when this was generated.");
		}

		List<String> points;
		try {
			String raw = llmClient.complete(
					llmProperties.composer(),
					TalkingPointsPrompt.SYSTEM,
					TalkingPointsPrompt.userPrompt(grounding),
					true);
			points = objectMapper.readValue(JsonExtraction.extractObject(raw), TalkingPointsResponse.class).talkingPoints();
		} catch (RuntimeException e) {
			log.warn("Talking points composition failed: {}", e.getMessage());
			return ProjectedSection.degraded(SectionKey.TALKING_POINTS,
					"The model was unavailable when this was generated.");
		}

		if (points.isEmpty()) {
			return ProjectedSection.declareEmpty(SectionKey.TALKING_POINTS,
					"Nothing specific to raise beyond the usual check-in.");
		}

		List<BriefingEntry> entries = points.stream()
				// AI_SUGGESTION has no dedicated Provenance value; INFERRED plus the label the
				// panel renders under an explicit "AI SUGGESTION" header is what E.4 asks for -
				// the brief's rule is a rendering requirement, not a new data-model concept.
				.map(point -> BriefingEntry.field(null, point, Provenance.INFERRED))
				.toList();

		return new ProjectedSection(SectionKey.TALKING_POINTS, RenderState.RENDER, entries,
				grounding.stream().map(AtomicFact::getFactId).toList());
	}
}
