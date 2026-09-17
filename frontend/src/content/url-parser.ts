import type { LeadRef } from '../types/messages'

/**
 * Resolves the lead identity from the page URL.
 *
 * Identity comes from the URL, never the DOM (IMPLEMENTATION_PLAN.md ledger #4). URL shapes
 * are stable across CRM releases; DOM selectors are not, and a mis-identified lead means a
 * briefing about the wrong customer - a far worse failure than no briefing at all.
 *
 * Patterns are supplied by the backend at runtime (GET /api/crm/adapters) so a CRM's URL
 * shape can change without reinstalling the extension. The Phase 0 skeleton below carries
 * only the Demo CRM pattern; Phase 6 replaces this constant with the fetched registry.
 */

export type AdapterPattern = {
  crmKey: string
  /** Regex with a named `leadId` capture group. */
  urlPattern: string
}

export const BUILT_IN_PATTERNS: AdapterPattern[] = [
  { crmKey: 'demo', urlPattern: '^https?://[^/]+/leads/(?<leadId>[^/?#]+)' },
]

/**
 * Returns the lead on this page, or null when the URL does not identify one.
 *
 * Null is a real answer, not a failure to try: the caller must say "no lead found here"
 * rather than guessing from page content.
 */
export function resolveLead(url: string, patterns: AdapterPattern[] = BUILT_IN_PATTERNS): LeadRef | null {
  for (const { crmKey, urlPattern } of patterns) {
    const match = new RegExp(urlPattern).exec(url)
    const leadId = match?.groups?.['leadId']
    if (leadId) {
      return { crmKey, leadRef: leadId }
    }
  }
  return null
}
