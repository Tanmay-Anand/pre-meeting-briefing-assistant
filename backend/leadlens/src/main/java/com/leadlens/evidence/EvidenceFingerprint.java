package com.leadlens.evidence;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.HexFormat;
import java.util.List;

/**
 * A hash of exactly which evidence a briefing was built from.
 *
 * <p>Comparing a stored fingerprint against the lead's current evidence is how staleness is
 * detected. That drives the honest version of refresh: serve the cached briefing immediately
 * with a banner saying what changed, rather than a spinner or a silent regeneration. It proves
 * the system knows the difference (IMPLEMENTATION_PLAN.md D.5).
 *
 * <p>The hash covers each item's id <em>and</em> its {@code updatedAt}, so an edited note
 * counts as a change even though no item was added or removed. Sorting first makes the result
 * independent of fetch order, which otherwise would make every regeneration look like a change.
 *
 * <p>Pure and static: no clock, no repository, no Spring. It is trivially testable and there is
 * nothing to mock.
 */
public final class EvidenceFingerprint {

	/** Distinguishes "no evidence" from "not yet computed" - they are different claims (F.10). */
	public static final String EMPTY = "sha256:empty";

	private EvidenceFingerprint() {
	}

	public static String of(Collection<EvidenceItem> items) {
		if (items == null || items.isEmpty()) {
			return EMPTY;
		}

		List<String> parts = items.stream()
				.map(item -> item.getId() + "@" + item.getUpdatedAt())
				.sorted()
				.toList();

		return "sha256:" + sha256(String.join("\n", parts));
	}

	/** True when this briefing was built from evidence that no longer matches the lead's. */
	public static boolean isStale(String storedFingerprint, Collection<EvidenceItem> currentItems) {
		return !of(currentItems).equals(storedFingerprint);
	}

	private static String sha256(String input) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(input.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			// SHA-256 is mandated by the JLS for every conforming JVM; if it is missing,
			// something is wrong that silently degrading would only hide.
			throw new IllegalStateException("SHA-256 unavailable", e);
		}
	}
}
