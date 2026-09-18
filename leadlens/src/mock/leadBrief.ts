import type { LeadBriefData } from '../types/leadBrief'

// Used only for standalone `npm run dev` UI work, where chrome.runtime isn't available. The
// real data flow is background/aiSdkClient.ts -> lib/mapAiSdkResponse.ts -> App.tsx.
export const mockLeadBrief: LeadBriefData = {
  leadId: '12345',
  crmName: 'LeadRat',
  updatedAt: '30 sec ago',
  customer: {
    name: 'Rahul Sharma',
    company: 'Whitefield Heights',
    stage: 'Negotiation',
  },
  meeting: {
    time: 'Today · 4:00 PM',
    activity: 'Scheduled activity',
  },
  snapshot: {
    status: 'Negotiation',
    timeline: 'Within 3 months',
  },
  summary: {
    text: 'Rahul is actively considering this project but **pricing remains the main concern**. He requested a revised payment plan and is expecting an update before the meeting.',
    chips: [
      { label: 'Negotiation', tone: 'warn' },
      { label: 'Pricing concern', tone: 'danger' },
      { label: 'Interested in Project B', tone: 'accent' },
      { label: 'Payment plan requested', tone: 'warn' },
      { label: 'Site visit pending', tone: 'neutral' },
    ],
    source: { label: 'CRM AI · anthropic/claude-sonnet-4.5' },
  },
}
