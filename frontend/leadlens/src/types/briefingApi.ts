/**
 * Mirrors the backend's JSON responses (BriefingController/RunController,
 * briefing/model/*). Kept in sync by hand — see IMPLEMENTATION_PLAN.md Part H.1
 * for the endpoint contract these types describe.
 */

export type BriefingStatus = 'PENDING' | 'PARTIAL' | 'COMPLETE' | 'DEGRADED' | 'FAILED'
export type RunStatus = 'PENDING' | 'RUNNING' | 'COMPLETE' | 'FAILED'
export type RenderState = 'RENDER' | 'DECLARE_EMPTY' | 'DEGRADED'
export type Provenance = 'CRM_FIELD' | 'CRM_ACTIVITY' | 'DERIVED' | 'INFERRED'
export type EntryFlag =
  | 'NONE'
  | 'NEVER_CAPTURED'
  | 'MASKED'
  | 'UNSYNCED'
  | 'CONTRADICTED'
  | 'STALE'
  | 'NOT_SUMMARISED'

export type SectionKey =
  | 'ATTENTION'
  | 'CUSTOMER_SNAPSHOT'
  | 'MEETING_CONTEXT'
  | 'RECENT_INTERACTIONS'
  | 'REQUIREMENTS'
  | 'PROPERTIES_DISCUSSED'
  | 'OBJECTIONS'
  | 'COMMITMENTS'
  | 'TALKING_POINTS'
  | 'MISSING_INFORMATION'
  | 'SOURCE_REFERENCES'
  | 'JOURNEY'

export interface BriefingSourceRef {
  evidenceId: string
  evidenceKey: string
  label: string
  occurredAt: string
  deepLink: string | null
  span: string | null
}

export interface BriefingEntry {
  label: string | null
  text: string
  provenance: Provenance
  flag: EntryFlag
  sources: BriefingSourceRef[]
}

export interface BriefingSection {
  key: SectionKey
  order: number
  renderState: RenderState
  renderStyle: string
  usesModel: boolean
  entries: BriefingEntry[]
}

export interface BriefingApiResponse {
  briefingId: string
  crmKey: string
  leadRef: string
  activityId: string | null
  status: BriefingStatus
  evidenceFingerprint: string
  createdAt: string
  sections: BriefingSection[]
}

export interface RunResponse {
  runId: string
  status: RunStatus
  briefingId: string | null
  completedItems: number
  totalItems: number
  steps: unknown[]
  errorMessage: string | null
}

export interface FieldContradiction {
  field: string
  detail: string
}

export interface LatestResponse {
  briefingId: string | null
  stale: boolean
  lastUpdatedAt: string | null
  fieldContradictions: FieldContradiction[]
}

/** GET /api/briefings/upcoming — drives the "meeting in N minutes" panel surface. */
export interface UpcomingResponse {
  activityType: string | null
  scheduledAt: string | null
  minutesUntil: number | null
  briefingReady: boolean
}
