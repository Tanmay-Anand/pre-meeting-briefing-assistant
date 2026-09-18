package com.leadlens.run;

import java.time.Instant;

/**
 * One narrated step of a run, so the client can show real progress rather than a spinner.
 *
 * <p>{@code steps} grows as extractions complete. Because the denominator (the evidence-item
 * count) is known up front, the client can render a determinate progress bar - a bar advancing
 * on a timer with no real denominator is a decoration pretending to be a measurement (F.8).
 */
public record RunStep(String label, Instant at) {
}
