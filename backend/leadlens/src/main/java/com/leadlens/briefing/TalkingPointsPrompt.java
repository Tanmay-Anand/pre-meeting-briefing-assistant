package com.leadlens.briefing;

import java.util.List;

import com.leadlens.facts.AtomicFact;

/**
 * The prompt for section 8 - the one place in the document that is genuinely model-authored
 * prose, by explicit design (IMPLEMENTATION_PLAN.md F.3).
 *
 * <p>The model sees only {@link AtomicFact} claims - never raw evidence text. That is what keeps
 * customer-authored text out of the component with reach across the whole briefing (F.6): by the
 * time anything reaches this prompt, an extractor already reduced it to a short, English,
 * system-generated sentence with nothing left of the original wording for an injected instruction
 * to hide inside.
 */
final class TalkingPointsPrompt {

	private TalkingPointsPrompt() {
	}

	static final String SYSTEM = """
			You suggest talking points for a sales agent about to meet a customer, based only on the \
			facts provided. Every fact is already verified and sourced - you are not verifying anything, \
			only deciding what is worth raising in the meeting and how to phrase it as a suggestion.

			Respond with a single JSON object of exactly this shape, and nothing else:

			{"talkingPoints": ["one short suggestion", "another short suggestion"]}

			Rules:
			- Every suggestion must be traceable to one or more of the facts given. Do not introduce a \
			name, number, project or commitment that is not in the facts.
			- Phrase every point as a suggestion, not a confirmed fact - "Consider asking about..." or \
			"Worth confirming...", never a flat assertion. The brief requires this distinction explicitly.
			- Prioritise: unresolved objections, open commitments on either side, properties awaiting a \
			response, and anything the requirements say that has not yet been addressed.
			- 3 to 6 points. If the facts given do not support any specific suggestion, return an empty list \
			- do not pad with generic sales advice unconnected to this lead.
			""";

	static String userPrompt(List<AtomicFact> facts) {
		StringBuilder sb = new StringBuilder("Known facts about this lead:\n");
		for (AtomicFact fact : facts) {
			sb.append("- [").append(fact.getKind()).append("] ").append(fact.getClaim()).append('\n');
		}
		return sb.toString();
	}
}
