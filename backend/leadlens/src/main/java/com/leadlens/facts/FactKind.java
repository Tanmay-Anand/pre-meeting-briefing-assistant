package com.leadlens.facts;

/**
 * What a fact is about, which determines where it lands in the briefing and how selection
 * treats it (IMPLEMENTATION_PLAN.md E.3).
 *
 * <p>Each kind carries its own {@link InclusionFloor} and, where it corresponds to a field on
 * the lead record, the {@code attributeKey} that field-versus-fact comparison uses (F.16).
 * A kind whose {@link #leadFieldAttribute()} is null simply never participates in the
 * contradiction check - that is a correct outcome, not a gap.
 */
public enum FactKind {

	/** A stated requirement: "3BHK", "needs parking". */
	REQUIREMENT(InclusionFloor.LATEST_PER_ATTRIBUTE, "bhk"),

	/** A softer preference: locality, floor, facing. */
	PREFERENCE(InclusionFloor.LATEST_PER_ATTRIBUTE, "location"),

	/** A budget figure the customer stated. */
	BUDGET_STATEMENT(InclusionFloor.LATEST_PLUS_PRIOR_IF_CHANGED, "budget"),

	/** A purchase-timeline statement. */
	TIMELINE_STATEMENT(InclusionFloor.LATEST, "timeline"),

	/** A financing or loan requirement. */
	FINANCING_NEED(InclusionFloor.LATEST, "financing"),

	/** The customer's response to a specific property or project. */
	PROPERTY_RESPONSE(InclusionFloor.ALL, null),

	/** An objection or concern. Never aged out while open. */
	OBJECTION(InclusionFloor.ALL_OPEN, null),

	/** Something the agent promised to do. */
	COMMITMENT_AGENT(InclusionFloor.ALL_OPEN, null),

	/** Something the customer promised to do. */
	COMMITMENT_CUSTOMER(InclusionFloor.ALL_OPEN, null),

	/** A document still owed by either side. */
	PENDING_DOCUMENT(InclusionFloor.ALL_OPEN, null),

	/** A question raised and never answered. */
	UNANSWERED_QUESTION(InclusionFloor.ALL_OPEN, null),

	/** Who actually decides. Feeds Missing Information when absent. */
	DECISION_MAKER(InclusionFloor.ANY, "decisionMaker"),

	/** A condensed account of one interaction. */
	INTERACTION_SUMMARY(InclusionFloor.RECENCY_CAPPED, null);

	private final InclusionFloor inclusionFloor;
	private final String leadFieldAttribute;

	FactKind(InclusionFloor inclusionFloor, String leadFieldAttribute) {
		this.inclusionFloor = inclusionFloor;
		this.leadFieldAttribute = leadFieldAttribute;
	}

	public InclusionFloor inclusionFloor() {
		return inclusionFloor;
	}

	/**
	 * The lead-record attribute this kind corresponds to, or null when it has no field
	 * counterpart. Used only by the field-versus-fact synchronisation check (F.16).
	 */
	public String leadFieldAttribute() {
		return leadFieldAttribute;
	}

	/** Whether facts of this kind can be compared against a lead field at all. */
	public boolean mapsToLeadField() {
		return leadFieldAttribute != null;
	}

	/** Whether an open fact of this kind must survive selection regardless of age. */
	public boolean survivesAtAnyAge() {
		return inclusionFloor == InclusionFloor.ALL_OPEN;
	}
}
