import type { AiSdkQueryResponse, AiSdkTarget, AiSdkTokenResponse } from '../types/aiSdk'

/**
 * Calls leads-crm-backend's mounted ai-query-sdk instance directly - there is no backend of
 * this extension's own any more. That is a real trade-off, not a simplification with no cost:
 * the SDK's only auth path is a single admin password (POST /ai-sdk/auth/token), and that
 * password has to live here, in a public extension bundle, for this to work at all. Accepted
 * for this hackathon-scale project the same way the previous architecture accepted a bearer
 * token in the bundle; do not deploy this to a real tenant's data without a server in between
 * again (see README "What this gave up").
 *
 * The SDK also has no tenant model of its own - unlike the previous LeadsCrmAdapter, nothing
 * here checks that the detected lead id belongs to the CRM tenant this extension is pointed at.
 * A single-tenant local demo is the only setting this is safe in.
 */

const DEFAULT_BASE_URL = 'http://localhost:8090/leads-crm'
const REFRESH_MARGIN_MS = 2 * 60 * 1000

const DEFAULT_QUESTION =
  "This agent has a meeting with this lead shortly. In at most 120 words, summarise who this " +
  'lead is, where they are in the pipeline, and the single most important thing to know before ' +
  'the conversation. Use only the records provided. If something is not in the records, do not ' +
  'mention it.'

function baseUrl(): string {
  return (import.meta.env.VITE_AI_SDK_BASE_URL as string | undefined)?.trim() || DEFAULT_BASE_URL
}

function adminPassword(): string | undefined {
  return (import.meta.env.VITE_AI_SDK_ADMIN_PASSWORD as string | undefined)?.trim() || undefined
}

function question(): string {
  return (import.meta.env.VITE_AI_SDK_QUESTION as string | undefined)?.trim() || DEFAULT_QUESTION
}

export class AiSdkError extends Error {}

let cachedToken: string | undefined
let cachedTokenExpiresAt = 0

async function token(): Promise<string> {
  if (cachedToken && Date.now() < cachedTokenExpiresAt - REFRESH_MARGIN_MS) {
    return cachedToken
  }

  const password = adminPassword()
  if (!password) {
    throw new AiSdkError(
      'VITE_AI_SDK_ADMIN_PASSWORD is not set - see .env.example. The extension has no other ' +
        'way to authenticate against the ai-query-sdk instance.',
    )
  }

  const response = await fetch(`${baseUrl()}/ai-sdk/auth/token`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ password }),
  })
  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new AiSdkError(`ai-sdk auth/token failed: ${response.status}${body ? ` — ${body}` : ''}`)
  }

  const body = (await response.json()) as AiSdkTokenResponse
  cachedToken = body.token
  cachedTokenExpiresAt = Date.now() + (body.expiresIn ?? 3600) * 1000
  return cachedToken
}

/**
 * Queries the SDK for one lead (and, when known, its project) and returns the raw response -
 * the answer paragraph plus the traversed records the SDK's own permission/exposure rules
 * already filtered. Callers map this onto the panel's display shape; this function does not
 * interpret it.
 */
export async function queryLead(leadId: string, projectId?: string): Promise<AiSdkQueryResponse> {
  const jwt = await token()

  const targets: AiSdkTarget[] = [{ entity: 'Lead', id: leadId }]
  if (projectId) {
    targets.push({ entity: 'Project', id: projectId })
  }

  const response = await fetch(`${baseUrl()}/ai-sdk/query`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${jwt}`,
    },
    body: JSON.stringify({
      question: question(),
      targets,
      options: { childDepth: 1, parentDepth: 2, maxChildrenPerRelation: 20 },
    }),
  })

  if (!response.ok) {
    const body = await response.text().catch(() => '')
    throw new AiSdkError(`ai-sdk query failed: ${response.status}${body ? ` — ${body}` : ''}`)
  }

  return (await response.json()) as AiSdkQueryResponse
}
