package com.leadlens.democrm;

import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

/**
 * Which lead fields each Demo CRM role may see.
 *
 * <p>Real, not decorative. The brief requires role and tenant permission enforcement, and the
 * "masked" kind of empty only exists if some role genuinely cannot see some field (E.6). This
 * is the Demo CRM's own RBAC, enforced before anything leaves its API - so LeadLens never holds
 * a value it is not allowed to show, and a masked value cannot leak into a talking point
 * (C4, F.7).
 *
 * <p>Leadrat has its own RBAC (Appendix 2 Q1, resolved), so {@code LeadratAdapter} enforces the
 * same shape against real role definitions rather than this map.
 */
@Component
public class DemoFieldVisibility {

	/** Fields hidden from a role. Anything not listed is visible. */
	private static final Map<String, Set<String>> HIDDEN_BY_ROLE = Map.of(
			// A junior agent can work the lead but cannot see commercials. This is what makes
			// the masked case demonstrable: they must escalate internally rather than ask the
			// customer for a budget the company already knows.
			"JUNIOR_AGENT", Set.of("budget"));

	public boolean isVisible(String role, String attributeKey) {
		return !HIDDEN_BY_ROLE.getOrDefault(role, Set.of()).contains(attributeKey);
	}

	public Set<String> hiddenFor(String role) {
		return HIDDEN_BY_ROLE.getOrDefault(role, Set.of());
	}
}
