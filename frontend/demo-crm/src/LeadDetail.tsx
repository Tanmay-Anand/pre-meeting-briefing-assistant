import { useCallback, useEffect, useState } from 'react'
import { api, type ActivityResponse, type Identity, type LeadResponse } from './api'

/**
 * Lead detail page at /leads/{id} — the URL the extension pattern-matches.
 *
 * Kept deliberately plain (R17): this is a stand-in for a CRM, not a product. It needs the
 * fields, the activity timeline and the add-activity form that drives the refresh demo. It
 * does not need search, editing or design.
 */
export function LeadDetail({ leadId, identity }: { leadId: string; identity: Identity }) {
  const [lead, setLead] = useState<LeadResponse | null>(null)
  const [activities, setActivities] = useState<ActivityResponse[]>([])
  const [scheduled, setScheduled] = useState<ActivityResponse[]>([])
  const [error, setError] = useState<string | null>(null)

  const load = useCallback(async () => {
    setError(null)
    try {
      const [leadData, activityData, scheduledData] = await Promise.all([
        api.getLead(leadId, identity),
        api.getActivities(leadId, identity),
        api.getScheduled(leadId, identity),
      ])
      setLead(leadData)
      setActivities(activityData.activities)
      setScheduled(scheduledData.activities)
    } catch (e) {
      // A 403 here is the tenant check doing its job, not a bug. Say which lead and which
      // identity, so the failure is legible during a demo.
      setError(e instanceof Error ? e.message : String(e))
      setLead(null)
    }
  }, [leadId, identity])

  useEffect(() => {
    void load()
  }, [load])

  if (error) {
    return (
      <section>
        <h2>Lead {leadId}</h2>
        <p style={{ color: '#b00' }}>
          Could not load this lead as <strong>{identity.label}</strong>: {error}
        </p>
        <p style={{ color: '#666' }}>
          A 403 is expected when the lead belongs to another tenant — that is the isolation
          check working.
        </p>
      </section>
    )
  }

  if (!lead) {
    return <p>Loading…</p>
  }

  return (
    <section>
      <h2 style={{ marginBottom: 4 }}>{lead.fields['name']?.value ?? `Lead ${leadId}`}</h2>
      <p style={{ color: '#666', marginTop: 0 }}>
        Lead #{lead.id} · assigned to {lead.assignedUserId ?? 'nobody'}
      </p>

      <h3>Fields</h3>
      <table style={{ borderCollapse: 'collapse', width: '100%' }}>
        <tbody>
          {Object.entries(lead.fields).map(([key, field]) => (
            <tr key={key} style={{ borderBottom: '1px solid #eee' }}>
              <td style={{ padding: '6px 12px 6px 0', color: '#666', width: 160 }}>{key}</td>
              <td style={{ padding: '6px 0' }}>
                {field.masked ? (
                  // Masked is shown as masked, never as blank. "Hidden from you" and "never
                  // recorded" lead to different actions, so they must look different.
                  <em style={{ color: '#a60' }}>hidden for your role</em>
                ) : (
                  (field.value ?? <em style={{ color: '#999' }}>not set</em>)
                )}
              </td>
              <td style={{ padding: '6px 0', color: '#999', fontSize: 12 }}>
                {field.updatedAt ? new Date(field.updatedAt).toLocaleDateString() : ''}
              </td>
            </tr>
          ))}
        </tbody>
      </table>

      {scheduled.length > 0 && (
        <>
          <h3>Upcoming</h3>
          <ul>
            {scheduled.map((activity) => (
              <li key={activity.id}>
                <strong>{activity.type}</strong> — {new Date(activity.occurredAt).toLocaleString()}
                {activity.purpose && <div style={{ color: '#666' }}>{activity.purpose}</div>}
              </li>
            ))}
          </ul>
        </>
      )}

      <h3>Activity ({activities.length})</h3>
      <ol style={{ paddingLeft: 18 }}>
        {activities.map((activity) => (
          <li key={activity.id} style={{ marginBottom: 10 }}>
            <div style={{ fontSize: 12, color: '#666' }}>
              {activity.type} · {activity.channel} · {activity.actor} ·{' '}
              {new Date(activity.occurredAt).toLocaleString()}
            </div>
            <div>
              {activity.text ?? (
                // Not the same as an empty activity. A call with a recording and no transcript
                // is a real gap the briefing has to report (E.5).
                <em style={{ color: '#a60' }}>recording only — not summarised</em>
              )}
            </div>
          </li>
        ))}
      </ol>

      <AddActivity leadId={leadId} identity={identity} onAdded={load} />
    </section>
  )
}

/**
 * The demo's pivot. Logging a WhatsApp message here is what makes the briefing go stale and
 * the What Changed panel light up (Part L, step 8).
 */
function AddActivity({
  leadId,
  identity,
  onAdded,
}: {
  leadId: string
  identity: Identity
  onAdded: () => void
}) {
  const [text, setText] = useState(
    'Can increase budget to Rs 1.9 Cr if payment plan is flexible.',
  )
  const [busy, setBusy] = useState(false)

  async function submit(event: React.FormEvent) {
    event.preventDefault()
    setBusy(true)
    try {
      await api.addActivity(leadId, identity, {
        type: 'MESSAGE',
        actor: 'CUSTOMER',
        channel: 'WHATSAPP',
        text,
      })
      onAdded()
    } finally {
      setBusy(false)
    }
  }

  return (
    <form onSubmit={submit} style={{ marginTop: 24, borderTop: '1px solid #ddd', paddingTop: 16 }}>
      <h3 style={{ marginTop: 0 }}>Log an activity</h3>
      <textarea
        value={text}
        onChange={(event) => setText(event.target.value)}
        rows={3}
        style={{ width: '100%', font: 'inherit', padding: 8 }}
      />
      <button type="submit" disabled={busy} style={{ marginTop: 8, padding: '6px 14px' }}>
        {busy ? 'Saving…' : 'Add WhatsApp message'}
      </button>
    </form>
  )
}
