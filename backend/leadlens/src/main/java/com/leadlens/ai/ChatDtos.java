package com.leadlens.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * The slice of the OpenAI-compatible chat-completions schema LeadLens actually uses.
 *
 * <p>OpenRouter proxies many providers behind one shape; {@code @JsonIgnoreProperties} is not
 * tidiness here, it is what stops an unrelated field a provider adds tomorrow from breaking
 * deserialization today.
 */
final class ChatDtos {

	private ChatDtos() {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ChatRequest(
			String model,
			List<Message> messages,
			double temperature,
			ResponseFormat response_format) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Message(String role, String content) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ResponseFormat(String type) {
		static final ResponseFormat JSON_OBJECT = new ResponseFormat("json_object");
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record ChatResponse(List<Choice> choices) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	record Choice(Message message) {
	}
}
