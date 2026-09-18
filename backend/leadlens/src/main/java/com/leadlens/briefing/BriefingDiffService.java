package com.leadlens.briefing;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.leadlens.briefing.model.SectionKey;
import org.springframework.stereotype.Service;

/**
 * What Changed - a deterministic diff between two stored briefing versions, never an LLM call
 * (IMPLEMENTATION_PLAN.md D.5, ledger #9).
 *
 * <p>Comparable because every rendered entry is frozen at generation time
 * ({@code BriefingSectionEntity}, "recomputing the entries later would quietly rewrite
 * history"). Diffing the two documents as they actually were shown is what makes this a
 * comparison of two facts rather than a third thing the agent has to trust.
 */
@Service
public class BriefingDiffService {

	private final BriefingSectionRepository sectionRepository;

	public BriefingDiffService(BriefingSectionRepository sectionRepository) {
		this.sectionRepository = sectionRepository;
	}

	public BriefingDiff diff(UUID fromBriefingId, UUID toBriefingId) {
		Map<SectionKey, BriefingSectionEntity> from = index(sectionRepository.findByBriefingId(fromBriefingId));
		Map<SectionKey, BriefingSectionEntity> to = index(sectionRepository.findByBriefingId(toBriefingId));

		List<SectionDiff> diffs = new ArrayList<>();
		for (SectionKey key : SectionKey.values()) {
			List<String> before = textsOf(from.get(key));
			List<String> after = textsOf(to.get(key));

			List<String> added = after.stream().filter(text -> !before.contains(text)).toList();
			List<String> removed = before.stream().filter(text -> !after.contains(text)).toList();

			if (!added.isEmpty() || !removed.isEmpty()) {
				diffs.add(new SectionDiff(key, added, removed));
			}
		}
		return new BriefingDiff(diffs);
	}

	private static List<String> textsOf(BriefingSectionEntity section) {
		if (section == null) {
			return List.of();
		}
		return section.getEntries().stream().map(entry -> entry.text()).toList();
	}

	private static Map<SectionKey, BriefingSectionEntity> index(List<BriefingSectionEntity> sections) {
		Map<SectionKey, BriefingSectionEntity> map = new EnumMap<>(SectionKey.class);
		sections.forEach(section -> map.put(section.getSectionKey(), section));
		return map;
	}

	/** @param added   text present in the newer version but not the older one
	 *  @param removed text present in the older version but not the newer one - includes both
	 *                 genuinely resolved items and values a supersession replaced */
	public record SectionDiff(SectionKey key, List<String> added, List<String> removed) {
	}

	public record BriefingDiff(List<SectionDiff> sections) {
		public BriefingDiff {
			sections = sections == null ? List.of() : List.copyOf(sections);
		}

		public boolean isEmpty() {
			return sections.isEmpty();
		}
	}
}
