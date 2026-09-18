package com.leadlens.briefing;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.leadlens.facts.AtomicFact;
import com.leadlens.facts.FactKind;
import com.leadlens.facts.FactStatus;
import com.leadlens.facts.InclusionFloor;

/**
 * Applies the inclusion floor for one {@link FactKind} to a lead's facts.
 *
 * <p>This is the direct mitigation for the failure IMPLEMENTATION_PLAN.md D.4 describes: if
 * selection is allowed to drop the one call note from four months ago where the customer raised
 * a financing objection that is still unresolved, the Objections section renders "no objections
 * recorded" with perfect structural integrity. So the floors are enforced here, deterministically,
 * over facts that have already all been extracted (D.4) - never over raw evidence under a token
 * budget, and never by a model that could be talked out of including one.
 *
 * <p>Pure and static: no repository, no clock. Takes every fact for the lead and narrows.
 */
public final class FactSelector {

	/** How many interaction summaries to show - the one floor where recency is genuinely the
	 *  relevant signal (E.3). */
	private static final int RECENCY_CAP = 8;

	private FactSelector() {
	}

	/** Every fact of this kind that survives its inclusion floor, newest first. */
	public static List<AtomicFact> select(FactKind kind, List<AtomicFact> allFactsForLead) {
		List<AtomicFact> ofKind = allFactsForLead.stream()
				.filter(fact -> fact.getKind() == kind)
				.toList();

		return switch (kind.inclusionFloor()) {
			case ALL_OPEN -> ofKind.stream()
					.filter(fact -> fact.getStatus() == FactStatus.OPEN)
					.sorted(byOccurredAtDesc())
					.toList();

			case ALL -> latestPerGroup(ofKind, AtomicFact::getSubjectRef);

			case LATEST_PER_ATTRIBUTE -> latestPerGroup(ofKind, AtomicFact::getAttributeKey);

			case LATEST_PLUS_PRIOR_IF_CHANGED -> latestPlusPriorIfChanged(ofKind);

			case LATEST -> ofKind.stream()
					.max(Comparator.comparing(AtomicFact::getOccurredAt))
					.map(List::of)
					.orElse(List.of());

			// Existence, not content: any one fact answers the question.
			case ANY -> ofKind.stream()
					.max(Comparator.comparing(AtomicFact::getOccurredAt))
					.map(List::of)
					.orElse(List.of());

			case RECENCY_CAPPED -> ofKind.stream()
					.sorted(byOccurredAtDesc())
					.limit(RECENCY_CAP)
					.toList();
		};
	}

	/** Every candidate section fed by more than one {@link FactKind} - convenience over {@link #select}. */
	public static List<AtomicFact> selectAll(List<FactKind> kinds, List<AtomicFact> allFactsForLead) {
		List<AtomicFact> combined = new ArrayList<>();
		for (FactKind kind : kinds) {
			combined.addAll(select(kind, allFactsForLead));
		}
		return combined;
	}

	private static List<AtomicFact> latestPerGroup(List<AtomicFact> facts, java.util.function.Function<AtomicFact, String> groupKey) {
		Map<String, AtomicFact> latestByGroup = new LinkedHashMap<>();
		for (AtomicFact fact : facts.stream().sorted(byOccurredAtDesc()).toList()) {
			// Sorted newest-first, so the first fact seen per group is already the latest one;
			// putIfAbsent keeps it and ignores anything older for that group.
			String key = groupKey.apply(fact);
			latestByGroup.putIfAbsent(key == null ? "" : key, fact);
		}
		return latestByGroup.values().stream().sorted(byOccurredAtDesc()).toList();
	}

	/**
	 * The latest fact, plus the immediately preceding one if its value actually differs -
	 * because the change is more useful to the agent than either number alone (F.14).
	 */
	private static List<AtomicFact> latestPlusPriorIfChanged(List<AtomicFact> facts) {
		List<AtomicFact> sorted = facts.stream().sorted(byOccurredAtDesc()).toList();
		if (sorted.isEmpty()) {
			return List.of();
		}
		if (sorted.size() == 1) {
			return List.of(sorted.get(0));
		}

		AtomicFact latest = sorted.get(0);
		AtomicFact prior = sorted.get(1);
		boolean changed = latest.getNormalizedValue() == null
				? !latest.getClaim().equals(prior.getClaim())
				: !latest.getNormalizedValue().equals(prior.getNormalizedValue());

		return changed ? List.of(latest, prior) : List.of(latest);
	}

	private static Comparator<AtomicFact> byOccurredAtDesc() {
		return Comparator.comparing(AtomicFact::getOccurredAt).reversed();
	}
}
