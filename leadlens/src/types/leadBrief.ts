export type Tone = 'danger' | 'accent' | 'warn' | 'neutral'

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
  timeline: string
}

export interface SummaryChip {
  label: string
  tone: Tone
}

export interface AiSummaryData {
  text: string
  chips: SummaryChip[]
  /** Citation for `text`: which records were queried and which model answered - there is no
   *  finer-grained provenance than this any more (no per-claim source, no CRM-fact/AI-reading
   *  split), because there is no extraction pipeline behind it - this whole paragraph is one
   *  LLM call over the CRM's own records. */
  source?: { label: string; unavailable?: boolean }
}

export interface LeadBriefData {
  leadId: string
  crmName: string
  updatedAt: string
  customer: CustomerInfo
  meeting: MeetingInfo
  snapshot: SnapshotInfo
  summary: AiSummaryData
}
