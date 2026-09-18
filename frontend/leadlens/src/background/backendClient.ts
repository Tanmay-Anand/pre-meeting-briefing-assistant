import type { CrmType } from '../types/crm'
import type { BriefingApiResponse, RunResponse, UpcomingResponse } from '../types/briefingApi'

const DEFAULT_BASE_URL = 'http://localhost:8080'
const POLL_INTERVAL_MS = 1500
const POLL_TIMEOUT_MS = 60_000

function baseUrl(): string {
  return (import.meta.env.VITE_LEADLENS_API_BASE as string | undefined)?.trim() || DEFAULT_BASE_URL
}

function apiToken(): string | undefined {
  const token = import.meta.env.VITE_LEADLENS_API_TOKEN as string | undefined
  return token?.trim() || undefined
}

/**
 * Which LeadLens tenant/user this extension acts as, per CRM — the X-LeadLens-Tenant /
 * X-LeadLens-User headers BriefingController reads (standing in for Phase 8's per-user auth,
 * which does not exist yet; see IMPLEMENTATION_PLAN.md Phase 8's own gap note).
 *
 * 'demo' MUST be a real seeded DemoDataSeeder tenant/user (t-acme/u-priya) — DemoCrmController
 * enforces real tenant/role checks server-side, so an unseeded pair gets an empty/masked result,
 * not an error. 'leadscrm' is unconstrained: LeadsCrmAdapter never forwards these values to
 * leads-crm-backend, they only scope LeadLens's own rows, so any consistent pair works — these
 * match LeadsCrmProperties' own defaults for readability. 'leadrat'/'leadrat-builder' have no
 * registered backend adapter yet (Part K Phase 10 item 6 stub) — generateBriefing() will fail
 * with a clear "unknown CRM" error for those, which is correct until that adapter is built.
 */
const ACTING_USER: Record<CrmType, { tenantId: string; userId: string }> = {
  demo: { tenantId: 't-acme', userId: 'u-priya' },
  leadscrm: { tenantId: 'leadscrm-default', userId: 'leadscrm-service' },
  leadrat: { tenantId: 't-acme', userId: 'u-priya' },
  'leadrat-builder': { tenantId: 't-acme', userId: 'u-priya' },
  unknown: { tenantId: 't-acme', userId: 'u-priya' },
}

export class BackendError extends Error {}

function headers(extra?: HeadersInit): HeadersInit {
  const token = apiToken()
  return {
    'Content-Type': 'application/json',
    ...(token ? { Authorization: `Bearer ${token}` } : {}),
    ...extra,
  }
}

async function raiseOnError(response: Response): Promise<Response> {
  if (response.ok || response.status === 202) return response
  const body = await response.text().catch(() => '')
  throw new BackendError(`${response.status} ${response.statusText}${body ? ` — ${body}` : ''}`)
}

/** Probed at panel startup (Part I.4) — an unreachable or false backend hides Prepare Me. */
export async function fetchBackendAvailable(): Promise<boolean> {
  try {
    const response = await fetch(`${baseUrl()}/api/briefings/status`, { headers: headers() })
    if (!response.ok) return false
    const body = (await response.json()) as { available?: boolean }
    return body.available === true
  } catch {
    return false
  }
}

function sleep(ms: number): Promise<void> {
  return new Promise((resolve) => setTimeout(resolve, ms))
}

async function pollRun(tenantId: string, runId: string): Promise<RunResponse> {
  const deadline = Date.now() + POLL_TIMEOUT_MS
  for (;;) {
    const response = await raiseOnError(
      await fetch(`${baseUrl()}/api/briefings/run/${runId}`, {
        headers: headers({ 'X-LeadLens-Tenant': tenantId }),
      }),
    )
    const run = (await response.json()) as RunResponse

    if (run.status === 'COMPLETE') return run
    if (run.status === 'FAILED') {
      throw new BackendError(run.errorMessage ?? 'Briefing generation failed')
    }
    if (Date.now() > deadline) {
      throw new BackendError('Timed out waiting for the briefing to generate')
    }
    await sleep(POLL_INTERVAL_MS)
  }
}

/**
 * Full generate-and-fetch round trip: POST /api/briefings (cache-or-202), poll the run if it was
 * accepted, then GET the finished document. All network I/O lives here in the service worker,
 * never in the content script or the panel (I.4).
 */
export async function generateBriefing(crm: CrmType, leadId: string): Promise<BriefingApiResponse> {
  const identity = ACTING_USER[crm] ?? ACTING_USER.unknown
  const requestHeaders = headers({
    'X-LeadLens-Tenant': identity.tenantId,
    'X-LeadLens-User': identity.userId,
  })

  const generateResponse = await raiseOnError(
    await fetch(`${baseUrl()}/api/briefings`, {
      method: 'POST',
      headers: requestHeaders,
      body: JSON.stringify({ crmKey: crm, leadRef: leadId }),
    }),
  )

  if (generateResponse.status === 202) {
    const { runId } = (await generateResponse.json()) as { runId: string }
    const run = await pollRun(identity.tenantId, runId)
    if (!run.briefingId) {
      throw new BackendError('Run completed without producing a briefing')
    }
    const briefingResponse = await raiseOnError(
      await fetch(`${baseUrl()}/api/briefings/${run.briefingId}`, {
        headers: headers({ 'X-LeadLens-Tenant': identity.tenantId }),
      }),
    )
    return (await briefingResponse.json()) as BriefingApiResponse
  }

  return (await generateResponse.json()) as BriefingApiResponse
}

/** Backs the "Your meeting is in N minutes — briefing ready" panel surface (Phase 9 step 4). */
export async function fetchUpcoming(crm: CrmType, leadId: string): Promise<UpcomingResponse> {
  const identity = ACTING_USER[crm] ?? ACTING_USER.unknown
  const params = new URLSearchParams({ crmKey: crm, leadRef: leadId })
  const response = await raiseOnError(
    await fetch(`${baseUrl()}/api/briefings/upcoming?${params.toString()}`, {
      headers: headers({ 'X-LeadLens-Tenant': identity.tenantId, 'X-LeadLens-User': identity.userId }),
    }),
  )
  return (await response.json()) as UpcomingResponse
}
