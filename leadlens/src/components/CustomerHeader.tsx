import type { CustomerInfo, MeetingInfo } from '../types/leadBrief'
import { initialsFromName } from '../lib/initials'

interface CustomerHeaderProps {
  customer: CustomerInfo
  meeting: MeetingInfo
}

/** Avatar, name, stage badge, company, and meeting time/type. */
export default function CustomerHeader({ customer, meeting }: CustomerHeaderProps) {
  return (
    <div className="px-4 pt-3.5 pb-3 border-b border-line bg-surface shrink-0">
      <div className="flex items-start gap-3">
        <div className="w-10 h-10 rounded-xl bg-brand flex items-center justify-center shrink-0">
          <span className="text-[13px] font-bold text-white">{initialsFromName(customer.name)}</span>
        </div>
        <div className="flex-1 min-w-0">
          <div className="flex items-center gap-2 flex-wrap">
            <span className="text-[15px] font-bold text-ink leading-tight">{customer.name}</span>
            <span className="text-[10px] font-semibold text-warning bg-warning-soft rounded-full px-1.5 py-0.5 leading-none">
              {customer.stage}
            </span>
          </div>
          <p className="text-[11px] text-muted mt-0.5 font-medium">{customer.company}</p>
        </div>
      </div>
      <div className="mt-2.5 flex items-center gap-3 text-[11px] text-muted">
        <span className="flex items-center gap-1">
          <svg className="w-3 h-3 text-slate-400" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
            <rect x="3" y="4" width="18" height="18" rx="2" />
            <line x1="16" y1="2" x2="16" y2="6" />
            <line x1="8" y1="2" x2="8" y2="6" />
            <line x1="3" y1="10" x2="21" y2="10" />
          </svg>
          <strong className="text-ink">{meeting.time}</strong>
        </span>
        <span className="text-slate-200">|</span>
        <span className="flex items-center gap-1">
          <svg className="w-3 h-3 text-slate-400" fill="none" stroke="currentColor" strokeWidth="2" viewBox="0 0 24 24">
            <path d="M3 9l9-7 9 7v11a2 2 0 01-2 2H5a2 2 0 01-2-2z" />
            <polyline points="9 22 9 12 15 12 15 22" />
          </svg>
          {meeting.activity}
        </span>
      </div>
    </div>
  )
}
