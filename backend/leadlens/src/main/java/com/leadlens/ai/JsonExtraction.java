package com.leadlens.ai;

/**
 * Pulls a JSON object out of a model's raw text response.
 *
 * <p>{@code response_format: json_object} is requested on every call, but not every model
 * behind OpenRouter honours it reliably, and some wrap the object in a markdown fence
 * ({@code ```json ... ```}) regardless of instruction. Trusting the flag and parsing the raw
 * string directly would make the extractor's reliability depend on a provider's compliance
 * rather than on this system's own code - exactly the "structure beats prompting" trap
 * (IMPLEMENTATION_PLAN.md F.2). So this always defensively locates the outermost {@code {...}}
 * block before parsing.
 */
public final class JsonExtraction {

	private JsonExtraction() {
	}

	/**
	 * @return the substring from the first {@code {} to its matching close, or the trimmed input
	 *         unchanged if no brace is found - letting the caller's JSON parser produce the
	 *         actual error rather than swallowing it here
	 */
	public static String extractObject(String raw) {
		if (raw == null) {
			return null;
		}
		String trimmed = raw.strip();
		int start = trimmed.indexOf('{');
		int end = trimmed.lastIndexOf('}');
		if (start < 0 || end < start) {
			return trimmed;
		}
		return trimmed.substring(start, end + 1);
	}
}
