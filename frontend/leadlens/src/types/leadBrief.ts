export type ObjectionSeverity = 'high' | 'medium' | 'low'

export interface Objection {
  title: string
  source: string
  date: string
  severity: ObjectionSeverity
}

export interface CustomerInfo {
  name: string
  company: string
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

export interface LeadBriefData {
  leadId: string
  customer: CustomerInfo
  meeting: MeetingInfo
  snapshot: SnapshotInfo
  keyInsights: string[]
  objections: Objection[]
  pendingActions: string[]
  talkingPoints: string[]
  missingInformation: string[]
}
