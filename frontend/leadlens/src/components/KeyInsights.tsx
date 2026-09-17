import { useState } from 'react'
import type { KeyInsight } from '../types/leadBrief'
import { iconToneClasses } from '../lib/tone'
import SectionHeader from './SectionHeader'

interface KeyInsightsProps {
  items: KeyInsight[]
}

export default function KeyInsights({ items }: KeyInsightsProps) {
  const [open, setOpen] = useState(true)
  if (items.length === 0) return null

  return (
    <div className="mb-4">
      <SectionHeader
        title="Key things to know"
        count={items.length}
        collapsible
        open={open}
        onToggle={() => setOpen((v) => !v)}
      />
      {open && (
        <div className="space-y-1">
          {items.map((insight, i) => (
            <div key={i} className="flex gap-2.5 py-2 border-b border-slate-50 last:border-0">
              <span className={`text-[11px] shrink-0 mt-0.5 ${iconToneClasses[insight.tone]}`}>{insight.icon}</span>
              <div className="flex-1 min-w-0">
                <p className="text-[12px] text-slate-700 leading-snug font-medium">{insight.text}</p>
                <p className="text-[10px] text-slate-400 mt-0.5">
                  {insight.source} · {insight.date}
                </p>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
