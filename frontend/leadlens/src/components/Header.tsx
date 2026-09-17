import type { LeadBriefData } from '../types/leadBrief'

interface HeaderProps {
  brief: LeadBriefData
}

export default function Header({ brief }: HeaderProps) {
  return (
    <header className="lb-header">
      <span className="lb-header__brand">LeadBrief</span>
      <h1 className="lb-header__customer">{brief.customer.name}</h1>
      <p className="lb-header__company">{brief.customer.company}</p>
      <div className="lb-header__meta">
        <span>{brief.meeting.time}</span>
        <span className="lb-dot" aria-hidden="true">
          •
        </span>
        <span>{brief.meeting.activity}</span>
      </div>
    </header>
  )
}
