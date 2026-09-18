package com.leadlens.crm.leadscrm;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.leadlens.crm.leadscrm.LeadsCrmDtos.CognitoAuthResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

/**
 * Acquires and caches a Cognito bearer token for {@code leads-crm-backend}'s own {@code /leads}
 * API, using a single service identity (Appendix 2 Q5 - real per-user auth from the extension is
 * unresolved; a service identity is the accepted gap for now).
 *
 * <p>Calls Cognito's {@code InitiateAuth} REST endpoint directly rather than pulling in the AWS
 * SDK for one call - it is an unauthenticated (no SigV4) JSON POST, same pattern as every other
 * external HTTP call in this codebase ({@code LlmClient}, {@code DemoCrmAdapter}).
 *
 * <p>Sends the <strong>access token</strong> as the bearer credential, the conventional choice
 * for calling an API (the ID token is for identifying the user to a client, not authorizing a
 * request) - if {@code leads-crm-backend}'s resource-server config turns out to expect the ID
 * token instead, both are equally valid, signed Cognito JWTs from the same pool, so swapping
 * {@link #idToken} for {@link #accessToken} below is a one-line change, not a redesign.
 */
@Component
class CognitoServiceAuthClient {

	/** Refresh this long before actual expiry, so a request never races a token going stale
	 *  mid-flight. */
	private static final Duration REFRESH_MARGIN = Duration.ofMinutes(2);

	private final RestClient.Builder builder;
	private final LeadsCrmProperties properties;
	private final ObjectMapper objectMapper;
	private volatile RestClient http;

	private volatile String cachedToken;
	private volatile Instant cachedTokenExpiresAt = Instant.EPOCH;

	CognitoServiceAuthClient(RestClient.Builder builder, LeadsCrmProperties properties, ObjectMapper objectMapper) {
		this.builder = builder;
		this.properties = properties;
		this.objectMapper = objectMapper;
	}

	private RestClient http() {
		RestClient local = http;
		if (local == null) {
			synchronized (this) {
				local = http;
				if (local == null) {
					local = builder.baseUrl("https://cognito-idp." + properties.cognitoRegion() + ".amazonaws.com/").build();
					http = local;
				}
			}
		}
		return local;
	}

	/** The access token, refreshed automatically once it is close to expiring. */
	synchronized String accessToken() {
		if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiresAt.minus(REFRESH_MARGIN))) {
			return cachedToken;
		}

		// [CORRECTED 2026-09-18] Cognito's JSON-protocol API uses the non-standard
		// application/x-amz-json-1.1 media type on both the request and the response, which
		// Spring's default Jackson HttpMessageConverter does not declare support for - passing a
		// Map body / requesting CognitoAuthResponse.class directly failed with "No
		// HttpMessageConverter for ... and content type application/x-amz-json-1.1" the first
		// time this method was ever actually exercised end-to-end (2026-09-18, once
		// USER_PASSWORD_AUTH was enabled on the app client). Serializing/parsing through the
		// injected ObjectMapper by hand and sending/reading a plain String sidesteps content-type
		// negotiation entirely - the body is still valid JSON either way.
		String requestBody = objectMapper.writeValueAsString(Map.of(
				"AuthFlow", "USER_PASSWORD_AUTH",
				"ClientId", properties.cognitoClientId(),
				"AuthParameters", Map.of(
						"USERNAME", properties.serviceUsername(),
						"PASSWORD", properties.servicePassword())));

		String responseBody = http().post()
				.contentType(MediaType.parseMediaType("application/x-amz-json-1.1"))
				.header("X-Amz-Target", "AWSCognitoIdentityProviderService.InitiateAuth")
				.body(requestBody)
				.retrieve()
				.body(String.class);

		CognitoAuthResponse response = objectMapper.readValue(responseBody, CognitoAuthResponse.class);

		if (response == null || response.authenticationResult() == null
				|| response.authenticationResult().accessToken() == null) {
			throw new IllegalStateException("Cognito InitiateAuth returned no AuthenticationResult");
		}

		int expiresInSeconds = response.authenticationResult().expiresIn() == null
				? 3600 : response.authenticationResult().expiresIn();

		cachedToken = response.authenticationResult().accessToken();
		cachedTokenExpiresAt = Instant.now().plusSeconds(expiresInSeconds);
		return cachedToken;
	}
}
