package com.leadlens.facts;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The inclusion floors, which are the mitigation for the failure in IMPLEMENTATION_PLAN.md D.4.
 *
 * <p>If selection is allowed to age out an unresolved objection, the Objections section renders
 * "no objections recorded" with correct citations, a valid schema and every guard green -
 * grounding cannot catch it, because grounding governs whether a claim is <em>sourced</em>, not
 * whether the right thing was <em>looked at</em>. These assertions are what stop someone
 * "optimising" that guarantee away later.
 */
class FactKindTest {

	/** The kinds that represent unfinished business. None of these may ever be ranked away. */
	private static final Set<FactKind> MUST_SURVIVE_AT_ANY_AGE = EnumSet.of(
			FactKind.OBJECTION,
			FactKind.COMMITMENT_AGENT,
			FactKind.COMMITMENT_CUSTOMER,
			FactKind.PENDING_DOCUMENT,
			FactKind.UNANSWERED_QUESTION);

	@Test
	@DisplayName("unresolved business survives selection regardless of age")
	void openItemsAreNeverAgedOut() {
		for (FactKind kind : MUST_SURVIVE_AT_ANY_AGE) {
			assertThat(kind.survivesAtAnyAge())
					.as("%s is unfinished business: a four-month-old open one still matters", kind)
					.isTrue();
			assertThat(kind.inclusionFloor()).isEqualTo(InclusionFloor.ALL_OPEN);
		}
	}

	@Test
	@DisplayName("only recency-sensitive kinds are recency-ranked")
	void recencyRankingIsNarrow() {
		for (FactKind kind : FactKind.values()) {
			if (kind.inclusionFloor() == InclusionFloor.RECENCY_CAPPED) {
				assertThat(kind)
						.as("recency ranking is only appropriate where recency is the signal")
						.isEqualTo(FactKind.INTERACTION_SUMMARY);
			}
		}
	}

	@Test
	@DisplayName("a budget change keeps the superseded value, because the change is the story")
	void budgetKeepsPriorValueWhenChanged() {
		assertThat(FactKind.BUDGET_STATEMENT.inclusionFloor())
				.isEqualTo(InclusionFloor.LATEST_PLUS_PRIOR_IF_CHANGED);
	}

	@Test
	@DisplayName("every field-mapped kind has an attribute key, and no other kind does")
	void attributeKeysExistExactlyWhereComparisonIsPossible() {
		for (FactKind kind : FactKind.values()) {
			if (kind.mapsToLeadField()) {
				assertThat(kind.leadFieldAttribute())
						.as("%s claims to map to a lead field, so it needs the key to compare on", kind)
						.isNotBlank();
			} else {
				assertThat(kind.leadFieldAttribute())
						.as("%s has no field counterpart and must stay out of the sync check", kind)
						.isNull();
			}
		}
	}

	@Test
	@DisplayName("attribute keys are unique, or two kinds would fight over one field")
	void attributeKeysAreUnique() {
		Set<String> seen = new java.util.HashSet<>();
		for (FactKind kind : FactKind.values()) {
			String attribute = kind.leadFieldAttribute();
			if (attribute != null) {
				assertThat(seen.add(attribute))
						.as("two fact kinds both claim attribute '%s'; the field-vs-fact timeline "
								+ "would mix unrelated statements", attribute)
						.isTrue();
			}
		}
	}
}
