/**
 * Message protocol between the panel / content script and the background service worker.
 *
 * Nothing in the extension talks to the backend directly. The CRM page's content-security
 * policy can block requests made from page context, and the auth token must not be readable
 * from an injected script, so every network call is proxied through the service worker.
 * See IMPLEMENTATION_PLAN.md I.4.
 */

export type BackendStatus = {
  /** False when the backend is unreachable; the UI hides its entry point rather than
   *  rendering a panel that cannot work. */
  available: boolean
}

/** A lead the extension has identified on the current page, resolved from the URL. */
export type LeadRef = {
  crmKey: string
  leadRef: string
}

export type Request =
  | { type: 'BACKEND_STATUS' }
  | { type: 'LEAD_DETECTED'; lead: LeadRef }

export type ResponseFor<R extends Request> = R extends { type: 'BACKEND_STATUS' }
  ? BackendStatus
  : R extends { type: 'LEAD_DETECTED' }
    ? { acknowledged: true }
    : never
