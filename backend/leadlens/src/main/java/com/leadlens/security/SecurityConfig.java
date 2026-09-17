package com.leadlens.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Web security.
 *
 * <p><strong>Phase 1 state: open.</strong> Real token authentication, tenant resolution and
 * role-based field masking land in Phase 8, which is also where the cross-tenant 403 test and
 * the masked-field test live. Until then every endpoint is reachable without credentials, and
 * the startup warning below exists so nobody mistakes that for a finished state.
 *
 * <p>The Demo CRM's own API already enforces its tenant and role rules independently (see
 * {@code DemoCrmController}), so the permission story being demonstrated is real even while
 * this filter chain is not.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		log.warn("LeadLens security is OPEN - every endpoint is unauthenticated. "
				+ "This is the Phase 1 state; Phase 8 replaces it with token auth and tenant scoping.");

		http
				// The extension is not a browser form client; there is no session cookie to
				// protect, and every mutating call carries an explicit identity header.
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());

		return http.build();
	}
}
