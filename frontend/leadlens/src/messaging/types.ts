import type { CrmContext, LeadReference } from '../types/crm'

/**
 * Contract for messages passed between the content script and the
 * background service worker via chrome.runtime.sendMessage.
 */
export type ExtensionMessage =
  | { type: 'CRM_CONTEXT'; payload: CrmContext }
  | { type: 'LEAD_CLICKED'; payload: LeadReference }
