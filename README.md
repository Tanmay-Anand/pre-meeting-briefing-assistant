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

There is no real backend integration yet. A Spring Boot skeleton exists (see below) but currently has no endpoints — CRM/lead data is just logged to the console for now, by design (see "Current status").

## Project structure

```
backend/leadlens/                        Spring Boot skeleton (no endpoints yet)
  src/main/java/com/leadlens/
    LeadlensApplication.java             Entry point; excludes datasource/JPA
                                          autoconfig since no DB is configured
    config/SecurityConfig.java           Permit-all + CORS, kept ready for when
                                          real endpoints are added (spring-boot-
                                          starter-security is on the classpath
                                          and will lock down any new controller
                                          by default otherwise)

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

- No real backend endpoints. There were dummy `/api/crm/context` and `/api/leads/brief` endpoints at one point; they were removed in favor of just logging to the console, since the backend wasn't ready to do anything real with the data yet.
- No AI-generated briefing content — the briefing shown is 100% static mock data.
- No authentication, anywhere.
- No lead detection for LeadRat proper (`*.leadrat.com`) — only `leadrat-builder` works right now.
- The `CrmAdapter` interface only supports the DOM-click strategy formally; the network-based strategy for `leadrat-builder` is currently hardcoded in `background.ts` rather than generalized as a per-adapter capability. Worth generalizing once a second network-based CRM shows up.

## Running it

**Frontend (extension):**
```
cd frontend/leadlens
npm run dev:extension      # watches and rebuilds into build/
```
Then in `chrome://extensions`: enable Developer Mode → "Load unpacked" → select `frontend/leadlens/build`.

**Backend (currently just a skeleton, nothing calls it):**
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
