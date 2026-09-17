export type BriefStatus = 'empty' | 'loading' | 'ready' | 'error'

interface StatusViewProps {
  status: Exclude<BriefStatus, 'ready'>
}

const COPY: Record<StatusViewProps['status'], { title: string; body: string }> = {
  empty: {
    title: 'No lead selected',
    body: 'Open a lead in your CRM to see its LeadBrief here.',
  },
  loading: {
    title: 'Loading briefing…',
    body: 'Fetching the latest information for this lead.',
  },
  error: {
    title: 'Could not load briefing',
    body: 'Check that the LeadBrief backend is running, then try clicking the lead again.',
  },
}

export default function StatusView({ status }: StatusViewProps) {
  const copy = COPY[status]
  return (
    <div className="lb-status">
      <p className="lb-status__title">{copy.title}</p>
      <p className="lb-status__body">{copy.body}</p>
    </div>
  )
}
