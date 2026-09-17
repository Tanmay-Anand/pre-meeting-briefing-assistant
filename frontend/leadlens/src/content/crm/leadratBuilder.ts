import type { CrmAdapter } from './types'
import { genericAdapter } from './generic'

// Fixed hostname, unlike leadrat.ts's tenant subdomains.
export const leadratBuilderAdapter: CrmAdapter = {
  ...genericAdapter,
  id: 'leadrat-builder',
  matchesHost: (hostname) => hostname === 'crm.builder.leadratd.com',
}
