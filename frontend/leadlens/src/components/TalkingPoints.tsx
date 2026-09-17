import SectionHeader from './SectionHeader'

interface TalkingPointsProps {
  items: string[]
}

export default function TalkingPoints({ items }: TalkingPointsProps) {
  if (items.length === 0) return null

  return (
    <div className="mb-4">
      <SectionHeader title="What to discuss" />
      <div className="space-y-1.5">
        {items.map((point, i) => (
          <div key={i} className="flex items-start gap-2.5 py-1.5">
            <span className="text-[10px] font-bold text-indigo-400 w-4 shrink-0 mt-0.5 tabular-nums">
              {String(i + 1).padStart(2, '0')}
            </span>
            <span className="text-[12px] text-slate-700 font-medium leading-snug">{point}</span>
          </div>
        ))}
      </div>
    </div>
  )
}
