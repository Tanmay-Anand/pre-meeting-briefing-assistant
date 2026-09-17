import type { LeadBriefData } from '../types/leadBrief'

// Used only for standalone `npm run dev` UI work, where chrome.runtime isn't
// available. The real data flow is API -> LeadBriefData -> React, see
// services/api.ts and App.tsx.
export const mockLeadBrief: LeadBriefData = {
  leadId: '12345',
  customer: {
    name: 'Rahul Sharma',
    company: 'Acme Corporation',
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
  keyInsights: [
    'Customer rejected Project A due to pricing',
    'Interested in Project B after latest WhatsApp conversation',
    'Asked for revised payment plan',
    'Site visit was promised last week but not completed',
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
}
