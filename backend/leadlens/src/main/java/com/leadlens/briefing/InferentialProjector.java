package com.leadlens.briefing;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import com.leadlens.briefing.model.BriefingEntry;
import com.leadlens.briefing.model.EntryFlag;
import com.leadlens.briefing.model.ProjectedSection;
import com.leadlens.briefing.model.RenderState;
import com.leadlens.briefing.model.SectionKey;
import com.leadlens.facts.AtomicFact;
import com.leadlens.facts.FactKind;

/**
 * Builds sections 3-7: grounded in facts, but only render-able if extraction actually ran.
 *
 * <h2>Where the composer went - and why there isn't one here</h2>
 * IMPLEMENTATION_PLAN.md's pipeline describes a composition pass that turns selected facts into
 * {@code factIds} per section. For these five sections, that pass would have nothing left to
 * decide: {@link FactSelector}'s inclusion floors are already exhaustive by construction - every
 * OPEN objection, every open commitment, the latest value per attribute, one response per
 * property. Asking a model to choose among a set that is already fully determined is asking it
 * to decide something the system already knows (F.2), and it is one more surface where a model
 * could be talked into dropping the one fact that mattered (D.4). So these sections render
 * directly from selection: the model's only remaining, genuine job is {@link SectionKey#TALKING_POINTS},
 * the one place IMPLEMENTATION_PLAN.md F.3 already names as free prose by design.
 *
 * <p>What still comes from a model, transitively: the {@code claim} text on every
 * {@link AtomicFact} itself, produced once by {@code FactExtractor} and rendered here verbatim,
 * never reworded (F.3). This class does not call an LLM; it renders what the LLM already said,
 * with the grounding gate deciding whether there was anything to render at all.
 */
public final class InferentialProjector {

	private static final Map<String, String> ATTRIBUTE_LABELS = Map.of(
			"bhk", "Property requirement",
			"budget", "Budget",
			"location", "Preferred location",
			"timeline", "Purchase timeline",
			"financing", "Financing requirement",
			"decisionMaker", "Decision maker");

	private InferentialProjector() {
	}

	public static List<ProjectedSection> project(BriefingContext context, boolean extractionComplete) {
		return List.of(
				recentInteractions(context, extractionComplete),
				requirements(context, extractionComplete),
				propertiesDiscussed(context, extractionComplete),
				objections(context, extractionComplete),
				commitments(context, extractionComplete));
	}

	private static ProjectedSection recentInteractions(BriefingContext context, boolean extractionComplete) {
		return build(SectionKey.RECENT_INTERACTIONS, List.of(FactKind.INTERACTION_SUMMARY), context,
				extractionComplete, "Nothing notable to summarise beyond what Missing Information already covers.",
				fact -> null);
	}

	private static ProjectedSection requirements(BriefingContext context, boolean extractionComplete) {
		return build(SectionKey.REQUIREMENTS,
				List.of(FactKind.REQUIREMENT, FactKind.PREFERENCE, FactKind.BUDGET_STATEMENT,
						FactKind.TIMELINE_STATEMENT, FactKind.FINANCING_NEED),
				context, extractionComplete, "No requirements or preferences captured yet.",
				fact -> ATTRIBUTE_LABELS.getOrDefault(fact.getAttributeKey(), null));
	}

	private static ProjectedSection propertiesDiscussed(BriefingContext context, boolean extractionComplete) {
		return build(SectionKey.PROPERTIES_DISCUSSED, List.of(FactKind.PROPERTY_RESPONSE), context,
				extractionComplete, "No properties or projects have been discussed yet.",
				fact -> fact.getSubjectRef());
	}

	private static ProjectedSection objections(BriefingContext context, boolean extractionComplete) {
		return build(SectionKey.OBJECTIONS, List.of(FactKind.OBJECTION), context,
				extractionComplete, "No objections recorded.",
				fact -> DeterministicProjector.label(fact.getKind()));
	}

	private static ProjectedSection commitments(BriefingContext context, boolean extractionComplete) {
		return build(SectionKey.COMMITMENTS,
				List.of(FactKind.COMMITMENT_AGENT, FactKind.COMMITMENT_CUSTOMER,
						FactKind.PENDING_DOCUMENT, FactKind.UNANSWERED_QUESTION),
				context, extractionComplete, "No commitments, pending documents or open questions recorded.",
				fact -> DeterministicProjector.label(fact.getKind()));
	}

	private static ProjectedSection build(
			SectionKey key,
			List<FactKind> kinds,
			BriefingContext context,
			boolean extractionComplete,
			String emptyStatement,
			java.util.function.Function<AtomicFact, String> labelFor) {

		List<AtomicFact> selected = FactSelector.selectAll(kinds, context.facts());
		RenderState verdict = GroundingPolicy.decide(selected, !context.hasNoEvidence(), extractionComplete);

		return switch (verdict) {
			case DECLARE_EMPTY -> ProjectedSection.declareEmpty(key, emptyStatement);
			case DEGRADED -> ProjectedSection.degraded(key, degradedStatement(context, extractionComplete));
			case RENDER -> {
				List<AtomicFact> ordered = selected.stream()
						.sorted(Comparator.comparing(AtomicFact::getOccurredAt).reversed())
						.toList();

				List<BriefingEntry> entries = ordered.stream()
						.map(fact -> new BriefingEntry(
								labelFor.apply(fact),
								fact.getClaim(),
								fact.getProvenance(),
								flagFor(fact),
								DeterministicProjector.sourcesFor(context, fact)))
						.toList();

				yield new ProjectedSection(key, RenderState.RENDER, entries,
						ordered.stream().map(AtomicFact::getFactId).toList());
			}
		};
	}

	private static EntryFlag flagFor(AtomicFact fact) {
		// A call that was never summarised produces no fact at all (E.5) - that gap is Missing
		// Information's job, not this one's. Nothing here currently needs a flag of its own, but
		// the hook exists so a future low-confidence-fact treatment has somewhere to attach.
		return EntryFlag.NONE;
	}

	private static String degradedStatement(BriefingContext context, boolean extractionComplete) {
		if (context.hasNoEvidence()) {
			return "Nothing on record for this lead yet - see Missing Information.";
		}
		return extractionComplete
				? "The model was unavailable when this was generated."
				: "Still analysing this lead's history.";
	}
}
