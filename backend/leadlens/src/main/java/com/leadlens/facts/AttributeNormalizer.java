package com.leadlens.facts;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns free text into a machine-comparable value, or refuses to.
 *
 * <p>Used on both sides of the field-versus-fact check (IMPLEMENTATION_PLAN.md F.16): the lead
 * field's raw string and the extractor's candidate both come through here, so a contradiction
 * is a value comparison rather than a string-similarity guess.
 *
 * <h2>Refusing is a feature</h2>
 * Returning null is the correct outcome whenever the text cannot be normalised confidently, and
 * it is the safe direction to fail in (R21). A <em>false negative</em> - failing to normalise
 * "one bedroom, maybe two" - just drops that fact out of the check, degrading to "not
 * detected". A <em>false positive</em> either hides a real contradiction or invents one, and
 * inventing one sends the agent into a meeting to re-confirm something that was never in doubt.
 *
 * <p>So the rules below are deliberately narrow, and attributes whose values are prose -
 * timeline, financing, decision maker - normalise to null on purpose. They still participate in
 * the unsynced and never-captured cases, which only need a fact to exist; they simply never
 * raise a contradiction. Widening this requires adding test cases for each new attribute, not
 * just a new branch.
 *
 * <p>Pure and static. No clock, no repository, no model.
 */
public final class AttributeNormalizer {

	private static final Pattern BHK = Pattern.compile("(\\d+)\\s*bhk", Pattern.CASE_INSENSITIVE);

	private static final String UNITS = "cr|crore|crores|l|lac|lacs|lakh|lakhs|k";

	/** An amount with an Indian-scale suffix: "65L", "1.8 Cr", "90 lakh". */
	private static final Pattern AMOUNT = Pattern.compile(
			"(\\d+(?:\\.\\d+)?)\\s*(" + UNITS + ")\\b", Pattern.CASE_INSENSITIVE);

	/**
	 * A range that states its unit once, at the end: "1.5-1.8 Cr", "65-80L".
	 *
	 * <p>This is how people actually write ranges, and matching only {@link #AMOUNT} would read
	 * "Rs 1.5-1.8 Cr" as the single figure 1.8 Cr - silently narrowing a range into a precise
	 * number the customer never gave, which is worse than refusing.
	 */
	private static final Pattern SHARED_UNIT_RANGE = Pattern.compile(
			"(\\d+(?:\\.\\d+)?)\\s*(?:-|–|—|to)\\s*(\\d+(?:\\.\\d+)?)\\s*(" + UNITS + ")\\b",
			Pattern.CASE_INSENSITIVE);

	private AttributeNormalizer() {
	}

	/**
	 * @param attributeKey one of the keys {@code FactKind.leadFieldAttribute()} returns
	 * @param raw          the text as written, from a CRM field or extracted from a message
	 * @return a comparable value, or null when it cannot be determined confidently
	 */
	public static String normalize(String attributeKey, String raw) {
		if (attributeKey == null || raw == null || raw.isBlank()) {
			return null;
		}
		return switch (attributeKey) {
			case "bhk" -> normalizeBhk(raw);
			case "budget" -> normalizeBudget(raw);
			case "location" -> normalizeLocation(raw);
			// Prose-valued. Comparing "within 3 months" against "by Diwali" would produce
			// confident nonsense, so these never yield a comparable value - see the class note.
			default -> null;
		};
	}

	/** "3 BHK", "3bhk", "looking for 2BHK" -> "3BHK" / "2BHK". */
	private static String normalizeBhk(String raw) {
		Matcher matcher = BHK.matcher(raw);
		if (!matcher.find()) {
			return null;
		}
		String first = matcher.group(1);
		// Two different configurations in one sentence ("2BHK or 3BHK") is genuinely ambiguous.
		// Picking the first would be a guess dressed as a fact.
		while (matcher.find()) {
			if (!matcher.group(1).equals(first)) {
				return null;
			}
		}
		return first + "BHK";
	}

	/**
	 * Indian currency shorthand to rupees. A range stays a range: "Rs 1.5-1.8 Cr" becomes
	 * "15000000-18000000", because collapsing it to one number would invent precision the
	 * customer never gave.
	 */
	private static String normalizeBudget(String raw) {
		// "1.5-1.8 Cr" first: both bounds share the trailing unit.
		Matcher shared = SHARED_UNIT_RANGE.matcher(raw);
		if (shared.find()) {
			String unit = shared.group(3);
			BigDecimal low = toRupees(shared.group(1), unit);
			BigDecimal high = toRupees(shared.group(2), unit);
			// A second such range in one string is more than we can read reliably.
			return shared.find() ? null : asRange(low, high);
		}

		Matcher matcher = AMOUNT.matcher(raw);
		if (!matcher.find()) {
			return null;
		}

		BigDecimal low = toRupees(matcher.group(1), matcher.group(2));
		BigDecimal high = low;
		int found = 1;

		while (matcher.find()) {
			if (++found > 2) {
				// Three or more amounts in one string is not a range we can read reliably.
				return null;
			}
			high = toRupees(matcher.group(1), matcher.group(2));
		}

		return found == 1 ? low.toPlainString() : asRange(low, high);
	}

	/** Always low-to-high, so "1.8 Cr to 1.5 Cr" and "1.5-1.8 Cr" compare equal. */
	private static String asRange(BigDecimal a, BigDecimal b) {
		BigDecimal low = a.min(b);
		BigDecimal high = a.max(b);
		return low.toPlainString() + "-" + high.toPlainString();
	}

	private static BigDecimal toRupees(String number, String unit) {
		BigDecimal multiplier = switch (unit.toLowerCase(Locale.ROOT)) {
			case "cr", "crore", "crores" -> BigDecimal.valueOf(10_000_000L);
			case "l", "lac", "lacs", "lakh", "lakhs" -> BigDecimal.valueOf(100_000L);
			case "k" -> BigDecimal.valueOf(1_000L);
			default -> BigDecimal.ONE;
		};
		// stripTrailingZeros keeps 1.5 Cr and 1.50 Cr comparing equal as strings.
		return new BigDecimal(number).multiply(multiplier).stripTrailingZeros();
	}

	/** Case and punctuation folded, so "Whitefield" and "whitefield," compare equal. */
	private static String normalizeLocation(String raw) {
		String cleaned = raw.toLowerCase(Locale.ROOT)
				.replaceAll("[^a-z0-9\\s]", " ")
				.replaceAll("\\s+", " ")
				.trim();
		return cleaned.isEmpty() ? null : cleaned;
	}
}
