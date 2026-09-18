package com.leadlens.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * A shared-secret gate in front of LeadLens's own API.
 *
 * <p>Guards {@code /api/briefings/**} and {@code /api/crm/**} - the surface a compromised or
 * misconfigured extension could hit. It deliberately does <strong>not</strong> guard
 * {@code /api/democrm/**}: that path plays the role of an external CRM's own API and enforces
 * its own tenant/role rules independently ({@code DemoCrmController}), the same way a real
 * adapter would be authenticating against Leadrat's or any other CRM's own auth, not this one.
 *
 * <p>If {@code LEADLENS_API_TOKEN} is not set, the filter passes every request through and logs
 * a warning once - the same "state your assumptions" convention the old open
 * {@code SecurityConfig} used, so a missing token fails loud in a log rather than silently.
 */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(TokenAuthFilter.class);

	private final SecurityProperties properties;
	private volatile boolean warnedOnce = false;

	public TokenAuthFilter(SecurityProperties properties) {
		this.properties = properties;
	}

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String path = request.getRequestURI();
		return !(path.startsWith("/api/briefings") || path.startsWith("/api/crm"));
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {

		if (!properties.isConfigured()) {
			if (!warnedOnce) {
				log.warn("LEADLENS_API_TOKEN is not set - {} and {} are reachable without a token. "
						+ "Fine for local development; never deploy like this.", "/api/briefings", "/api/crm");
				warnedOnce = true;
			}
			chain.doFilter(request, response);
			return;
		}

		String header = request.getHeader("Authorization");
		String expected = "Bearer " + properties.apiToken();
		if (header == null || !constantTimeEquals(header, expected)) {
			response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
			response.setContentType("application/json");
			response.getWriter().write("{\"error\":\"missing or invalid token\"}");
			return;
		}

		chain.doFilter(request, response);
	}

	/** Ordinary {@code equals} on a secret leaks its length via timing; this does not. */
	private static boolean constantTimeEquals(String a, String b) {
		return java.security.MessageDigest.isEqual(
				a.getBytes(java.nio.charset.StandardCharsets.UTF_8),
				b.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}
}
