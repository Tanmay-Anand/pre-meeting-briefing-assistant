import type { CrmAdapter } from './types'
import { genericAdapter } from './generic'

// mock-crm/index.html is a static test page for the backend's seeded DemoCrmAdapter fixture
// (crmKey "demo") — there's no built frontend/demo-crm/ app (Part G.4 was never built, only
// this harness), so it's served locally on a fixed port the same way leadscrm.ts pins its
// dev-server port range. See the "run it locally" instructions for the serve command.
const DEMO_PORT = '8085'

export const demoAdapter: CrmAdapter = {
  ...genericAdapter,
  id: 'demo',
  matchesHost: (hostname, port) =>
    (hostname === 'localhost' || hostname === '127.0.0.1') && port === DEMO_PORT,
}
