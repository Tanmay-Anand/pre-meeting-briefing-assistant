package com.leadlens.facts;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.leadlens.evidence.EvidenceItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Turns the extractor's raw, untrusted JSON into facts safe to persist - or drops them.
 *
 * <p>A malformed response must cost one item's facts, never the run (IMPLEMENTATION_PLAN.md
 * Phase 3 step 7). "Malformed" here is not just "did not parse as JSON" - that failure happens
 * one level up, in {@code FactExtractor}, before this class ever sees anything. This class
 * handles the more common case: valid JSON with an invalid enum name, a missing claim, or a
 * confidence outside [0, 1]. Each of those drops <em>that fact</em>, not the item.
 *
 * <p>Pure and static: no clock, no repository, no model. Fully deterministic given the same
 * input, which is what makes it worth unit-testing harder than the prompt itself - the prompt
 * can only ever be probabilistically right, this can be provably right.
 */
public final class SchemaValidator {

	private static final Logger log = LoggerFactory.getLogger(SchemaValidator.class);

	private SchemaValidator() {
	}

	/**
	 * @param item             the evidence item these facts were extracted from - {@code
	 *                         evidenceId} and {@code occurredAt} are inherited from here, never
	 *                         from the model's output (C1)
	 * @param raw              the extractor's candidate facts, straight off the wire
	 * @param extractorVersion stamped on every surviving fact, so a later prompt change can
	 *                         selectively re-extract without touching facts from an older
	 *                         version (F.4)
	 */
	public static List<AtomicFact> validate(EvidenceItem item, List<ExtractedFact> raw, String extractorVersion) {
		List<AtomicFact> facts = new ArrayList<>();
		int ordinal = 0;

		for (ExtractedFact candidate : raw) {
			AtomicFact fact = toFact(item, candidate, extractorVersion, ordinal);
			if (fact != null) {
				facts.add(fact);
				ordinal++;
			}
		}
		return facts;
	}

	private static AtomicFact toFact(EvidenceItem item, ExtractedFact candidate, String extractorVersion, int ordinal) {
		if (candidate.claim() == null || candidate.claim().isBlank()) {
			log.debug("Dropping fact from evidence {} with no claim", item.getId());
			return null;
		}

		FactKind kind = parseEnum(FactKind.class, candidate.kind());
		if (kind == null) {
			log.debug("Dropping fact from evidence {} with unrecognised kind '{}'", item.getId(), candidate.kind());
			return null;
		}

		String attributeKey = null;
		String normalizedValue = null;
		if (kind.mapsToLeadField() && candidate.attributeKey() != null) {
			// The model may propose an attributeKey; only the one FactKind actually maps to is
			// trusted, so a model confusing "bhk" and "location" cannot corrupt the comparison.
			if (kind.leadFieldAttribute().equalsIgnoreCase(candidate.attributeKey())) {
				attributeKey = kind.leadFieldAttribute();
				normalizedValue = AttributeNormalizer.normalize(attributeKey, candidate.rawValue());
			}
		}

		return AtomicFact.builder()
				.evidenceId(item.getId())
				.tenantId(item.getTenantId())
				.leadRef(item.getLeadRef())
				.kind(kind)
				.claim(candidate.claim().strip())
				.span(blankToNull(candidate.span()))
				.provenance(com.leadlens.facts.Provenance.INFERRED)
				.subjectRef(blankToNull(candidate.subjectRef()))
				.polarity(parseEnumOrDefault(Polarity.class, candidate.polarity(), Polarity.NEUTRAL))
				.status(parseEnumOrDefault(FactStatus.class, candidate.status(), FactStatus.OPEN))
				.confidence(clampConfidence(candidate.confidence()))
				.occurredAt(item.getOccurredAt())
				.attributeKey(attributeKey)
				.normalizedValue(normalizedValue)
				.extractorVersion(extractorVersion)
				.ordinal(ordinal)
				.build();
	}

	private static double clampConfidence(Double raw) {
		if (raw == null || raw.isNaN()) {
			return 1.0d;
		}
		return Math.max(0.0d, Math.min(1.0d, raw));
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value;
	}

	private static <E extends Enum<E>> E parseEnum(Class<E> type, String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return Enum.valueOf(type, raw.trim().toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			return null;
		}
	}

	private static <E extends Enum<E>> E parseEnumOrDefault(Class<E> type, String raw, E fallback) {
		E parsed = parseEnum(type, raw);
		return parsed == null ? fallback : parsed;
	}
}
