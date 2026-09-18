interface SectionHeaderProps {
  title: string
  count?: number
  collapsible?: boolean
  open?: boolean
  onToggle?: () => void
}

/** Label row used above every list-style section; optionally collapsible. */
export default function SectionHeader({ title, count, collapsible, open, onToggle }: SectionHeaderProps) {
  return (
    <button
      type="button"
      className="w-full flex items-center justify-between mb-3 group"
      onClick={collapsible ? onToggle : undefined}
      disabled={!collapsible}
    >
      <div className="flex items-center gap-2">
        <span className="text-[11px] font-semibold uppercase tracking-wider text-muted">{title}</span>
        {count !== undefined && (
          <span className="text-[10px] font-semibold text-muted bg-slate-100 rounded-full px-1.5 py-0.5 leading-none">
            {count}
          </span>
        )}
      </div>
      {collapsible && (
        <span className={`text-slate-300 text-xs transition-transform duration-200 ${open ? 'rotate-180' : ''}`}>▾</span>
      )}
    </button>
  )
}
