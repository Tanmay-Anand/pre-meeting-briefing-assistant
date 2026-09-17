import { useEffect, useState } from 'react'
import { api, IDENTITIES, type Identity, type LeadSummary } from './api'
import { LeadDetail } from './LeadDetail'

/**
 * Demo CRM shell.
 *
 * This is the CRM the extension attaches to, and the primary integration target for the build
 * (IMPLEMENTATION_PLAN.md G.3, G.4). Routing is path-based rather than hash-based on purpose:
 * the extension resolves lead identity from the URL path, so the URL has to look like a real
 * CRM's.
 */
export function App() {
  const [path, setPath] = useState(window.location.pathname)
  const [identity, setIdentity] = useState<Identity>(IDENTITIES[0]!)

  useEffect(() => {
    const onPopState = () => setPath(window.location.pathname)
    window.addEventListener('popstate', onPopState)
    return () => window.removeEventListener('popstate', onPopState)
  }, [])

  function navigate(to: string) {
    window.history.pushState({}, '', to)
    setPath(to)
  }

  const leadMatch = /^\/leads\/([^/?#]+)/.exec(path)

  return (
    <main style={{ font: '15px system-ui, sans-serif', padding: 24, maxWidth: 900, margin: '0 auto' }}>
      <header style={{ display: 'flex', alignItems: 'baseline', gap: 16, marginBottom: 20 }}>
        <h1 style={{ fontSize: 20, margin: 0, cursor: 'pointer' }} onClick={() => navigate('/')}>
          Demo CRM
        </h1>
        <label style={{ marginLeft: 'auto', fontSize: 13, color: '#666' }}>
          Signed in as{' '}
          <select
            value={identity.userId}
            onChange={(event) =>
              setIdentity(IDENTITIES.find((i) => i.userId === event.target.value) ?? IDENTITIES[0]!)
            }
          >
            {IDENTITIES.map((i) => (
              <option key={i.userId} value={i.userId}>
                {i.label}
              </option>
            ))}
          </select>
        </label>
      </header>

      {leadMatch ? (
        <>
          <button onClick={() => navigate('/')} style={{ marginBottom: 12 }}>
            ← All leads
          </button>
          <LeadDetail leadId={leadMatch[1]!} identity={identity} />
        </>
      ) : (
        <LeadList identity={identity} onOpen={(id) => navigate(`/leads/${id}`)} />
      )}
    </main>
  )
}

function LeadList({ identity, onOpen }: { identity: Identity; onOpen: (id: string) => void }) {
  const [leads, setLeads] = useState<LeadSummary[]>([])
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    api
      .listLeads(identity)
      .then(setLeads)
      .catch((e: unknown) => setError(e instanceof Error ? e.message : String(e)))
  }, [identity])

  if (error) {
    return <p style={{ color: '#b00' }}>Could not load leads: {error}</p>
  }

  return (
    <section>
      <h2>Leads</h2>
      <p style={{ color: '#666' }}>
        A tenant only ever sees its own leads — switching to the Globex user shows a different
        list, which is the isolation the briefing engine depends on.
      </p>
      <ul>
        {leads.map((lead) => (
          <li key={lead.id} style={{ marginBottom: 6 }}>
            <a
              href={`/leads/${lead.id}`}
              onClick={(event) => {
                event.preventDefault()
                onOpen(lead.id)
              }}
            >
              {lead.name ?? lead.id}
            </a>{' '}
            <span style={{ color: '#666' }}>· {lead.status ?? 'no status'}</span>
          </li>
        ))}
      </ul>
    </section>
  )
}
