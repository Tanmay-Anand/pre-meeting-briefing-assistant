import { useState } from 'react'
import type { Objection } from '../types/leadBrief'
import SectionHeader from './SectionHeader'

interface ObjectionsProps {
  items: Objection[]
}

const SEVERITY_STYLES: Record<Objection['severity'], { card: string; dot: string; text: string; meta: string }> = {
  high: { card: 'bg-red-50 border-red-200', dot: 'bg-red-500', text: 'text-red-800', meta: 'text-red-400' },
  medium: {
    card: 'bg-orange-50 border-orange-200',
    dot: 'bg-orange-400',
    text: 'text-orange-800',
    meta: 'text-orange-400',
  },
  low: { card: 'bg-slate-50 border-slate-200', dot: 'bg-slate-400', text: 'text-slate-700', meta: 'text-slate-400' },
}

export default function Objections({ items }: ObjectionsProps) {
  const [open, setOpen] = useState(true)
  if (items.length === 0) return null

  return (
    <div className="mb-4">
      <SectionHeader
        title="Objections"
        count={items.length}
        collapsible
        open={open}
        onToggle={() => setOpen((v) => !v)}
      />
      {open && (
        <div className="space-y-1.5">
          {items.map((objection, i) => {
            const styles = SEVERITY_STYLES[objection.severity]
            return (
              <div key={i} className={`flex items-center gap-2.5 px-3 py-2.5 rounded-lg border ${styles.card}`}>
                <span className={`w-2 h-2 rounded-full shrink-0 ${styles.dot}`} />
                <div className="flex-1 min-w-0">
                  <span className={`text-[12px] font-semibold ${styles.text}`}>{objection.title}</span>
                </div>
                <span className={`text-[10px] font-medium ${styles.meta}`}>
                  {objection.source} · {objection.date}
                </span>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
