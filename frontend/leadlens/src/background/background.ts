import { setCurrentLead } from '../services/leadContext'
import { generateBriefing, fetchUpcoming, BackendError } from './backendClient'
import type { ExtensionMessage, GenerateBriefingResult, CheckUpcomingResult } from '../messaging/types'

function toErrorMessage(error: unknown): string {
  return error instanceof BackendError || error instanceof Error ? error.message : 'Unknown error'
}

// crm.builder.leadratd.com doesn't expose the lead ID in the URL or the DOM —
// opening a lead's preview fires GET /pre-sales/leads/{uuid}, so that network
// call is the only observable signal. If more CRMs need this approach later,
// turn this into a small registry keyed by adapter instead of one-off code.
const LEADRAT_BUILDER_LEAD_PATTERN = /\/pre-sales\/leads\/([0-9a-fA-F-]{36})(?:[/?]|$)/

chrome.webRequest.onBeforeRequest.addListener(
  (details) => {
    const match = details.url.match(LEADRAT_BUILDER_LEAD_PATTERN)
    if (!match) return

    // sidePanel.open() must be called synchronously in the handler, before any await/.then —
    // Chrome only honors it within the same turn as the triggering event, and a network
    // request isn't a user gesture to begin with, so this may still be refused by Chrome. The
    // storage write below has no such restriction and can safely stay async.
    if (details.tabId >= 0) {
      chrome.sidePanel.open({ tabId: details.tabId }).catch((error: unknown) => {
        console.error('[LeadBrief] Failed to open side panel for lead (network-detected)', error)
      })
    }

    setCurrentLead({ crm: 'leadrat-builder', leadId: match[1] }).catch((error: unknown) => {
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
    // sidePanel.open() must be the first thing called, synchronously, in this listener —
    // Chrome only honors "may only be called in response to a user gesture" within the same
    // synchronous turn as the message that carried it. Calling it after an awaited
    // setCurrentLead() (as this used to) loses that window and Chrome silently refuses it.
    if (tabId !== undefined) {
      chrome.sidePanel.open({ tabId }).catch((error: unknown) => {
        console.error('[LeadBrief] Failed to open side panel for lead', error)
      })
    }
    setCurrentLead(message.payload).catch((error: unknown) => {
      console.error('[LeadBrief] Failed to store lead reference', error)
    })
    return false
  }

  if (message.type === 'GENERATE_BRIEFING') {
    // Returning a Promise here is how Chrome sends an async response in MV3 — the panel's
    // sendMessage call resolves with whatever this resolves to. All network I/O stays in this
    // worker, never in the panel itself (I.4).
    return generateBriefing(message.payload.crm, message.payload.leadId)
      .then((briefing): GenerateBriefingResult => ({ ok: true, briefing }))
      .catch((error: unknown): GenerateBriefingResult => ({ ok: false, error: toErrorMessage(error) }))
  }

  if (message.type === 'CHECK_UPCOMING') {
    return fetchUpcoming(message.payload.crm, message.payload.leadId)
      .then((upcoming): CheckUpcomingResult => ({ ok: true, upcoming }))
      .catch((error: unknown): CheckUpcomingResult => ({ ok: false, error: toErrorMessage(error) }))
  }

  return false
})
