const SKELETON_WIDTHS = [80, 60, 70, 45]

export default function LoadingState() {
  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-4 px-8 py-12">
      <div className="w-10 h-10 rounded-full border-2 border-indigo-200 border-t-indigo-500 animate-spin" />
      <div className="text-center">
        <p className="text-sm font-semibold text-slate-700 mb-1">Generating briefing…</p>
        <p className="text-xs text-slate-400">Fetching the latest information for this lead</p>
      </div>
      <div className="w-full space-y-2 mt-2">
        {SKELETON_WIDTHS.map((w, i) => (
          <div key={i} className="h-3 bg-slate-100 rounded animate-pulse" style={{ width: `${w}%` }} />
        ))}
      </div>
    </div>
  )
}
