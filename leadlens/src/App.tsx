import { useCallback, useEffect, useState } from 'react'
import AppHeader from './components/AppHeader'
import CustomerHeader from './components/CustomerHeader'
import AiSummary from './components/AiSummary'
import CustomerSnapshot from './components/CustomerSnapshot'
import Divider from './components/Divider'
import BottomBar from './components/BottomBar'
import StatusView, { type BriefStatus } from './components/StatusView'
import { getCurrentLeadReference, subscribeToLeadChanges } from './services/leadContext'
import { mockLeadBrief } from './mock/leadBrief'
import type { LeadBriefData } from './types/leadBrief'
import type { CrmType, LeadReference } from './types/crm'
import type { GenerateBriefingResult } from './messaging/types'

function isExtensionContext(): boolean {
  return typeof chrome !== 'undefined' && !!chrome.runtime?.id
}

const CRM_LABELS: Record<CrmType, string> = {
  leadrat: 'LeadRat',
  'leadrat-builder': 'LeadRat Builder',
  leadscrm: 'LeadsCRM',
  demo: 'Demo CRM',
  unknown: 'CRM',
}

// Outside the extension (plain `npm run dev` in a browser tab) there is no content script and
// no background worker — force the mock so the UI stays previewable. Inside the extension this
// is false: a real lead click drives a real POST /ai-sdk/query round trip.
// Flip to true to preview the UI against the dummy briefing with nothing else running at all.
const SHOW_MOCK_DATA = false

function App() {
  const [brief, setBrief] = useState<LeadBriefData | null>(() =>
    SHOW_MOCK_DATA || !isExtensionContext() ? mockLeadBrief : null,
  )
  const [status, setStatus] = useState<BriefStatus>(() =>
    SHOW_MOCK_DATA || !isExtensionContext() ? 'ready' : 'empty',
  )

  // Independent of SHOW_MOCK_DATA: surfaces whatever lead the detection layer
  // (click message, page message, or network watcher) last found, so detection can be
  // verified against real CRMs while the panel still shows dummy data.
  const [detectedLead, setDetectedLead] = useState<LeadReference | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined)

  useEffect(() => {
    if (!isExtensionContext()) return
    getCurrentLeadReference().then((reference) => {
      if (reference) setDetectedLead(reference)
    })
    return subscribeToLeadChanges(setDetectedLead)
  }, [])

  // Asks the background worker to do the actual POST /ai-sdk/query round trip (all network I/O
  // stays in the service worker, so the admin password used to authenticate never has to pass
  // through this panel's own code) and renders whatever it returns.
  const loadBrief = useCallback((reference: LeadReference | null) => {
    if (!reference) {
      // LEAD_CLOSED: nobody has a lead open any more, so a stale briefing must not linger.
      setBrief(null)
      setStatus('empty')
      setErrorMessage(undefined)
      return
    }

    setStatus('loading')
    setErrorMessage(undefined)

    chrome.runtime
      .sendMessage({ type: 'GENERATE_BRIEFING', payload: reference })
      .then((result: GenerateBriefingResult) => {
        if (result.ok) {
          setBrief(result.briefing)
          setStatus('ready')
        } else {
          setErrorMessage(result.error)
          setStatus('error')
        }
      })
      .catch((error: unknown) => {
        console.error('[LeadBrief] Failed to generate briefing', error)
        setErrorMessage(error instanceof Error ? error.message : 'Unknown error')
        setStatus('error')
      })
  }, [])

  useEffect(() => {
    if (SHOW_MOCK_DATA || !isExtensionContext()) return

    getCurrentLeadReference().then((reference) => {
      if (reference) loadBrief(reference)
    })

    return subscribeToLeadChanges(loadBrief)
  }, [loadBrief])

  const crmLabel = detectedLead ? CRM_LABELS[detectedLead.crm] : (brief?.crmName ?? 'CRM')

  return (
    <div className="h-screen w-full flex flex-col bg-white">
      <AppHeader crmLabel={crmLabel} connected={!!brief} />

      {detectedLead && (
        <div className="text-[10px] font-mono text-indigo-600 bg-indigo-50 border-b border-indigo-100 px-4 py-1.5 break-all shrink-0">
          Detected lead · {detectedLead.crm} · {detectedLead.leadId}
          {detectedLead.projectName && <> · project {detectedLead.projectName}</>}
        </div>
      )}

      {brief && status === 'ready' ? (
        <>
          <CustomerHeader customer={brief.customer} meeting={brief.meeting} />

          <div className="flex-1 overflow-y-auto px-4 py-4 space-y-1" style={{ scrollbarWidth: 'thin' }}>
            <AiSummary summary={brief.summary} />
            <CustomerSnapshot snapshot={brief.snapshot} />
            <Divider />
          </div>

          <BottomBar updatedAt={brief.updatedAt} onRefresh={() => loadBrief(detectedLead)} />
        </>
      ) : (
        <StatusView status={status === 'ready' ? 'empty' : status} errorMessage={errorMessage} />
      )}
    </div>
  )
}

export default App
