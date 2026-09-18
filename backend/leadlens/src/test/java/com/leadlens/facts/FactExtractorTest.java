package com.leadlens.facts;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.leadlens.ai.LlmClient;
import com.leadlens.ai.LlmProperties;
import com.leadlens.evidence.Actor;
import com.leadlens.evidence.Channel;
import com.leadlens.evidence.EvidenceItem;
import com.leadlens.evidence.EvidenceRepository;
import com.leadlens.evidence.EvidenceType;
import com.leadlens.evidence.SourceMode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link FactExtractor} is the one place that talks to the model, so its failure modes are
 * expensive: a broken cache re-bills every refresh, and a broken supersession check either loses
 * history or lets a stale value keep rendering as current.
 *
 * <h2>What each case maps to</h2>
 * <ul>
 *   <li>{@link #ensureExtractedDoesNotReextractACachedItem()} is the extraction cache
 *       (IMPLEMENTATION_PLAN.md F.4, C5): {@code findByEvidenceIdAndExtractorVersion} returning a
 *       hit must short-circuit before the model is ever asked, or "one extraction per new
 *       activity" quietly becomes "one extraction per refresh" and the whole incremental-refresh
 *       argument stops being true. The {@link LlmClient} collaborator here throws if invoked at
 *       all, so a regression fails loudly instead of merely running slow.</li>
 *   <li>{@link #supersedeEarlierFactsMarksTheOlderOneSupersededAndKeepsBoth()} is F.14: a later
 *       OPEN fact for the same {@code attributeKey} must flip the earlier one to SUPERSEDED, not
 *       delete or overwrite it, because the change itself ("2BHK -> 3BHK") is what the agent
 *       needs, not just the latest value.</li>
 *   <li>{@link #nullTextProducesNoFactsAndNeverInventsOne()} is E.5: a call, message or note with
 *       {@code text = null} is a real gap (an unread recording, an untranscribed note) and must
 *       surface as "nothing extracted here", never as a fabricated claim standing in for the
 *       missing text.</li>
 * </ul>
 *
 * <p>{@code FactRepository} and {@code EvidenceRepository} are plain Spring Data interfaces with
 * no logic of their own to protect, so they are mocked. {@link LlmClient} is the one collaborator
 * that does real network I/O; rather than mock it, each test below gives it a hand-built
 * subclass that either throws (proving it was never called) or returns a canned response body -
 * no real request ever leaves the process.
 */
class FactExtractorTest {

	private static final String TENANT = "t-acme";
	private static final String LEAD_REF = "12345";

	private final ObjectMapper objectMapper = JsonMapper.builder().build();

	@Test
	@DisplayName("ensureExtracted() returns the cached facts and never calls the model for an "
			+ "item already extracted under the current extractorVersion")
	void ensureExtractedDoesNotReextractACachedItem() {
		EvidenceItem item = evidence(EvidenceType.NOTE, "Customer called back about pricing")
				.toBuilder()
				.factsExtractedVersion(FactExtractor.EXTRACTOR_VERSION)
				.build();

		AtomicFact cached = AtomicFact.builder()
				.evidenceId(item.getId())
				.tenantId(TENANT)
				.leadRef(LEAD_REF)
				.kind(FactKind.OBJECTION)
				.claim("Feels the pricing is above budget")
				.provenance(Provenance.INFERRED)
				.status(FactStatus.OPEN)
				.occurredAt(item.getOccurredAt())
				.extractorVersion(FactExtractor.EXTRACTOR_VERSION)
				.build();

		FactRepository factRepository = mock(FactRepository.class);
		when(factRepository.findByEvidenceIdAndExtractorVersion(item.getId(), FactExtractor.EXTRACTOR_VERSION))
				.thenReturn(List.of(cached));

		EvidenceRepository evidenceRepository = mock(EvidenceRepository.class);

		FactExtractor extractor = new FactExtractor(
				factRepository,
				evidenceRepository,
				explodingLlmClient(),
				configuredLlmProperties(),
				objectMapper);

		List<AtomicFact> result = extractor.ensureExtracted(item);

		assertThat(result)
				.as("a cache hit must return exactly what was cached, not re-derive it")
				.containsExactly(cached);

		// No verify(mock, never())... needed: the exploding LlmClient above already fails the
		// test with an AssertionError the moment complete() is invoked. This assertion documents
		// that intent for a reader who has not seen explodingLlmClient() yet.
		verify(factRepository, never()).saveAll(anyList());
		verify(evidenceRepository, never()).save(any());
	}

	@Test
	@DisplayName("supersedeEarlierFacts(): a later OPEN fact sharing an attributeKey marks the "
			+ "earlier one SUPERSEDED, and both rows are retained")
	void supersedeEarlierFactsMarksTheOlderOneSupersededAndKeepsBoth() {
		EvidenceItem item = evidence(EvidenceType.MESSAGE, "Actually we now need 3BHK, not 2BHK")
				.toBuilder()
				.occurredAt(Instant.parse("2026-09-14T11:40:00Z"))
				.build();

		AtomicFact earlierOpenFact = AtomicFact.builder()
				.evidenceId(UUID.randomUUID())
				.tenantId(TENANT)
				.leadRef(LEAD_REF)
				.kind(FactKind.REQUIREMENT)
				.claim("Looking for a 2BHK")
				.provenance(Provenance.INFERRED)
				.status(FactStatus.OPEN)
				.attributeKey("bhk")
				.normalizedValue("2BHK")
				.occurredAt(Instant.parse("2026-06-01T10:00:00Z"))
				.extractorVersion(FactExtractor.EXTRACTOR_VERSION)
				.build();

		// Stands in for the facts table: saveAll() "persists" the newly extracted fact into this
		// list, and the attributeKey lookup that supersedeEarlierFacts() performs afterwards
		// reads back from the same list - exactly what a real repository would do, without
		// needing to hand-implement the rest of JpaRepository.
		List<AtomicFact> persisted = new ArrayList<>(List.of(earlierOpenFact));

		FactRepository factRepository = mock(FactRepository.class);
		when(factRepository.findByEvidenceIdAndExtractorVersion(item.getId(), FactExtractor.EXTRACTOR_VERSION))
				.thenReturn(List.of());
		when(factRepository.saveAll(anyList())).thenAnswer(invocation -> {
			List<AtomicFact> saved = invocation.getArgument(0);
			persisted.addAll(saved);
			return saved;
		});
		when(factRepository.findByTenantIdAndLeadRefAndAttributeKeyOrderByOccurredAtAsc(TENANT, LEAD_REF, "bhk"))
				.thenAnswer(invocation -> new ArrayList<>(persisted));

		EvidenceRepository evidenceRepository = mock(EvidenceRepository.class);

		String cannedResponse = """
				{"facts": [{
					"kind": "REQUIREMENT",
					"claim": "Now wants a 3BHK instead of 2BHK",
					"attributeKey": "bhk",
					"rawValue": "3BHK",
					"status": "OPEN",
					"confidence": 0.9
				}]}
				""";

		FactExtractor extractor = new FactExtractor(
				factRepository,
				evidenceRepository,
				cannedLlmClient(cannedResponse),
				configuredLlmProperties(),
				objectMapper);

		List<AtomicFact> extracted = extractor.ensureExtracted(item);

		assertThat(extracted).hasSize(1);
		AtomicFact newFact = extracted.get(0);
		assertThat(newFact.getAttributeKey()).isEqualTo("bhk");
		assertThat(newFact.getStatus())
				.as("the new fact is what is current now")
				.isEqualTo(FactStatus.OPEN);

		assertThat(earlierOpenFact.getStatus())
				.as("F.14: a later fact for the same attribute supersedes the earlier one instead "
						+ "of the earlier one silently staying OPEN forever")
				.isEqualTo(FactStatus.SUPERSEDED);

		assertThat(persisted)
				.as("F.14: retained, never deleted - both the old and the new value must still "
						+ "be reachable so the change itself can be shown")
				.containsExactlyInAnyOrder(earlierOpenFact, newFact);
		verify(factRepository, never()).deleteByEvidenceIdAndExtractorVersion(any(), any());
	}

	@Test
	@DisplayName("a call/message/note with text = null produces zero facts, never an invented one")
	void nullTextProducesNoFactsAndNeverInventsOne() {
		EvidenceItem item = EvidenceItem.builder()
				.tenantId(TENANT)
				.crmKey("demo")
				.leadRef(LEAD_REF)
				.evidenceKey("call:88213")
				.type(EvidenceType.CALL)
				.occurredAt(Instant.parse("2026-09-14T06:10:00Z"))
				.actor(Actor.CUSTOMER)
				.channel(Channel.PHONE)
				.text(null)
				.sourceMode(SourceMode.CRM_API)
				.updatedAt(Instant.parse("2026-09-14T06:10:00Z"))
				.build();

		FactRepository factRepository = mock(FactRepository.class);
		when(factRepository.findByEvidenceIdAndExtractorVersion(item.getId(), FactExtractor.EXTRACTOR_VERSION))
				.thenReturn(List.of());
		when(factRepository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

		EvidenceRepository evidenceRepository = mock(EvidenceRepository.class);

		FactExtractor extractor = new FactExtractor(
				factRepository,
				evidenceRepository,
				explodingLlmClient(),
				configuredLlmProperties(),
				objectMapper);

		List<AtomicFact> result = extractor.ensureExtracted(item);

		assertThat(result)
				.as("E.5: a call with a recording but no transcript is a real gap the agent "
						+ "should be told about via Missing Information - it must never be papered "
						+ "over with a fact nobody actually said")
				.isEmpty();

		assertThat(item.getFactsExtractedVersion())
				.as("F.4: zero facts is still a cacheable answer, so this item is not resent to "
						+ "the model on every future refresh")
				.isEqualTo(FactExtractor.EXTRACTOR_VERSION);
	}

	private static EvidenceItem evidence(EvidenceType type, String text) {
		return EvidenceItem.builder()
				.tenantId(TENANT)
				.crmKey("demo")
				.leadRef(LEAD_REF)
				.evidenceKey(type.name().toLowerCase() + ":" + UUID.randomUUID())
				.type(type)
				.occurredAt(Instant.parse("2026-09-14T11:40:00Z"))
				.actor(Actor.CUSTOMER)
				.channel(Channel.WHATSAPP)
				.text(text)
				.structured(Map.of())
				.sourceMode(SourceMode.CRM_API)
				.updatedAt(Instant.parse("2026-09-14T11:40:00Z"))
				.build();
	}

	private static LlmProperties configuredLlmProperties() {
		return new LlmProperties(
				null,
				"test-key",
				new LlmProperties.ModelConfig("test/extractor-model", null),
				new LlmProperties.ModelConfig("test/composer-model", null));
	}

	/**
	 * A real {@link LlmClient} whose {@code complete()} is overridden to throw rather than ever
	 * make a request. Preferred over {@code Mockito.mock(LlmClient.class)} because a genuine
	 * subclass makes "this must not be called" a compile-time-checked override rather than a
	 * mock-framework stub someone could forget to verify.
	 */
	private static LlmClient explodingLlmClient() {
		return new LlmClient(RestClient.builder(), configuredLlmProperties()) {
			@Override
			public String complete(LlmProperties.ModelConfig model, String systemPrompt, String userPrompt,
					boolean jsonMode) {
				throw new AssertionError("LlmClient.complete() must not be called for a cached item (F.4)");
			}
		};
	}

	/** A real {@link LlmClient} that returns a fixed response body instead of making a request. */
	private static LlmClient cannedLlmClient(String responseBody) {
		return new LlmClient(RestClient.builder(), configuredLlmProperties()) {
			@Override
			public String complete(LlmProperties.ModelConfig model, String systemPrompt, String userPrompt,
					boolean jsonMode) {
				return responseBody;
			}
		};
	}
}
