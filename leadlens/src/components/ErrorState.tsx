interface ErrorStateProps {
  message?: string
  onRetry?: () => void
}

export default function ErrorState({ message, onRetry }: ErrorStateProps) {
  return (
    <div className="flex-1 flex flex-col items-center justify-center gap-3 px-8 py-12 text-center">
      <div className="w-12 h-12 rounded-xl bg-red-50 flex items-center justify-center text-red-400 text-xl mb-1">
        ✕
      </div>
      <p className="text-sm font-semibold text-slate-700">Couldn't load briefing</p>
      <p className="text-xs text-slate-400 leading-relaxed break-words">
        {message || 'Check that the LeadBrief backend is running, then try clicking the lead again.'}
      </p>
      {onRetry && (
        <button
          type="button"
          onClick={onRetry}
          className="mt-1 flex items-center gap-1.5 text-[12px] font-semibold text-indigo-600 bg-indigo-50 hover:bg-indigo-100 border border-indigo-200 rounded-lg px-3 py-1.5 transition-colors"
        >
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" strokeWidth="2.5" viewBox="0 0 24 24">
            <path
              strokeLinecap="round"
              d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15"
            />
          </svg>
          Try again
        </button>
      )}
    </div>
  )
}
