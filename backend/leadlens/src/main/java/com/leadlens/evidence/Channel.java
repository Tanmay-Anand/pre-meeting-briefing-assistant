package com.leadlens.evidence;

/** How an {@link EvidenceItem} reached the CRM. */
public enum Channel {
	PHONE,
	WHATSAPP,
	EMAIL,
	CRM,
	IN_PERSON,
	/** A video call (Google Meet, etc.) - distinct from PHONE rather than folded into it,
	 *  since a transcribed video call is a different evidentiary source than a logged call. */
	VIDEO_CALL
}
