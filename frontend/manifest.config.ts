import { defineManifest } from '@crxjs/vite-plugin'

/**
 * MV3 manifest for the LeadLens sidecar.
 *
 * Host permissions stay narrow on purpose. The extension only ever needs to run on CRM
 * pages it has an adapter for, and the adapter's URL patterns come from the backend at
 * runtime (GET /api/crm/adapters) rather than being frozen here - see IMPLEMENTATION_PLAN.md
 * G.3. The entries below are the development targets only; broadening them is a deliberate
 * decision, not a default.
 */
export default defineManifest({
  manifest_version: 3,
  name: 'LeadLens',
  description: 'Understand any lead before you call them. AI pre-meeting briefings, sourced from your CRM.',
  version: '0.1.0',

  action: {
    default_title: 'LeadLens',
  },

  background: {
    service_worker: 'src/background/service-worker.ts',
    type: 'module',
  },

  content_scripts: [
    {
      // Demo CRM (dev) - the primary integration target for the build (G.3).
      matches: ['http://localhost:5174/*'],
      js: ['src/content/content.ts'],
      run_at: 'document_idle',
    },
  ],

  // The panel is a normal extension page loaded into an iframe by the content script, so it
  // must be reachable from the CRM page's origin.
  web_accessible_resources: [
    {
      resources: ['src/panel/index.html'],
      matches: ['http://localhost:5174/*'],
    },
  ],

  permissions: ['storage'],

  // The backend is the only network destination. No LLM provider is ever reachable from
  // here - all model access is backend-side (I.4).
  host_permissions: ['http://localhost:8080/*'],
})
