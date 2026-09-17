package com.leadlens.facts;

/**
 * How a claim was derived. <strong>These must never blur.</strong>
 *
 * <p>The brief requires recorded objections to be distinguishable from AI interpretation. That
 * is a data-model requirement, not a prompt instruction (IMPLEMENTATION_PLAN.md C3, E.4): an
 * objection a model <em>read into</em> a WhatsApp message must never render identically to one
 * an agent explicitly logged, because sharing a rendering lets the weaker one borrow the
 * stronger one's authority.
 */
public enum Provenance {

	/** A column on the lead record. Renders as CRM FACT. */
	CRM_FIELD,

	/** A note, call, message, task or status change. Renders as CRM FACT, with a deep link. */
	CRM_ACTIVITY,

	/** Computed in code: days since contact, overdue count, budget delta. Renders as COMPUTED. */
	DERIVED,

	/** Read out of unstructured text by a model. Renders as AI READING, visually marked as
	 *  interpretation. */
	INFERRED;

	/** Whether this class of claim may be presented to the agent as established fact. */
	public boolean isRecorded() {
		return this == CRM_FIELD || this == CRM_ACTIVITY;
	}
}
