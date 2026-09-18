import EmptyState from './EmptyState'
import LoadingState from './LoadingState'
import ErrorState from './ErrorState'

export type BriefStatus = 'empty' | 'loading' | 'ready' | 'error'

interface StatusViewProps {
  status: Exclude<BriefStatus, 'ready'>
  errorMessage?: string
}

/** Dispatches to the right full-panel screen for every non-`ready` status. */
export default function StatusView({ status, errorMessage }: StatusViewProps) {
  if (status === 'loading') return <LoadingState />
  if (status === 'error') return <ErrorState message={errorMessage} />
  return <EmptyState />
}
