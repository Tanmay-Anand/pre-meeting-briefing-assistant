package com.leadlens.run;

import java.util.UUID;

import com.leadlens.briefing.Briefing;
import com.leadlens.briefing.BriefingService;
import com.leadlens.briefing.RunProgressListener;
import com.leadlens.common.tenant.ActingUser;
import com.leadlens.crm.model.LeadRef;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Runs generation on the bounded executor and narrates it into {@link RunRegistry}.
 *
 * <p>Deliberately a separate bean from {@link BriefingService}: {@code @Async} only takes effect
 * on a call that arrives through the Spring proxy, so the method has to live somewhere other
 * than wherever it gets called from. Callers (the controller, the scheduled worker) create the
 * run row first via {@link RunRegistry#start}, then hand this the id.
 */
@Service
public class BriefingRunService {

	private static final Logger log = LoggerFactory.getLogger(BriefingRunService.class);

	private final BriefingService briefings;
	private final RunRegistry runs;

	public BriefingRunService(BriefingService briefings, RunRegistry runs) {
		this.briefings = briefings;
		this.runs = runs;
	}

	@Async("briefingExecutor")
	public void generateAsync(UUID runId, LeadRef ref, ActingUser user) {
		try {
			RunProgressListener progress = (completed, total, label) ->
					runs.recordProgress(runId, completed, "%s (%d/%d)".formatted(label, completed, total));

			Briefing briefing = briefings.generateFull(ref, user, progress);
			runs.complete(runId, briefing.getId());
		} catch (RuntimeException e) {
			log.warn("Run {} failed for {}/{}: {}", runId, ref.crmKey(), ref.leadRef(), e.toString());
			runs.fail(runId, e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
		}
	}
}
