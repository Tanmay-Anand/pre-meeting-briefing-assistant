# LeadLens

**AI pre-meeting briefing assistant, delivered as a CRM-agnostic browser sidecar.**

An agent opens a lead in their CRM, clicks **✨ Prepare Me**, and understands the customer in
ten seconds — requirements, objections, commitments, what's missing, and what to ask — with
every claim traceable to the CRM record it came from.

The design principle throughout: **the model is never trusted to originate a fact.** Roughly
40% of the briefing never touches an LLM, and the rest can only select from pre-extracted,
pre-cited facts. See [`IMPLEMENTATION_PLAN.md`](IMPLEMENTATION_PLAN.md) — it is the single
source of truth for architecture, phases and decisions.

---

## Repository layout

```
backend/leadlens/      Spring Boot 4 API, the briefing engine and the Demo CRM's data API
frontend/              MV3 browser extension (Vite + React + TypeScript)
frontend/demo-crm/     The Demo CRM the extension attaches to
docker-compose.yml     Postgres for local development
```

## Prerequisites

| Tool | Version | Notes |
|---|---|---|
| JDK | **25+** | The build targets Java 25. A JDK 21 on `PATH` will not work — set `JAVA_HOME` to a 25/26 JDK. |
| Node | **22+** | Vite 8 requires Node 20.19+ / 22.12+. |
| Docker | any recent | Needed for the dev database and for integration tests. |

## Running it

Everything goes through the `Makefile`.

```bash
make install
```

```bash
make up
```

`make up` starts Postgres, builds the backend jar and runs it on `http://localhost:8080`.
The schema is created by Hibernate on startup — there is no migration tool, by deliberate
decision (see plan §0.3 and R20).

Then, in separate terminals:

```bash
make dev-demo-crm
```

```bash
make dev-extension
```

The Demo CRM serves on `http://localhost:5174`. That port is referenced by the extension
manifest and by `DemoCrmAdapter`; changing it means changing both.

To load the extension: `make build-extension`, then `chrome://extensions` → *Developer mode*
→ *Load unpacked* → select `frontend/dist/`.

### Useful targets

| Target | What it does |
|---|---|
| `make status` | What is running, and whether it actually answers |
| `make health` | Backend health endpoint |
| `make tail-backend N=200` | Last N lines of the backend log |
| `make demo-rahul` | Dump the main demo lead straight from the Demo CRM API |
| `make infra-reset` | Drop the database volume; the Demo CRM re-seeds on next boot |
| `make down` | Stop everything |

**JDK note.** The build targets Java 25, and the `Makefile` pins `JAVA_HOME` to
`C:/Program Files/Java/jdk-26`. Override it if yours lives elsewhere:
`make build JAVA_HOME=/path/to/jdk`.

**No Docker?** The datasource is env-driven, so you can point the backend at any Postgres:

```bash
make start-backend LEADLENS_DB_URL=jdbc:postgresql://localhost:5432/leadlens LEADLENS_DB_USER=postgres LEADLENS_DB_PASSWORD=secret
```

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

## Where to start reading

| If you want | Read |
|---|---|
| Why the architecture is shaped this way | Plan Parts B, D, F |
| What to build next | Plan Part K (phases), K.2 (checklist coverage) |
| The data model | Plan Part E |
| How a new CRM gets added | Plan Part G |
