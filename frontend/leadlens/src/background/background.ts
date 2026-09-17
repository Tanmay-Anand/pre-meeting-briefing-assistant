import { setCurrentLead } from '../services/leadContext'
import type { ExtensionMessage } from '../messaging/types'

// crm.builder.leadratd.com doesn't expose the lead ID in the URL or the DOM —
// opening a lead's preview fires GET /pre-sales/leads/{uuid}, so that network
// call is the only observable signal. If more CRMs need this approach later,
// turn this into a small registry keyed by adapter instead of one-off code.
const LEADRAT_BUILDER_LEAD_PATTERN = /\/pre-sales\/leads\/([0-9a-fA-F-]{36})(?:[/?]|$)/

chrome.webRequest.onBeforeRequest.addListener(
  (details) => {
    const match = details.url.match(LEADRAT_BUILDER_LEAD_PATTERN)
    if (!match) return

    setCurrentLead({ crm: 'leadrat-builder', leadId: match[1] })
      .then(() => {
        if (details.tabId >= 0) {
          return chrome.sidePanel.open({ tabId: details.tabId })
        }
      })
      .catch((error: unknown) => {
        console.error('[LeadBrief] Failed to store lead detected via network call', error)
      })
  },
  { urls: ['https://api.crm.builder.leadratd.com/pre-sales/leads/*'] },
)

chrome.sidePanel
  .setPanelBehavior({ openPanelOnActionClick: true })
  .catch((error: unknown) => {
    console.error('[LeadBrief] Failed to set side panel behavior', error)
  })

chrome.runtime.onMessage.addListener((message: ExtensionMessage, sender) => {
  if (message.type === 'CRM_CONTEXT') {
    console.log('[LeadBrief] CRM context:', message.payload)
    return false
  }

  if (message.type === 'LEAD_CLICKED') {
    const tabId = sender.tab?.id
    setCurrentLead(message.payload)
      .then(() => {
        if (tabId !== undefined) {
          return chrome.sidePanel.open({ tabId })
        }
      })
      .catch((error: unknown) => {
        console.error('[LeadBrief] Failed to open side panel for lead', error)
      })
    return false
  }

  return false
})
