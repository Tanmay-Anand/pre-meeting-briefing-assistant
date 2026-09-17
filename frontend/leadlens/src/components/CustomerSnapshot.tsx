import type { SnapshotInfo } from '../types/leadBrief'
import SectionHeader from './SectionHeader'

interface CustomerSnapshotProps {
  snapshot: SnapshotInfo
}

const FIELDS: Array<{ key: keyof SnapshotInfo; label: string; accent?: boolean }> = [
  { key: 'status', label: 'Status', accent: true },
  { key: 'requirement', label: 'Requirement' },
  { key: 'budget', label: 'Budget' },
  { key: 'preferredLocation', label: 'Location' },
  { key: 'timeline', label: 'Timeline' },
]

export default function CustomerSnapshot({ snapshot }: CustomerSnapshotProps) {
  return (
    <div className="mb-4">
      <SectionHeader title="Customer Snapshot" />
      <div className="grid grid-cols-2 gap-1.5">
        {FIELDS.map(({ key, label, accent }) => (
          <div
            key={key}
            className={`rounded-lg border px-3 py-2.5 ${accent ? 'border-amber-200 bg-amber-50' : 'border-slate-100 bg-white'}`}
          >
            <p
              className={`text-[10px] font-semibold uppercase tracking-wider mb-0.5 ${accent ? 'text-amber-500' : 'text-slate-400'}`}
            >
              {label}
            </p>
            <p className={`text-[12.5px] font-bold ${accent ? 'text-amber-800' : 'text-slate-800'}`}>
              {snapshot[key]}
            </p>
          </div>
        ))}
      </div>
    </div>
  )
}
