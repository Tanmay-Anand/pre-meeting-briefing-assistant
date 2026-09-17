package com.leadlens.facts;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Normalisation, and - more importantly - its refusals.
 *
 * <p>A false negative here costs a missed contradiction, which degrades to "not detected". A
 * false positive either hides a real contradiction or invents one, and an invented one sends
 * the agent into a meeting to re-confirm something that was never in doubt. So roughly half of
 * these tests assert that ambiguous input yields null (R21).
 */
class AttributeNormalizerTest {

	@Nested
	class Bhk {

		@Test
		void readsCommonSpellings() {
			assertThat(AttributeNormalizer.normalize("bhk", "3BHK")).isEqualTo("3BHK");
			assertThat(AttributeNormalizer.normalize("bhk", "3 bhk")).isEqualTo("3BHK");
			assertThat(AttributeNormalizer.normalize("bhk", "looking for a 2bhk")).isEqualTo("2BHK");
		}

		@Test
		@DisplayName("code-mixed Hinglish is ordinary input here, not an edge case")
		void readsCodeMixedText() {
			assertThat(AttributeNormalizer.normalize("bhk", "2bhk chahiye, budget 65L tak, loan SBI se"))
					.isEqualTo("2BHK");
		}

		@Test
		@DisplayName("two configurations in one sentence is ambiguous, so it refuses")
		void refusesWhenAmbiguous() {
			assertThat(AttributeNormalizer.normalize("bhk", "2BHK or 3BHK, either works")).isNull();
		}

		@Test
		void repeatingTheSameValueIsNotAmbiguous() {
			assertThat(AttributeNormalizer.normalize("bhk", "3BHK only. Confirmed 3 BHK.")).isEqualTo("3BHK");
		}

		@Test
		void refusesProse() {
			assertThat(AttributeNormalizer.normalize("bhk", "one bedroom, maybe two")).isNull();
			assertThat(AttributeNormalizer.normalize("bhk", "")).isNull();
			assertThat(AttributeNormalizer.normalize("bhk", null)).isNull();
		}
	}

	@Nested
	class Budget {

		@Test
		void readsIndianShorthand() {
			assertThat(AttributeNormalizer.normalize("budget", "65L")).isEqualTo("6500000");
			assertThat(AttributeNormalizer.normalize("budget", "90 lakh")).isEqualTo("9000000");
			assertThat(AttributeNormalizer.normalize("budget", "1.8 Cr")).isEqualTo("18000000");
			assertThat(AttributeNormalizer.normalize("budget", "Rs 1.9 Crore")).isEqualTo("19000000");
		}

		@Test
		@DisplayName("a range stays a range rather than being collapsed to one number")
		void keepsRanges() {
			// Collapsing this to a midpoint would invent precision the customer never gave.
			assertThat(AttributeNormalizer.normalize("budget", "Rs 1.5-1.8 Cr"))
					.isEqualTo("15000000-18000000");
		}

		@Test
		void ordersRangesLowToHighRegardlessOfHowTheyWereWritten() {
			assertThat(AttributeNormalizer.normalize("budget", "1.8 Cr to 1.5 Cr"))
					.isEqualTo("15000000-18000000");
		}

		@Test
		void treatsEquivalentWritingsAsEqual() {
			assertThat(AttributeNormalizer.normalize("budget", "1.50 Cr"))
					.isEqualTo(AttributeNormalizer.normalize("budget", "1.5 Cr"));
			assertThat(AttributeNormalizer.normalize("budget", "150 lakh"))
					.isEqualTo(AttributeNormalizer.normalize("budget", "1.5 Cr"));
		}

		@Test
		@DisplayName("more than two amounts is not a range we can read, so it refuses")
		void refusesWhenTooManyAmounts() {
			assertThat(AttributeNormalizer.normalize("budget", "was 65L, then 80L, now 1.2 Cr")).isNull();
		}

		@Test
		void refusesUnitlessNumbers() {
			// "budget 65" could be lakhs, thousands or a flat number. Guessing is the failure mode.
			assertThat(AttributeNormalizer.normalize("budget", "budget is 65")).isNull();
		}
	}

	@Nested
	class Location {

		@Test
		void foldsCaseAndPunctuation() {
			assertThat(AttributeNormalizer.normalize("location", "Whitefield"))
					.isEqualTo(AttributeNormalizer.normalize("location", "whitefield,"));
		}

		@Test
		void collapsesWhitespace() {
			assertThat(AttributeNormalizer.normalize("location", "  Electronic   City  "))
					.isEqualTo("electronic city");
		}
	}

	@Nested
	class ProseAttributes {

		@Test
		@DisplayName("prose-valued attributes never yield a comparable value, by design")
		void refuseByDesign() {
			// "within 3 months" vs "by Diwali" cannot be compared without inventing a calendar
			// opinion. These attributes still support unsynced and never-captured, which only
			// need a fact to exist - they just never raise a contradiction.
			assertThat(AttributeNormalizer.normalize("timeline", "within 3 months")).isNull();
			assertThat(AttributeNormalizer.normalize("financing", "SBI home loan")).isNull();
			assertThat(AttributeNormalizer.normalize("decisionMaker", "his wife")).isNull();
		}

		@Test
		void unknownAttributesRefuse() {
			assertThat(AttributeNormalizer.normalize("somethingNew", "3BHK")).isNull();
		}
	}
}
