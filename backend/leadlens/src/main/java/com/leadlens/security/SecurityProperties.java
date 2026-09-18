package com.leadlens.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Bound to {@code leadlens.security.*}. {@code apiToken} comes from the
 * {@code LEADLENS_API_TOKEN} environment variable, never committed.
 *
 * <p>This is a shared-secret gate between the extension's background service worker and the
 * backend, not per-user authentication - there is no login flow, no session, no JWT. It exists
 * to close the "every endpoint is open" state Phase 1 shipped with (see the old
 * {@code SecurityConfig} warning), which matters most for a backend that is reachable from
 * outside localhost. Per-user identity still comes from the {@code X-LeadLens-Tenant} /
 * {@code X-LeadLens-User} headers (C4) - this token only answers "is this caller allowed to
 * claim identity at all," not "which real person is this."
 */
@ConfigurationProperties(prefix = "leadlens.security")
public record SecurityProperties(String apiToken) {

	public boolean isConfigured() {
		return apiToken != null && !apiToken.isBlank();
	}
}
