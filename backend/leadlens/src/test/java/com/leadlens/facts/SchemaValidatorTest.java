package com.leadlens.facts;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.leadlens.evidence.Actor;
import com.leadlens.evidence.Channel;
import com.leadlens.evidence.EvidenceItem;
import com.leadlens.evidence.EvidenceType;
import com.leadlens.evidence.SourceMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The line between "the model said something odd" and "the run fails" (Phase 3 step 7): one bad
 * fact must never cost the rest of an item's extraction, and never the run.
 */
class SchemaValidatorTest {

	private static final Instant OCCURRED = Instant.parse("2026-09-14T11:40:00Z");

	@Test
	@DisplayName("evidenceId and occurredAt come from the item, never from the model's output")
	void identityAlwaysInherited() {
		EvidenceItem item = item();
		ExtractedFact candidate = new ExtractedFact(
				"OBJECTION", "Customer feels pricing is too high", null, null,
				"NEGATIVE", "OPEN", 0.9, null, null);

		List<AtomicFact> facts = SchemaValidator.validate(item, List.of(candidate), "v1");

		assertThat(facts).hasSize(1);
		assertThat(facts.get(0).getEvidenceId())
				.as("C1's mechanical guarantee: the model never sees or supplies this")
				.isEqualTo(item.getId());
		assertThat(facts.get(0).getOccurredAt()).isEqualTo(item.getOccurredAt());
		assertThat(facts.get(0).getProvenance()).isEqualTo(Provenance.INFERRED);
	}

	@Test
	@DisplayName("a blank claim drops the fact, not the item")
	void blankClaimDropped() {
		ExtractedFact blank = new ExtractedFact("OBJECTION", "  ", null, null, null, null, null, null, null);
		ExtractedFact valid = new ExtractedFact("OBJECTION", "A real objection", null, null, null, null, null, null, null);

		List<AtomicFact> facts = SchemaValidator.validate(item(), List.of(blank, valid), "v1");

		assertThat(facts).hasSize(1);
		assertThat(facts.get(0).getClaim()).isEqualTo("A real objection");
	}

	@Test
	@DisplayName("an unrecognised kind drops the fact rather than failing the item")
	void unknownKindDropped() {
		ExtractedFact candidate = new ExtractedFact(
				"NOT_A_REAL_KIND", "Some claim", null, null, null, null, null, null, null);

		assertThat(SchemaValidator.validate(item(), List.of(candidate), "v1")).isEmpty();
	}

	@Test
	@DisplayName("confidence is clamped into [0, 1] rather than trusted or dropped")
	void confidenceClamped() {
		ExtractedFact tooHigh = new ExtractedFact("OBJECTION", "claim", null, null, null, null, 5.0, null, null);
		ExtractedFact negative = new ExtractedFact("OBJECTION", "claim", null, null, null, null, -3.0, null, null);
		ExtractedFact missing = new ExtractedFact("OBJECTION", "claim", null, null, null, null, null, null, null);

		List<AtomicFact> facts = SchemaValidator.validate(item(), List.of(tooHigh, negative, missing), "v1");

		assertThat(facts).extracting(AtomicFact::getConfidence).containsExactly(1.0, 0.0, 1.0);
	}

	@Test
	@DisplayName("attributeKey is trusted only when it matches the kind's own mapping")
	void attributeKeyMustMatchTheKind() {
		// REQUIREMENT maps to "bhk" (FactKind.leadFieldAttribute()). A model claiming REQUIREMENT
		// with "budget" is confused about its own schema, and normalising on that basis would
		// compare a bhk statement against the budget field - corrupting F.16 rather than merely
		// failing to help it.
		ExtractedFact mismatched = new ExtractedFact(
				"REQUIREMENT", "Wants a bigger place", null, null, null, null, null, "budget", "65L");

		List<AtomicFact> facts = SchemaValidator.validate(item(), List.of(mismatched), "v1");

		assertThat(facts).hasSize(1);
		assertThat(facts.get(0).getAttributeKey()).isNull();
		assertThat(facts.get(0).getNormalizedValue()).isNull();
	}

	@Test
	@DisplayName("a matching attributeKey is normalised in code, never trusted from the model")
	void attributeKeyNormalisedDeterministically() {
		ExtractedFact candidate = new ExtractedFact(
				"REQUIREMENT", "Customer wants 3BHK", null, null, null, null, null, "bhk", "3 bhk");

		List<AtomicFact> facts = SchemaValidator.validate(item(), List.of(candidate), "v1");

		assertThat(facts.get(0).getAttributeKey()).isEqualTo("bhk");
		assertThat(facts.get(0).getNormalizedValue())
				.as("normalisation is AttributeNormalizer's job (F.16), not the model's (F.2)")
				.isEqualTo("3BHK");
	}

	@Test
	@DisplayName("ordinals are assigned in order and skip dropped facts")
	void ordinalsSkipDropped() {
		ExtractedFact blank = new ExtractedFact("OBJECTION", "", null, null, null, null, null, null, null);
		ExtractedFact first = new ExtractedFact("OBJECTION", "First", null, null, null, null, null, null, null);
		ExtractedFact second = new ExtractedFact("OBJECTION", "Second", null, null, null, null, null, null, null);

		List<AtomicFact> facts = SchemaValidator.validate(item(), List.of(blank, first, second), "v1");

		assertThat(facts).extracting(AtomicFact::getOrdinal).containsExactly(0, 1);
	}

	private static EvidenceItem item() {
		return EvidenceItem.builder()
				.tenantId("t-acme")
				.crmKey("demo")
				.leadRef("12345")
				.evidenceKey("call:1")
				.type(EvidenceType.CALL)
				.occurredAt(OCCURRED)
				.actor(Actor.AGENT)
				.channel(Channel.PHONE)
				.text("Customer feels the pricing is too high.")
				.deepLink("http://localhost:5174/leads/12345/activities/call:1")
				.sourceMode(SourceMode.CRM_API)
				.updatedAt(OCCURRED)
				.build();
	}
}
