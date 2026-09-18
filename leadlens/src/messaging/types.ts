import type { CrmContext, LeadReference } from '../types/crm'
import type { LeadBriefData } from '../types/leadBrief'

/**
 * Contract for messages passed between the content script/panel and the
 * background service worker via chrome.runtime.sendMessage.
 */
export type ExtensionMessage =
  | { type: 'CRM_CONTEXT'; payload: CrmContext }
  | { type: 'LEAD_CLICKED'; payload: LeadReference }
  /** Sent by content.ts when a CrmAdapter's readPageMessage recognises a same-origin
   *  window.postMessage from the CRM's own page as a lead opening (see leadscrm.ts). */
  | { type: 'LEAD_OPENED'; payload: LeadReference }
  /** The same channel's "lead closed" counterpart - clears the panel back to empty rather than
   *  leaving a stale briefing on screen for a lead nobody has open any more. */
  | { type: 'LEAD_CLOSED' }
  | { type: 'GENERATE_BRIEFING'; payload: LeadReference }

/**
 * What the panel gets back for GENERATE_BRIEFING — background.ts's listener returns this
 * Promise directly (Chrome resolves it as the sendMessage response), so the panel never touches
 * fetch/network state itself.
 */
export type GenerateBriefingResult =
  | { ok: true; briefing: LeadBriefData }
  | { ok: false; error: string }
