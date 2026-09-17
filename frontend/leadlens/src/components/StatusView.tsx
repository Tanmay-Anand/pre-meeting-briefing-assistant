import EmptyState from './EmptyState'
import LoadingState from './LoadingState'
import ErrorState from './ErrorState'

export type BriefStatus = 'empty' | 'loading' | 'ready' | 'error'

interface StatusViewProps {
  status: Exclude<BriefStatus, 'ready'>
}

/** Dispatches to the right full-panel screen for every non-`ready` status. */
export default function StatusView({ status }: StatusViewProps) {
  if (status === 'loading') return <LoadingState />
  if (status === 'error') return <ErrorState />
  return <EmptyState />
}
