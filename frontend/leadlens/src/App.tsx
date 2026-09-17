import { useEffect, useState } from 'react'
import './App.css'
import Header from './components/Header'
import CustomerSnapshot from './components/CustomerSnapshot'
import KeyInsights from './components/KeyInsights'
import Objections from './components/Objections'
import PendingActions from './components/PendingActions'
import TalkingPoints from './components/TalkingPoints'
import MissingInformation from './components/MissingInformation'
import StatusView, { type BriefStatus } from './components/StatusView'
import { getCurrentLeadReference, subscribeToLeadChanges } from './services/leadContext'
import { mockLeadBrief } from './mock/leadBrief'
import type { LeadBriefData } from './types/leadBrief'
import type { LeadReference } from './types/crm'

function isExtensionContext(): boolean {
  return typeof chrome !== 'undefined' && !!chrome.runtime?.id
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

  return (
    <main className="lb-panel">
      {detectedLead && (
        <div className="lb-debug-banner">
          Detected lead · {detectedLead.crm} · {detectedLead.leadId}
        </div>
      )}
      {brief && status === 'ready' ? (
        <>
          <Header brief={brief} />
          <CustomerSnapshot snapshot={brief.snapshot} />
          <KeyInsights items={brief.keyInsights} />
          <Objections items={brief.objections} />
          <PendingActions items={brief.pendingActions} />
          <TalkingPoints items={brief.talkingPoints} />
          <MissingInformation items={brief.missingInformation} />
        </>
      ) : (
        <StatusView status={status === 'ready' ? 'empty' : status} />
      )}
    </main>
  )
}

export default App
