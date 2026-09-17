import type { CrmType } from '../../types/crm'

/**
 * Contract every CRM integration implements. Detection and lead extraction
 * are mocked for now, but real adapters plug into this same interface later
 * without touching the detector, the content script, or the UI.
 */
export interface CrmAdapter {
  id: CrmType
  matchesHost(hostname: string): boolean
  extractLeadId(element: Element): string | null
}
