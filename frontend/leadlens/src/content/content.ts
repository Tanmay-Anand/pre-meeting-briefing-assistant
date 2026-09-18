import { detectCrm } from './crm/detector'
import type { ExtensionMessage } from '../messaging/types'

// Mock detection/extraction only: matches on hostname and reads a
// `data-lead-id` attribute. Real adapters will replace both with CRM-specific
// DOM parsing or API calls, behind the same CrmAdapter interface.
const adapter = detectCrm(window.location.href)

function notifyCrmDetected(): void {
  const message: ExtensionMessage = {
    type: 'CRM_CONTEXT',
    payload: {
      crm: adapter.id,
      url: window.location.href,
      timestamp: new Date().toISOString(),
    },
  }

  console.info('[LeadBrief] CRM detected:', adapter.id)
  chrome.runtime.sendMessage(message).catch((error) => {
    console.warn('[LeadBrief] Failed to report CRM context', error)
  })
}

function handleClick(event: MouseEvent): void {
  const target = event.target as Element | null
  const leadElement = target?.closest('[data-lead-id]')
  if (!leadElement) return

  const leadId = adapter.extractLeadId(leadElement)
  if (!leadId) return

  const message: ExtensionMessage = {
    type: 'LEAD_CLICKED',
    payload: { crm: adapter.id, leadId },
  }

  console.info('[LeadBrief] Lead clicked:', message.payload)
  chrome.runtime.sendMessage(message).catch((error) => {
    console.warn('[LeadBrief] Failed to report lead click', error)
  })
}

function handlePageMessage(event: MessageEvent): void {
  // Same-origin only: a page message from another frame/origin is not this CRM announcing its
  // own state, and readPageMessage's contract assumes it only ever sees this page's own data.
  if (event.source !== window || !adapter.readPageMessage) return

  const reference = adapter.readPageMessage(event.data)
  if (reference === undefined) return // not a message this adapter recognises

  const message: ExtensionMessage =
    reference === null ? { type: 'LEAD_CLOSED' } : { type: 'LEAD_OPENED', payload: reference }

  console.info('[LeadBrief] Page message:', message)
  chrome.runtime.sendMessage(message).catch((error) => {
    console.warn('[LeadBrief] Failed to report page message', error)
  })
}

notifyCrmDetected()
document.addEventListener('click', handleClick, true)
window.addEventListener('message', handlePageMessage)
