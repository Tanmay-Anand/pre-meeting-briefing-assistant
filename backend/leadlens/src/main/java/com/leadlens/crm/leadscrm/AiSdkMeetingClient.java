package com.leadlens.crm.leadscrm;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.leadlens.crm.leadscrm.LeadsCrmDtos.AiSdkTokenResponse;
import com.leadlens.crm.leadscrm.LeadsCrmDtos.DiscussionResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Reads meeting transcripts out of the {@code ai-query-sdk} instance mounted inside
 * {@code leads-crm-backend}, so its Google Calendar + Recall.ai capture becomes one more
 * evidence source for this adapter's {@code fetchEvidence} (F.16-adjacent: this is the
 * meeting-transcripts-as-evidence integration scoped alongside F.16, not F.16 itself).
 *
 * <p>A separate auth domain from {@link CognitoServiceAuthClient} - the SDK has its own single
 * admin password and issues its own JWT (see its README: "the recommended token shape is a
 * route in your own application that calls {@code POST /ai-sdk/auth/token} server-side"). This
 * class is exactly that server-side caller.
 *
 * <p>Every failure here is swallowed and logged, never thrown: meeting transcripts are a
 * supplementary evidence source, and an adapter that fails whenever a secondary system is
 * unreachable would make the primary CRM integration only as reliable as the SDK's uptime,
 * which is not a trade worth making (F.5's isolation principle, applied to a data source
 * instead of a single extraction).
 */
@Component
class AiSdkMeetingClient {

	private static final Logger log = LoggerFactory.getLogger(AiSdkMeetingClient.class);
	private static final Duration REFRESH_MARGIN = Duration.ofMinutes(2);

	private final RestClient.Builder builder;
	private final LeadsCrmProperties properties;
	private volatile RestClient http;

	private volatile String cachedToken;
	private volatile Instant cachedTokenExpiresAt = Instant.EPOCH;

	AiSdkMeetingClient(RestClient.Builder builder, LeadsCrmProperties properties) {
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

	/**
	 * Every discussion recorded for this lead, or empty if meetings are not configured, the SDK
	 * is unreachable, or nothing has been discussed yet - all three are ordinary, not errors.
	 */
	List<DiscussionResponse> fetchDiscussions(String leadId) {
		if (!properties.meetingsConfigured()) {
			return List.of();
		}

		try {
			String token = token();
			DiscussionResponse[] response = http().get()
					.uri(uriBuilder -> uriBuilder.path("/ai-sdk/meetings/discussions")
							.queryParam("leadId", leadId)
							.build())
					.headers(headers -> headers.setBearerAuth(token))
					.retrieve()
					.body(DiscussionResponse[].class);

			return response == null ? List.of() : List.of(response);
		} catch (RuntimeException e) {
			log.warn("Could not fetch meeting discussions for lead {}: {}", leadId, e.toString());
			return List.of();
		}
	}

	private synchronized String token() {
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
