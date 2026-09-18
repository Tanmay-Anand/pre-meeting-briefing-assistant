package com.leadlens.ai;

import java.util.List;

import com.leadlens.ai.ChatDtos.ChatRequest;
import com.leadlens.ai.ChatDtos.ChatResponse;
import com.leadlens.ai.ChatDtos.Message;
import com.leadlens.ai.ChatDtos.ResponseFormat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin wrapper over OpenRouter's OpenAI-compatible chat-completions endpoint.
 *
 * <p>Deliberately not a framework: no retries baked in beyond what the caller asks for, no
 * prompt templating, no schema validation. Extraction and composition each own their own prompt
 * shape and response handling ({@code ExtractorPrompt}, {@code ComposerPrompt}); this class only
 * knows how to ask a model a question and get text back.
 *
 * <p>Every call is a single request-response round trip. There is no conversation state - each
 * evidence item is extracted independently (F.5), and the composer sees only the facts it is
 * given, never a running chat history.
 */
@Component
public class LlmClient {

	private static final Logger log = LoggerFactory.getLogger(LlmClient.class);

	private final RestClient.Builder builder;
	private final LlmProperties properties;
	private volatile RestClient http;

	public LlmClient(RestClient.Builder builder, LlmProperties properties) {
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
	 * Asks the model one question and returns its raw text response.
	 *
	 * <p>{@code jsonMode} requests {@code response_format: json_object} where the provider
	 * honours it. Not every model behind OpenRouter does, so callers must still parse
	 * defensively rather than trust the flag (see {@code JsonExtraction}).
	 *
	 * @throws LlmException on any failure - a missing key, a timeout, a non-2xx response, or an
	 *         empty completion. Callers decide what "the model failed" means for their section;
	 *         this class does not degrade on their behalf.
	 */
	public String complete(LlmProperties.ModelConfig model, String systemPrompt, String userPrompt, boolean jsonMode) {
		if (!properties.isConfigured()) {
			throw new LlmException("OPENROUTER_API_KEY is not configured");
		}
		if (model.model() == null || model.model().isBlank()) {
			throw new LlmException("No model configured for this role");
		}

		ChatRequest request = new ChatRequest(
				model.model(),
				List.of(new Message("system", systemPrompt), new Message("user", userPrompt)),
				0.1,
				jsonMode ? ResponseFormat.JSON_OBJECT : null);

		try {
			ChatResponse response = http().post()
					.uri("/chat/completions")
					.headers(this::applyAuth)
					.body(request)
					.retrieve()
					.body(ChatResponse.class);

			if (response == null || response.choices() == null || response.choices().isEmpty()) {
				throw new LlmException("Model returned no choices for " + model.model());
			}

			String content = response.choices().get(0).message().content();
			if (content == null || content.isBlank()) {
				throw new LlmException("Model returned an empty completion for " + model.model());
			}
			return content;
		} catch (RestClientException e) {
			log.warn("LLM call to {} failed: {}", model.model(), e.toString());
			throw new LlmException("LLM call failed for " + model.model(), e);
		}
	}

	private void applyAuth(HttpHeaders headers) {
		headers.setBearerAuth(properties.apiKey());
		// OpenRouter uses this to attribute usage; harmless to omit, useful to have.
		headers.set("X-Title", "LeadLens");
	}

	/** Raised whenever the model cannot be asked or does not answer usefully. */
	public static class LlmException extends RuntimeException {
		public LlmException(String message) {
			super(message);
		}

		public LlmException(String message, Throwable cause) {
			super(message, cause);
		}
	}
}
