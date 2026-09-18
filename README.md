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

**The extension and the backend are wired together.** The backend (`backend/leadlens/`) implements the full extraction → grounding → composition pipeline described in `IMPLEMENTATION_PLAN.md` (Phases 0-9) against the `CrmAdapter` interface, and the extension calls it: `background.ts` does the `POST /api/briefings` → poll → `GET /api/briefings/{id}` round trip and the panel renders the real 12-section document (`SHOW_MOCK_DATA = false` in `App.tsx`). `crm/leadscrm/LeadsCrmAdapter` is the adapter for this project's actual demo CRM, `leads-crm-backend`/`leads-crm-frontend` - see "What's implemented" below.

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
- `leadscrm.ts` — this project's actual demo CRM, `leads-crm-frontend`'s Vite dev server (`localhost`/`127.0.0.1` on ports 5173-5175)
- `leadrat.ts` — any `*.leadrat.com` subdomain (tenant-specific, e.g. `turbo.leadrat.com`, `surya.leadrat.com`)
- `leadratBuilder.ts` — the fixed hostname `crm.builder.leadratd.com`
- anything else falls back to `generic.ts` (`id: 'unknown'`)

**2. Lead detection — three strategies, because the CRMs expose lead identity differently:**
- **Page message strategy** (`content.ts` + `CrmAdapter.readPageMessage`, `leadscrm.ts`): `leads-crm-frontend`'s lead detail sheet is a React state overlay, not a route change - the URL never carries the lead id, so nothing in the DOM or network is stable to key off. Its `lead-detail-sheet.tsx` instead sends a same-origin `window.postMessage` (`shared/lib/ai-sdk-broadcast.ts` in that repo) on open/close, carrying the lead id and, when the lead has one, its project id and name. This is the one CRM this repo's own extension detects end-to-end today.
- **DOM click strategy** (`content.ts` + `CrmAdapter.extractLeadId`): listens for clicks on `[data-lead-id]` elements. Exercised against the mock CRM test page; neither `leadrat.com` nor `leadratd.com` exposes a usable DOM attribute for this.
- **Network strategy** (`background.ts`, `chrome.webRequest.onBeforeRequest`): for `crm.builder.leadratd.com`, opening a lead's preview fires `GET https://api.crm.builder.leadratd.com/pre-sales/leads/{uuid}` — the UUID is extracted directly from that URL via regex.
- **LeadRat proper (`*.leadrat.com`) has no working detection yet** — it doesn't expose the ID in the URL, the DOM, or (as far as investigated) a distinct network call. The next avenue to try is walking React's internal fiber tree from the clicked DOM element (frameworks like React attach the component's props, including whatever lead object it was given, as hidden properties on the DOM node) — untested against the real app so far.

**3. Side panel UI, wired to a real backend.** Whatever lead reference gets detected (by any strategy) is written to `chrome.storage.session` by the background worker; the side panel (`App.tsx`) reads it via `services/leadContext.ts`, asks the background worker to `POST /api/briefings` (which polls the async run and fetches the finished document), and renders the real 12-section briefing via `lib/mapBriefing.ts`. A small "Detected lead · `{crm}` · `{leadId}`" banner always shows the last detected reference (plus project, when one is known) — useful for confirming detection is working without needing devtools open. Closing the lead (a `LEAD_CLOSED` page message) clears both the detected-lead banner and the panel back to empty, rather than leaving a stale briefing on screen. `SHOW_MOCK_DATA` in `App.tsx` still exists for previewing the UI with no backend running at all.

**4. The AI_NARRATIVE section.** The briefing's first section is a short paragraph from `leads-crm-backend`'s mounted `ai-query-sdk` instance (`POST /ai-sdk/query`, via `AiSdkQueryClient`) - genuinely different from LeadLens's own extraction pipeline: it is the CRM's own AI reading its own records, badged in the panel as "AI reading · not a CRM fact" rather than blended into the grounded sections. Absent (SDK unconfigured/unreachable) degrades this one section without affecting the rest of the document - see `leads-crm-backend/scripts/provision-ai-sdk.sh` for the one-time setup this needs (entities/fields/guardrails are runtime SQLite state inside that SDK, not code, so a fresh clone starts with none enabled). `LeadsCrmAdapter` also sends the lead's phone number on this query (sourced from the same tenant-filtered fetch, never guessed) so the SDK's own WhatsApp-chat-context feature can ground the narrative in that lead's WhatsApp thread when it is configured - a separate, SDK-side-only opt-in (`ai-sdk.whatsapp.*`, an Engageto API key) that this project's own config never touches.

## What's explicitly not implemented yet

- No authentication end-to-end. The backend has a shared-token gate (`TokenAuthFilter`) the extension can send (`VITE_LEADLENS_API_TOKEN`); there is still no per-user identity, just a shared secret plus caller-asserted `X-LeadLens-Tenant`/`X-LeadLens-User` headers (Appendix 2 Q5 territory).
- No lead detection for LeadRat proper (`*.leadrat.com`) — only `leadrat-builder` and `leadscrm` work right now.
- `leadscrm`'s AI narrative source has no tenant model of its own to enforce - `LeadsCrmAdapter.narrate` requires a successful, tenant-filtered `fetchLead` first as the actual control (see that class's doc), but this is a hackathon-scale mitigation, not a production one.
- The duplicate `GET /leads/{id}` `LeadsCrmAdapter` makes per briefing (once for the snapshot/evidence, again inside `narrate`'s tenant check) is unmerged - a known latency cost on the critical path, not a correctness issue.

## Running it

The full loop (a real lead click producing a real briefing) needs four services up, plus one
one-time setup step. See `../Plan.md` at the repo root for the end-to-end architecture; this is
just the run order.

| # | Service | Command | URL |
|---|---|---|---|
| 1 | `leads-crm-backend` | set `AI_SDK_ADMIN_PASSWORD` in `.env` (see below), then `docker compose up -d && set -a && source .env && set +a && ./mvnw spring-boot:run` | `http://localhost:8090/leads-crm` |
| 2 | (once) provision the SDK | `AI_SDK_ADMIN_PASSWORD=... ../leads-crm-backend/scripts/provision-ai-sdk.sh` | — |
| 3 | `leads-crm-frontend` | `npm run dev` | `http://localhost:5173` |
| 4 | `leadlens` backend | `cd backend/leadlens && ./mvnw spring-boot:run` | `http://localhost:8080` |
| 5 | extension | `cd frontend/leadlens && npm run dev:extension` | load unpacked `frontend/leadlens/build` |

Setting `AI_SDK_ADMIN_PASSWORD` in `leads-crm-backend/.env` (12+ characters) is what makes step 2
non-interactive - the SDK syncs its admin password from that env var on every boot, so there is no
OTP to copy out of a log and paste into `/ai-sdk/setup` by hand. Leave it unset to use the
one-time OTP flow instead; `provision-ai-sdk.sh` handles both.

Fill in `backend/leadlens/.env.leadscrm` (copy from `.env.leadscrm.example`) before step 4 -
every value in it is a demo-stopper if blank, and three of them fail with a message that does
not say so (see that file's comments and `LeadsCrmProperties`' javadoc for the two different
tenant ids this adapter needs).

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
