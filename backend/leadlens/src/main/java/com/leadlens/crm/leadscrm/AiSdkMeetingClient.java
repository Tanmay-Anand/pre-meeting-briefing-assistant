package com.leadlens.crm.leadscrm;

import java.util.List;

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
 * <p>Auth is {@link AiSdkAuthClient} - a separate domain from {@link CognitoServiceAuthClient},
 * shared with {@link AiSdkQueryClient}.
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

	private final RestClient.Builder builder;
	private final LeadsCrmProperties properties;
	private final AiSdkAuthClient auth;
	private volatile RestClient http;

	AiSdkMeetingClient(RestClient.Builder builder, LeadsCrmProperties properties, AiSdkAuthClient auth) {
		this.builder = builder;
		this.properties = properties;
		this.auth = auth;
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
		if (!properties.aiSdkConfigured()) {
			return List.of();
		}

		try {
			String token = auth.token();
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
}
