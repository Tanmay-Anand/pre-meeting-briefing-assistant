package com.leadlens.ai;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * LLM configuration, bound to {@code leadlens.ai.*}.
 *
 * <p>Extractor and composer are configured separately, per IMPLEMENTATION_PLAN.md F.15:
 * extraction is high-volume and schema-bound, composition is low-volume and needs judgement
 * about relevance and ordering, so the two are meant to scale independently even though the
 * current testing configuration (Appendix 2 Q8) happens to point both at the same model.
 * Swapping one later is a config change here, not a code change.
 *
 * <p>The key comes from the environment - {@code OPENROUTER_API_KEY} - never hardcoded, never
 * logged, and never shipped anywhere near the extension bundle (I.4).
 *
 * @param baseUrl  the OpenRouter chat-completions base URL
 * @param apiKey   the OpenRouter API key
 * @param extractor per-role model config for fact extraction (Phase 3)
 * @param composer  per-role model config for composition (Phase 4)
 */
@ConfigurationProperties(prefix = "leadlens.ai")
public record LlmProperties(String baseUrl, String apiKey, ModelConfig extractor, ModelConfig composer) {

	public LlmProperties {
		baseUrl = (baseUrl == null || baseUrl.isBlank()) ? "https://openrouter.ai/api/v1" : baseUrl;
		extractor = extractor == null ? new ModelConfig(null, null) : extractor;
		composer = composer == null ? new ModelConfig(null, null) : composer;
	}

	/**
	 * @param model   the OpenRouter model slug, e.g. {@code "google/gemma-4-26b-a4b"}. Confirm
	 *                the exact slug against OpenRouter's catalog - ids change (Appendix 2 Q8).
	 * @param timeout per-request timeout; a slow model must not hang the extraction loop
	 */
	public record ModelConfig(String model, Duration timeout) {
		public ModelConfig {
			timeout = timeout == null ? Duration.ofSeconds(45) : timeout;
		}
	}

	public boolean isConfigured() {
		return apiKey != null && !apiKey.isBlank();
	}
}
