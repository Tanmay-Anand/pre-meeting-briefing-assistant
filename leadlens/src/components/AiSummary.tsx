import type { AiSummaryData } from '../types/leadBrief'
import InsightChip from './InsightChip'

interface AiSummaryProps {
  summary: AiSummaryData
}

/** "Before you walk in" callout: the one-paragraph brief + quick-scan chips. */
export default function AiSummary({ summary }: AiSummaryProps) {
  const { text, highlight, chips, source } = summary
  const highlightIndex = highlight ? text.indexOf(highlight) : -1
  const before = highlightIndex >= 0 ? text.slice(0, highlightIndex) : text
  const after = highlightIndex >= 0 ? text.slice(highlightIndex + (highlight?.length ?? 0)) : ''

  return (
    <div className="rounded-xl border border-indigo-100 bg-indigo-50 p-4 mb-4">
      <div className="flex items-center gap-1.5 mb-2.5">
        <div className="w-4 h-4 rounded bg-indigo-600 flex items-center justify-center">
          <svg width="9" height="9" viewBox="0 0 9 9" fill="white">
            <circle cx="4.5" cy="4.5" r="2" />
            <circle cx="4.5" cy="1.5" r="0.8" />
            <circle cx="4.5" cy="7.5" r="0.8" />
            <circle cx="1.5" cy="4.5" r="0.8" />
            <circle cx="7.5" cy="4.5" r="0.8" />
          </svg>
        </div>
        <span className="text-[11px] font-bold text-indigo-700 uppercase tracking-wide">Before you walk in</span>
      </div>
      <p className="text-[12.5px] text-slate-700 leading-relaxed font-medium">
        {before}
        {highlightIndex >= 0 && <span className="text-red-600 font-semibold">{highlight}</span>}
        {after}
      </p>
      {source && (
        <p className={`text-[10px] mt-1.5 ${source.unavailable ? 'text-slate-400 italic' : 'text-indigo-400'}`}>
          {source.unavailable ? source.label : `AI reading · not a CRM fact · ${source.label}`}
        </p>
      )}
      {chips.length > 0 && (
        <div className="flex flex-wrap gap-1.5 mt-3">
          {chips.map((chip) => (
            <InsightChip key={chip.label} label={chip.label} tone={chip.tone} />
          ))}
        </div>
      )}
    </div>
  )
}
