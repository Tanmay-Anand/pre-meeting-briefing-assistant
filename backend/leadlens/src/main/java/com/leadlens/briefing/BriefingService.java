package com.leadlens.briefing;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.leadlens.briefing.model.ProjectedSection;
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
import com.leadlens.facts.FactRepository;
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

	private final CrmAdapterRegistry adapters;
	private final EvidenceRepository evidenceRepository;
	private final FactRepository factRepository;
	private final BriefingRepository briefingRepository;
	private final BriefingSectionRepository sectionRepository;
	private final BriefingClock clock;

	public BriefingService(
			CrmAdapterRegistry adapters,
			EvidenceRepository evidenceRepository,
			FactRepository factRepository,
			BriefingRepository briefingRepository,
			BriefingSectionRepository sectionRepository,
			BriefingClock clock) {
		this.adapters = adapters;
		this.evidenceRepository = evidenceRepository;
		this.factRepository = factRepository;
		this.briefingRepository = briefingRepository;
		this.sectionRepository = sectionRepository;
		this.clock = clock;
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
			sectionRepository.save(BriefingSectionEntity.builder()
					.briefingId(briefing.getId())
					.sectionKey(section.key())
					.renderState(section.renderState())
					.orderedFactIds(section.orderedFactIds())
					.entries(section.entries())
					.build());
		}

		return briefing;
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
