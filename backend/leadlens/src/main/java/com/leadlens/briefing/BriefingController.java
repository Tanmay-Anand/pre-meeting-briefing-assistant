package com.leadlens.briefing;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.leadlens.briefing.model.BriefingEntry;
import com.leadlens.briefing.model.RenderState;
import com.leadlens.briefing.model.RenderStyle;
import com.leadlens.briefing.model.SectionKey;
import com.leadlens.common.tenant.ActingUser;
import com.leadlens.crm.model.LeadRef;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Briefing read and generate endpoints.
 *
 * <p>Phase 2 scope. The asynchronous run API (202 + polling) arrives in Phase 5; generation
 * here is synchronous because the deterministic half takes milliseconds, not the 5-20 seconds
 * a cold model-backed run will.
 *
 * <p>Identity comes from headers for now, standing in for the token auth Phase 8 adds. The
 * shape is already the one that matters: the tenant is never taken from a request body or
 * query parameter, so no client can ask for another tenant's data by editing a payload.
 */
@RestController
@RequestMapping("/api/briefings")
public class BriefingController {

	private final BriefingService briefings;

	public BriefingController(BriefingService briefings) {
		this.briefings = briefings;
	}

	/**
	 * Probed by the extension at startup. When this is unreachable or reports false, the panel
	 * hides its entry point rather than offering a Prepare Me button that cannot work (I.4).
	 */
	@GetMapping("/status")
	public Map<String, Object> status() {
		return Map.of("available", true);
	}

	@PostMapping
	public BriefingResponse generate(
			@RequestHeader("X-LeadLens-Tenant") String tenantId,
			@RequestHeader("X-LeadLens-User") String userId,
			@RequestBody GenerateRequest request) {

		ActingUser user = new ActingUser(tenantId, userId, java.util.Set.of());
		LeadRef ref = new LeadRef(request.crmKey(), request.leadRef());

		Briefing briefing = briefings.generateDeterministic(ref, user);
		return toResponse(briefing);
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
}
