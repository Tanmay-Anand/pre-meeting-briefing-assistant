package com.leadlens.briefing;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
record TalkingPointsResponse(List<String> talkingPoints) {

	TalkingPointsResponse {
		talkingPoints = talkingPoints == null ? List.of() : List.copyOf(talkingPoints);
	}
}
