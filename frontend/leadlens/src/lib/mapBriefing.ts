import type { BriefingApiResponse, BriefingEntry, BriefingSection, SectionKey } from '../types/briefingApi'
import type { LeadBriefData, Objection, SourceRef, Tone } from '../types/leadBrief'

/**
 * Projects the backend's full 10-section grounded document (Part H.1's BriefingResponse) onto
 * the panel's existing, simpler LeadBriefData shape, so the current UI components can render
 * real data without a rewrite. This is deliberately a *view* over the real document, not a
 * second source of truth — every string here is copied verbatim from a BriefingEntry.text,
 * never reworded (F.3). The full 10-section/provenance-badge panel (Part I.2) is still Phase 6
 * remaining work; this mapper exists to make the current UI real in the meantime.
 */
export function mapBriefingToLeadBriefData(api: BriefingApiResponse): LeadBriefData {
  const byKey = new Map<SectionKey, BriefingSection>(api.sections.map((section) => [section.key, section]))
  const section = (key: SectionKey): BriefingSection | undefined => byKey.get(key)
  const entryText = (label: string, entries: BriefingEntry[] | undefined): string =>
    entries?.find((entry) => entry.label === label)?.text ?? ''

  const snapshotEntries = section('CUSTOMER_SNAPSHOT')?.entries
  const meetingEntries = section('MEETING_CONTEXT')?.entries
  const attentionSection = section('ATTENTION')
  const objectionsSection = section('OBJECTIONS')
  const commitmentsSection = section('COMMITMENTS')

  const customerName = entryText('Customer', snapshotEntries) || 'Unknown customer'
  const leadStatus = entryText('Lead status', snapshotEntries)

  return {
    leadId: api.leadRef,
    crmName: api.crmKey,
    updatedAt: formatUpdatedAt(api.createdAt),
    customer: {
      name: customerName,
      company: '', // Not modeled server-side (no company field on LeadSnapshot) — left blank rather than guessed.
      stage: leadStatus || 'Unknown stage',
    },
    meeting: {
      time: entryText('Scheduled for', meetingEntries) || '',
      activity: entryText('Activity', meetingEntries) || sectionStatement(section('MEETING_CONTEXT')) || 'No upcoming activity scheduled',
    },
    snapshot: {
      status: leadStatus,
      requirement: entryText('Requirement', snapshotEntries),
      budget: entryText('Budget', snapshotEntries),
      preferredLocation: entryText('Preferred location', snapshotEntries),
      timeline: entryText('Purchase timeline', snapshotEntries),
    },
    summary: {
      text: summaryText(attentionSection),
      chips: [
        ...(objectionsSection?.renderState === 'RENDER'
          ? [{ label: `${objectionsSection.entries.length} open objection${objectionsSection.entries.length === 1 ? '' : 's'}`, tone: 'danger' as Tone }]
          : []),
        ...(commitmentsSection?.renderState === 'RENDER'
          ? [{ label: `${commitmentsSection.entries.length} pending item${commitmentsSection.entries.length === 1 ? '' : 's'}`, tone: 'warn' as Tone }]
          : []),
      ],
    },
    keyInsights: mapEntries(section('RECENT_INTERACTIONS'), (entry) => ({
      text: entry.text,
      source: entry.sources[0]?.label ?? '',
      date: formatDate(entry.sources[0]?.occurredAt),
      icon: '◎',
      tone: toneForProvenance(entry.provenance),
    })),
    objections: mapEntries(objectionsSection, (entry): Objection => ({
      title: entry.text,
      source: entry.sources[0]?.label ?? '',
      date: formatDate(entry.sources[0]?.occurredAt),
      // The backend does not classify severity today — every real objection renders as
      // 'medium' rather than a guessed high/low, matching E.6's "never invent" rule.
      severity: 'medium',
    })),
    pendingActions: mapEntries(commitmentsSection, describeEntry),
    talkingPoints: mapEntries(section('TALKING_POINTS'), (entry) => entry.text),
    missingInformation: mapEntries(section('MISSING_INFORMATION'), describeEntry),
    sources: mapEntries(section('SOURCE_REFERENCES'), (entry): SourceRef => ({
      field: entry.label ?? entry.sources[0]?.label ?? 'Record',
      source: entry.sources[0]?.label ?? '',
      date: formatDate(entry.sources[0]?.occurredAt),
      icon: '↗',
    })),
  }
}

function mapEntries<T>(section: BriefingSection | undefined, project: (entry: BriefingEntry) => T): T[] {
  if (!section || section.renderState !== 'RENDER') return []
  return section.entries.map(project)
}

function describeEntry(entry: BriefingEntry): string {
  return entry.label ? `${entry.label}: ${entry.text}` : entry.text
}

function sectionStatement(section: BriefingSection | undefined): string | undefined {
  // DECLARE_EMPTY/DEGRADED sections carry their explanation as the sole entry's text
  // (ProjectedSection.declareEmpty/degraded — see DeterministicProjector/InferentialProjector).
  return section?.entries[0]?.text
}

function summaryText(attention: BriefingSection | undefined): string {
  if (!attention || attention.entries.length === 0) {
    return sectionStatement(attention) ?? 'No summary available yet.'
  }
  return attention.entries.map((entry) => entry.text).join(' ')
}

function toneForProvenance(provenance: BriefingEntry['provenance']): Tone {
  switch (provenance) {
    case 'INFERRED':
      return 'accent'
    case 'DERIVED':
      return 'warn'
    default:
      return 'neutral'
  }
}

function formatDate(occurredAt: string | undefined): string {
  if (!occurredAt) return ''
  const date = new Date(occurredAt)
  return Number.isNaN(date.getTime())
    ? occurredAt
    : date.toLocaleDateString(undefined, { day: 'numeric', month: 'short' })
}

function formatUpdatedAt(createdAt: string): string {
  const date = new Date(createdAt)
  return Number.isNaN(date.getTime()) ? createdAt : date.toLocaleString()
}
