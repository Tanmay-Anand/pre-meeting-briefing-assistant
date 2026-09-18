package com.leadlens.crm.leadscrm;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

import com.leadlens.crm.leadscrm.LeadsCrmDtos.AiSdkTokenResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Acquires and caches a JWT for the {@code ai-query-sdk} instance mounted inside
 * {@code leads-crm-backend} - a separate auth domain from {@link CognitoServiceAuthClient}, with
 * its own single admin password (see that SDK's README: "the recommended token shape is a route
 * in your own application that calls {@code POST /ai-sdk/auth/token} server-side").
 *
 * <p>Shared by every consumer of that SDK instance - {@link AiSdkMeetingClient} (meeting
 * transcripts) and {@link AiSdkQueryClient} (the CRM-wide narrative) - so the token is fetched
 * and refreshed once, not once per consumer.
 */
@Component
class AiSdkAuthClient {

	private static final Duration REFRESH_MARGIN = Duration.ofMinutes(2);

	private final RestClient.Builder builder;
	private final LeadsCrmProperties properties;
	private volatile RestClient http;

	private volatile String cachedToken;
	private volatile Instant cachedTokenExpiresAt = Instant.EPOCH;

	AiSdkAuthClient(RestClient.Builder builder, LeadsCrmProperties properties) {
		this.builder = builder;
		this.properties = properties;
	}

	private RestClient http() {
		RestClient local = http;
		if (local == null) {
			synchronized (this) {
				local = http;
				if (local == null) {
					local = builder.baseUrl(properties.baseUrl()).build();
					http = local;
				}
			}
		}
		return local;
	}

	synchronized String token() {
		if (cachedToken != null && Instant.now().isBefore(cachedTokenExpiresAt.minus(REFRESH_MARGIN))) {
			return cachedToken;
		}

		AiSdkTokenResponse response = http().post()
				.uri("/ai-sdk/auth/token")
				.body(Map.of("password", properties.aiSdkAdminPassword()))
				.retrieve()
				.body(AiSdkTokenResponse.class);

		if (response == null || response.token() == null) {
			throw new IllegalStateException("ai-sdk auth/token returned no token");
		}

		long expiresInSeconds = response.expiresIn() == null ? 3600 : response.expiresIn();
		cachedToken = response.token();
		cachedTokenExpiresAt = Instant.now().plusSeconds(expiresInSeconds);
		return cachedToken;
	}
}
