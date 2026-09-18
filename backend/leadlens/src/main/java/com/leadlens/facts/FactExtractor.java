package com.leadlens.facts;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.leadlens.ai.LlmClient;
import com.leadlens.ai.LlmProperties;
import com.leadlens.evidence.Actor;
import com.leadlens.evidence.EvidenceItem;
import com.leadlens.evidence.EvidenceRepository;
import com.leadlens.evidence.EvidenceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

/**
 * Turns one evidence item into zero or more {@link AtomicFact}s, cached forever.
 *
 * <p>Every item, not a selected subset - selection happens later, over facts, not over raw
 * evidence (D.4). This is what keeps a four-month-old unresolved objection reachable no matter
 * how much has happened since: extraction never decides what is relevant, only what is true.
 *
 * <h2>Which items call a model</h2>
 * {@link EvidenceType#CALL}, {@link EvidenceType#MESSAGE}, {@link EvidenceType#NOTE},
 * {@link EvidenceType#MEETING}, {@link EvidenceType#SITE_VISIT} and
 * {@link EvidenceType#DOCUMENT} carry freeform text that only a model can read. The rest -
 * {@link EvidenceType#TASK}, {@link EvidenceType#PROPERTY_SHARED},
 * {@link EvidenceType#STATUS_CHANGE}, {@link EvidenceType#FIELD_UPDATE} - are administrative
 * records whose meaning is already in their structured fields, and are projected deterministically
 * instead (Phase 3 step 4). Zero model calls for roughly half of a typical lead's history, before
 * caching even enters into it.
 *
 * <h2>Per-item transactions</h2>
 * Each item commits independently in its own transaction ({@code REQUIRES_NEW}, F.5). A crash or
 * a bad response mid-run loses one item's facts, not the whole run, and a restart simply
 * re-extracts whatever has no cached result.
 */
@Service
public class FactExtractor {

	private static final Logger log = LoggerFactory.getLogger(FactExtractor.class);

	/** Bumping this forces re-extraction of every item, deliberately - see {@link EvidenceItem}. */
	public static final String EXTRACTOR_VERSION = "v1";

	private static final Set<EvidenceType> MODEL_BACKED = EnumSet.of(
			EvidenceType.CALL, EvidenceType.MESSAGE, EvidenceType.NOTE,
			EvidenceType.MEETING, EvidenceType.SITE_VISIT, EvidenceType.DOCUMENT);

	/**
	 * Attribute-mapped kinds: when a new one lands, any earlier OPEN fact for the same attribute
	 * is superseded, because only the latest is ever the current value (F.14).
	 */
	private static final Set<FactKind> ATTRIBUTE_MAPPED = EnumSet.of(
			FactKind.REQUIREMENT, FactKind.PREFERENCE, FactKind.BUDGET_STATEMENT,
			FactKind.TIMELINE_STATEMENT, FactKind.FINANCING_NEED, FactKind.DECISION_MAKER);

	private final FactRepository factRepository;
	private final EvidenceRepository evidenceRepository;
	private final LlmClient llmClient;
	private final LlmProperties llmProperties;
	private final ObjectMapper objectMapper;

	public FactExtractor(
			FactRepository factRepository,
			EvidenceRepository evidenceRepository,
			LlmClient llmClient,
			LlmProperties llmProperties,
			ObjectMapper objectMapper) {
		this.factRepository = factRepository;
		this.evidenceRepository = evidenceRepository;
		this.llmClient = llmClient;
		this.llmProperties = llmProperties;
		this.objectMapper = objectMapper;
	}

	/**
	 * Returns this item's facts, extracting them first if this version has not run on it yet.
	 *
	 * <p>A malformed or failed model response drops this item's facts and leaves it unmarked, so
	 * the next run retries it - it never fails the caller, and it never poisons the cache with a
	 * result that was actually a failure (Phase 3 step 7).
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public List<AtomicFact> ensureExtracted(EvidenceItem item) {
		if (EXTRACTOR_VERSION.equals(item.getFactsExtractedVersion())) {
			return factRepository.findByEvidenceIdAndExtractorVersion(item.getId(), EXTRACTOR_VERSION);
		}

		List<AtomicFact> facts;
		try {
			facts = extract(item);
		} catch (LlmClient.LlmException e) {
			log.warn("Extraction failed for evidence {} ({}): {}", item.getId(), item.getType(), e.getMessage());
			return List.of();
		}

		factRepository.saveAll(facts);
		supersedeEarlierFacts(facts);

		item.setFactsExtractedVersion(EXTRACTOR_VERSION);
		evidenceRepository.save(item);

		return facts;
	}

	private List<AtomicFact> extract(EvidenceItem item) {
		if (item.hasNoText()) {
			// A call with a recording but no transcript is a real gap, not nothing - it surfaces
			// in Missing Information instead (E.5). Nothing to extract from here either way.
			return List.of();
		}

		return switch (item.getType()) {
			case TASK -> List.of(taskAsCommitment(item));
			case PROPERTY_SHARED -> List.of(propertyAwaitingFeedback(item));
			case STATUS_CHANGE, FIELD_UPDATE ->
					// Already fully represented by the live field snapshot and the journey
					// timeline (both deterministic); a fact here would be a redundant surface for
					// the same information to drift from its source.
					List.of();
			default -> {
				if (!MODEL_BACKED.contains(item.getType())) {
					yield List.of();
				}
				yield extractWithModel(item);
			}
		};
	}

	private List<AtomicFact> extractWithModel(EvidenceItem item) {
		if (!llmProperties.isConfigured()) {
			// Not a hard failure of this item - the deterministic half of the briefing must still
			// ship (D.2). The caller sees an empty, uncached result and will retry once a key is
			// configured.
			log.debug("Skipping model extraction for {}: no API key configured", item.getId());
			return List.of();
		}

		String raw = llmClient.complete(
				llmProperties.extractor(),
				ExtractorPrompt.SYSTEM,
				ExtractorPrompt.userPrompt(item),
				true);

		ExtractionResponse response = parse(raw, item);
		return SchemaValidator.validate(item, response.facts(), EXTRACTOR_VERSION);
	}

	private ExtractionResponse parse(String raw, EvidenceItem item) {
		try {
			String jsonObject = com.leadlens.ai.JsonExtraction.extractObject(raw);
			return objectMapper.readValue(jsonObject, ExtractionResponse.class);
		} catch (Exception e) {
			log.warn("Could not parse extractor output for evidence {}: {}", item.getId(), e.getMessage());
			return new ExtractionResponse(List.of());
		}
	}

	/** A task is an agent- or customer-authored record of a promise; no interpretation needed. */
	private AtomicFact taskAsCommitment(EvidenceItem item) {
		FactKind kind = item.getActor() == Actor.CUSTOMER
				? FactKind.COMMITMENT_CUSTOMER
				: FactKind.COMMITMENT_AGENT;
		boolean open = !"RESOLVED".equalsIgnoreCase(String.valueOf(item.getStructured().get("state")));

		return AtomicFact.builder()
				.evidenceId(item.getId())
				.tenantId(item.getTenantId())
				.leadRef(item.getLeadRef())
				.kind(kind)
				.claim(item.getText().strip())
				.provenance(Provenance.CRM_ACTIVITY)
				.status(open ? FactStatus.OPEN : FactStatus.RESOLVED)
				.occurredAt(item.getOccurredAt())
				.extractorVersion(EXTRACTOR_VERSION)
				.build();
	}

	/** A shared property is "awaiting feedback" until a later record states the customer's response. */
	private AtomicFact propertyAwaitingFeedback(EvidenceItem item) {
		Object projectId = item.getStructured().get("projectId");
		return AtomicFact.builder()
				.evidenceId(item.getId())
				.tenantId(item.getTenantId())
				.leadRef(item.getLeadRef())
				.kind(FactKind.PROPERTY_RESPONSE)
				.claim(item.getText().strip())
				.provenance(Provenance.CRM_ACTIVITY)
				.subjectRef(projectId == null ? null : String.valueOf(projectId))
				.polarity(Polarity.NEUTRAL)
				.status(FactStatus.OPEN)
				.occurredAt(item.getOccurredAt())
				.extractorVersion(EXTRACTOR_VERSION)
				.build();
	}

	/**
	 * Marks any earlier OPEN fact for the same attribute superseded - retained, never deleted,
	 * because the change itself is more useful to the agent than either value alone (F.14).
	 */
	private void supersedeEarlierFacts(List<AtomicFact> newFacts) {
		for (AtomicFact newFact : newFacts) {
			if (!ATTRIBUTE_MAPPED.contains(newFact.getKind()) || newFact.getAttributeKey() == null) {
				continue;
			}

			List<AtomicFact> sameAttribute = factRepository
					.findByTenantIdAndLeadRefAndAttributeKeyOrderByOccurredAtAsc(
							newFact.getTenantId(), newFact.getLeadRef(), newFact.getAttributeKey());

			for (AtomicFact existing : sameAttribute) {
				boolean isThisFact = existing.getFactId().equals(newFact.getFactId());
				boolean isOlderOrSameMoment = !existing.getOccurredAt().isAfter(newFact.getOccurredAt());

				if (!isThisFact && isOlderOrSameMoment && existing.getStatus() == FactStatus.OPEN) {
					existing.setStatus(FactStatus.SUPERSEDED);
					factRepository.save(existing);
				}
			}
		}
	}
}
