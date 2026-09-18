package com.leadlens.run;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/** Polling endpoint for a generation run (IMPLEMENTATION_PLAN.md F.8). */
@RestController
@RequestMapping("/api/briefings/run")
public class RunController {

	private final RunRegistry runs;

	public RunController(RunRegistry runs) {
		this.runs = runs;
	}

	@GetMapping("/{runId}")
	public ResponseEntity<RunResponse> get(
			@RequestHeader("X-LeadLens-Tenant") String tenantId,
			@PathVariable UUID runId) {

		BriefingRun run = runs.find(tenantId, runId)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
						"No run %s in tenant %s".formatted(runId, tenantId)));

		RunResponse body = new RunResponse(
				run.getId(),
				run.getStatus(),
				run.getBriefingId(),
				run.getCompletedItems(),
				run.getTotalItems(),
				run.getSteps(),
				run.getErrorMessage());

		// A run in flight is not a finished answer - the same 202-until-done rule the briefing
		// endpoint itself follows (F.9).
		return run.getStatus() == RunStatus.RUNNING || run.getStatus() == RunStatus.PENDING
				? ResponseEntity.accepted().body(body)
				: ResponseEntity.ok(body);
	}

	public record RunResponse(
			UUID runId,
			RunStatus status,
			UUID briefingId,
			int completedItems,
			int totalItems,
			List<RunStep> steps,
			String errorMessage) {
	}
}
