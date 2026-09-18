import type { CrmType, LeadReference } from '../../types/crm'

/**
 * Contract every CRM integration implements. Detection and lead extraction
 * are mocked for now, but real adapters plug into this same interface later
 * without touching the detector, the content script, or the UI.
 */
export interface CrmAdapter {
  id: CrmType
  // port is '' when the URL has none (e.g. a production CRM on the default https port).
  // Adapters that match on a fixed hostname can ignore it; leadscrm.ts needs it since its
  // dev server has no fixed domain, only a local port.
  matchesHost(hostname: string, port: string): boolean
  extractLeadId(element: Element): string | null
  /**
   * Optional second detection strategy: a same-origin `window.postMessage` the CRM's own page
   * sends when a lead opens or closes, instead of (or in addition to) a DOM click landing on a
   * `[data-lead-id]` element - the only signal available for a CRM whose lead detail view is a
   * state-driven overlay rather than a route change (leads-crm-frontend's lead sheet never
   * changes the URL, so there is nothing for `extractLeadId` to key off of on its own).
   *
   * Returns a full reference (including project context, when the message carried one) for a
   * "lead opened" message, `null` for a "lead closed" message, and `undefined` when `data` is
   * not a message this adapter recognises - `content.ts` ignores it in that last case rather
   * than forwarding a null lead reference.
   */
  readPageMessage?(data: unknown): LeadReference | null | undefined
}
