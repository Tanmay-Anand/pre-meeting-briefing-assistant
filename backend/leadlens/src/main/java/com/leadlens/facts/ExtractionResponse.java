package com.leadlens.facts;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** The extractor's response envelope: zero or more candidate facts for one evidence item. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ExtractionResponse(List<ExtractedFact> facts) {

	public ExtractionResponse {
		facts = facts == null ? List.of() : List.copyOf(facts);
	}
}
