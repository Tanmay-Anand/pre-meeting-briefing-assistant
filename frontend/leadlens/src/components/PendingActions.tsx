import SectionHeader from './SectionHeader'

interface PendingActionsProps {
  items: string[]
}

export default function PendingActions({ items }: PendingActionsProps) {
  if (items.length === 0) return null

  return (
    <div className="mb-4">
      <SectionHeader title="Pending actions" count={items.length} />
      <div className="space-y-1.5">
        {items.map((action, i) => (
          <div key={i} className="flex items-start gap-2.5 py-1.5">
            <div className="w-4 h-4 rounded-full border-2 border-slate-300 shrink-0 mt-0.5" />
            <span className="text-[12px] text-slate-700 font-medium">{action}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
