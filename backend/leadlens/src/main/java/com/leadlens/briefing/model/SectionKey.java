package com.leadlens.briefing.model;

/**
 * The briefing's sections.
 *
 * <p>Sections 1-10 are the ones the brief requires. {@link #ATTENTION} and {@link #JOURNEY} are
 * additions, and both are computed without a model - they add reading speed, not a new surface
 * for invention (IMPLEMENTATION_PLAN.md ledger #11, #12).
 *
 * <p>Each key knows two things the rest of the system reads off it rather than deciding for
 * itself: whether producing it involves a model at all (D.2), and how it renders (F.12).
 * Letting a model plan the presentation would be free hallucination surface with no upside,
 * because every useful layout here is already implied by which section it is.
 */
public enum SectionKey {

	ATTENTION(0, false, RenderStyle.RANKED_LIST),
	CUSTOMER_SNAPSHOT(1, false, RenderStyle.FIELD_TABLE),
	MEETING_CONTEXT(2, false, RenderStyle.FIELD_TABLE),
	RECENT_INTERACTIONS(3, true, RenderStyle.TIMELINE),
	REQUIREMENTS(4, true, RenderStyle.FIELD_TABLE),
	PROPERTIES_DISCUSSED(5, true, RenderStyle.TABLE),
	OBJECTIONS(6, true, RenderStyle.LIST),
	COMMITMENTS(7, true, RenderStyle.CHECKLIST),
	TALKING_POINTS(8, true, RenderStyle.LIST),
	MISSING_INFORMATION(9, false, RenderStyle.GROUPED_LIST),
	SOURCE_REFERENCES(10, false, RenderStyle.LIST),
	JOURNEY(11, false, RenderStyle.TIMELINE);

	private final int ordinalInBrief;
	private final boolean usesModel;
	private final RenderStyle renderStyle;

	SectionKey(int ordinalInBrief, boolean usesModel, RenderStyle renderStyle) {
		this.ordinalInBrief = ordinalInBrief;
		this.usesModel = usesModel;
		this.renderStyle = renderStyle;
	}

	public int ordinalInBrief() {
		return ordinalInBrief;
	}

	/**
	 * Whether this section's content passes through a model.
	 *
	 * <p>A section where this is false is structurally incapable of inventing anything, and
	 * still renders when the model is unavailable, over budget, or returning nonsense. That is
	 * what lets a briefing always ship something true (D.2).
	 */
	public boolean usesModel() {
		return usesModel;
	}

	public boolean isDeterministic() {
		return !usesModel;
	}

	public RenderStyle renderStyle() {
		return renderStyle;
	}
}
