package com.leadlens.run;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.leadlens.common.clock.BriefingClock;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Postgres-backed run tracking (IMPLEMENTATION_PLAN.md 0.3, F.8).
 *
 * <p>Every write here commits in its own transaction ({@code REQUIRES_NEW}), independently of
 * whatever generation transaction is calling it. That is deliberate and load-bearing: generation
 * runs inside one longer transaction, and a client polling {@code GET /run/{runId}} needs to see
 * each step the moment it happens, not only once the whole run commits at the end - otherwise
 * the progress bar the plan asks for would sit frozen until the very last instant.
 */
@Service
public class RunRegistry {

	private final RunRepository repository;
	private final BriefingClock clock;

	public RunRegistry(RunRepository repository, BriefingClock clock) {
		this.repository = repository;
		this.clock = clock;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public UUID start(String tenantId, String crmKey, String leadRef, int totalItems) {
		BriefingRun run = repository.save(BriefingRun.builder()
				.tenantId(tenantId)
				.crmKey(crmKey)
				.leadRef(leadRef)
				.status(RunStatus.RUNNING)
				.totalItems(totalItems)
				.startedAt(clock.now())
				.steps(new ArrayList<>(List.of(new RunStep("Starting generation", clock.now()))))
				.build());
		return run.getId();
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void recordProgress(UUID runId, int completedItems, String label) {
		repository.findById(runId).ifPresent(run -> {
			run.setCompletedItems(completedItems);
			List<RunStep> steps = new ArrayList<>(run.getSteps());
			steps.add(new RunStep(label, clock.now()));
			run.setSteps(steps);
			repository.save(run);
		});
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void complete(UUID runId, UUID briefingId) {
		repository.findById(runId).ifPresent(run -> {
			run.setStatus(RunStatus.COMPLETE);
			run.setBriefingId(briefingId);
			run.setFinishedAt(clock.now());
			List<RunStep> steps = new ArrayList<>(run.getSteps());
			steps.add(new RunStep("Briefing ready", clock.now()));
			run.setSteps(steps);
			repository.save(run);
		});
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void fail(UUID runId, String message) {
		repository.findById(runId).ifPresent(run -> {
			run.setStatus(RunStatus.FAILED);
			run.setErrorMessage(message);
			run.setFinishedAt(clock.now());
			repository.save(run);
		});
	}

	@Transactional(readOnly = true)
	public Optional<BriefingRun> find(String tenantId, UUID runId) {
		return repository.findByTenantIdAndId(tenantId, runId);
	}
}
