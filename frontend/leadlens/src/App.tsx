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
import { getCurrentLeadReference, subscribeToLeadChanges } from './services/leadContext'
import { mockLeadBrief } from './mock/leadBrief'
import type { LeadBriefData } from './types/leadBrief'
import type { CrmType, LeadReference } from './types/crm'

function isExtensionContext(): boolean {
  return typeof chrome !== 'undefined' && !!chrome.runtime?.id
}

const CRM_LABELS: Record<CrmType, string> = {
  leadrat: 'LeadRat',
  'leadrat-builder': 'LeadRat Builder',
  unknown: 'CRM',
}

// While the UI is being built, always show the dummy briefing instead of
// waiting on a real CRM click + backend round trip. Set this back to false
// to resume exercising the real content-script/background/API flow.
const SHOW_MOCK_DATA = true

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

  useEffect(() => {
    if (!isExtensionContext()) return
    getCurrentLeadReference().then((reference) => {
      if (reference) setDetectedLead(reference)
    })
    return subscribeToLeadChanges(setDetectedLead)
  }, [])

  useEffect(() => {
    if (SHOW_MOCK_DATA || !isExtensionContext()) return

    // No backend to fetch a real briefing from yet — log what detection
    // found and fall back to the local mock data so the panel still shows
    // something. Swap this for a real fetch once there's an API again.
    const loadBrief = (reference: LeadReference) => {
      console.log('[LeadBrief] Lead detected:', reference)
      setBrief(mockLeadBrief)
      setStatus('ready')
    }

    getCurrentLeadReference().then((reference) => {
      if (reference) loadBrief(reference)
    })

    return subscribeToLeadChanges(loadBrief)
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
        <StatusView status={status === 'ready' ? 'empty' : status} />
      )}
    </div>
  )
}

export default App
