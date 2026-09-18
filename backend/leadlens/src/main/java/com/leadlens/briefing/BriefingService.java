package com.leadlens.briefing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.leadlens.ai.LlmProperties;
import com.leadlens.briefing.model.ProjectedSection;
import com.leadlens.briefing.model.RenderState;
import com.leadlens.common.clock.BriefingClock;
import com.leadlens.common.tenant.ActingUser;
import com.leadlens.crm.CrmAdapter;
import com.leadlens.crm.CrmAdapterRegistry;
import com.leadlens.crm.model.LeadRef;
import com.leadlens.crm.model.LeadSnapshot;
import com.leadlens.crm.model.ScheduledActivity;
import com.leadlens.evidence.EvidenceFingerprint;
import com.leadlens.evidence.EvidenceItem;
import com.leadlens.evidence.EvidenceRepository;
import com.leadlens.facts.AtomicFact;
import com.leadlens.facts.FactExtractor;
import com.leadlens.facts.FactRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Assembles and stores briefings.
 *
 * <p>Phase 2 scope: the deterministic half only. Extraction (Phase 3) and composition
 * (Phase 4) add the model-backed sections; until then a briefing is produced with zero model
 * calls and is still genuinely useful - Customer Snapshot, Meeting Context, Missing
 * Information, the journey and every source reference. That is not a placeholder, it is the
 * degraded mode the system is required to have (IMPLEMENTATION_PLAN.md D.2, F.1 rung 5).
 */
@Service
public class BriefingService {

	private static final Logger log = LoggerFactory.getLogger(BriefingService.class);

	private final CrmAdapterRegistry adapters;
	private final EvidenceRepository evidenceRepository;
	private final FactRepository factRepository;
	private final BriefingRepository briefingRepository;
	private final BriefingSectionRepository sectionRepository;
	private final BriefingClock clock;
	private final FactExtractor factExtractor;
	private final TalkingPointsComposer talkingPointsComposer;
	private final LlmProperties llmProperties;

	public BriefingService(
			CrmAdapterRegistry adapters,
			EvidenceRepository evidenceRepository,
			FactRepository factRepository,
			BriefingRepository briefingRepository,
			BriefingSectionRepository sectionRepository,
			BriefingClock clock,
			FactExtractor factExtractor,
			TalkingPointsComposer talkingPointsComposer,
			LlmProperties llmProperties) {
		this.adapters = adapters;
		this.evidenceRepository = evidenceRepository;
		this.factRepository = factRepository;
		this.briefingRepository = briefingRepository;
		this.sectionRepository = sectionRepository;
		this.clock = clock;
		this.factExtractor = factExtractor;
		this.talkingPointsComposer = talkingPointsComposer;
		this.llmProperties = llmProperties;
	}

	/**
	 * Fetches from the CRM, normalises, and stores the evidence for this lead.
	 *
	 * <p>Evidence is upserted by its CRM-side key so re-running is safe and the internal id
	 * stays stable. That stability is load-bearing: cached facts cite evidence ids, and a
	 * re-fetch that minted new ids would silently invalidate every extraction ever done for
	 * this lead.
	 */
	@Transactional
	public List<EvidenceItem> syncEvidence(LeadRef ref, ActingUser user) {
		CrmAdapter adapter = adapters.require(ref.crmKey());
		List<EvidenceItem> fetched = adapter.fetchEvidence(ref, user, null);

		return fetched.stream().map(incoming -> {
			Optional<EvidenceItem> existing = evidenceRepository
					.findByTenantIdAndCrmKeyAndEvidenceKey(
							user.tenantId(), ref.crmKey(), incoming.getEvidenceKey());

			if (existing.isEmpty()) {
				return evidenceRepository.save(incoming);
			}

			EvidenceItem stored = existing.get();
			stored.setText(incoming.getText());
			stored.setStructured(incoming.getStructured());
			stored.setOccurredAt(incoming.getOccurredAt());
			stored.setDeepLink(incoming.getDeepLink());
			stored.setUpdatedAt(incoming.getUpdatedAt());
			return evidenceRepository.save(stored);
		}).toList();
	}

	/** Assembles the inputs, permission-filtered, before anything downstream sees them (F.7). */
	@Transactional
	public BriefingContext buildContext(LeadRef ref, ActingUser user) {
		CrmAdapter adapter = adapters.require(ref.crmKey());

		LeadSnapshot lead = adapter.fetchLead(ref, user);
		List<EvidenceItem> evidence = syncEvidence(ref, user);
		List<ScheduledActivity> upcoming = adapter.fetchUpcoming(ref, user);
		List<AtomicFact> facts = factRepository
				.findByTenantIdAndLeadRefOrderByOccurredAtAsc(user.tenantId(), ref.leadRef());

		return new BriefingContext(ref, user, lead, evidence, upcoming, facts);
	}

	/**
	 * Syncs evidence and ensures every item has been through extraction, returning a context
	 * whose facts are current.
	 *
	 * <p>This is what makes the F.16 field-sync check live: {@code GET /api/briefings/latest}
	 * calls this - not {@link #generateFull} - so a field/fact contradiction is detectable the
	 * moment the lead is opened, independent of whether a full briefing run has ever completed.
	 * Extraction is still cached per item, so calling this on every page load costs nothing once
	 * a lead's evidence has already been read.
	 *
	 * <p><strong>Deliberately not {@code @Transactional}.</strong> {@link #buildContext} persists
	 * newly-synced {@code EvidenceItem} rows, and {@link FactExtractor#ensureExtracted} runs in
	 * its own {@code REQUIRES_NEW} transaction per item (F.5) - both self-invoked from here, so an
	 * ambient transaction on this method would never let the sync commit before extraction tried
	 * to see it. On a lead with no evidence_items rows yet, that made every REQUIRES_NEW insert
	 * block forever on the still-open sync transaction's uncommitted row: a same-thread deadlock
	 * with no timeout, found by actually booting this against a real Postgres for the first time
	 * (2026-09-18) rather than Testcontainers/unit tests, which never exercised a cold sync. Each
	 * step below now commits on its own via Spring Data's per-method transactions.
	 */
	public ExtractedContext ensureExtractedContext(LeadRef ref, ActingUser user, RunProgressListener progress) {
		BriefingContext context = buildContext(ref, user);

		int total = context.evidence().size();
		int completed = 0;
		boolean extractionComplete = true;
		for (EvidenceItem item : context.evidence()) {
			try {
				factExtractor.ensureExtracted(item);
			} catch (RuntimeException e) {
				// One item's extraction failing must cost that item, never the run (F.5). It
				// stays unmarked, so the next generation retries it.
				log.warn("Extraction threw for evidence {}: {}", item.getId(), e.toString());
			}
			if (!FactExtractor.EXTRACTOR_VERSION.equals(item.getFactsExtractedVersion())) {
				extractionComplete = false;
			}
			completed++;
			progress.onProgress(completed, total, "Extracted " + item.getType() + " from " + item.getOccurredAt());
		}

		List<AtomicFact> facts = factRepository
				.findByTenantIdAndLeadRefOrderByOccurredAtAsc(user.tenantId(), ref.leadRef());
		BriefingContext enriched = new BriefingContext(
				context.ref(), context.user(), context.lead(), context.evidence(), context.upcoming(), facts);

		return new ExtractedContext(enriched, extractionComplete);
	}

	public record ExtractedContext(BriefingContext context, boolean extractionComplete) {
	}

	/**
	 * Generates and stores a briefing using only the deterministic sections.
	 *
	 * <p>Marked {@link BriefingStatus#DEGRADED} rather than COMPLETE, because the model-backed
	 * sections genuinely are not there. Calling it complete would be the "existence is not
	 * generation" mistake in miniature: the document would look finished while several sections
	 * had never been attempted (F.9).
	 */
	@Transactional
	public Briefing generateDeterministic(LeadRef ref, ActingUser user) {
		Instant now = clock.now();
		BriefingContext context = buildContext(ref, user);

		Briefing briefing = briefingRepository.save(Briefing.builder()
				.tenantId(user.tenantId())
				.crmKey(ref.crmKey())
				.leadRef(ref.leadRef())
				.activityId(context.nextActivity().map(ScheduledActivity::activityId).orElse(null))
				.generatedFor(user.userId())
				.evidenceFingerprint(EvidenceFingerprint.of(context.evidence()))
				.status(BriefingStatus.DEGRADED)
				.createdAt(now)
				.build());

		for (ProjectedSection section : DeterministicProjector.project(context, now)) {
			saveSection(briefing.getId(), section);
		}

		return briefing;
	}

	/**
	 * The full pipeline: extraction, selection, grounding and composition, on top of the
	 * deterministic half. This is what Phase 5's run API drives asynchronously - cold generation
	 * is 5-20 seconds because of the extraction loop below, which is exactly why it must not run
	 * on the request thread (F.8).
	 *
	 * <p>Extraction is per-item and cached ({@code FactExtractor}), so a lead already fully
	 * extracted costs zero model calls here - only new evidence since the last run does. That is
	 * the mechanism behind "refresh made exactly one model call," not a claim about the prompt.
	 */
	public Briefing generateFull(LeadRef ref, ActingUser user) {
		return generateFull(ref, user, RunProgressListener.NOOP);
	}

	// Not @Transactional, for the same reason as ensureExtractedContext just above: this method
	// calls it (self-invoked), and wrapping this one too would recreate the identical deadlock.
	public Briefing generateFull(LeadRef ref, ActingUser user, RunProgressListener progress) {
		Instant now = clock.now();
		Optional<Briefing> previous = findLatest(ref, user);
		ExtractedContext extracted = ensureExtractedContext(ref, user, progress);
		BriefingContext enriched = extracted.context();
		boolean extractionComplete = extracted.extractionComplete();

		List<ProjectedSection> deterministic = DeterministicProjector.project(enriched, now);
		List<ProjectedSection> inferential = new ArrayList<>(InferentialProjector.project(enriched, extractionComplete));
		inferential.add(talkingPointsComposer.compose(enriched, extractionComplete));

		boolean anyDegraded = inferential.stream().anyMatch(section -> section.renderState() == RenderState.DEGRADED);

		Briefing briefing = briefingRepository.save(Briefing.builder()
				.tenantId(user.tenantId())
				.crmKey(ref.crmKey())
				.leadRef(ref.leadRef())
				.activityId(enriched.nextActivity().map(ScheduledActivity::activityId).orElse(null))
				.generatedFor(user.userId())
				.evidenceFingerprint(EvidenceFingerprint.of(enriched.evidence()))
				.status(anyDegraded ? BriefingStatus.DEGRADED : BriefingStatus.COMPLETE)
				.model(llmProperties.composer().model())
				.promptVersion(FactExtractor.EXTRACTOR_VERSION)
				.createdAt(now)
				.build());

		for (ProjectedSection section : deterministic) {
			saveSection(briefing.getId(), section);
		}
		for (ProjectedSection section : inferential) {
			saveSection(briefing.getId(), section);
		}

		// Every version is retained, never overwritten - what makes What Changed a diff of two
		// real documents rather than a second thing to trust (D.5).
		previous.filter(p -> !p.getId().equals(briefing.getId())).ifPresent(p -> {
			p.setSupersededBy(briefing.getId());
			briefingRepository.save(p);
		});

		return briefing;
	}

	/** The newest briefing for this lead and user, regardless of whether it is still fresh. */
	@Transactional(readOnly = true)
	public Optional<Briefing> findLatest(LeadRef ref, ActingUser user) {
		return briefingRepository.findFirstByTenantIdAndCrmKeyAndLeadRefAndGeneratedForOrderByCreatedAtDesc(
				user.tenantId(), ref.crmKey(), ref.leadRef(), user.userId());
	}

	private void saveSection(UUID briefingId, ProjectedSection section) {
		sectionRepository.save(BriefingSectionEntity.builder()
				.briefingId(briefingId)
				.sectionKey(section.key())
				.renderState(section.renderState())
				.orderedFactIds(section.orderedFactIds())
				.entries(section.entries())
				.build());
	}

	@Transactional(readOnly = true)
	public Optional<Briefing> find(String tenantId, UUID briefingId) {
		return briefingRepository.findByTenantIdAndId(tenantId, briefingId);
	}

	@Transactional(readOnly = true)
	public List<BriefingSectionEntity> sectionsOf(UUID briefingId) {
		return sectionRepository.findByBriefingId(briefingId).stream()
				.sorted(java.util.Comparator.comparingInt(section -> section.getSectionKey().ordinalInBrief()))
				.toList();
	}

	/**
	 * Whether a stored briefing still matches the lead's current evidence.
	 *
	 * <p>Drives the freshness pill. Serving the stale document with an honest banner beats
	 * regenerating silently: it proves the system knows what changed (D.5).
	 */
	@Transactional(readOnly = true)
	public boolean isStale(Briefing briefing, List<EvidenceItem> currentEvidence) {
		return EvidenceFingerprint.isStale(briefing.getEvidenceFingerprint(), currentEvidence);
	}
}
