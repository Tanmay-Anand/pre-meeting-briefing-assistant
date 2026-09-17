package com.leadlens.briefing;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.leadlens.briefing.model.EntryFlag;
import com.leadlens.crm.model.FieldValue;
import com.leadlens.facts.AtomicFact;
import com.leadlens.facts.AttributeNormalizer;

/**
 * Compares what the CRM field says against what the conversations say, for one attribute.
 *
 * <p>Contradictions <em>between facts</em> are handled by recency (F.14). This handles the gap
 * that leaves: a CRM field disagreeing with a fact, <strong>in either direction</strong>.
 *
 * <h2>Why direction must not matter</h2>
 * The obvious reading is "the field is stale": a customer states a new requirement and nobody
 * updates the CRM. But the reverse happens too - an agent edits a field to a value that
 * contradicts the most recent call. Three events in sequence: a call says 1BHK, a later call
 * says 4BHK, then the agent sets the field to 1BHK. The field is now the newest event, and
 * "latest wins" would silently resolve the disagreement in its favour. That is exactly
 * backwards. A field edit that contradicts the most recent conversation is <em>more</em> worth
 * flagging, not less.
 *
 * <p>So the rule asks only which entry is most recent and whether it disagrees with the one
 * before it. It never asks which <em>source type</em> is more trustworthy.
 *
 * <h2>Why this runs on page load</h2>
 * The freshness pill already tells the agent something changed, but only after they notice it
 * and click Refresh. A field/fact contradiction has to be visible passively, the moment the
 * lead is opened. This is a plain query over already-persisted facts - no model call, no
 * dependency on a briefing run having completed - so {@code GET /api/briefings/latest} can
 * compute it every time (F.16).
 *
 * <p>Pure and static: no clock, no repository, no Spring.
 */
public final class FieldSyncCheck {

	private FieldSyncCheck() {
	}

	/** Where a value in the timeline came from. */
	public enum Origin {
		FIELD,
		FACT
	}

	/**
	 * One known value for an attribute at a point in time.
	 *
	 * @param origin     whether this is the CRM field or something someone said
	 * @param normalized the comparable form, never null in a timeline entry
	 * @param display    the value as a human should read it
	 * @param at         when it was set or said
	 * @param evidenceId the fact's source record, or null for a field value
	 */
	public record ValuePoint(
			Origin origin,
			String normalized,
			String display,
			Instant at,
			UUID evidenceId) {
	}

	/**
	 * What the check concluded for one attribute.
	 *
	 * @param attributeKey  which attribute
	 * @param flag          the kind of empty, or CONTRADICTED, or NONE when all is well
	 * @param current       the most recent known value, from whichever source; null when none
	 * @param disagreesWith the entry it contradicts, present only for CONTRADICTED
	 */
	public record Finding(
			String attributeKey,
			EntryFlag flag,
			ValuePoint current,
			ValuePoint disagreesWith) {

		public boolean needsAgentAttention() {
			return flag != EntryFlag.NONE;
		}
	}

	/**
	 * Evaluates one attribute.
	 *
	 * @param attributeKey the attribute to check
	 * @param field        the lead's current field, including masked and absent states
	 * @param facts        facts for this attribute; unordered, may be empty, may include facts
	 *                     whose value could not be normalised
	 */
	public static Finding evaluate(String attributeKey, FieldValue field, List<AtomicFact> facts) {
		List<AtomicFact> relevant = facts == null ? List.of() : facts.stream()
				.filter(fact -> attributeKey.equals(fact.getAttributeKey()))
				.toList();

		// Masked outranks everything else. We genuinely do not know what the value is, so we
		// must not claim it is missing, and we must not compare against it.
		if (field != null && field.masked()) {
			return new Finding(attributeKey, EntryFlag.MASKED, null, null);
		}

		boolean fieldPresent = field != null && field.isPresent();

		if (!fieldPresent) {
			// A fact exists for an empty field: the CRM does not know something the
			// conversation already established. Not missing - unsynced.
			Optional<AtomicFact> newest = relevant.stream()
					.max(Comparator.comparing(AtomicFact::getOccurredAt));
			return newest
					.map(fact -> new Finding(attributeKey, EntryFlag.UNSYNCED, toPoint(fact), null))
					.orElseGet(() -> new Finding(attributeKey, EntryFlag.NEVER_CAPTURED, null, null));
		}

		List<ValuePoint> timeline = buildTimeline(attributeKey, field, relevant);

		// Only the field is comparable (no normalisable facts): nothing to contradict it.
		if (timeline.size() < 2) {
			return new Finding(attributeKey, EntryFlag.NONE, timeline.isEmpty() ? null : timeline.get(0), null);
		}

		ValuePoint current = timeline.get(0);
		ValuePoint previous = timeline.get(1);

		if (!current.normalized().equals(previous.normalized())) {
			return new Finding(attributeKey, EntryFlag.CONTRADICTED, current, previous);
		}
		return new Finding(attributeKey, EntryFlag.NONE, current, null);
	}

	/** Every known value for the attribute, newest first, dropping anything uncomparable. */
	private static List<ValuePoint> buildTimeline(
			String attributeKey, FieldValue field, List<AtomicFact> facts) {

		java.util.ArrayList<ValuePoint> points = new java.util.ArrayList<>();

		String fieldNormalized = AttributeNormalizer.normalize(attributeKey, field.value());
		if (fieldNormalized != null) {
			points.add(new ValuePoint(
					Origin.FIELD, fieldNormalized, field.value(), field.updatedAt(), null));
		}

		for (AtomicFact fact : facts) {
			// A fact the extractor could not normalise drops out rather than being guessed at.
			// That degrades to "not detected", which is the safe direction (R21).
			if (fact.isComparableToLeadField()) {
				points.add(toPoint(fact));
			}
		}

		points.sort(Comparator.comparing(ValuePoint::at, Comparator.nullsLast(Comparator.reverseOrder())));
		return points;
	}

	private static ValuePoint toPoint(AtomicFact fact) {
		return new ValuePoint(
				Origin.FACT,
				fact.getNormalizedValue(),
				fact.getClaim(),
				fact.getOccurredAt(),
				fact.getEvidenceId());
	}
}
