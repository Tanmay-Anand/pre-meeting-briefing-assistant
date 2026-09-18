package com.leadlens.briefing;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import com.leadlens.briefing.model.BriefingEntry;
import com.leadlens.briefing.model.EntryFlag;
import com.leadlens.briefing.model.RenderState;
import com.leadlens.briefing.model.RenderStyle;
import com.leadlens.briefing.model.SectionKey;
import com.leadlens.common.clock.BriefingClock;
import com.leadlens.common.tenant.ActingUser;
import com.leadlens.crm.CrmAdapter;
import com.leadlens.crm.CrmAdapterRegistry;
import com.leadlens.crm.model.LeadRef;
import com.leadlens.crm.model.ScheduledActivity;
import com.leadlens.evidence.EvidenceFingerprint;
import com.leadlens.evidence.EvidenceRepository;
import com.leadlens.run.BriefingRunService;
import com.leadlens.run.RunRegistry;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Briefing read and generate endpoints.
 *
 * <p>Identity comes from headers, standing in for the token auth Phase 8's {@code
 * TokenAuthFilter} validates before these ever run. The shape is already the one that matters:
 * the tenant is never taken from a request body or query parameter, so no client can ask for
 * another tenant's data by editing a payload.
 */
@RestController
@RequestMapping("/api/briefings")
public class BriefingController {

	/** How far ahead "upcoming" looks. Matches UpcomingActivityWorker's own T-30 window. */
	private static final Duration UPCOMING_WINDOW = Duration.ofMinutes(30);

	private final BriefingService briefings;
	private final BriefingDiffService diffs;
	private final EvidenceRepository evidenceRepository;
	private final RunRegistry runs;
	private final BriefingRunService runService;
	private final CrmAdapterRegistry adapters;
	private final BriefingClock clock;

	public BriefingController(
			BriefingService briefings,
			BriefingDiffService diffs,
			EvidenceRepository evidenceRepository,
			RunRegistry runs,
			BriefingRunService runService,
			CrmAdapterRegistry adapters,
			BriefingClock clock) {
		this.briefings = briefings;
		this.diffs = diffs;
		this.evidenceRepository = evidenceRepository;
		this.runs = runs;
		this.runService = runService;
		this.adapters = adapters;
		this.clock = clock;
	}

	/**
	 * Probed by the extension at startup. When this is unreachable or reports false, the panel
	 * hides its entry point rather than offering a Prepare Me button that cannot work (I.4).
	 */
	@GetMapping("/status")
	public Map<String, Object> status() {
		return Map.of("available", true);
	}

	/**
	 * Returns the cached briefing if its fingerprint still matches the lead's evidence, else
	 * starts a run and answers 202 immediately (F.8) - cold generation is 5-20 seconds and must
	 * not hold this connection.
	 */
	@PostMapping
	public ResponseEntity<?> generate(
			@RequestHeader("X-LeadLens-Tenant") String tenantId,
			@RequestHeader("X-LeadLens-User") String userId,
			@RequestBody GenerateRequest request) {

		ActingUser user = new ActingUser(tenantId, userId, Set.of());
		LeadRef ref = new LeadRef(request.crmKey(), request.leadRef());

		BriefingService.ExtractedContext extracted =
				briefings.ensureExtractedContext(ref, user, com.leadlens.briefing.RunProgressListener.NOOP);
		String currentFingerprint = EvidenceFingerprint.of(extracted.context().evidence());

		Optional<Briefing> cached = briefings.findLatest(ref, user)
				.filter(Briefing::isComplete)
				.filter(b -> b.getEvidenceFingerprint().equals(currentFingerprint));

		if (cached.isPresent()) {
			return ResponseEntity.ok(toResponse(cached.get()));
		}

		return accepted(ref, user, extracted.context().evidence().size());
	}

	/** Forces regeneration, reusing whatever extractions are already cached (D.5). */
	@PostMapping("/{briefingId}/refresh")
	public ResponseEntity<?> refresh(
			@RequestHeader("X-LeadLens-Tenant") String tenantId,
			@RequestHeader("X-LeadLens-User") String userId,
			@PathVariable UUID briefingId) {

		Briefing existing = briefings.find(tenantId, briefingId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"No briefing %s in tenant %s".formatted(briefingId, tenantId)));

		ActingUser user = new ActingUser(tenantId, userId, Set.of());
		LeadRef ref = new LeadRef(existing.getCrmKey(), existing.getLeadRef());
		long evidenceCount = evidenceRepository.countByTenantIdAndLeadRef(tenantId, ref.leadRef());

		return accepted(ref, user, (int) evidenceCount);
	}

	/**
	 * The freshness pill and the F.16 field-sync check, computed live on every call - never
	 * gated behind a completed run, because a field/fact contradiction has to be visible the
	 * moment the lead is opened, not only after the agent notices a pill and clicks Refresh.
	 */
	@GetMapping("/latest")
	public ResponseEntity<LatestResponse> latest(
			@RequestHeader("X-LeadLens-Tenant") String tenantId,
			@RequestHeader("X-LeadLens-User") String userId,
			@RequestParam String crmKey,
			@RequestParam String leadRef) {

		ActingUser user = new ActingUser(tenantId, userId, Set.of());
		LeadRef ref = new LeadRef(crmKey, leadRef);

		BriefingService.ExtractedContext extracted =
				briefings.ensureExtractedContext(ref, user, com.leadlens.briefing.RunProgressListener.NOOP);
		String currentFingerprint = EvidenceFingerprint.of(extracted.context().evidence());

		Optional<Briefing> latest = briefings.findLatest(ref, user);
		boolean stale = latest.isEmpty() || !latest.get().getEvidenceFingerprint().equals(currentFingerprint);

		List<FieldContradiction> contradictions = MissingInfoRules
				.evaluate(extracted.context(), java.time.Instant.now())
				.stream()
				.filter(entry -> entry.flag() == EntryFlag.CONTRADICTED)
				.map(entry -> new FieldContradiction(entry.label(), entry.text()))
				.toList();

		return ResponseEntity.ok(new LatestResponse(
				latest.map(Briefing::getId).orElse(null),
				stale,
				latest.map(Briefing::getCreatedAt).map(Object::toString).orElse(null),
				contradictions));
	}

	/**
	 * Drives the "Your meeting is in 28 minutes — briefing ready" panel surface (Part K Phase 9
	 * step 4, Part H.1). Scoped to one lead rather than a tenant-wide dashboard: the panel always
	 * already knows which lead it is looking at (content-script detection), so this answers "is
	 * *this* lead's next activity coming up, and is a briefing already sitting there for it" —
	 * the same question {@link com.leadlens.schedule.UpcomingActivityWorker} answers in bulk,
	 * asked here for one lead on demand.
	 */
	@GetMapping("/upcoming")
	public ResponseEntity<UpcomingResponse> upcoming(
			@RequestHeader("X-LeadLens-Tenant") String tenantId,
			@RequestHeader("X-LeadLens-User") String userId,
			@RequestParam String crmKey,
			@RequestParam String leadRef) {

		ActingUser user = new ActingUser(tenantId, userId, Set.of());
		LeadRef ref = new LeadRef(crmKey, leadRef);
		CrmAdapter adapter = adapters.require(crmKey);

		Instant now = clock.now();
		Instant horizon = now.plus(UPCOMING_WINDOW);

		Optional<ScheduledActivity> next = adapter.fetchUpcoming(ref, user).stream()
				.filter(activity -> !activity.scheduledAt().isBefore(now) && !activity.scheduledAt().isAfter(horizon))
				.min(Comparator.comparing(ScheduledActivity::scheduledAt));

		boolean briefingReady = briefings.findLatest(ref, user)
				.filter(Briefing::isComplete)
				.isPresent();

		return ResponseEntity.ok(new UpcomingResponse(
				next.map(ScheduledActivity::type).map(Enum::name).orElse(null),
				next.map(ScheduledActivity::scheduledAt).map(Object::toString).orElse(null),
				next.map(activity -> Duration.between(now, activity.scheduledAt()).toMinutes()).orElse(null),
				next.isPresent() && briefingReady));
	}

	/** The What Changed panel: a deterministic diff between two stored versions (D.5, ledger #9). */
	@GetMapping("/{briefingId}/changes")
	public BriefingDiffService.BriefingDiff changes(
			@RequestHeader("X-LeadLens-Tenant") String tenantId,
			@PathVariable UUID briefingId,
			@RequestParam UUID since) {

		briefings.find(tenantId, briefingId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No briefing " + briefingId));
		briefings.find(tenantId, since)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "No briefing " + since));

		return diffs.diff(since, briefingId);
	}

	private ResponseEntity<Map<String, Object>> accepted(LeadRef ref, ActingUser user, int totalItems) {
		UUID runId = runs.start(user.tenantId(), ref.crmKey(), ref.leadRef(), totalItems);
		runService.generateAsync(runId, ref, user);
		return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of("runId", runId));
	}

	/**
	 * Returns the stored briefing.
	 *
	 * <p>Answers 202 while a briefing is still being produced, and 200 only once it is
	 * finished. A row existing does not mean the briefing was generated: returning 200 early
	 * would show the agent "No objections recorded" for a lead the system never read, which
	 * reassures them with nothing (F.9).
	 */
	@GetMapping("/{briefingId}")
	public ResponseEntity<BriefingResponse> get(
			@RequestHeader("X-LeadLens-Tenant") String tenantId,
			@PathVariable UUID briefingId) {

		Briefing briefing = briefings.find(tenantId, briefingId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"No briefing %s in tenant %s".formatted(briefingId, tenantId)));

		BriefingResponse body = toResponse(briefing);
		return briefing.isComplete()
				? ResponseEntity.ok(body)
				: ResponseEntity.accepted().body(body);
	}

	private BriefingResponse toResponse(Briefing briefing) {
		List<SectionResponse> sections = briefings.sectionsOf(briefing.getId()).stream()
				.map(section -> new SectionResponse(
						section.getSectionKey(),
						section.getSectionKey().ordinalInBrief(),
						section.getRenderState(),
						// Derived from the section, never chosen by a model (F.12).
						section.getSectionKey().renderStyle(),
						section.getSectionKey().usesModel(),
						section.getEntries()))
				.toList();

		return new BriefingResponse(
				briefing.getId(),
				briefing.getCrmKey(),
				briefing.getLeadRef(),
				briefing.getActivityId(),
				briefing.getStatus(),
				briefing.getEvidenceFingerprint(),
				briefing.getCreatedAt().toString(),
				sections);
	}

	public record GenerateRequest(@NotBlank String crmKey, @NotBlank String leadRef) {
	}

	public record BriefingResponse(
			UUID briefingId,
			String crmKey,
			String leadRef,
			String activityId,
			BriefingStatus status,
			String evidenceFingerprint,
			String createdAt,
			List<SectionResponse> sections) {
	}

	/**
	 * @param usesModel lets the panel badge model-backed sections distinctly, so the CRM-fact
	 *                  and AI-reading distinction survives the trip to the client (E.4)
	 */
	public record SectionResponse(
			SectionKey key,
			int order,
			RenderState renderState,
			RenderStyle renderStyle,
			boolean usesModel,
			List<BriefingEntry> entries) {
	}

	/**
	 * @param briefingId  the latest briefing, or null if none has ever been generated
	 * @param stale       true when the lead's evidence has changed since {@code briefingId} was built
	 * @param lastUpdatedAt when the latest briefing was generated, or null
	 * @param fieldContradictions live, model-free (F.16) - never waits for stale/refresh
	 */
	public record LatestResponse(
			UUID briefingId,
			boolean stale,
			String lastUpdatedAt,
			List<FieldContradiction> fieldContradictions) {
	}

	public record FieldContradiction(String field, String detail) {
	}

	/**
	 * @param activityType   the next upcoming activity's type, or null if nothing is due within
	 *                       the window
	 * @param scheduledAt    when it starts, or null
	 * @param minutesUntil   minutes from now until it starts, or null
	 * @param briefingReady  true only when an activity is due soon AND a complete briefing
	 *                       already exists for this lead
	 */
	public record UpcomingResponse(
			String activityType,
			String scheduledAt,
			Long minutesUntil,
			boolean briefingReady) {
	}
}
