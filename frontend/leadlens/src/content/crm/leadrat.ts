import type { CrmAdapter } from './types'
import { genericAdapter } from './generic'

// Tenant-specific subdomain (turbo.leadrat.com, surya.leadrat.com, ...),
// so match on the shared parent domain rather than a fixed hostname.
export const leadratAdapter: CrmAdapter = {
  ...genericAdapter,
  id: 'leadrat',
  matchesHost: (hostname) => hostname.endsWith('.leadrat.com'),
}
