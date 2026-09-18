import type { Tone } from '../types/leadBrief'
import { chipToneClasses } from '../lib/tone'

interface InsightChipProps {
  label: string
  tone: Tone
}

export default function InsightChip({ label, tone }: InsightChipProps) {
  return (
    <span className={`inline-flex items-center px-2 py-0.5 rounded text-[11px] font-medium leading-5 border ${chipToneClasses[tone]}`}>
      {label}
    </span>
  )
}
