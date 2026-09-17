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

notifyCrmDetected()
document.addEventListener('click', handleClick, true)
