import type { CrmAdapter } from './types'
import { genericAdapter } from './generic'

// leads-crm-frontend runs two ways: a local Vite dev server, which picks the next free port
// when one is taken (leads-crm-backend's CORS config already allows the whole 5173-5175 range),
// and the deployed Vercel app at a fixed hostname.
const DEV_PORTS = new Set(['5173', '5174', '5175'])
const PROD_HOSTS = new Set(['leads-crm-frontend-nine.vercel.app'])

export const leadscrmAdapter: CrmAdapter = {
  ...genericAdapter,
  id: 'leadscrm',
  matchesHost: (hostname, port) =>
    ((hostname === 'localhost' || hostname === '127.0.0.1') && DEV_PORTS.has(port)) ||
    PROD_HOSTS.has(hostname),
}
