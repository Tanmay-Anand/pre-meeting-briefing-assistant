package com.leadlens.crm.model;

import java.time.Instant;

/**
 * One field on a lead record, together with the two things that make it usable downstream:
 * when it was last set, and whether this user is allowed to see it.
 *
 * <p>A masked field is <em>not</em> the same as an absent one, and collapsing them would
 * delegate the guesswork to the least reliable component in the system (F.10). "Never captured"
 * means ask the customer; "masked" means do not ask, escalate internally. So masking is
 * represented explicitly rather than by a null.
 *
 * @param value      the value, or null when absent or masked
 * @param updatedAt  when it was last set, used for staleness and for the F.16 sync timeline
 * @param masked     true when this user's permissions hide a value that does exist
 */
public record FieldValue(String value, Instant updatedAt, boolean masked) {

	public static FieldValue of(String value, Instant updatedAt) {
		return new FieldValue(value, updatedAt, false);
	}

	/** A field that exists but is hidden from this user. Never carries the value. */
	public static FieldValue maskedField() {
		return new FieldValue(null, null, true);
	}

	/** A field that was never captured. */
	public static FieldValue absent() {
		return new FieldValue(null, null, false);
	}

	public boolean isPresent() {
		return value != null && !value.isBlank();
	}

	/** True only when the field was genuinely never captured - not when it is merely hidden. */
	public boolean isNeverCaptured() {
		return !masked && !isPresent();
	}
}
