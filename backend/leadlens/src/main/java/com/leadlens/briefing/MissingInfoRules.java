package com.leadlens.briefing;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.leadlens.briefing.FieldSyncCheck.Finding;
import com.leadlens.briefing.model.BriefingEntry;
import com.leadlens.briefing.model.EntryFlag;
import com.leadlens.briefing.model.SourceRef;
import com.leadlens.crm.model.ScheduledActivity;
import com.leadlens.evidence.Actor;
import com.leadlens.evidence.EvidenceItem;
import com.leadlens.facts.AtomicFact;
import com.leadlens.facts.Provenance;

/**
 * Missing Information - the section the brief is most explicit about and most implementations
 * are weakest at.
 *
 * <p>The brief's rule is "say the information is missing instead of inventing a value". The
 * trap is treating that as one rule. A single "field is null" check collapses situations that
 * imply completely different actions (E.6):
 *
 * <ul>
 *   <li><strong>Never captured</strong> - ask the customer in the meeting.</li>
 *   <li><strong>Masked</strong> - do <em>not</em> ask; escalate internally. Asking the customer
 *       for something the company already knows and hid from this user is embarrassing.</li>
 *   <li><strong>Unsynced</strong> - a conversation already answered it; update the CRM.</li>
 *   <li><strong>Contradicted</strong> - the field and a statement disagree; show both.</li>
 *   <li><strong>Not summarised</strong> - a call happened and nobody wrote down what was said.</li>
 * </ul>
 *
 * <p>Produced entirely without a model. Nothing here can be invented, because nothing here is
 * generated - it is all read off field nullity, timestamps and fact existence.
 *
 * <p>Pure: takes a clock reading rather than reading a clock, so "no contact in 14 days" is
 * testable without waiting fourteen days or mocking time (F.11).
 */
public final class MissingInfoRules {

	/**
	 * Attributes checked against the lead record, with the label the agent reads.
	 *
	 * <p>These are the ones the brief names in section 9, plus bhk, which is not itself a
	 * "missing" case but is where field-versus-fact contradictions show up most often.
	 */
	private static final List<Attribute> TRACKED = List.of(
			new Attribute("budget", "Budget"),
			new Attribute("timeline", "Purchase timeline"),
			new Attribute("location", "Preferred location"),
			new Attribute("financing", "Financing requirement"),
			new Attribute("decisionMaker", "Decision maker"),
			new Attribute("bhk", "Property requirement"));

	/** How long without a customer-initiated interaction before it is worth flagging. */
	private static final Duration QUIET_PERIOD = Duration.ofDays(14);

	private record Attribute(String key, String label) {
	}

	private MissingInfoRules() {
	}

	/**
	 * @param context the assembled, permission-filtered briefing inputs
	 * @param now     the server's idea of now, in the tenant's frame - passed in, never read
	 *                from a static clock, so day-boundary claims are testable (F.11)
	 */
	public static List<BriefingEntry> evaluate(BriefingContext context, Instant now) {
		List<BriefingEntry> entries = new ArrayList<>();

		for (Attribute attribute : TRACKED) {
			Finding finding = FieldSyncCheck.evaluate(
					attribute.key(),
					context.lead().field(attribute.key()),
					context.facts());

			if (finding.needsAgentAttention()) {
				entries.add(toEntry(attribute, finding, context));
			}
		}

		quietPeriodEntry(context, now).ifPresent(entries::add);
		meetingObjectiveEntry(context).ifPresent(entries::add);
		entries.addAll(unsummarisedCallEntries(context));

		return entries;
	}

	private static BriefingEntry toEntry(Attribute attribute, Finding finding, BriefingContext context) {
		List<SourceRef> sources = finding.current() != null && finding.current().evidenceId() != null
				? sourcesFor(context, finding.current().evidenceId())
				: List.of();

		String text = switch (finding.flag()) {
			case NEVER_CAPTURED -> "%s was never recorded. Ask for it.".formatted(attribute.label());

			// Deliberately does not name a value, and deliberately does not tell the agent to
			// ask the customer.
			case MASKED -> "%s is not visible to your role. Ask internally rather than the customer."
					.formatted(attribute.label());

			// The highest-value line in the briefing: the answer already exists, in a
			// conversation, and only the CRM does not know it.
			case UNSYNCED -> "%s is not on the lead record, but was stated: \"%s\". Update the field."
					.formatted(attribute.label(), finding.current().display());

			case CONTRADICTED -> "%s does not match what was said: %s (%s) vs %s (%s)."
					.formatted(
							attribute.label(),
							finding.current().display(),
							describe(finding.current()),
							finding.disagreesWith().display(),
							describe(finding.disagreesWith()));

			case STALE -> "%s was recorded a while ago and has not been re-confirmed."
					.formatted(attribute.label());

			default -> "%s needs attention.".formatted(attribute.label());
		};

		return new BriefingEntry(attribute.label(), text, Provenance.DERIVED, finding.flag(), sources);
	}

	private static String describe(FieldSyncCheck.ValuePoint point) {
		return point.origin() == FieldSyncCheck.Origin.FIELD ? "lead field" : "from a conversation";
	}

	/**
	 * No customer-initiated contact for a while.
	 *
	 * <p>Counts only what the <em>customer</em> did. An agent leaving four voicemails is not
	 * contact; treating it as contact would tell the agent the relationship is warm when the
	 * customer has gone silent, which is the opposite of useful.
	 */
	private static java.util.Optional<BriefingEntry> quietPeriodEntry(BriefingContext context, Instant now) {
		java.util.Optional<EvidenceItem> latest = context.evidence().stream()
				.filter(item -> item.getActor() == Actor.CUSTOMER)
				.max(java.util.Comparator.comparing(EvidenceItem::getOccurredAt));

		if (latest.isEmpty()) {
			return context.hasNoEvidence()
					? java.util.Optional.of(new BriefingEntry(
							"Customer contact",
							"The customer has not interacted at all yet.",
							Provenance.DERIVED, EntryFlag.NEVER_CAPTURED, List.of()))
					: java.util.Optional.of(new BriefingEntry(
							"Customer contact",
							"No customer-initiated interaction is on record - only agent activity.",
							Provenance.DERIVED, EntryFlag.NEVER_CAPTURED, List.of()));
		}

		Duration silence = Duration.between(latest.get().getOccurredAt(), now);
		if (silence.compareTo(QUIET_PERIOD) <= 0) {
			return java.util.Optional.empty();
		}

		return java.util.Optional.of(new BriefingEntry(
				"Customer contact",
				"No customer-initiated interaction in %d days.".formatted(silence.toDays()),
				Provenance.DERIVED,
				EntryFlag.STALE,
				List.of()));
	}

	/** A meeting with no stated objective, which the brief names explicitly. */
	private static java.util.Optional<BriefingEntry> meetingObjectiveEntry(BriefingContext context) {
		return context.nextActivity()
				.filter(ScheduledActivity::hasNoStatedPurpose)
				.map(activity -> new BriefingEntry(
						"Meeting objective",
						"The upcoming %s has no stated objective.".formatted(activity.type()),
						Provenance.DERIVED,
						EntryFlag.NEVER_CAPTURED,
						List.of()));
	}

	/**
	 * Calls that happened but were never written up.
	 *
	 * <p>Production transcription is out of scope, so these exist and matter. Dropping them
	 * would make an unread call and an uneventful call indistinguishable (E.5) - the agent
	 * would assume nothing happened, when in fact nobody knows.
	 */
	private static List<BriefingEntry> unsummarisedCallEntries(BriefingContext context) {
		return context.evidence().stream()
				.filter(EvidenceItem::hasNoText)
				.map(item -> new BriefingEntry(
						"Unsummarised interaction",
						"A %s on %s has no notes or summary.".formatted(
								item.getType().name().toLowerCase().replace('_', ' '),
								item.getOccurredAt()),
						Provenance.CRM_ACTIVITY,
						EntryFlag.NOT_SUMMARISED,
						List.of(toSource(item))))
				.toList();
	}

	private static List<SourceRef> sourcesFor(BriefingContext context, java.util.UUID evidenceId) {
		return context.evidence().stream()
				.filter(item -> item.getId().equals(evidenceId))
				.map(MissingInfoRules::toSource)
				.toList();
	}

	static SourceRef toSource(EvidenceItem item) {
		return new SourceRef(
				item.getId(),
				item.getEvidenceKey(),
				"%s - %s".formatted(item.getType(), item.getChannel()),
				item.getOccurredAt(),
				item.getDeepLink(),
				item.getText());
	}
}
