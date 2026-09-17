interface MissingInformationProps {
  items: string[]
}

export default function MissingInformation({ items }: MissingInformationProps) {
  if (items.length === 0) return null

  return (
    <div className="mb-4">
      <div className="rounded-lg border border-amber-200 bg-amber-50 px-3 py-3">
        <div className="flex items-center gap-1.5 mb-2">
          <svg
            className="w-3.5 h-3.5 text-amber-500 shrink-0"
            fill="none"
            stroke="currentColor"
            strokeWidth="2.5"
            viewBox="0 0 24 24"
          >
            <path
              strokeLinecap="round"
              d="M12 9v4m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z"
            />
          </svg>
          <span className="text-[11px] font-bold text-amber-700 uppercase tracking-wider">Missing information</span>
        </div>
        <div className="space-y-1">
          {items.map((item, i) => (
            <div key={i} className="flex items-center gap-2 text-[11px] text-amber-700">
              <span className="w-1 h-1 rounded-full bg-amber-400 shrink-0" />
              {item}
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
