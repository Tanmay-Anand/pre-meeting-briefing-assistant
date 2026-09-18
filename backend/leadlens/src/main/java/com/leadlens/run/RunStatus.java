package com.leadlens.run;

/**
 * Where a generation run is in its lifecycle.
 *
 * <p>Long work is a run, not a request (IMPLEMENTATION_PLAN.md F.8): cold generation is 5-20
 * seconds, too long to hold an HTTP connection at the mercy of every proxy and sleeping laptop
 * in the path. {@code POST /api/briefings} returns {@code 202 {runId}} immediately and the
 * client polls {@code GET /api/briefings/run/{runId}} against this state.
 */
public enum RunStatus {
	PENDING,
	RUNNING,
	COMPLETE,
	FAILED
}
