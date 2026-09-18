import { useEffect, useState } from 'react'
import AppHeader from './components/AppHeader'
import CustomerHeader from './components/CustomerHeader'
import AiSummary from './components/AiSummary'
import CustomerSnapshot from './components/CustomerSnapshot'
import KeyInsights from './components/KeyInsights'
import Objections from './components/Objections'
import PendingActions from './components/PendingActions'
import TalkingPoints from './components/TalkingPoints'
import MissingInformation from './components/MissingInformation'
import Sources from './components/Sources'
import Divider from './components/Divider'
import BottomBar from './components/BottomBar'
import StatusView, { type BriefStatus } from './components/StatusView'
import UpcomingMeetingBanner from './components/UpcomingMeetingBanner'
import { getCurrentLeadReference, subscribeToLeadChanges } from './services/leadContext'
import { mockLeadBrief } from './mock/leadBrief'
import { mapBriefingToLeadBriefData } from './lib/mapBriefing'
import type { LeadBriefData } from './types/leadBrief'
import type { UpcomingResponse } from './types/briefingApi'
import type { CrmType, LeadReference } from './types/crm'
import type { GenerateBriefingResult, CheckUpcomingResult } from './messaging/types'

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

// Outside the extension (plain `npm run dev` in a browser tab) there is no content script, no
// background worker and no guaranteed backend — force the mock so the UI stays previewable.
// Inside the extension this is false: a real lead click drives a real backend round trip.
// Flip to true to preview the UI against the dummy briefing without a backend running at all.
const SHOW_MOCK_DATA = false

function App() {
  // Outside the extension (plain `npm run dev` in a browser tab) there is no
  // CRM, no background worker, and no backend guaranteed to be running —
  // fall back to mock data so the UI is still previewable while building it.
  const [brief, setBrief] = useState<LeadBriefData | null>(() =>
    SHOW_MOCK_DATA || !isExtensionContext() ? mockLeadBrief : null,
  )
  const [status, setStatus] = useState<BriefStatus>(() =>
    SHOW_MOCK_DATA || !isExtensionContext() ? 'ready' : 'empty',
  )

  // Independent of SHOW_MOCK_DATA: surfaces whatever lead the detection layer
  // (click message or network watcher) last found, so detection can be
  // verified against real CRMs while the panel still shows dummy data.
  const [detectedLead, setDetectedLead] = useState<LeadReference | null>(null)
  const [errorMessage, setErrorMessage] = useState<string | undefined>(undefined)
  const [upcoming, setUpcoming] = useState<UpcomingResponse | null>(null)

  useEffect(() => {
    if (!isExtensionContext()) return
    getCurrentLeadReference().then((reference) => {
      if (reference) setDetectedLead(reference)
    })
    return subscribeToLeadChanges(setDetectedLead)
  }, [])

  useEffect(() => {
    if (SHOW_MOCK_DATA || !isExtensionContext()) return

    // Asks the background worker to do the actual POST /api/briefings -> poll -> GET round trip
    // (all network I/O stays in the service worker, per I.4) and renders whatever it returns.
    const loadBrief = (reference: LeadReference) => {
      setStatus('loading')
      setErrorMessage(undefined)

      chrome.runtime
        .sendMessage({ type: 'GENERATE_BRIEFING', payload: reference })
        .then((result: GenerateBriefingResult) => {
          if (result.ok) {
            setBrief(mapBriefingToLeadBriefData(result.briefing))
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
    }

    getCurrentLeadReference().then((reference) => {
      if (reference) loadBrief(reference)
    })

    return subscribeToLeadChanges(loadBrief)
  }, [])

  useEffect(() => {
    if (SHOW_MOCK_DATA || !isExtensionContext()) return

    // Independent of the briefing fetch above: this can say "ready" before generation finishes
    // (a pre-warmed briefing from Phase 9's worker) or "still preparing" while it's in flight.
    const checkUpcoming = (reference: LeadReference) => {
      chrome.runtime
        .sendMessage({ type: 'CHECK_UPCOMING', payload: reference })
        .then((result: CheckUpcomingResult) => {
          setUpcoming(result.ok && result.upcoming.minutesUntil !== null ? result.upcoming : null)
        })
        .catch(() => setUpcoming(null))
    }

    getCurrentLeadReference().then((reference) => {
      if (reference) checkUpcoming(reference)
    })

    return subscribeToLeadChanges(checkUpcoming)
  }, [])

  const crmLabel = detectedLead ? CRM_LABELS[detectedLead.crm] : (brief?.crmName ?? 'CRM')

  return (
    <div className="h-screen w-full flex flex-col bg-white">
      <AppHeader crmLabel={crmLabel} connected={!!brief} />

      {detectedLead && (
        <div className="text-[10px] font-mono text-indigo-600 bg-indigo-50 border-b border-indigo-100 px-4 py-1.5 break-all shrink-0">
          Detected lead · {detectedLead.crm} · {detectedLead.leadId}
        </div>
      )}

      {brief && status === 'ready' ? (
        <>
          {upcoming && <UpcomingMeetingBanner upcoming={upcoming} />}
          <CustomerHeader customer={brief.customer} meeting={brief.meeting} />

          <div className="flex-1 overflow-y-auto px-4 py-4 space-y-1" style={{ scrollbarWidth: 'thin' }}>
            <AiSummary summary={brief.summary} />
            <CustomerSnapshot snapshot={brief.snapshot} />
            <Divider />
            <KeyInsights items={brief.keyInsights} />
            <Divider />
            <Objections items={brief.objections} />
            <Divider />
            <PendingActions items={brief.pendingActions} />
            <Divider />
            <TalkingPoints items={brief.talkingPoints} />
            <Divider />
            <MissingInformation items={brief.missingInformation} />
            <Sources items={brief.sources} />
          </div>

          <BottomBar updatedAt={brief.updatedAt} />
        </>
      ) : (
        <StatusView status={status === 'ready' ? 'empty' : status} errorMessage={errorMessage} />
      )}
    </div>
  )
}

export default App
