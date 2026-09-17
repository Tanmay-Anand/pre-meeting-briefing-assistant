/**
 * Demo CRM client.
 *
 * Identity travels in headers, standing in for whatever auth a real CRM uses. The user
 * switcher in the UI exists so the masked-field case is demonstrable: the same lead looks
 * different to a sales agent and to a junior agent, which is what makes "masked" a real kind
 * of empty rather than a story (IMPLEMENTATION_PLAN.md E.6).
 */

export type FieldResponse = {
  value: string | null
  updatedAt: string | null
  masked: boolean
}

export type LeadResponse = {
  id: string
  assignedUserId: string | null
  createdAt: string
  fields: Record<string, FieldResponse>
}

export type ActivityResponse = {
  id: string
  type: string
  occurredAt: string
  actor: string
  channel: string
  text: string | null
  structured: Record<string, unknown>
  scheduled: boolean
  purpose: string | null
  updatedAt: string
}

export type LeadSummary = {
  id: string
  name: string | null
  status: string | null
  createdAt: string
}

export type Identity = { tenantId: string; userId: string; label: string }

export const IDENTITIES: Identity[] = [
  { tenantId: 't-acme', userId: 'u-priya', label: 'Priya Nair — Sales Agent' },
  { tenantId: 't-acme', userId: 'u-arjun', label: 'Arjun Rao — Junior Agent (budget masked)' },
  { tenantId: 't-globex', userId: 'u-meera', label: 'Meera Iyer — Globex tenant' },
]

async function request<T>(path: string, identity: Identity, init?: RequestInit): Promise<T> {
  const response = await fetch(`/api/democrm${path}`, {
    ...init,
    headers: {
      'Content-Type': 'application/json',
      'X-Demo-Tenant': identity.tenantId,
      'X-Demo-User': identity.userId,
      ...(init?.headers ?? {}),
    },
  })
  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`)
  }
  return (await response.json()) as T
}

export const api = {
  listLeads: (identity: Identity) => request<LeadSummary[]>('/leads', identity),

  getLead: (leadId: string, identity: Identity) =>
    request<LeadResponse>(`/leads/${leadId}`, identity),

  getActivities: (leadId: string, identity: Identity) =>
    request<{ activities: ActivityResponse[] }>(`/leads/${leadId}/activities`, identity),

  getScheduled: (leadId: string, identity: Identity) =>
    request<{ activities: ActivityResponse[] }>(`/leads/${leadId}/scheduled`, identity),

  addActivity: (
    leadId: string,
    identity: Identity,
    body: { type: string; actor: string; channel: string; text: string },
  ) =>
    request<ActivityResponse>(`/leads/${leadId}/activities`, identity, {
      method: 'POST',
      body: JSON.stringify(body),
    }),
}
