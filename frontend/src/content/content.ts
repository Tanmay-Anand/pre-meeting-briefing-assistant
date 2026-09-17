import { send } from '../api/backend'
import { resolveLead } from './url-parser'

/**
 * Content script: detects the lead on the current CRM page and tells the service worker.
 *
 * Phase 0 skeleton - it proves the detection path and the page -> worker channel. Phase 6
 * adds the injected panel (in a shadow root, so CRM styles cannot leak in), the DOM
 * fallback, and the highlight-to-analyse path.
 */

const lead = resolveLead(window.location.href)

if (lead) {
  void send({ type: 'LEAD_DETECTED', lead })
} else {
  // Not an error. A CRM page that is not a lead page is the common case, and guessing a
  // lead from page content is exactly what ledger #4 forbids.
  console.debug('[LeadLens] no lead identified in URL:', window.location.href)
}
