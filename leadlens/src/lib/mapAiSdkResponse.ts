import type { AiSdkQueryResponse } from '../types/aiSdk'
import type { LeadBriefData } from '../types/leadBrief'

const TIMELINE_LABELS: Record<string, string> = {
  IMMEDIATE: 'Immediate',
  ONE_MONTH: 'Within 1 month',
  THREE_MONTHS: 'Within 3 months',
  SIX_MONTHS: 'Within 6 months',
  ONE_YEAR: 'Within 1 year',
  EXPLORING: 'Just exploring',
}

function str(value: unknown): string | undefined {
  return typeof value === 'string' && value.trim() ? value : undefined
}

function readableTimeline(value: unknown): string | undefined {
  const raw = str(value)
  if (!raw) return undefined
  return TIMELINE_LABELS[raw] ?? raw.toLowerCase().replaceAll('_', ' ')
}

function formatDate(value: unknown): string {
  const raw = str(value)
  if (!raw) return ''
  const date = new Date(raw)
  return Number.isNaN(date.getTime()) ? raw : date.toLocaleString()
}

/**
 * Projects the SDK's raw query response onto the panel's display shape. This is a much thinner
 * mapping than the old backend-fed one: `self` fields are genuine CRM columns the SDK's own
 * field-exposure rules already filtered, never invented here, but there is no per-claim source
 * or CRM-fact/AI-reading distinction any more - that required the extraction pipeline this
 * project no longer has. `answer` is one LLM paragraph over everything the query touched.
 */
export function mapAiSdkResponseToLeadBrief(
  response: AiSdkQueryResponse,
  crmName: string,
  leadId: string,
  projectId?: string,
): LeadBriefData {
  const leadNode = response.data[`Lead:${leadId}`]
  const self = leadNode?.self ?? {}
  const projectNode = projectId ? response.data[`Project:${projectId}`] : undefined
  const projectName = str(projectNode?.self.name)

  const firstName = str(self.firstName)
  const lastName = str(self.lastName)
  const name = [firstName, lastName].filter(Boolean).join(' ').trim() || 'Unknown customer'

  const statusParent = leadNode?.parents.find((parent) => parent.relation === 'status')
  const stage = str(statusParent?.data.displayName) ?? str(statusParent?.data.name) ?? 'Unknown stage'

  const timeline = readableTimeline(self.purchaseTimeline) ?? ''

  return {
    leadId,
    crmName,
    updatedAt: formatDate(response.meta.generatedAt) || new Date().toLocaleString(),
    customer: {
      name,
      company: projectName ?? '',
      stage,
    },
    meeting: {
      time: formatDate(self.scheduleDate),
      activity: self.scheduleDate ? 'Scheduled activity' : 'No upcoming activity on record',
    },
    snapshot: {
      status: stage,
      timeline,
    },
    summary: {
      text: response.answer,
      chips: response.meta.cached ? [{ label: 'Cached answer', tone: 'neutral' }] : [],
      source: { label: `CRM AI · ${response.meta.summarizerModel}` },
    },
  }
}
