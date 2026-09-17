package com.leadlens.crm.model;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The distinction between "hidden from you" and "never recorded".
 *
 * <p>These two states lead to different agent actions - escalate internally versus ask the
 * customer - so collapsing them into a single null would hand the guesswork to the least
 * reliable component in the system (IMPLEMENTATION_PLAN.md E.6, F.10). This test exists to
 * make that collapse impossible to introduce by accident.
 */
class FieldValueTest {

	@Test
	@DisplayName("a masked field is not reported as never-captured")
	void maskedIsNotAbsent() {
		FieldValue masked = FieldValue.maskedField();

		assertThat(masked.masked()).isTrue();
		assertThat(masked.isPresent()).isFalse();
		assertThat(masked.isNeverCaptured())
				.as("masked means escalate internally, never-captured means ask the customer")
				.isFalse();
	}

	@Test
	void absentIsNeverCaptured() {
		FieldValue absent = FieldValue.absent();

		assertThat(absent.masked()).isFalse();
		assertThat(absent.isPresent()).isFalse();
		assertThat(absent.isNeverCaptured()).isTrue();
	}

	@Test
	void aMaskedFieldNeverCarriesItsValue() {
		assertThat(FieldValue.maskedField().value())
				.as("a value this user may not see must not exist in the object at all")
				.isNull();
	}

	@Test
	void presentFieldKeepsValueAndTimestamp() {
		Instant when = Instant.parse("2026-09-12T04:15:00Z");
		FieldValue value = FieldValue.of("Rs 1.5-1.8 Cr", when);

		assertThat(value.isPresent()).isTrue();
		assertThat(value.isNeverCaptured()).isFalse();
		assertThat(value.updatedAt())
				.as("the per-field timestamp is what the field-vs-fact timeline is built from")
				.isEqualTo(when);
	}

	@Test
	void blankIsTreatedAsAbsentNotPresent() {
		assertThat(FieldValue.of("   ", Instant.now()).isPresent()).isFalse();
	}
}
