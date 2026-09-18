interface BottomBarProps {
  updatedAt: string
  onRefresh?: () => void
}

export default function BottomBar({ updatedAt, onRefresh }: BottomBarProps) {
  return (
    <div className="border-t border-line bg-white px-4 py-3 flex items-center justify-between shrink-0">
      <button
        type="button"
        onClick={onRefresh}
        disabled={!onRefresh}
        className="flex items-center gap-1.5 text-[12px] font-semibold text-brand bg-white hover:bg-surface border border-line rounded-lg px-3 py-1.5 transition-colors disabled:opacity-40"
      >
        <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24">
          <path
            strokeLinecap="round"
            d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
          />
        </svg>
        Refresh brief
      </button>
      <span className="text-[10px] text-slate-400 font-medium">Updated {updatedAt}</span>
    </div>
  )
}
