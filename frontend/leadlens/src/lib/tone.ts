import type { Tone } from '../types/leadBrief'

/** Shared color mapping so chips, insight icons, etc. stay visually consistent. */
export const chipToneClasses: Record<Tone, string> = {
  danger: 'bg-red-50 text-red-700 border-red-200',
  accent: 'bg-indigo-50 text-indigo-700 border-indigo-200',
  warn: 'bg-amber-50 text-amber-700 border-amber-200',
  neutral: 'bg-slate-100 text-slate-600 border-slate-200',
}

export const iconToneClasses: Record<Tone, string> = {
  danger: 'text-red-500',
  accent: 'text-indigo-500',
  warn: 'text-amber-500',
  neutral: 'text-slate-400',
}
