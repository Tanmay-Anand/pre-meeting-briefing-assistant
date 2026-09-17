package com.leadlens.briefing;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.leadlens.briefing.model.BriefingEntry;
import com.leadlens.briefing.model.EntryFlag;
import com.leadlens.briefing.model.ProjectedSection;
import com.leadlens.briefing.model.SectionKey;
import com.leadlens.briefing.model.SourceRef;
import com.leadlens.crm.model.FieldValue;
import com.leadlens.crm.model.LeadSnapshot;
import com.leadlens.crm.model.ScheduledActivity;
import com.leadlens.evidence.EvidenceItem;
import com.leadlens.facts.AtomicFact;
import com.leadlens.facts.FactKind;
import com.leadlens.facts.FactStatus;
import com.leadlens.facts.Provenance;

/**
 * Builds the sections that never call a model.
 *
 * <p>Roughly 40% of the briefing comes from here: Attention, Customer Snapshot, Meeting
 * Context, Missing Information, Source References and the journey timeline. Two consequences
 * worth being explicit about (IMPLEMENTATION_PLAN.md D.2):
 *
 * <ol>
 *   <li>These sections are <em>structurally</em> incapable of inventing a budget. When a judge
 *       asks how we know it will not, the answer is that no model is involved, not that the
 *       prompt says please.</li>
 *   <li>They still render when the model is unavailable, over budget or returning nonsense. A
 *       briefing can always ship something true.</li>
 * </ol>
 *
 * <p>Pure: every input arrives in the {@link BriefingContext} and the current time is passed
 * in. No repositories, no clock, no Spring.
 */
public final class DeterministicProjector {

	/** Snapshot rows, in the order the brief lists them. */
	private static final List<String[]> SNAPSHOT_FIELDS = List.of(
			new String[] {"name", "Customer"},
			new String[] {"status", "Lead status"},
			new String[] {"subStatus", "Sub-status"},
			new String[] {"source", "Lead source"},
			new String[] {"bhk", "Requirement"},
			new String[] {"location", "Preferred location"},
			new String[] {"budget", "Budget"},
			new String[] {"timeline", "Purchase timeline"});

	private static final int ATTENTION_LIMIT = 3;
	private static final int JOURNEY_LIMIT = 12;

	private DeterministicProjector() {
	}

	public static List<ProjectedSection> project(BriefingContext context, Instant now) {
		List<ProjectedSection> sections = new ArrayList<>();
		sections.add(attention(context));
		sections.add(customerSnapshot(context));
		sections.add(meetingContext(context));
		sections.add(missingInformation(context, now));
		sections.add(journey(context));
		sections.add(sourceReferences(context));
		return sections;
	}

	/**
	 * The three things to know before walking in.
	 *
	 * <p>Ranked from facts that already carry an "all open, regardless of age" inclusion floor,
	 * so this adds reading speed without adding a new surface for invention. With no facts yet
	 * it degrades rather than guessing - an empty Attention block is honest, an invented one is
	 * not.
	 */
	private static ProjectedSection attention(BriefingContext context) {
		List<AtomicFact> urgent = context.facts().stream()
				.filter(fact -> fact.getStatus() == FactStatus.OPEN)
				.filter(fact -> fact.getKind().survivesAtAnyAge())
				.sorted(Comparator.comparing(AtomicFact::getOccurredAt).reversed())
				.limit(ATTENTION_LIMIT)
				.toList();

		if (urgent.isEmpty()) {
			return context.hasNoEvidence()
					? ProjectedSection.degraded(SectionKey.ATTENTION,
							"Nothing on record for this lead yet - see Missing Information.")
					: ProjectedSection.declareEmpty(SectionKey.ATTENTION,
							"No unresolved objections, commitments or open questions on record.");
		}

		List<BriefingEntry> entries = urgent.stream()
				.map(fact -> new BriefingEntry(
						label(fact.getKind()),
						fact.getClaim(),
						fact.getProvenance(),
						EntryFlag.NONE,
						sourcesFor(context, fact)))
				.toList();

		return new ProjectedSection(
				SectionKey.ATTENTION,
				com.leadlens.briefing.model.RenderState.RENDER,
				entries,
				urgent.stream().map(AtomicFact::getFactId).toList());
	}

	/** Direct field projection. Every row is a CRM fact or an explicit statement of absence. */
	private static ProjectedSection customerSnapshot(BriefingContext context) {
		LeadSnapshot lead = context.lead();
		List<BriefingEntry> entries = new ArrayList<>();

		for (String[] row : SNAPSHOT_FIELDS) {
			FieldValue value = lead.field(row[0]);

			if (value.masked()) {
				// Shown as withheld rather than omitted. An omitted row reads as "not
				// applicable"; this one means "exists, and not for you".
				entries.add(BriefingEntry.flagged(row[1], "Hidden for your role",
						Provenance.CRM_FIELD, EntryFlag.MASKED));
			} else if (value.isPresent()) {
				entries.add(BriefingEntry.field(row[1], value.value(), Provenance.CRM_FIELD));
			} else {
				entries.add(BriefingEntry.flagged(row[1], "Not recorded",
						Provenance.CRM_FIELD, EntryFlag.NEVER_CAPTURED));
			}
		}

		return ProjectedSection.rendered(SectionKey.CUSTOMER_SNAPSHOT, entries);
	}

	/** Direct projection from the scheduled activity record. */
	private static ProjectedSection meetingContext(BriefingContext context) {
		return context.nextActivity()
				.map(activity -> {
					List<BriefingEntry> entries = new ArrayList<>();
					entries.add(BriefingEntry.field("Activity", activity.type().name(), Provenance.CRM_ACTIVITY));
					entries.add(BriefingEntry.field("Scheduled for", activity.scheduledAt().toString(),
							Provenance.CRM_ACTIVITY));

					if (activity.hasNoStatedPurpose()) {
						entries.add(BriefingEntry.flagged("Purpose", "No objective recorded",
								Provenance.CRM_ACTIVITY, EntryFlag.NEVER_CAPTURED));
					} else {
						entries.add(BriefingEntry.field("Purpose", activity.purpose(), Provenance.CRM_ACTIVITY));
					}

					if (!activity.participants().isEmpty()) {
						entries.add(BriefingEntry.field("Participants",
								String.join(", ", activity.participants()), Provenance.CRM_ACTIVITY));
					}

					previousInteraction(context).ifPresent(item -> entries.add(new BriefingEntry(
							"Previous interaction",
							"%s on %s".formatted(item.getType(), item.getOccurredAt()),
							Provenance.CRM_ACTIVITY,
							EntryFlag.NONE,
							List.of(MissingInfoRules.toSource(item)))));

					return ProjectedSection.rendered(SectionKey.MEETING_CONTEXT, entries);
				})
				// No meeting is an ordinary state, not a failure. Say so plainly.
				.orElseGet(() -> ProjectedSection.declareEmpty(SectionKey.MEETING_CONTEXT,
						"No upcoming activity is scheduled for this lead."));
	}

	private static ProjectedSection missingInformation(BriefingContext context, Instant now) {
		List<BriefingEntry> entries = MissingInfoRules.evaluate(context, now);

		// An empty Missing Information section is good news, and saying so is more useful than
		// showing nothing - the agent learns the check ran.
		return entries.isEmpty()
				? ProjectedSection.declareEmpty(SectionKey.MISSING_INFORMATION,
						"Nothing obvious is missing from this lead record.")
				: ProjectedSection.rendered(SectionKey.MISSING_INFORMATION, entries);
	}

	/** The lead's progression, straight off the evidence timeline. */
	private static ProjectedSection journey(BriefingContext context) {
		if (context.hasNoEvidence()) {
			return ProjectedSection.degraded(SectionKey.JOURNEY, "No activity recorded yet.");
		}

		List<BriefingEntry> entries = context.evidence().stream()
				.sorted(Comparator.comparing(EvidenceItem::getOccurredAt))
				.skip(Math.max(0, context.evidence().size() - JOURNEY_LIMIT))
				.map(item -> new BriefingEntry(
						item.getOccurredAt().toString(),
						describe(item),
						Provenance.CRM_ACTIVITY,
						item.hasNoText() ? EntryFlag.NOT_SUMMARISED : EntryFlag.NONE,
						List.of(MissingInfoRules.toSource(item))))
				.toList();

		return ProjectedSection.rendered(SectionKey.JOURNEY, entries);
	}

	/**
	 * Source References.
	 *
	 * <p>Not generated at all - it falls out of the data model. Every evidence item that could
	 * back a claim is listed with its deep link, so the agent can open the referenced activity.
	 */
	private static ProjectedSection sourceReferences(BriefingContext context) {
		if (context.hasNoEvidence()) {
			return ProjectedSection.declareEmpty(SectionKey.SOURCE_REFERENCES,
					"No CRM records were available for this lead.");
		}

		// Deduplicated by record, because one note can back several briefing points and the
		// agent only needs one way in.
		Map<String, SourceRef> byKey = new LinkedHashMap<>();
		context.evidence().stream()
				.sorted(Comparator.comparing(EvidenceItem::getOccurredAt).reversed())
				.forEach(item -> byKey.putIfAbsent(item.getEvidenceKey(), MissingInfoRules.toSource(item)));

		List<BriefingEntry> entries = byKey.values().stream()
				.map(source -> new BriefingEntry(
						source.label(),
						"%s - %s".formatted(source.label(), source.occurredAt()),
						Provenance.CRM_ACTIVITY,
						EntryFlag.NONE,
						List.of(source)))
				.toList();

		return ProjectedSection.rendered(SectionKey.SOURCE_REFERENCES, entries);
	}

	private static java.util.Optional<EvidenceItem> previousInteraction(BriefingContext context) {
		return context.evidence().stream().max(Comparator.comparing(EvidenceItem::getOccurredAt));
	}

	private static String describe(EvidenceItem item) {
		if (item.hasNoText()) {
			return "%s (%s) - no summary recorded".formatted(item.getType(), item.getChannel());
		}
		String text = item.getText().strip();
		return text.length() <= 140 ? text : text.substring(0, 137) + "...";
	}

	private static List<SourceRef> sourcesFor(BriefingContext context, AtomicFact fact) {
		return context.evidence().stream()
				.filter(item -> item.getId().equals(fact.getEvidenceId()))
				.map(MissingInfoRules::toSource)
				.toList();
	}

	private static String label(FactKind kind) {
		return switch (kind) {
			case OBJECTION -> "Unresolved objection";
			case COMMITMENT_AGENT -> "You promised";
			case COMMITMENT_CUSTOMER -> "Customer promised";
			case PENDING_DOCUMENT -> "Pending document";
			case UNANSWERED_QUESTION -> "Unanswered question";
			default -> kind.name();
		};
	}
}
