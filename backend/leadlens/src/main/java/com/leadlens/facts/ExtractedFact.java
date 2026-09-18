package com.leadlens.facts;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * One fact as the extractor's raw JSON describes it, before validation.
 *
 * <p>Notably absent: {@code evidenceId} and {@code occurredAt}. Both are inherited from the
 * evidence item by {@link FactExtractor} after this is parsed, never supplied by the model - the
 * schema itself is the mechanical guarantee behind C1, not a convention someone has to remember.
 *
 * <p>Also notably absent: {@code normalizedValue}. The model supplies {@link #rawValue} - the
 * value as stated, in whatever form the customer said it - and {@link AttributeNormalizer}
 * turns that into a comparable value in code. Asking the model to both extract *and* normalise
 * would be asking it to decide something the system can already do deterministically (F.2), and
 * a model-normalised "1BHK" is one more thing that could quietly disagree with what the field
 * comparison in F.16 expects.
 *
 * <p>Every field is a raw string so a malformed or unexpected value fails validation in
 * {@link SchemaValidator} rather than failing JSON deserialization itself - one bad enum name
 * should drop one fact, not the whole item's extraction.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractedFact(
		String kind,
		String claim,
		String span,
		String subjectRef,
		String polarity,
		String status,
		Double confidence,
		String attributeKey,
		String rawValue) {
}
