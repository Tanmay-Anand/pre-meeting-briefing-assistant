import type { CrmType } from '../../types/crm'

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
}
