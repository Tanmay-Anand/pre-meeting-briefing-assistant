package com.leadlens.briefing;

/**
 * Lets {@link BriefingService#generateFull} narrate its progress without depending on the
 * {@code run} package - the engine reports progress, it does not know what a run is (Part J's
 * package-boundary rule, applied here to a different seam: generation logic stays ignorant of
 * how it is invoked).
 */
public interface RunProgressListener {

	RunProgressListener NOOP = (completed, total, label) -> {
	};

	void onProgress(int completed, int total, String label);
}
