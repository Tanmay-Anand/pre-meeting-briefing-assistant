package com.leadlens.briefing.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.leadlens.facts.Provenance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Every type stored in a {@code jsonb} column must survive a serialize/deserialize round trip.
 *
 * <p>This exists because of a real failure. {@link BriefingEntry} had a derived
 * {@code isCited()} accessor; Jackson treats {@code isX()} as a property and wrote it out as
 * {@code "cited"}, which the record's canonical constructor then refused to read back.
 * Hibernate round-trips JSON-mapped values on every save, so the break surfaced at <em>write</em>
 * time with a message about <em>deserialization</em>, from inside a Spring proxy chain, and only
 * in an integration test that needs Docker and a working web server. It cost a CI cycle to find.
 *
 * <p>These assertions cost milliseconds and need neither. Add any new persisted JSON type here.
 */
class PersistedJsonRoundTripTest {

	/**
	 * Jackson 3 (tools.jackson), configured to match Hibernate's format mapper.
	 *
	 * <p>{@code FAIL_ON_UNKNOWN_PROPERTIES} is the important part and is not the default in
	 * Jackson 3. Hibernate's {@code Jackson3JsonFormatMapper} does fail on unknown properties -
	 * that is precisely how the original bug surfaced - so a lenient mapper here would happily
	 * round-trip a value that the database layer rejects, and these tests would pass while
	 * production broke. Verified by removing the fix and watching the round trip fail.
	 */
	private final ObjectMapper mapper = JsonMapper.builder()
			.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
			.build();

	@Test
	@DisplayName("a fully populated entry survives the round trip Hibernate performs on save")
	void briefingEntryRoundTrips() {
		BriefingEntry original = new BriefingEntry(
				"Budget",
				"Customer feels the current pricing is above their budget.",
				Provenance.CRM_ACTIVITY,
				EntryFlag.CONTRADICTED,
				List.of(new SourceRef(
						UUID.randomUUID(),
						"call:88213",
						"Call note - 14 September",
						Instant.parse("2026-09-14T06:10:00Z"),
						"http://localhost:5174/leads/12345/activities/88213",
						"pricing is above their budget")));

		String json = mapper.writeValueAsString(original);

		assertThat(mapper.readValue(json, BriefingEntry.class))
				.as("if this fails, something on the record serialises to a property the "
						+ "canonical constructor cannot accept - usually a derived isX()/getX() "
						+ "accessor that needs @JsonIgnore")
				.isEqualTo(original);
	}

	@Test
	@DisplayName("an entry with no sources round trips too")
	void entryWithoutSourcesRoundTrips() {
		BriefingEntry original = BriefingEntry.flagged(
				"Decision maker", "Not recorded", Provenance.CRM_FIELD, EntryFlag.NEVER_CAPTURED);

		String json = mapper.writeValueAsString(original);

		assertThat(mapper.readValue(json, BriefingEntry.class)).isEqualTo(original);
	}

	@Test
	@DisplayName("derived accessors stay out of the stored JSON")
	void derivedAccessorsAreNotSerialised() {
		String json = mapper.writeValueAsString(
				BriefingEntry.field("Budget", "Rs 1.5-1.8 Cr", Provenance.CRM_FIELD));

		// Naming the property explicitly, so a future rename that reintroduces the problem
		// under a different name is still caught by the round-trip tests above.
		assertThat(json)
				.as("isCited() is derived from sources and must not be persisted")
				.doesNotContain("\"cited\"");
	}

	@Test
	void sourceRefRoundTrips() {
		SourceRef original = new SourceRef(
				UUID.randomUUID(),
				"note:4410",
				"Note - CRM",
				Instant.parse("2026-09-16T13:30:00Z"),
				"http://localhost:5174/leads/12345/activities/4410",
				"Wants pricing for 1BHK too.");

		String json = mapper.writeValueAsString(original);

		assertThat(mapper.readValue(json, SourceRef.class)).isEqualTo(original);
	}

	@Test
	@DisplayName("a list of entries round trips, which is the actual stored column type")
	void listOfEntriesRoundTrips() {
		// briefing_sections.entries is List<BriefingEntry>; the generic type is what Hibernate
		// hands the format mapper, so assert on the collection rather than a single value.
		List<BriefingEntry> original = List.of(
				BriefingEntry.field("Customer", "Rahul Sharma", Provenance.CRM_FIELD),
				BriefingEntry.flagged("Budget", "Hidden for your role",
						Provenance.CRM_FIELD, EntryFlag.MASKED));

		String json = mapper.writeValueAsString(original);

		assertThat(mapper.readValue(json, new tools.jackson.core.type.TypeReference<List<BriefingEntry>>() {
		})).isEqualTo(original);
	}
}
