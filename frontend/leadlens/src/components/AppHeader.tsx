interface AppHeaderProps {
  crmLabel: string
  connected: boolean
}

/** Top bar: extension brand + connected-CRM status badge. */
export default function AppHeader({ crmLabel, connected }: AppHeaderProps) {
  return (
    <header className="flex items-center justify-between px-4 py-3 border-b border-slate-100 bg-white shrink-0">
      <div className="flex items-center gap-2">
        <div className="w-7 h-7 rounded-lg bg-indigo-600 flex items-center justify-center">
          <svg width="14" height="14" viewBox="0 0 14 14" fill="none">
            <rect x="1" y="1" width="5" height="5" rx="1" fill="white" fillOpacity="0.9" />
            <rect x="8" y="1" width="5" height="5" rx="1" fill="white" fillOpacity="0.5" />
            <rect x="1" y="8" width="5" height="5" rx="1" fill="white" fillOpacity="0.5" />
            <rect x="8" y="8" width="5" height="5" rx="1" fill="white" fillOpacity="0.9" />
          </svg>
        </div>
        <div>
          <span className="text-[13px] font-bold text-slate-800 leading-none block">LeadBrief</span>
          <span className="text-[10px] font-medium text-indigo-500 leading-none">AI Briefing</span>
        </div>
      </div>
      <div className="flex items-center gap-2">
        {connected && (
          <div className="flex items-center gap-1.5 text-[10px] font-semibold text-emerald-600 bg-emerald-50 border border-emerald-200 rounded-full px-2 py-0.5">
            <span className="w-1.5 h-1.5 rounded-full bg-emerald-500 inline-block" />
            {crmLabel}
          </div>
        )}
        <button
          type="button"
          className="w-6 h-6 flex items-center justify-center text-slate-400 hover:text-slate-600 rounded transition-colors text-sm"
        >
          ···
        </button>
      </div>
    </header>
  )
}
