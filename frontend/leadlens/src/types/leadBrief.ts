export type Tone = 'danger' | 'accent' | 'warn' | 'neutral'
export type ObjectionSeverity = 'high' | 'medium' | 'low'

export interface CustomerInfo {
  name: string
  company: string
  stage: string
}

export interface MeetingInfo {
  time: string
  activity: string
}

export interface SnapshotInfo {
  status: string
  requirement: string
  budget: string
  preferredLocation: string
  timeline: string
}

export interface SummaryChip {
  label: string
  tone: Tone
}

export interface AiSummaryData {
  text: string
  /** Substring of `text` to call out in red, e.g. the core objection. */
  highlight?: string
  chips: SummaryChip[]
  /** Set only when `text` is a rendering of the AI_NARRATIVE section - the CRM's own AI reading,
   *  as distinct from LeadLens's own extraction/grounding pipeline. Null when this fell back to
   *  the ATTENTION-section summary instead, which is deterministic and needs no such badge. */
  source?: { label: string; unavailable?: boolean }
}

export interface KeyInsight {
  text: string
  source: string
  date: string
  icon: string
  tone: Tone
}

export interface Objection {
  title: string
  source: string
  date: string
  severity: ObjectionSeverity
}

export interface SourceRef {
  field: string
  source: string
  date: string
  icon: string
}

export interface LeadBriefData {
  leadId: string
  crmName: string
  updatedAt: string
  customer: CustomerInfo
  meeting: MeetingInfo
  snapshot: SnapshotInfo
  summary: AiSummaryData
  keyInsights: KeyInsight[]
  objections: Objection[]
  pendingActions: string[]
  talkingPoints: string[]
  missingInformation: string[]
  sources: SourceRef[]
}
