import type { LeadReference } from '../../types/crm'
import type { CrmAdapter } from './types'
import { genericAdapter } from './generic'

// leads-crm-frontend (the hackathon's dummy CRM) has no fixed production hostname yet - it's
// hosted on AWS Amplify with a per-app domain assigned at deploy time. It's demoed as a local
// Vite dev server instead, which picks the next free port when one is taken, so leads-crm-backend's
// own CORS config already allows the same 5173-5175 range rather than a single port.
const DEV_PORTS = new Set(['5173', '5174', '5175'])

/** Shape of the `window.postMessage` leads-crm-frontend's lead sheet sends - see that repo's
 *  `shared/lib/ai-sdk-broadcast.ts`. Read loosely and defensively: this is untrusted page data,
 *  not a message this extension controls the shape of. */
interface LeadsCrmMessage {
  source?: unknown
  type?: unknown
  leadId?: unknown
  projectId?: unknown
  projectName?: unknown
}

export const leadscrmAdapter: CrmAdapter = {
  ...genericAdapter,
  id: 'leadscrm',
  matchesHost: (hostname, port) =>
    (hostname === 'localhost' || hostname === '127.0.0.1') && DEV_PORTS.has(port),
  readPageMessage(data): LeadReference | null | undefined {
    if (typeof data !== 'object' || data === null) return undefined
    const message = data as LeadsCrmMessage
    if (message.source !== 'leads-crm') return undefined

    if (message.type === 'LEAD_CLOSED') return null

    if (message.type === 'LEAD_OPENED' && typeof message.leadId === 'string' && message.leadId) {
      return {
        crm: 'leadscrm',
        leadId: message.leadId,
        projectId: typeof message.projectId === 'string' ? message.projectId : undefined,
        projectName: typeof message.projectName === 'string' ? message.projectName : undefined,
        contextSource: 'page-message',
      }
    }

    return undefined
  },
}
