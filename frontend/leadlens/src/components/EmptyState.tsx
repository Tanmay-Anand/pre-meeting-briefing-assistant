export default function EmptyState() {
  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-3 px-8 py-12 text-center">
      <div className="w-12 h-12 rounded-xl bg-slate-100 flex items-center justify-center text-slate-400 text-xl mb-1">
        ◉
      </div>
      <p className="text-sm font-semibold text-slate-700">No lead selected</p>
      <p className="text-xs text-slate-400 leading-relaxed">
        Open a lead record in your CRM and LeadBrief will automatically generate a pre-meeting briefing.
      </p>
      <div className="mt-2 flex items-center gap-1.5 text-[11px] text-slate-400 bg-slate-50 border border-slate-200 rounded-lg px-3 py-2">
        <span>⌨</span>
        <span>Navigate to a lead in your CRM</span>
      </div>
    </div>
  )
}
