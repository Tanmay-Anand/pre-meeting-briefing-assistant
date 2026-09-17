package com.leadlens.common.tenant;

import java.util.Set;

/**
 * Who is asking, and what they are allowed to see.
 *
 * <p>Every {@code CrmAdapter} method takes one of these. That is deliberate: it makes it
 * impossible to fetch CRM data without saying on whose behalf, which is how C4 ("permission and
 * tenant filtering happens before any data reaches the model") is enforced at the type level
 * rather than by discipline. A masked field that reaches the model can leak into a talking
 * point even if it is stripped from the snapshot, so the filter has to sit upstream of
 * everything (F.7).
 *
 * @param tenantId the tenant this request is scoped to; never accepted from a client, always
 *                 derived from the token
 * @param userId   the acting user, used for permission checks in the CRM
 * @param roles    the user's roles, which decide field-level visibility
 */
public record ActingUser(String tenantId, String userId, Set<String> roles) {

	public ActingUser {
		if (tenantId == null || tenantId.isBlank()) {
			throw new IllegalArgumentException("tenantId is required");
		}
		if (userId == null || userId.isBlank()) {
			throw new IllegalArgumentException("userId is required");
		}
		roles = roles == null ? Set.of() : Set.copyOf(roles);
	}

	public boolean hasRole(String role) {
		return roles.contains(role);
	}
}
