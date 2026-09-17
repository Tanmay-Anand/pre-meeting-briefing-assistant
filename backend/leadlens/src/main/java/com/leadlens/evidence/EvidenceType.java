package com.leadlens.evidence;

/**
 * The kind of CRM record an {@link EvidenceItem} was normalised from.
 *
 * <p>Every heterogeneous CRM record collapses into one of these (IMPLEMENTATION_PLAN.md E.1).
 * Adding a type means teaching the normaliser and the extractor about it; it is not a free
 * extension point.
 */
public enum EvidenceType {
	CALL,
	MESSAGE,
	NOTE,
	TASK,
	MEETING,
	SITE_VISIT,
	STATUS_CHANGE,
	FIELD_UPDATE,
	PROPERTY_SHARED,
	DOCUMENT
}
