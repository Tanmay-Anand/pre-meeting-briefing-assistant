import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import type { Components } from 'react-markdown'
import type { AiSummaryData } from '../types/leadBrief'
import InsightChip from './InsightChip'

interface AiSummaryProps {
  summary: AiSummaryData
}

const markdownComponents: Components = {
  p: ({ children }) => <p className="text-[12.5px] text-ink leading-relaxed font-medium mb-2 last:mb-0">{children}</p>,
  strong: ({ children }) => <strong className="font-bold text-ink">{children}</strong>,
  em: ({ children }) => <em className="italic">{children}</em>,
  ul: ({ children }) => <ul className="list-disc pl-4 space-y-1 mb-2 last:mb-0">{children}</ul>,
  ol: ({ children }) => <ol className="list-decimal pl-4 space-y-1 mb-2 last:mb-0">{children}</ol>,
  li: ({ children }) => <li className="text-[12.5px] text-ink leading-relaxed">{children}</li>,
  h1: ({ children }) => <p className="text-[13px] font-bold text-ink mb-1.5 mt-2 first:mt-0">{children}</p>,
  h2: ({ children }) => <p className="text-[13px] font-bold text-ink mb-1.5 mt-2 first:mt-0">{children}</p>,
  h3: ({ children }) => <p className="text-[12.5px] font-bold text-ink mb-1 mt-2 first:mt-0">{children}</p>,
  a: ({ children, href }) => (
    <a href={href} target="_blank" rel="noreferrer" className="text-brand underline">
      {children}
    </a>
  ),
  code: ({ children }) => (
    <code className="rounded bg-black/5 px-1 py-0.5 text-[11.5px] font-mono">{children}</code>
  ),
}

/** "Before you walk in" callout: the one-paragraph brief + quick-scan chips. */
export default function AiSummary({ summary }: AiSummaryProps) {
  const { text, chips, source } = summary

  return (
    <div className="rounded-xl border border-line bg-highlight-soft p-4 mb-4">
      <div className="flex items-center gap-1.5 mb-2.5">
        <div className="w-4 h-4 rounded bg-brand flex items-center justify-center">
          <svg width="9" height="9" viewBox="0 0 9 9" fill="white">
            <circle cx="4.5" cy="4.5" r="2" />
            <circle cx="4.5" cy="1.5" r="0.8" />
            <circle cx="4.5" cy="7.5" r="0.8" />
            <circle cx="1.5" cy="4.5" r="0.8" />
            <circle cx="7.5" cy="4.5" r="0.8" />
          </svg>
        </div>
        <span className="text-[11px] font-bold text-muted uppercase tracking-wide">Before you walk in</span>
      </div>
      <ReactMarkdown remarkPlugins={[remarkGfm]} components={markdownComponents}>
        {text}
      </ReactMarkdown>
      {source && (
        <p className={`text-[10px] mt-1.5 ${source.unavailable ? 'text-slate-400 italic' : 'text-muted'}`}>
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
