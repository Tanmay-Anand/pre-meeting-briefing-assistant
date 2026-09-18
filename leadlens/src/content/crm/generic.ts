import type { CrmAdapter } from './types'

/**
 * Fallback adapter for any site that isn't a recognized CRM, and the base
 * lead-extraction behavior every mock adapter below reuses: a
 * `data-lead-id` attribute on the clicked element (or an ancestor).
 * Real adapters will replace `extractLeadId` with CRM-specific DOM/API logic.
 */
export const genericAdapter: CrmAdapter = {
  id: 'unknown',
  matchesHost: () => true,
  extractLeadId(element) {
    return element.getAttribute('data-lead-id')
  },
}
