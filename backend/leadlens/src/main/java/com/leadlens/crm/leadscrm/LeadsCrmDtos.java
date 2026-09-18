package com.leadlens.crm.leadscrm;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Wire types for the two services this adapter talks to: {@code leads-crm-backend}'s own
 * {@code /leads} API, and the {@code ai-query-sdk} meetings API mounted inside it.
 *
 * <p>Deliberately local rather than a cross-project dependency on either service's own DTOs -
 * this adapter only needs a handful of fields from each, and the engine must not gain a compile
 * dependency on a CRM's own code (G.1).
 */
final class LeadsCrmDtos {

	private LeadsCrmDtos() {
	}

	// --- leads-crm-backend, /leads ------------------------------------------------------

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LeadResponse(
			UUID id,
			String firstName,
			String lastName,
			String mobile,
			String countryCode,
			String email,
			String propertyCategory,
			String purchaseTimeline,
			NamedRef status,
			NamedRef temperature,
			NamedRef sourceCategory,
			NamedRef sourceType,
			UUID assignedTo,
			String assignedToName,
			LocalDateTime scheduleDate,
			String notes) {

		String displayName() {
			String first = firstName == null ? "" : firstName.strip();
			String last = lastName == null ? "" : lastName.strip();
			String full = (first + " " + last).strip();
			return full.isEmpty() ? null : full;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record NamedRef(UUID id, String name, String displayName) {
		String label() {
			return (displayName != null && !displayName.isBlank()) ? displayName : name;
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record LeadsPage(List<LeadResponse> content) {
		LeadsPage {
			content = content == null ? List.of() : List.copyOf(content);
		}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record NoteResponse(
			UUID id,
			UUID leadId,
			String type,
			String body,
			String externalReference,
			UUID performedByUserId,
			String performedByUsername,
			LocalDateTime createdOn) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record NotesPage(List<NoteResponse> content) {
		NotesPage {
			content = content == null ? List.of() : List.copyOf(content);
		}
	}

	// --- AWS Cognito, InitiateAuth (USER_PASSWORD_AUTH) ---------------------------------

	@JsonIgnoreProperties(ignoreUnknown = true)
	record CognitoAuthResponse(@JsonProperty("AuthenticationResult") AuthenticationResult authenticationResult) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AuthenticationResult(
			@JsonProperty("IdToken") String idToken,
			@JsonProperty("AccessToken") String accessToken,
			@JsonProperty("ExpiresIn") Integer expiresIn) {
	}

	// --- ai-query-sdk, /ai-sdk/auth and /ai-sdk/meetings --------------------------------

	@JsonIgnoreProperties(ignoreUnknown = true)
	record AiSdkTokenResponse(String token, Long expiresIn) {
	}

	// --- ai-query-sdk, /ai-sdk/query -----------------------------------------------------

	record QueryRequest(String question, List<Target> targets, Options options) {
		// phone is optional and Lead-only (see AiSdkQueryClient.narrate) - the SDK's own
		// WhatsApp-chat-context feature (whatsapp/WhatsappContextProvider) resolves it from
		// whichever traversed target it appears on, so a Project target simply omits it.
		record Target(String entity, String id, String phone) {}

		record Options(Integer childDepth, Integer parentDepth, Integer maxChildrenPerRelation) {}
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record QueryResponse(String answer, QueryMeta meta) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record QueryMeta(String summarizerModel, String generatedAt) {}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record DiscussionResponse(
			long id,
			String meetingId,
			String leadEntity,
			String leadId,
			String meetingTitle,
			String meetingUrl,
			String discussion,
			List<Map<String, String>> participants,
			Instant occurredAt,
			Instant receivedAt,
			String matchStatus) {
	}
}
