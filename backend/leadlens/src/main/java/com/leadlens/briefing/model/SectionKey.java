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

	/**
	 * Not part of the original 1-10/11/12 numbering (ledger #11, #12) - a further addition, same
	 * spirit: it adds reading speed, not a new surface for invention, because it is a rendering
	 * of a {@link com.leadlens.crm.CrmNarrativeSource} answer that is never fed back into fact
	 * extraction. Ordered first because it is the one paragraph an agent reads before anything
	 * else, not because it outranks the grounded sections in reliability - it is the only section
	 * without a citeable {@code AtomicFact} behind it.
	 */
	AI_NARRATIVE(0, true, RenderStyle.PARAGRAPH),
	ATTENTION(1, false, RenderStyle.RANKED_LIST),
	CUSTOMER_SNAPSHOT(2, false, RenderStyle.FIELD_TABLE),
	MEETING_CONTEXT(3, false, RenderStyle.FIELD_TABLE),
	RECENT_INTERACTIONS(4, true, RenderStyle.TIMELINE),
	REQUIREMENTS(5, true, RenderStyle.FIELD_TABLE),
	PROPERTIES_DISCUSSED(6, true, RenderStyle.TABLE),
	OBJECTIONS(7, true, RenderStyle.LIST),
	COMMITMENTS(8, true, RenderStyle.CHECKLIST),
	TALKING_POINTS(9, true, RenderStyle.LIST),
	MISSING_INFORMATION(10, false, RenderStyle.GROUPED_LIST),
	SOURCE_REFERENCES(11, false, RenderStyle.LIST),
	JOURNEY(12, false, RenderStyle.TIMELINE);

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
