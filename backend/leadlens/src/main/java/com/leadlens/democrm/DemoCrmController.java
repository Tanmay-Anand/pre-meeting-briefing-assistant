package com.leadlens.democrm;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.leadlens.common.clock.BriefingClock;
import com.leadlens.democrm.DemoCrmDtos.ActivityListResponse;
import com.leadlens.democrm.DemoCrmDtos.ActivityResponse;
import com.leadlens.democrm.DemoCrmDtos.CreateActivityRequest;
import com.leadlens.democrm.DemoCrmDtos.FieldResponse;
import com.leadlens.democrm.DemoCrmDtos.LeadResponse;
import com.leadlens.democrm.DemoCrmDtos.LeadSummaryResponse;
import com.leadlens.democrm.DemoCrmDtos.UpdateFieldRequest;
import com.leadlens.democrm.model.DemoActivity;
import com.leadlens.democrm.model.DemoLead;
import com.leadlens.democrm.model.DemoLeadField;
import com.leadlens.democrm.model.DemoUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * The Demo CRM's own HTTP API.
 *
 * <p>This is a stand-in for a third-party CRM, not part of LeadLens. {@code DemoCrmAdapter}
 * calls it over HTTP exactly as a real adapter would, which is what keeps the adapter boundary
 * honest - if the adapter read these tables directly, the "swap in any CRM" claim would be
 * untested (G.4).
 *
 * <p>Identity arrives in headers, standing in for whatever auth a real CRM uses. The tenant is
 * validated against the user on every call: a user may only ever act inside their own tenant,
 * and the API says 403 rather than quietly returning nothing, because "you may not see this"
 * and "this does not exist" are different answers (F.10).
 */
@RestController
@RequestMapping("/api/democrm")
public class DemoCrmController {

	private static final String NAME_FIELD = "name";
	private static final String STATUS_FIELD = "status";

	private final DemoLeadRepository leads;
	private final DemoLeadFieldRepository fields;
	private final DemoActivityRepository activities;
	private final DemoUserRepository users;
	private final DemoFieldVisibility visibility;
	private final BriefingClock clock;

	public DemoCrmController(
			DemoLeadRepository leads,
			DemoLeadFieldRepository fields,
			DemoActivityRepository activities,
			DemoUserRepository users,
			DemoFieldVisibility visibility,
			BriefingClock clock) {
		this.leads = leads;
		this.fields = fields;
		this.activities = activities;
		this.users = users;
		this.visibility = visibility;
		this.clock = clock;
	}

	@GetMapping("/leads")
	public List<LeadSummaryResponse> listLeads(
			@RequestHeader("X-Demo-Tenant") String tenantId,
			@RequestHeader("X-Demo-User") String userId) {

		DemoUser user = requireUser(tenantId, userId);
		return leads.findByTenantIdOrderByCreatedAtDesc(user.getTenantId()).stream()
				.map(lead -> new LeadSummaryResponse(
						lead.getId(),
						fieldValue(tenantId, lead.getId(), NAME_FIELD),
						fieldValue(tenantId, lead.getId(), STATUS_FIELD),
						lead.getCreatedAt()))
				.toList();
	}

	@GetMapping("/leads/{leadId}")
	public LeadResponse getLead(
			@RequestHeader("X-Demo-Tenant") String tenantId,
			@RequestHeader("X-Demo-User") String userId,
			@PathVariable String leadId) {

		DemoUser user = requireUser(tenantId, userId);
		DemoLead lead = requireLead(tenantId, leadId);

		Map<String, FieldResponse> payload = new LinkedHashMap<>();
		for (DemoLeadField field : fields.findByTenantIdAndLeadId(tenantId, leadId)) {
			boolean visible = visibility.isVisible(user.getRole(), field.getAttributeKey());
			// A masked field is reported as existing-but-hidden. It must not be silently
			// omitted: "never captured" tells the agent to ask the customer, "masked" tells
			// them to escalate internally, and those are different actions (E.6).
			payload.put(
					field.getAttributeKey(),
					visible
							? new FieldResponse(field.getValue(), field.getUpdatedAt(), false)
							: new FieldResponse(null, null, true));
		}

		return new LeadResponse(lead.getId(), lead.getAssignedUserId(), lead.getCreatedAt(), payload);
	}

	@GetMapping("/leads/{leadId}/activities")
	public ActivityListResponse getActivities(
			@RequestHeader("X-Demo-Tenant") String tenantId,
			@RequestHeader("X-Demo-User") String userId,
			@PathVariable String leadId) {

		requireUser(tenantId, userId);
		requireLead(tenantId, leadId);

		return new ActivityListResponse(
				activities.findByTenantIdAndLeadIdAndScheduledOrderByOccurredAtAsc(tenantId, leadId, false)
						.stream()
						.map(DemoCrmController::toResponse)
						.toList());
	}

	@GetMapping("/leads/{leadId}/scheduled")
	public ActivityListResponse getScheduled(
			@RequestHeader("X-Demo-Tenant") String tenantId,
			@RequestHeader("X-Demo-User") String userId,
			@PathVariable String leadId) {

		requireUser(tenantId, userId);
		requireLead(tenantId, leadId);

		return new ActivityListResponse(
				activities.findByTenantIdAndLeadIdAndScheduledOrderByOccurredAtAsc(tenantId, leadId, true)
						.stream()
						.map(DemoCrmController::toResponse)
						.toList());
	}

	/**
	 * Logs a new activity. This is the demo's pivot: adding a WhatsApp message here is what
	 * makes the briefing go stale and the What Changed panel light up (Part L, step 8).
	 */
	@PostMapping("/leads/{leadId}/activities")
	public ResponseEntity<ActivityResponse> addActivity(
			@RequestHeader("X-Demo-Tenant") String tenantId,
			@RequestHeader("X-Demo-User") String userId,
			@PathVariable String leadId,
			@Valid @RequestBody CreateActivityRequest request) {

		requireUser(tenantId, userId);
		requireLead(tenantId, leadId);

		Instant now = clock.now();
		DemoActivity activity = DemoActivity.builder()
				.tenantId(tenantId)
				.leadId(leadId)
				.type(request.type())
				.actor(request.actor())
				.channel(request.channel())
				.text(request.text())
				.occurredAt(request.occurredAt() == null ? now : request.occurredAt())
				.structured(request.structured() == null ? new LinkedHashMap<>() : new LinkedHashMap<>(request.structured()))
				.purpose(request.purpose())
				.scheduled(false)
				.updatedAt(now)
				.build();

		return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(activities.save(activity)));
	}

	/**
	 * Sets one field on the lead.
	 *
	 * <p>This is the write behind the one-click "update lead field" affordance. LeadLens never
	 * calls it on its own initiative: the agent clicks, and the agent's session performs the
	 * write. That distinction is what keeps the feature inside the brief's scope (A.6).
	 */
	@PutMapping("/leads/{leadId}/fields/{attributeKey}")
	public FieldResponse updateField(
			@RequestHeader("X-Demo-Tenant") String tenantId,
			@RequestHeader("X-Demo-User") String userId,
			@PathVariable String leadId,
			@PathVariable String attributeKey,
			@Valid @RequestBody UpdateFieldRequest request) {

		DemoUser user = requireUser(tenantId, userId);
		requireLead(tenantId, leadId);

		if (!visibility.isVisible(user.getRole(), attributeKey)) {
			// Cannot see it, cannot set it. Otherwise a junior agent could overwrite a budget
			// they are not allowed to read.
			throw new ResponseStatusException(HttpStatus.FORBIDDEN,
					"Role %s may not modify field '%s'".formatted(user.getRole(), attributeKey));
		}

		Instant now = clock.now();
		DemoLeadField field = fields
				.findByTenantIdAndLeadIdAndAttributeKey(tenantId, leadId, attributeKey)
				.orElseGet(() -> DemoLeadField.builder()
						.tenantId(tenantId)
						.leadId(leadId)
						.attributeKey(attributeKey)
						.build());

		field.setValue(request.value());
		field.setUpdatedAt(now);
		fields.save(field);

		return new FieldResponse(field.getValue(), field.getUpdatedAt(), false);
	}

	private DemoUser requireUser(String tenantId, String userId) {
		return users.findByTenantIdAndId(tenantId, userId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN,
						"User '%s' does not belong to tenant '%s'".formatted(userId, tenantId)));
	}

	private DemoLead requireLead(String tenantId, String leadId) {
		return leads.findByTenantIdAndId(tenantId, leadId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"No lead '%s' in tenant '%s'".formatted(leadId, tenantId)));
	}

	private String fieldValue(String tenantId, String leadId, String attributeKey) {
		return fields.findByTenantIdAndLeadIdAndAttributeKey(tenantId, leadId, attributeKey)
				.map(DemoLeadField::getValue)
				.orElse(null);
	}

	private static ActivityResponse toResponse(DemoActivity activity) {
		return new ActivityResponse(
				activity.getId().toString(),
				activity.getType(),
				activity.getOccurredAt(),
				activity.getActor(),
				activity.getChannel(),
				activity.getText(),
				activity.getStructured(),
				activity.isScheduled(),
				activity.getPurpose(),
				activity.getUpdatedAt());
	}
}
