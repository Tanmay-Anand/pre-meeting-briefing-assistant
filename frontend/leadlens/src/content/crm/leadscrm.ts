import type { CrmAdapter } from './types'
import { genericAdapter } from './generic'

// leads-crm-frontend (the hackathon's dummy CRM) has no fixed production hostname yet - it's
// hosted on AWS Amplify with a per-app domain assigned at deploy time. It's demoed as a local
// Vite dev server instead, which picks the next free port when one is taken, so leads-crm-backend's
// own CORS config already allows the same 5173-5175 range rather than a single port.
const DEV_PORTS = new Set(['5173', '5174', '5175'])

export const leadscrmAdapter: CrmAdapter = {
  ...genericAdapter,
  id: 'leadscrm',
  matchesHost: (hostname, port) =>
    (hostname === 'localhost' || hostname === '127.0.0.1') && DEV_PORTS.has(port),
}
