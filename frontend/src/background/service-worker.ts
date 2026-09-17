import type { BackendStatus, Request } from '../types/messages'

/**
 * Background service worker: the extension's only network boundary.
 *
 * Two reasons this exists rather than fetching from the panel directly (I.4):
 *   1. The CRM page's CSP can block requests issued from page context.
 *   2. The auth token lives here, out of reach of anything injected into the CRM page.
 *
 * The backend is the only destination. No LLM provider key or prompt ever ships in this
 * bundle - an extension bundle is public.
 */

const BACKEND_BASE = 'http://localhost:8080'

async function fetchBackendStatus(): Promise<BackendStatus> {
  try {
    const response = await fetch(`${BACKEND_BASE}/api/briefings/status`)
    if (!response.ok) {
      return { available: false }
    }
    const body = (await response.json()) as Partial<BackendStatus>
    return { available: body.available === true }
  } catch {
    // Unreachable backend is an expected state, not an error worth surfacing as a crash.
    // The UI hides its entry point instead of showing a panel that cannot work.
    return { available: false }
  }
}

chrome.runtime.onMessage.addListener((request: Request, _sender, sendResponse) => {
  switch (request.type) {
    case 'BACKEND_STATUS':
      fetchBackendStatus().then(sendResponse)
      return true // keep the channel open for the async response

    case 'LEAD_DETECTED':
      // Phase 6 wires this to the panel. For now, acknowledging is enough to prove the
      // content script -> worker channel is live.
      sendResponse({ acknowledged: true })
      return false
  }
})
