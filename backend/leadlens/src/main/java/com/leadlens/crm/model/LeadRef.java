package com.leadlens.crm.model;

/**
 * Identifies one lead in one CRM.
 *
 * <p>Always resolved from the page URL, never from the DOM (ledger #4). URL shapes are stable
 * across CRM releases; DOM selectors are not, and a mis-identified lead means a briefing about
 * the wrong customer - a worse failure than no briefing at all.
 */
public record LeadRef(String crmKey, String leadRef) {

	public LeadRef {
		if (crmKey == null || crmKey.isBlank()) {
			throw new IllegalArgumentException("crmKey is required");
		}
		if (leadRef == null || leadRef.isBlank()) {
			throw new IllegalArgumentException("leadRef is required");
		}
	}
}
