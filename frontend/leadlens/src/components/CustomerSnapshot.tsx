import type { SnapshotInfo } from '../types/leadBrief'

interface CustomerSnapshotProps {
  snapshot: SnapshotInfo
}

const FIELDS: Array<{ key: keyof SnapshotInfo; label: string }> = [
  { key: 'status', label: 'Status' },
  { key: 'requirement', label: 'Requirement' },
  { key: 'budget', label: 'Budget' },
  { key: 'preferredLocation', label: 'Preferred location' },
  { key: 'timeline', label: 'Timeline' },
]

export default function CustomerSnapshot({ snapshot }: CustomerSnapshotProps) {
  return (
    <section className="lb-card">
      <h2 className="lb-card__title">Customer Snapshot</h2>
      <dl className="lb-snapshot">
        {FIELDS.map(({ key, label }) => (
          <div className="lb-snapshot__row" key={key}>
            <dt>{label}</dt>
            <dd>{snapshot[key]}</dd>
          </div>
        ))}
      </dl>
    </section>
  )
}
