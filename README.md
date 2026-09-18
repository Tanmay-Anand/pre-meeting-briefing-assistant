# LeadBrief (AI CRM Copilot)

LeadBrief is an AI-powered pre-meeting briefing assistant for CRM sales agents. Before a customer meeting, agents normally have to manually piece together context scattered across CRM fields, call notes, WhatsApp threads, and pending tasks. LeadBrief's goal is to surface a single, concise briefing — who the customer is, what's happened so far, what's outstanding, what to talk about — inside a Chrome side panel, without the agent leaving their CRM.

This is a 2-day hackathon build. It prioritizes a working, demoable pipeline over completeness — most of the "brief" content is still mock data; what's real so far is the **extension architecture and CRM-detection pipeline**.

## Architecture

```
CRM webpage (LeadRat, LeadRat Builder, ...)
   │
   ├─ content script — detects which CRM is open, watches for lead clicks
   │
   ▼ chrome.runtime.sendMessage
background service worker — routes messages, watches network traffic,
                              opens the side panel, stores the current lead
   │
   ▼ chrome.storage.session
React side panel — reads the current lead, renders the briefing UI
```

The core design principle: **the extension is not tightly coupled to any one CRM.** Everything CRM-specific lives behind a `CrmAdapter` interface (hostname matching + lead-ID extraction). Detecting a new CRM or a new way of finding a lead ID means adding an adapter file and registering it — the content script, background worker, storage layer, and UI never need to change.

**The extension and the backend are not wired together yet.** The backend (`backend/leadlens/`) is not a skeleton - it implements the full extraction → grounding → composition pipeline described in `IMPLEMENTATION_PLAN.md` (Phases 0-9) against the `CrmAdapter` interface - but the extension currently never calls it; CRM/lead data detected client-side is just logged to the console, and the panel always renders mock data (see "What's explicitly not implemented yet"). Connecting the two is the largest remaining piece of work.

## Project structure

```
backend/leadlens/                        Spring Boot 4 API - the briefing engine
  src/main/java/com/leadlens/
    evidence/, facts/                    EvidenceItem, AtomicFact - the data model
    ai/                                  LlmClient against OpenRouter
    briefing/                            Deterministic + inferential projectors,
                                          FactSelector, GroundingPolicy, the
                                          BriefingService orchestrator
    run/                                 Async run API (POST /api/briefings -> 202)
    crm/                                 CrmAdapter interface + registry; adapters
                                          for a given CRM live under crm/<key>/
    security/                            TokenAuthFilter (shared-token gate) + CORS
    schedule/                            Pre-meeting scheduled generation worker
  See IMPLEMENTATION_PLAN.md for the full architecture and phase-by-phase detail.

frontend/leadlens/                       Chrome extension (React + TS + Vite, MV3)
  public/manifest.json                   Extension manifest
  mock-crm/index.html                    Standalone test page simulating a CRM
                                          lead list with [data-lead-id] rows
  src/
    types/
      crm.ts                             CrmType, CrmContext, LeadReference
      leadBrief.ts                       LeadBriefData and its nested shapes
    messaging/types.ts                   ExtensionMessage union (content <-> background)
    content/
      content.ts                         Content script entry: detects CRM on
                                          load, listens for lead clicks
      crm/
        types.ts                         CrmAdapter interface
        detector.ts                      Registry: hostname -> adapter
        leadrat.ts                       Matches any *.leadrat.com tenant subdomain
        leadratBuilder.ts                Matches crm.builder.leadratd.com exactly
        generic.ts                       Fallback adapter + shared data-lead-id logic
    background/background.ts             Service worker: message routing,
                                          chrome.webRequest lead detection,
                                          opens the side panel
    services/leadContext.ts              chrome.storage.session bridge between
                                          background and the side panel
    mock/leadBrief.ts                    Hardcoded LeadBriefData for UI preview
    components/                          Header, CustomerSnapshot, KeyInsights,
                                          Objections, PendingActions,
                                          TalkingPoints, MissingInformation,
                                          StatusView
    App.tsx                              Side panel root; SHOW_MOCK_DATA toggle
                                          controls whether the briefing content
                                          is always the mock data
```

## What's implemented

**1. CRM detection.** On page load, the content script calls `detectCrm(window.location.href)`, which matches the hostname against the registered adapters:
- `leadrat.ts` — any `*.leadrat.com` subdomain (tenant-specific, e.g. `turbo.leadrat.com`, `surya.leadrat.com`)
- `leadratBuilder.ts` — the fixed hostname `crm.builder.leadratd.com`
- anything else falls back to `generic.ts` (`id: 'unknown'`)

The detected CRM is currently just logged to the console via the background worker (see "Current status").

**2. Lead-click detection — two different strategies, because the two CRMs expose lead identity differently:**
- **DOM click strategy** (`content.ts` + `CrmAdapter.extractLeadId`): listens for clicks on `[data-lead-id]` elements. This only works against the mock CRM test page right now — neither real CRM exposes a usable DOM attribute.
- **Network strategy** (`background.ts`, `chrome.webRequest.onBeforeRequest`): for `crm.builder.leadratd.com`, opening a lead's preview fires `GET https://api.crm.builder.leadratd.com/pre-sales/leads/{uuid}` — the UUID is extracted directly from that URL via regex. This is the one CRM where lead detection is confirmed working end-to-end.
- **LeadRat proper (`*.leadrat.com`) has no working detection yet** — it doesn't expose the ID in the URL, the DOM, or (as far as investigated) a distinct network call. The next avenue to try is walking React's internal fiber tree from the clicked DOM element (frameworks like React attach the component's props, including whatever lead object it was given, as hidden properties on the DOM node) — untested against the real app so far.

**3. Side panel UI.** Whatever lead reference gets detected (by either strategy) is written to `chrome.storage.session` by the background worker, and the side panel (`App.tsx`) reads it via `services/leadContext.ts` and re-renders when it changes. A small "Detected lead · `{crm}` · `{leadId}`" banner always shows the last detected reference, independent of the mock-data toggle — useful for confirming detection is working without needing devtools open.

The actual briefing content (customer snapshot, objections, pending actions, etc.) is currently **always the hardcoded mock data** (`mock/leadBrief.ts`), controlled by the `SHOW_MOCK_DATA` flag at the top of `App.tsx`. This was intentionally decoupled from real detection while the UI was being built — flip it to `false` to make the panel depend on real detection again (it'll then log the detected lead reference to console and still show the mock briefing, since there's no backend to fetch a real one from).

## What's explicitly not implemented yet

- **The extension never calls the backend.** The backend has real endpoints (`POST /api/briefings`, `GET /api/leads/{leadId}/report`, etc. - see `IMPLEMENTATION_PLAN.md` Part H) and a real pipeline behind them; nothing in `frontend/leadlens` calls any of it yet. Detected CRM/lead data is only logged to the console client-side.
- No AI-generated briefing content **in the panel** — the briefing shown is 100% static mock data (`mock/leadBrief.ts`), independent of whether the backend could produce a real one.
- No authentication end-to-end. The backend has a shared-token gate (`TokenAuthFilter`) ready to be sent by the extension; the extension doesn't send it yet.
- No lead detection for LeadRat proper (`*.leadrat.com`) — only `leadrat-builder` works right now.
- No `CrmAdapter` (backend side) targets the actual demo CRM (`leads-crm-backend`/`leads-crm-frontend`) this project now uses - the extension's detectors are still hardcoded to real `leadrat.com`/`leadratd.com` domains instead.
- The `CrmAdapter` interface (extension side) only supports the DOM-click strategy formally; the network-based strategy for `leadrat-builder` is currently hardcoded in `background.ts` rather than generalized as a per-adapter capability. Worth generalizing once a second network-based CRM shows up.

## Running it

**Frontend (extension):**
```
cd frontend/leadlens
npm run dev:extension      # watches and rebuilds into build/
```
Then in `chrome://extensions`: enable Developer Mode → "Load unpacked" → select `frontend/leadlens/build`.

**Backend (the extension doesn't call it yet, but it's a real API - see "Configuration and secrets" below for required env vars):**
```
cd backend/leadlens
./mvnw spring-boot:run
```

**Mock CRM test page** (for exercising the DOM click-detection path without needing real CRM access):
```
npx serve frontend/leadlens/mock-crm
```
Must be served over `http://localhost/…` or `http://127.0.0.1/…` — the content script's match patterns don't cover arbitrary `file://` URLs.

## Gotchas worth knowing before touching this again

- **MV3 service worker vs. content script bundling**: `background.ts` is declared as an ES module (`"type": "module"` in the manifest) so it can share code (e.g. `services/leadContext.ts`) with the React app via a bundled chunk. `content.ts` cannot do this — content scripts are always classic scripts, so anything it imports (the `content/crm/*` adapters) gets fully inlined into `content.js` by the bundler instead.
- **`chrome.storage.session` is cleared every time the extension is reloaded** in `chrome://extensions`. When testing lead detection, reload the extension *before* clicking a lead, not after — otherwise you'll be looking at stale/empty storage.
- **Chrome's per-site "site access" permission toggle can silently block `chrome.webRequest`**, even when `host_permissions` in the manifest correctly lists the domain. This cost significant debugging time: the extension's site-access list (visible via the puzzle-piece icon → extension → "This can read and change site data") had every listed site toggled off despite the manifest being correct. `host_permissions` is currently set to `["<all_urls>"]` as a result — broader than strictly necessary, kept that way because narrowing it back down wasn't reliably reproducible during testing. Worth revisiting if this becomes more than a hackathon project.
- **MV3 service workers terminate after ~30s idle** and their devtools console history doesn't survive that. If you're debugging background script behavior, don't trust "no console output" — persist anything important to `chrome.storage.session` instead of relying on `console.log` alone.

## Tests

```bash
make verify
```

Unit tests (`*Test`) run under surefire; integration tests (`*IT`) run under failsafe during
`verify`. Integration tests use a real Postgres started by Testcontainers, because the schema
is Hibernate-generated and an in-memory database would validate against the wrong dialect.

**Two conditions make integration tests skip rather than fail**, so a developer is never
blocked by their machine:

| Condition | Effect |
|---|---|
| No Docker daemon | Everything needing a database skips |
| `Selector.open()` fails | Everything needing an embedded web server skips |

CI does not get that option. It sets `LEADLENS_REQUIRE_DOCKER=true`, which turns both skips
into build failures (`DockerRequiredInCiTest`). **A green local build that skipped everything
is not a passing build** — check the skip count before trusting it.

### Known issue: `Unable to establish loopback connection`

On some Windows machines `Selector.open()` fails with this error. Plain loopback sockets
still work, so it looks like networking is fine — but Tomcat's connector and the JDK's
`HttpClient` both need a selector, so **the app cannot start and Docker Desktop often won't
either**. It is usually local security software breaking the authenticated loopback socket
pair that selector creation performs.

Reproduce it in isolation:

```bash
java -e 'try (var s = java.nio.channels.Selector.open()) { System.out.println("OK"); }'
```

Worth trying, in order: restart Docker Desktop, `netsh winsock reset` from an admin prompt
followed by a reboot, then check antivirus/EDR loopback filtering. Until it is fixed, the
backend cannot be run locally on that machine; the unit suite and CI still cover the code.

## Configuration and secrets

Never committed. `.gitignore` excludes `.env*`.

- **Per-CRM config** lives in one file per adapter: `backend/leadlens/.env.demo`,
  `.env.leadrat`, and so on, namespaced under `leadlens.crm.<crmKey>.*`. An adapter reads
  only its own prefix. Adding a CRM is one adapter class plus one env file — no shared
  config to edit. See plan §G.7.
- **The LLM key** (`OPENROUTER_API_KEY`) is a single cross-cutting secret, supplied as an
  environment variable. It is backend-only and must never reach `frontend/` — an extension
  bundle is public.
- **`LEADLENS_API_TOKEN`** gates `/api/briefings/**` and `/api/crm/**` behind a shared bearer
  token (`TokenAuthFilter`, Phase 8). Leave it unset for local development — every request is
  accepted and a warning is logged once. Set it before deploying anywhere reachable off
  localhost; the extension's background service worker sends it as `Authorization: Bearer
  <token>` alongside the identity headers.

## Where to start reading

| If you want | Read |
|---|---|
| Why the architecture is shaped this way | Plan Parts B, D, F |
| What to build next | Plan Part K (phases), K.2 (checklist coverage) |
| The data model | Plan Part E |
| How a new CRM gets added | Plan Part G |
