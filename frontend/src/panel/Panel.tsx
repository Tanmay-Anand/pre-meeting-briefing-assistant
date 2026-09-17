import { useEffect, useState } from 'react'
import { send } from '../api/backend'

/**
 * The LeadLens panel.
 *
 * Phase 0 skeleton: it probes the backend and reports honestly whether it is reachable.
 * Phase 6 builds the real surface - section 0 Attention, sections 1-10, the source drawer,
 * the freshness pill and the What Changed panel.
 *
 * The probe is not decoration. When the backend is down, the panel says so instead of
 * offering a "Prepare Me" button that cannot work (I.4).
 */
export function Panel() {
  const [status, setStatus] = useState<'checking' | 'available' | 'unavailable'>('checking')

  useEffect(() => {
    send({ type: 'BACKEND_STATUS' })
      .then((result) => setStatus(result.available ? 'available' : 'unavailable'))
      .catch(() => setStatus('unavailable'))
  }, [])

  return (
    <main style={{ font: '14px system-ui, sans-serif', padding: '16px', minWidth: '320px' }}>
      <h1 style={{ fontSize: '16px', margin: '0 0 12px' }}>LeadLens</h1>

      {status === 'checking' && <p>Checking backend…</p>}

      {status === 'available' && (
        <p>Backend reachable. Briefing UI arrives in Phase 6.</p>
      )}

      {status === 'unavailable' && (
        <p>
          LeadLens backend is unreachable. Start it with <code>./mvnw spring-boot:run</code>{' '}
          in <code>backend/leadlens</code>.
        </p>
      )}
    </main>
  )
}
