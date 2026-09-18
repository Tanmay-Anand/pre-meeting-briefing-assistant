# LeadBrief (AI CRM Copilot)

LeadBrief is an AI-powered pre-meeting briefing assistant for CRM sales agents. Before a customer meeting, agents normally have to manually piece together context scattered across CRM fields, call notes, WhatsApp threads, and pending tasks. LeadBrief surfaces a single AI-written paragraph — who the customer is, what's happened so far, what to talk about — inside a Chrome side panel, without the agent leaving their CRM.

This is a 2-day hackathon build. **It is a Chrome extension with no backend of its own.** Earlier in the project there was a Spring Boot backend (`backend/leadlens/`) implementing a full extraction → grounding → composition pipeline over CRM notes, meeting transcripts and WhatsApp threads, with per-claim source citations and a CRM-fact/AI-reading distinction. That pipeline was removed - see "What this gave up" below - in favor of the extension calling `leads-crm-backend`'s mounted `ai-query-sdk` instance directly. What's left is much smaller and much easier to run, at a real cost in rigor and security that section spells out.

## Architecture

```
CRM webpage (leads-crm-frontend, LeadRat, LeadRat Builder, ...)
   │
   ├─ content script — detects which CRM is open, watches for lead clicks
   │                    and same-origin page messages
   │
   ▼ chrome.runtime.sendMessage
background service worker — routes messages, opens the side panel,
                              stores the current lead, and on
                              GENERATE_BRIEFING calls leads-crm-backend's
                              ai-query-sdk instance directly:
                                POST /ai-sdk/auth/token  (admin password)
                                POST /ai-sdk/query       (the briefing)
   │
   ▼ chrome.storage.session
React side panel — reads the current lead, renders the AI paragraph
                    plus whatever CRM fields the query response carried
```

The core design principle that survived the rewrite: **the extension is not tightly coupled to any one CRM.** Everything CRM-specific lives behind a `CrmAdapter` interface (hostname matching + lead-ID extraction, DOM or page-message or network based). Detecting a new CRM means adding an adapter file and registering it — the content script, background worker, storage layer and UI never need to change. What each CRM needs on its own side to make this useful is an `ai-query-sdk` instance mounted with its entities/fields configured (see `leads-crm-backend/scripts/provision-ai-sdk.sh` in that sibling repo) - the extension itself has no CRM-specific query logic.

## What this gave up

Removing the backend was a deliberate trade, not a free simplification. Two things changed for the worse, and several features are simply gone:

**1. The SDK admin password now ships inside the extension bundle.** `POST /ai-sdk/auth/token` is the SDK's only auth path, and there is no way to call `/ai-sdk/query` without it. `background/aiSdkClient.ts` holds it (`VITE_AI_SDK_ADMIN_PASSWORD`, baked in at build time). An extension bundle is public - anyone who installs it can extract that password and call the SDK as an admin. Fine for a private, local, single-tenant hackathon demo; **do not ship this to a real deployment** without putting a small server back in front of that call, scoped to exactly this one endpoint.

**2. There is no tenant check before a lead id reaches the SDK.** The previous architecture required a successful, tenant-filtered `GET /leads/{id}` (via Cognito) *before* the SDK ever saw an id - the SDK itself has no tenant model of its own. That control is gone. Any lead id detected in the DOM goes straight to the SDK.

**Features that no longer exist**, because they required the extraction pipeline:
- Per-claim source citations with deep links back to the originating note/call/message.
- The CRM-fact vs. AI-reading distinction (`Provenance`) on individual claims - there is now exactly one LLM paragraph (`answer`) and nothing structured beneath it except whatever raw fields the SDK's traversal returned.
- Structured Objections / Commitments / Talking Points / Missing Information sections. What replaces them is one narrative paragraph that may mention any of these, unstructured.
- "What Changed" diffing between briefing versions, and versioned storage generally - nothing is persisted anywhere now; every panel open is a fresh query.
- Async run progress narration - moot, since `/ai-sdk/query` answers synchronously in one request.
- The pre-meeting scheduled pre-warming worker (`UpcomingActivityWorker`) and the "meeting in N minutes" panel surface it drove.
- Multi-CRM evidence normalisation (notes + meeting transcripts + WhatsApp merged into one fact timeline) - the SDK's own traversal and its own WhatsApp-chat-context feature partially cover this now, but through the SDK's general-purpose lens, not LeadLens's lead-specific one.

**What is still real and still worth having**: CRM detection, three different lead-identification strategies per CRM, a side panel that renders a genuine AI answer sourced from that CRM's own live records (never mock data, never invented), and a couple of real fields (name, status, purchase timeline, scheduled activity) read directly off the SDK's traversal - not fabricated.

## Project structure

```
leadlens/                       Chrome extension (React + TS + Vite, MV3) - the entire project now
  public/manifest.json                   Extension manifest
  mock-crm/index.html                    Standalone test page simulating a CRM
                                          lead list with [data-lead-id] rows
  src/
    types/
      crm.ts                             CrmType, CrmContext, LeadReference
      leadBrief.ts                       LeadBriefData and its nested shapes (trimmed - see above)
      aiSdk.ts                           Wire types for leads-crm-backend's ai-query-sdk
    messaging/types.ts                   ExtensionMessage union (content <-> background)
    content/
      content.ts                         Content script entry: detects CRM on load,
                                          listens for lead clicks and page messages
      crm/
        types.ts                         CrmAdapter interface (extractLeadId, readPageMessage)
        detector.ts                      Registry: hostname -> adapter
        leadscrm.ts                      leads-crm-frontend's Vite dev server, page-message based
        leadrat.ts                       Matches any *.leadrat.com tenant subdomain
        leadratBuilder.ts                Matches crm.builder.leadratd.com exactly
        generic.ts                       Fallback adapter + shared data-lead-id logic
    background/
      background.ts                      Service worker: message routing, side panel,
                                          chrome.webRequest lead detection
      aiSdkClient.ts                     Talks to leads-crm-backend's ai-query-sdk directly -
                                          token exchange + POST /ai-sdk/query. All network I/O
                                          for briefing generation lives here.
    services/leadContext.ts              chrome.storage.session bridge between
                                          background and the side panel
    lib/mapAiSdkResponse.ts              Projects the SDK's raw query response onto the
                                          panel's display shape
    mock/leadBrief.ts                    Hardcoded LeadBriefData for UI preview
    components/                          AppHeader, CustomerHeader, CustomerSnapshot,
                                          AiSummary, BottomBar, StatusView
    App.tsx                              Side panel root; SHOW_MOCK_DATA toggle
```

## What's implemented

**1. CRM detection.** On page load, the content script calls `detectCrm(window.location.href)`, which matches the hostname against the registered adapters:
- `leadscrm.ts` — this project's actual demo CRM, `leads-crm-frontend`'s Vite dev server (`localhost`/`127.0.0.1` on ports 5173-5175)
- `leadrat.ts` — any `*.leadrat.com` subdomain (tenant-specific, e.g. `turbo.leadrat.com`, `surya.leadrat.com`)
- `leadratBuilder.ts` — the fixed hostname `crm.builder.leadratd.com`
- anything else falls back to `generic.ts` (`id: 'unknown'`)

**2. Lead detection — three strategies, because the CRMs expose lead identity differently:**
- **Page message strategy** (`content.ts` + `CrmAdapter.readPageMessage`, `leadscrm.ts`): `leads-crm-frontend`'s lead detail sheet is a React state overlay, not a route change - the URL never carries the lead id, so nothing in the DOM or network is stable to key off. Its `lead-detail-sheet.tsx` instead sends a same-origin `window.postMessage` (`shared/lib/ai-sdk-broadcast.ts` in that repo) on open/close, carrying the lead id and, when the lead has one, its project id and name. This is the one CRM this repo's own extension detects end-to-end today.
- **DOM click strategy** (`content.ts` + `CrmAdapter.extractLeadId`): listens for clicks on `[data-lead-id]` elements. Exercised against the mock CRM test page; neither `leadrat.com` nor `leadratd.com` exposes a usable DOM attribute for this.
- **Network strategy** (`background.ts`, `chrome.webRequest.onBeforeRequest`): for `crm.builder.leadratd.com`, opening a lead's preview fires `GET https://api.crm.builder.leadratd.com/pre-sales/leads/{uuid}` — the UUID is extracted directly from that URL via regex.
- **LeadRat proper (`*.leadrat.com`) has no working detection yet** — it doesn't expose the ID in the URL, the DOM, or (as far as investigated) a distinct network call.

**3. Side panel UI, wired directly to the SDK.** Whatever lead reference gets detected (by any strategy) is written to `chrome.storage.session` by the background worker; the side panel (`App.tsx`) reads it via `services/leadContext.ts`, asks the background worker for `GENERATE_BRIEFING`, which calls `POST /ai-sdk/query` on `leads-crm-backend` (one request, synchronous - no polling, no run tracking) and renders the response via `lib/mapAiSdkResponse.ts`. A small "Detected lead · `{crm}` · `{leadId}`" banner always shows the last detected reference (plus project, when one is known). Closing the lead (a `LEAD_CLOSED` page message) clears both the banner and the panel back to empty. `SHOW_MOCK_DATA` in `App.tsx` still exists for previewing the UI with nothing running at all.

**4. The AI summary.** The panel's one substantive section is the SDK's `answer` - the CRM's own AI reading its own records (Lead, and Project when known, as two query targets - never a relationship, since `Lead.projectId` has no JPA association in `leads-crm-backend`). Badged "AI reading · not a CRM fact" with the model name. A handful of genuinely-present fields (name, status, purchase timeline, scheduled activity) are read directly from the query response's own traversed data and shown in the header/snapshot - never invented, and blank rather than guessed when the SDK didn't return them.

## What's explicitly not implemented

- Everything listed under "What this gave up" above.
- No lead detection for LeadRat proper (`*.leadrat.com`) — only `leadrat-builder` and `leadscrm` work right now.
- No WhatsApp-chat grounding from this extension - the SDK's WhatsApp-chat-context feature resolves a lead's phone number from whatever query-result fields it can see, but `mobile` is masked (see `provision-ai-sdk.sh` in `leads-crm-backend`) and this extension has no separate, authenticated way to read it the way the old server-side adapter did.

## Running it

Two services, no database, no build-time secrets beyond one password.

| # | Service | Command | URL |
|---|---|---|---|
| 1 | `leads-crm-backend` | set `AI_SDK_ADMIN_PASSWORD` in `.env` (see that repo's README), then `docker compose up -d && set -a && source .env && set +a && ./mvnw spring-boot:run` | `http://localhost:8090/leads-crm` |
| 2 | (once) provision the SDK | `AI_SDK_ADMIN_PASSWORD=... ../leads-crm-backend/scripts/provision-ai-sdk.sh` | — |
| 3 | `leads-crm-frontend` | `npm run dev` | `http://localhost:5173` |
| 4 | extension | `cd leadlens && cp .env.example .env` (fill in `VITE_AI_SDK_ADMIN_PASSWORD`) `&& npm run dev:extension` | load unpacked `leadlens/build` |

`VITE_AI_SDK_ADMIN_PASSWORD` must match whatever `leads-crm-backend`'s `AI_SDK_ADMIN_PASSWORD` was set to (or whatever password was set through `/ai-sdk/setup` if that env var was left unset there) - see "What this gave up" for why this is in the extension bundle at all.

**Mock CRM test page** (for exercising the DOM click-detection path without needing real CRM access):
```
npx serve leadlens/mock-crm
```
Must be served over `http://localhost/…` or `http://127.0.0.1/…` — the content script's match patterns don't cover arbitrary `file://` URLs.

## Gotchas worth knowing before touching this again

- **MV3 service worker vs. content script bundling**: `background.ts` is declared as an ES module (`"type": "module"` in the manifest) so it can share code (e.g. `services/leadContext.ts`) with the React app via a bundled chunk. `content.ts` cannot do this — content scripts are always classic scripts, so anything it imports (the `content/crm/*` adapters) gets fully inlined into `content.js` by the bundler instead.
- **`chrome.storage.session` is cleared every time the extension is reloaded** in `chrome://extensions`. When testing lead detection, reload the extension *before* clicking a lead, not after — otherwise you'll be looking at stale/empty storage.
- **Chrome's per-site "site access" permission toggle can silently block `chrome.webRequest`**, even when `host_permissions` in the manifest correctly lists the domain. `host_permissions` is currently set to `["<all_urls>"]` as a result — broader than strictly necessary, kept that way because narrowing it back down wasn't reliably reproducible during testing. Worth revisiting if this becomes more than a hackathon project.
- **MV3 service workers terminate after ~30s idle** and their devtools console history doesn't survive that. If you're debugging background script behavior, don't trust "no console output" — persist anything important to `chrome.storage.session` instead of relying on `console.log` alone.
- **The SDK's `/ai-sdk/query` has no per-lead caching of its own beyond what the SDK does internally** (keyed on config version + discussion/WhatsApp fingerprint, per that project's README) - every panel open is a fresh call, so `BottomBar`'s "Refresh brief" button and simply reopening the panel do the same thing.

## Configuration and secrets

Never committed. `.gitignore` excludes `.env*`; `leadlens/.env.example` documents every key. There is exactly one secret now: `VITE_AI_SDK_ADMIN_PASSWORD`, and it is not really secret once the extension ships - see "What this gave up".
