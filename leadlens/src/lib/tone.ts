import type { Tone } from '../types/leadBrief'

/** Shared color mapping so chips, insight icons, etc. stay visually consistent. */
export const chipToneClasses: Record<Tone, string> = {
  danger: 'bg-danger-soft text-danger',
  accent: 'bg-info-soft text-info',
  warn: 'bg-warning-soft text-warning',
  neutral: 'bg-slate-100 text-slate-600',
}

export const iconToneClasses: Record<Tone, string> = {
  danger: 'text-danger',
  accent: 'text-info',
  warn: 'text-warning',
  neutral: 'text-slate-400',
}
