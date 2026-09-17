export default function ErrorState() {
  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-3 px-8 py-12 text-center">
      <div className="w-12 h-12 rounded-xl bg-red-50 flex items-center justify-center text-red-400 text-xl mb-1">
        ✕
      </div>
      <p className="text-sm font-semibold text-slate-700">Couldn't load briefing</p>
      <p className="text-xs text-slate-400 leading-relaxed">
        Check that the LeadBrief backend is running, then try clicking the lead again.
      </p>
    </div>
  )
}
