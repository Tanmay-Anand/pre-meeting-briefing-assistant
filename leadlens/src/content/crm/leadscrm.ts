import type { LeadReference } from '../../types/crm'
import type { CrmAdapter } from './types'
import { genericAdapter } from './generic'

// leads-crm-frontend runs two ways: a local Vite dev server, which picks the next free port
// when one is taken (leads-crm-backend's CORS config already allows the whole 5173-5175 range),
// and the deployed Vercel app at a fixed hostname.
const DEV_PORTS = new Set(['5173', '5174', '5175'])
const PROD_HOSTS = new Set(['leads-crm-frontend-nine.vercel.app'])

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
    ((hostname === 'localhost' || hostname === '127.0.0.1') && DEV_PORTS.has(port)) ||
    PROD_HOSTS.has(hostname),
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
