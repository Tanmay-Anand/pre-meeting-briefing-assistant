export type CrmType = 'leadrat' | 'leadrat-builder' | 'leadscrm' | 'demo' | 'unknown'

export interface CrmContext {
  crm: CrmType
  url: string
  timestamp: string
}

export interface LeadReference {
  crm: CrmType
  leadId: string
  /** Present when the CRM sent one alongside the lead (e.g. leads-crm-frontend's LEAD_OPENED
   *  page message) - the backend forwards this as a second target to the CRM's AI narrative
   *  source, since a plain UUID column with no JPA association is all that links a lead to a
   *  project on that CRM's side (see LeadsCrmAdapter's own doc). */
  projectId?: string
  projectName?: string
  /** How this reference was detected - purely diagnostic, shown in the panel's "Detected lead"
   *  banner so a demo can tell which detection path actually fired. */
  contextSource?: 'dom-click' | 'page-message' | 'network'
}
