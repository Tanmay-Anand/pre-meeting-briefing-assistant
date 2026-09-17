package com.leadlens.briefing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.leadlens.briefing.FieldSyncCheck.Finding;
import com.leadlens.briefing.FieldSyncCheck.Origin;
import com.leadlens.briefing.model.EntryFlag;
import com.leadlens.crm.model.FieldValue;
import com.leadlens.facts.AtomicFact;
import com.leadlens.facts.FactKind;
import com.leadlens.facts.Provenance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The field-versus-fact check (IMPLEMENTATION_PLAN.md F.16).
 *
 * <p>The two directional cases are the point of this suite. It is easy to write a check that
 * catches a stale field and quietly blesses a bad field edit, because "latest wins" feels
 * right; these tests pin down that the rule is about <em>disagreement between the two most
 * recent entries</em>, not about which source is trusted.
 */
class FieldSyncCheckTest {

	private static final Instant AUG_20 = Instant.parse("2026-08-20T10:00:00Z");
	private static final Instant SEP_02 = Instant.parse("2026-09-02T10:00:00Z");
	private static final Instant SEP_10 = Instant.parse("2026-09-10T10:00:00Z");
	private static final Instant SEP_16 = Instant.parse("2026-09-16T10:00:00Z");

	@Test
	@DisplayName("field says 3BHK, a newer conversation says 1BHK -> contradicted")
	void flagsStaleFieldAgainstNewerFact() {
		// The seeded demo case: the bhk field was last set on 2 Sep, and at the 16 Sep site
		// visit the customer asked about a 1BHK.
		Finding finding = FieldSyncCheck.evaluate(
				"bhk",
				FieldValue.of("3BHK", SEP_02),
				List.of(fact("bhk", "1BHK", "Customer asked about a 1BHK unit", SEP_16)));

		assertThat(finding.flag()).isEqualTo(EntryFlag.CONTRADICTED);
		assertThat(finding.current().origin()).isEqualTo(Origin.FACT);
		assertThat(finding.current().normalized()).isEqualTo("1BHK");
		assertThat(finding.disagreesWith().origin()).isEqualTo(Origin.FIELD);
		assertThat(finding.disagreesWith().normalized()).isEqualTo("3BHK");
	}

	@Test
	@DisplayName("the field is newest but contradicts the newest conversation -> still contradicted")
	void flagsFieldEditThatContradictsTheMostRecentConversation() {
		// The reverse direction, and the case a naive "latest wins" gets exactly backwards.
		// Sequence: an early call says 1BHK, a later call says 4BHK, then an agent sets the
		// field to 1BHK. The field is the most recent event, and it matches the OLDEST
		// statement. Resolving silently in its favour would bury a real disagreement.
		Finding finding = FieldSyncCheck.evaluate(
				"bhk",
				FieldValue.of("1BHK", SEP_16),
				List.of(
						fact("bhk", "1BHK", "Customer asked for 1BHK", AUG_20),
						fact("bhk", "4BHK", "Customer now wants 4BHK", SEP_10)));

		assertThat(finding.flag())
				.as("a field edit disagreeing with the most recent call is MORE worth flagging, not less")
				.isEqualTo(EntryFlag.CONTRADICTED);
		assertThat(finding.current().origin()).isEqualTo(Origin.FIELD);
		assertThat(finding.current().normalized()).isEqualTo("1BHK");
		assertThat(finding.disagreesWith().origin()).isEqualTo(Origin.FACT);
		assertThat(finding.disagreesWith().normalized()).isEqualTo("4BHK");
	}

	@Test
	@DisplayName("a field edit that agrees with the newest conversation is not flagged")
	void staysQuietWhenTheTwoNewestAgree() {
		Finding finding = FieldSyncCheck.evaluate(
				"bhk",
				FieldValue.of("4BHK", SEP_16),
				List.of(
						fact("bhk", "1BHK", "Customer asked for 1BHK", AUG_20),
						fact("bhk", "4BHK", "Customer now wants 4BHK", SEP_10)));

		assertThat(finding.flag())
				.as("only the two most recent entries matter; an older superseded value is not a contradiction")
				.isEqualTo(EntryFlag.NONE);
	}

	@Test
	@DisplayName("empty field with a fact is unsynced, not missing")
	void reportsUnsyncedRatherThanMissing() {
		// The highest-value output in the system: the CRM does not know something the
		// conversation already established.
		Finding finding = FieldSyncCheck.evaluate(
				"financing",
				FieldValue.absent(),
				List.of(fact("financing", null, "Customer wants an SBI home loan", AUG_20)));

		assertThat(finding.flag()).isEqualTo(EntryFlag.UNSYNCED);
		assertThat(finding.current().display()).isEqualTo("Customer wants an SBI home loan");
	}

	@Test
	@DisplayName("unsynced does not require a normalisable value - existence is enough")
	void unsyncedWorksForProseAttributes() {
		Finding finding = FieldSyncCheck.evaluate(
				"timeline",
				FieldValue.absent(),
				List.of(fact("timeline", null, "Customer wants possession before Diwali", SEP_10)));

		assertThat(finding.flag()).isEqualTo(EntryFlag.UNSYNCED);
	}

	@Test
	@DisplayName("empty field with no fact is never-captured - ask the customer")
	void reportsNeverCaptured() {
		Finding finding = FieldSyncCheck.evaluate("decisionMaker", FieldValue.absent(), List.of());

		assertThat(finding.flag()).isEqualTo(EntryFlag.NEVER_CAPTURED);
		assertThat(finding.current()).isNull();
	}

	@Test
	@DisplayName("masked outranks everything - we do not know the value, so we cannot judge it")
	void maskedIsNeverReportedAsMissingOrContradicted() {
		Finding finding = FieldSyncCheck.evaluate(
				"budget",
				FieldValue.maskedField(),
				List.of(fact("budget", "19000000", "Customer can go to Rs 1.9 Cr", SEP_16)));

		assertThat(finding.flag())
				.as("claiming a masked field is missing would send the agent to ask for something "
						+ "the company already knows and merely hid from them")
				.isEqualTo(EntryFlag.MASKED);
	}

	@Test
	@DisplayName("a fact that could not be normalised drops out instead of raising a false contradiction")
	void degradesGracefullyWhenNormalisationFailed() {
		Finding finding = FieldSyncCheck.evaluate(
				"bhk",
				FieldValue.of("3BHK", SEP_02),
				List.of(fact("bhk", null, "Customer mentioned something about a smaller unit", SEP_16)));

		assertThat(finding.flag())
				.as("a null normalizedValue means 'not detected', which is the safe direction (R21)")
				.isEqualTo(EntryFlag.NONE);
	}

	@Test
	void ignoresFactsForOtherAttributes() {
		Finding finding = FieldSyncCheck.evaluate(
				"bhk",
				FieldValue.of("3BHK", SEP_02),
				List.of(fact("budget", "19000000", "Customer can go to Rs 1.9 Cr", SEP_16)));

		assertThat(finding.flag()).isEqualTo(EntryFlag.NONE);
	}

	@Test
	@DisplayName("a budget range and a single figure that falls outside it disagree")
	void comparesRangesAgainstFigures() {
		Finding finding = FieldSyncCheck.evaluate(
				"budget",
				FieldValue.of("Rs 1.5-1.8 Cr", Instant.parse("2026-09-12T10:00:00Z")),
				List.of(fact("budget", "19000000", "Can increase budget to Rs 1.9 Cr", SEP_16)));

		assertThat(finding.flag()).isEqualTo(EntryFlag.CONTRADICTED);
		assertThat(finding.disagreesWith().normalized()).isEqualTo("15000000-18000000");
	}

	private static AtomicFact fact(String attributeKey, String normalized, String claim, Instant at) {
		return AtomicFact.builder()
				.evidenceId(UUID.randomUUID())
				.tenantId("t-acme")
				.leadRef("12345")
				.kind(FactKind.REQUIREMENT)
				.claim(claim)
				.provenance(Provenance.INFERRED)
				.occurredAt(at)
				.attributeKey(attributeKey)
				.normalizedValue(normalized)
				.extractorVersion("test")
				.build();
	}
}
