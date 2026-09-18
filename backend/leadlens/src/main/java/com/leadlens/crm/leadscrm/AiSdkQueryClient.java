package com.leadlens.crm.leadscrm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import com.leadlens.briefing.model.CrmNarrative;
import com.leadlens.crm.leadscrm.LeadsCrmDtos.QueryRequest;
import com.leadlens.crm.leadscrm.LeadsCrmDtos.QueryResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Calls the {@code ai-query-sdk} instance's business endpoint, {@code POST /ai-sdk/query}, to
 * produce the AI_NARRATIVE section - the "power of java-sdk" surfaced in the briefing, as
 * distinct from {@link AiSdkMeetingClient}'s use of the same SDK for meeting transcripts.
 *
 * <p>The lead is always sent as one target; the project, when known, as a second one - never as
 * a relationship for the SDK to traverse, because {@code Lead.projectId} in {@code
 * leads-crm-backend} is a plain UUID column with no JPA association (that CRM's own README,
 * "Deviations §4"), so the SDK's Criteria-API traversal has nothing to walk from one to the
 * other. Two targets in the same query is the only way both reach the model.
 *
 * <p>The lead target also carries {@code phone} when known, so the SDK's WhatsApp-chat-context
 * feature can ground the narrative in that lead's WhatsApp thread. This is passed explicitly by
 * the caller ({@code LeadsCrmAdapter.narrate}) rather than left to that SDK feature's own
 * field-based resolution, because {@code mobile} is deliberately marked sensitive/not-exposed in
 * {@code provision-ai-sdk.sh} - the SDK would not find a phone number on its own, by design.
 *
 * <p>Every failure - unconfigured, unreachable, {@code 403} because the entity is not enabled,
 * {@code 502} because the summariser failed, {@code 429} rate limited - returns empty, logged at
 * WARN, never thrown: this section is supplementary, exactly like {@link AiSdkMeetingClient}'s
 * evidence (F.5's isolation principle, applied to a narrative source instead of a data source).
 */
@Component
class AiSdkQueryClient {

	private static final Logger log = LoggerFactory.getLogger(AiSdkQueryClient.class);
	private static final String SOURCE_LABEL = "CRM AI";

	private final RestClient.Builder builder;
	private final LeadsCrmProperties properties;
	private final AiSdkAuthClient auth;
	private volatile RestClient http;

	AiSdkQueryClient(RestClient.Builder builder, LeadsCrmProperties properties, AiSdkAuthClient auth) {
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
					SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
					requestFactory.setConnectTimeout(properties.aiSdkQueryTimeout());
					requestFactory.setReadTimeout(properties.aiSdkQueryTimeout());
					local = builder.baseUrl(properties.baseUrl()).requestFactory(requestFactory).build();
					http = local;
				}
			}
		}
		return local;
	}

	Optional<CrmNarrative> narrate(String leadId, Optional<String> projectId, Optional<String> phone) {
		if (!properties.aiSdkConfigured()) {
			return Optional.empty();
		}

		try {
			String token = auth.token();
			List<QueryRequest.Target> targets = new ArrayList<>();
			targets.add(new QueryRequest.Target("Lead", leadId, phone.orElse(null)));
			projectId.ifPresent(id -> targets.add(new QueryRequest.Target("Project", id, null)));

			QueryRequest request = new QueryRequest(
					properties.aiSdkQuestion(), targets,
					new QueryRequest.Options(1, 2, 20));

			QueryResponse response = http().post()
					.uri("/ai-sdk/query")
					.headers(headers -> headers.setBearerAuth(token))
					.body(request)
					.retrieve()
					.body(QueryResponse.class);

			if (response == null || response.answer() == null || response.answer().isBlank()) {
				return Optional.empty();
			}

			String model = response.meta() == null ? null : response.meta().summarizerModel();
			Instant generatedAt = parseInstant(response.meta() == null ? null : response.meta().generatedAt());
			String label = SOURCE_LABEL + " · Lead " + leadId
					+ projectId.map(id -> ", Project " + id).orElse("");

			return Optional.of(new CrmNarrative(response.answer(), model, generatedAt, label));
		} catch (RuntimeException e) {
			log.warn("ai-sdk narrative query failed for lead {}: {}", leadId, e.toString());
			return Optional.empty();
		}
	}

	private static Instant parseInstant(String value) {
		if (value == null || value.isBlank()) {
			return Instant.now();
		}
		try {
			return Instant.parse(value);
		} catch (RuntimeException e) {
			return Instant.now();
		}
	}
}
