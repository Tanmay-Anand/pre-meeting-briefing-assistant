package com.leadlens.briefing.model;

/** How a section is laid out. Derived from the section, never chosen by a model (F.12). */
public enum RenderStyle {
	PARAGRAPH,
	FIELD_TABLE,
	TABLE,
	LIST,
	RANKED_LIST,
	GROUPED_LIST,
	CHECKLIST,
	TIMELINE
}
