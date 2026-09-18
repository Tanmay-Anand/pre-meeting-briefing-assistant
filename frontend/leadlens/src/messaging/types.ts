import type { CrmContext, LeadReference } from '../types/crm'
import type { BriefingApiResponse, UpcomingResponse } from '../types/briefingApi'

/**
 * Contract for messages passed between the content script/panel and the
 * background service worker via chrome.runtime.sendMessage.
 */
export type ExtensionMessage =
  | { type: 'CRM_CONTEXT'; payload: CrmContext }
  | { type: 'LEAD_CLICKED'; payload: LeadReference }
  | { type: 'GENERATE_BRIEFING'; payload: LeadReference }
  | { type: 'CHECK_UPCOMING'; payload: LeadReference }

/**
 * What the panel gets back for GENERATE_BRIEFING — background.ts's listener returns this
 * Promise directly (Chrome resolves it as the sendMessage response), so the panel never touches
 * fetch/network state itself (I.4).
 */
export type GenerateBriefingResult =
  | { ok: true; briefing: BriefingApiResponse }
  | { ok: false; error: string }

export type CheckUpcomingResult =
  | { ok: true; upcoming: UpcomingResponse }
  | { ok: false; error: string }
