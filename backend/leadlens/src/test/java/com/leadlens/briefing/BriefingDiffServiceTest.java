package com.leadlens.briefing;

import java.util.List;
import java.util.UUID;

import com.leadlens.briefing.model.BriefingEntry;
import com.leadlens.briefing.model.RenderState;
import com.leadlens.briefing.model.SectionKey;
import com.leadlens.facts.Provenance;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What Changed (IMPLEMENTATION_PLAN.md D.5, ledger #9): a deterministic diff between two
 * retained briefing versions' rendered entries, never an LLM call.
 *
 * <p>{@link BriefingDiffService} compares {@link BriefingEntry#text()} strings, not fact ids -
 * "simpler, and still fully deterministic" per the Phase 7 note, because
 * {@code BriefingSectionEntity.entries} is frozen at generation time and comparing two versions'
 * text is comparing two real documents rather than recomputing anything. There is no separate
 * new/resolved/changed enum: a text present only in the newer version lands in
 * {@link BriefingDiffService.SectionDiff#added()}, a text present only in the older version lands
 * in {@link BriefingDiffService.SectionDiff#removed()} - and per that field's own Javadoc,
 * {@code removed} deliberately covers both a genuinely resolved item and a value a later
 * supersession replaced, collapsing D.5's "resolved" and "changed" buckets into one text-level
 * removal.
 *
 * <p>Every case below maps to a real demo failure mode: a new concern silently missing from the
 * panel (case 1), a resolved objection that keeps looking open because nothing was ever
 * subtracted (case 2), and a "What Changed" panel that fires - or omits a section - when nothing
 * about the briefing actually changed (cases 3-6), which is exactly the kind of false confidence
 * D.5 exists to avoid (see also F.9, F.10 on not reassuring the agent with nothing).
 * {@link BriefingSectionRepository} is a Spring Data interface with dozens of inherited abstract
 * methods that this test does not use; hand-implementing all of them to stub one query would not
 * be a cheap real object, so it is mocked instead (Mockito is already on the test classpath via
 * {@code spring-boot-starter-test}).
 */
class BriefingDiffServiceTest {

	private final BriefingSectionRepository sectionRepository = mock(BriefingSectionRepository.class);
	private final BriefingDiffService diffService = new BriefingDiffService(sectionRepository);

	@Test
	@DisplayName("an entry text present only in the newer version is reported as added")
	void entryOnlyInNewerVersionIsAdded() {
		UUID fromId = UUID.randomUUID();
		UUID toId = UUID.randomUUID();
		stub(fromId, section(fromId, SectionKey.OBJECTIONS, "Pricing is a concern"));
		stub(toId, section(toId, SectionKey.OBJECTIONS, "Pricing is a concern", "Wants a payment plan"));

		BriefingDiffService.BriefingDiff diff = diffService.diff(fromId, toId);

		assertThat(diff.sections()).hasSize(1);
		BriefingDiffService.SectionDiff sectionDiff = diff.sections().get(0);
		assertThat(sectionDiff.key()).isEqualTo(SectionKey.OBJECTIONS);
		assertThat(sectionDiff.added())
				.as("a concern raised only in the refreshed version must surface as new, or the "
						+ "agent walks into the meeting not knowing it exists")
				.containsExactly("Wants a payment plan");
		assertThat(sectionDiff.removed()).isEmpty();
	}

	@Test
	@DisplayName("an entry text present only in the older version is reported as removed")
	void entryOnlyInOlderVersionIsRemoved() {
		UUID fromId = UUID.randomUUID();
		UUID toId = UUID.randomUUID();
		stub(fromId, section(fromId, SectionKey.OBJECTIONS, "Customer feels pricing is high", "No timeline yet"));
		stub(toId, section(toId, SectionKey.OBJECTIONS, "No timeline yet"));

		BriefingDiffService.BriefingDiff diff = diffService.diff(fromId, toId);

		assertThat(diff.sections()).hasSize(1);
		BriefingDiffService.SectionDiff sectionDiff = diff.sections().get(0);
		assertThat(sectionDiff.removed())
				.as("whether the objection was genuinely resolved or superseded by a later fact, "
						+ "it must stop looking open once it is gone from the newer version (D.5)")
				.containsExactly("Customer feels pricing is high");
		assertThat(sectionDiff.added()).isEmpty();
	}

	@Test
	@DisplayName("diffing a briefing against itself (same briefingId on both sides) produces no changes")
	void diffingSameBriefingIdProducesNoChanges() {
		UUID briefingId = UUID.randomUUID();
		stub(briefingId, section(briefingId, SectionKey.REQUIREMENTS, "3BHK", "Budget 65L"));

		BriefingDiffService.BriefingDiff diff = diffService.diff(briefingId, briefingId);

		assertThat(diff.isEmpty())
				.as("a briefing cannot have changed relative to itself - a false positive here "
						+ "would fire the What Changed panel for nothing")
				.isTrue();
		assertThat(diff.sections()).isEmpty();
	}

	@Test
	@DisplayName("two distinct versions with identical rendered entries produce no changes")
	void identicalEntriesAcrossDifferentVersionsProduceNoChanges() {
		UUID fromId = UUID.randomUUID();
		UUID toId = UUID.randomUUID();
		stub(fromId, section(fromId, SectionKey.OBJECTIONS, "Pricing is a concern"));
		stub(toId, section(toId, SectionKey.OBJECTIONS, "Pricing is a concern"));

		BriefingDiffService.BriefingDiff diff = diffService.diff(fromId, toId);

		assertThat(diff.isEmpty())
				.as("a refresh that re-persisted the same text (e.g. no new evidence) must not be "
						+ "reported as a change just because the two rows have different ids")
				.isTrue();
	}

	@Test
	@DisplayName("a section entirely absent from the older version reports every entry as added")
	void sectionMissingFromOlderVersionReportsAllEntriesAsAdded() {
		UUID fromId = UUID.randomUUID();
		UUID toId = UUID.randomUUID();
		stub(fromId, section(fromId, SectionKey.REQUIREMENTS, "3BHK"));
		stub(toId,
				section(toId, SectionKey.REQUIREMENTS, "3BHK"),
				section(toId, SectionKey.COMMITMENTS, "Agreed to a site visit Friday"));

		BriefingDiffService.BriefingDiff diff = diffService.diff(fromId, toId);

		assertThat(diff.sections())
				.as("a section that did not exist yet in the older version is not a diffing error "
						+ "- everything in it is new")
				.hasSize(1);
		BriefingDiffService.SectionDiff commitments = diff.sections().get(0);
		assertThat(commitments.key()).isEqualTo(SectionKey.COMMITMENTS);
		assertThat(commitments.added()).containsExactly("Agreed to a site visit Friday");
		assertThat(commitments.removed()).isEmpty();
	}

	@Test
	@DisplayName("sections with no actual difference are left out of the diff entirely")
	void unchangedSectionsAreOmittedFromTheDiff() {
		UUID fromId = UUID.randomUUID();
		UUID toId = UUID.randomUUID();
		stub(fromId,
				section(fromId, SectionKey.REQUIREMENTS, "3BHK"),
				section(fromId, SectionKey.OBJECTIONS, "Pricing is a concern"));
		stub(toId,
				section(toId, SectionKey.REQUIREMENTS, "3BHK"),
				section(toId, SectionKey.OBJECTIONS, "Pricing is a concern", "Wants a payment plan"));

		BriefingDiffService.BriefingDiff diff = diffService.diff(fromId, toId);

		assertThat(diff.sections())
				.as("a what-changed panel listing every section, changed or not, would bury the "
						+ "one line the agent actually needs to read")
				.extracting(BriefingDiffService.SectionDiff::key)
				.containsExactly(SectionKey.OBJECTIONS);
	}

	private void stub(UUID briefingId, BriefingSectionEntity... sections) {
		when(sectionRepository.findByBriefingId(briefingId)).thenReturn(List.of(sections));
	}

	private static BriefingSectionEntity section(UUID briefingId, SectionKey key, String... entryTexts) {
		List<BriefingEntry> entries = List.of(entryTexts).stream()
				.map(text -> BriefingEntry.field(null, text, Provenance.INFERRED))
				.toList();
		return BriefingSectionEntity.builder()
				.briefingId(briefingId)
				.sectionKey(key)
				.renderState(RenderState.RENDER)
				.entries(entries)
				.build();
	}
}
