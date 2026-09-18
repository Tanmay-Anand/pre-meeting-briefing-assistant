package com.leadlens.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Web security.
 *
 * <h2>Phase 8 state</h2>
 * Authorization happens in {@link TokenAuthFilter}, not here - Spring Security's own
 * authorization is left permissive because there is no user/password/session model to build on
 * top of (I.4: the extension holds a shared token, not per-user credentials). What this class
 * still owns: wiring that filter into the chain, CORS (the extension calls this API
 * cross-origin from a {@code chrome-extension://} page), and disabling CSRF, which protects a
 * browser form client's session cookie against a threat that does not exist here (every
 * mutating call already carries an explicit bearer token and identity headers, not a cookie).
 *
 * <h2>What is still not real auth</h2>
 * {@code X-LeadLens-Tenant} / {@code X-LeadLens-User} are trusted once the shared token checks
 * out - there is no signature binding a specific user to those header values the way a JWT
 * claim would. That is an accepted gap for a hackathon-scale extension talking to a backend it
 * also controls; a production deployment would replace the shared token with per-user JWTs
 * carrying tenant and role claims, and this filter chain is exactly where that would plug in.
 *
 * <p>The Demo CRM's own API enforces its tenant and role rules independently
 * ({@code DemoCrmController}), so the permission story being demonstrated (masked fields,
 * cross-tenant denial) is real even where this filter chain's own model is intentionally thin.
 *
 * <p>CORS is permissive for the same reason the token auth is shared rather than per-user:
 * there is no cookie-based session to scope an origin allowlist against. Tightened alongside
 * real per-user auth if this ever moves beyond a hackathon-scale deployment.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	private final TokenAuthFilter tokenAuthFilter;

	public SecurityConfig(TokenAuthFilter tokenAuthFilter) {
		this.tokenAuthFilter = tokenAuthFilter;
	}

	@Bean
	SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
		http
				.cors(cors -> cors.configurationSource(corsConfigurationSource()))
				// The extension is not a browser form client; there is no session cookie to
				// protect, and every mutating call carries an explicit identity header.
				.csrf(csrf -> csrf.disable())
				.authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
				.addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class);

		return http.build();
	}

	private CorsConfigurationSource corsConfigurationSource() {
		CorsConfiguration configuration = new CorsConfiguration();
		configuration.setAllowedOriginPatterns(List.of("*"));
		configuration.setAllowedMethods(List.of("GET", "POST", "OPTIONS"));
		configuration.setAllowedHeaders(List.of("*"));

		UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
		source.registerCorsConfiguration("/**", configuration);
		return source;
	}
}
