import type { LeadBriefData } from '../types/leadBrief'

// Used only for standalone `npm run dev` UI work, where chrome.runtime isn't
// available. The real data flow is API -> LeadBriefData -> React, see
// services/api.ts and App.tsx.
export const mockLeadBrief: LeadBriefData = {
  leadId: '12345',
  crmName: 'LeadRat',
  updatedAt: '30 sec ago',
  customer: {
    name: 'Rahul Sharma',
    company: 'Acme Corporation',
    stage: 'Negotiation',
  },
  meeting: {
    time: 'Today · 4:00 PM',
    activity: 'Site Visit',
  },
  snapshot: {
    status: 'Negotiation',
    requirement: '3BHK',
    budget: '₹1.5–1.8 Cr',
    preferredLocation: 'Whitefield',
    timeline: 'Within 3 months',
  },
  summary: {
    text: 'Rahul is actively considering Project B but pricing remains the main concern. He requested a revised payment plan and is expecting an update before the meeting.',
    highlight: 'pricing remains the main concern',
    chips: [
      { label: 'Pricing concern', tone: 'danger' },
      { label: 'Interested in Project B', tone: 'accent' },
      { label: 'Payment plan requested', tone: 'warn' },
      { label: 'Site visit pending', tone: 'neutral' },
    ],
  },
  keyInsights: [
    {
      text: 'Customer rejected Project A due to pricing',
      source: 'Call',
      date: '14 Sep',
      icon: '✕',
      tone: 'danger',
    },
    {
      text: 'Interested in Project B after latest WhatsApp conversation',
      source: 'WhatsApp',
      date: '16 Sep',
      icon: '★',
      tone: 'accent',
    },
    {
      text: 'Asked for revised payment plan',
      source: 'Call',
      date: '14 Sep',
      icon: '◎',
      tone: 'warn',
    },
    {
      text: 'Site visit was promised last week but not completed',
      source: 'Task',
      date: '15 Sep',
      icon: '⌂',
      tone: 'neutral',
    },
  ],
  objections: [
    { title: 'Pricing', source: 'Call', date: '14 Sep', severity: 'high' },
    { title: 'Possession timeline', source: 'WhatsApp', date: '16 Sep', severity: 'medium' },
  ],
  pendingActions: [
    'Share revised payment plan',
    'Confirm site-visit timing',
    'Customer to provide financing details',
  ],
  talkingPoints: [
    'Follow up on pricing concern',
    'Confirm whether Project B meets timeline',
    'Ask about financing requirement',
  ],
  missingInformation: ['Financing requirement', 'Decision maker', 'Exact meeting objective'],
  sources: [
    { field: 'Budget', source: 'Lead field', date: '12 Sep', icon: '⊞' },
    { field: 'Pricing objection', source: 'Call', date: '14 Sep', icon: '☏' },
    { field: 'Latest requirement', source: 'WhatsApp', date: '16 Sep', icon: '◉' },
    { field: 'Site visit', source: 'Task', date: '15 Sep', icon: '✓' },
  ],
}
