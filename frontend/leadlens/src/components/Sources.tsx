import { useState } from 'react'
import type { SourceRef } from '../types/leadBrief'
import SectionHeader from './SectionHeader'

interface SourcesProps {
  items: SourceRef[]
}

export default function Sources({ items }: SourcesProps) {
  const [open, setOpen] = useState(false)
  if (items.length === 0) return null

  return (
    <div className="mb-2">
      <SectionHeader title="Sources" collapsible open={open} onToggle={() => setOpen((v) => !v)} />
      {open && (
        <div className="space-y-1">
          {items.map((s, i) => (
            <div key={i} className="flex items-center gap-2.5 py-1.5 border-b border-slate-50 last:border-0">
              <span className="text-[11px] text-slate-400 w-4 shrink-0 text-center">{s.icon}</span>
              <div className="flex-1 min-w-0 flex items-center gap-1.5">
                <span className="text-[11px] font-semibold text-slate-700">{s.field}</span>
                <span className="text-slate-200">→</span>
                <span className="text-[11px] text-slate-500">{s.source}</span>
              </div>
              <span className="text-[10px] text-slate-400 shrink-0">{s.date}</span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
