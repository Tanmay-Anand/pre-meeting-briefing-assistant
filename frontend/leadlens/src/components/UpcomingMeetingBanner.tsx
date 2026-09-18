import type { UpcomingResponse } from '../types/briefingApi'

interface UpcomingMeetingBannerProps {
  upcoming: UpcomingResponse
}

/**
 * "Your meeting is in 28 minutes — briefing ready" (Part K Phase 9 step 4). Only rendered by
 * the caller when minutesUntil is non-null, i.e. something is actually due within the window —
 * see App.tsx.
 */
export default function UpcomingMeetingBanner({ upcoming }: UpcomingMeetingBannerProps) {
  const minutes = upcoming.minutesUntil ?? 0
  const activity = upcoming.activityType ? upcoming.activityType.toLowerCase().replace(/_/g, ' ') : 'meeting'

  return (
    <div
      className={`px-4 py-2 text-[11px] font-medium border-b shrink-0 ${
        upcoming.briefingReady
          ? 'bg-emerald-50 border-emerald-100 text-emerald-800'
          : 'bg-amber-50 border-amber-100 text-amber-800'
      }`}
    >
      {upcoming.briefingReady ? '✓ ' : '⏳ '}
      Your {activity} is in {minutes <= 0 ? 'less than a minute' : `${minutes} minute${minutes === 1 ? '' : 's'}`}
      {upcoming.briefingReady ? ' — briefing ready' : ' — briefing still preparing'}
    </div>
  )
}
