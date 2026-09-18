export type CrmType = 'leadrat' | 'leadrat-builder' | 'leadscrm' | 'demo' | 'unknown'

export interface CrmContext {
  crm: CrmType
  url: string
  timestamp: string
}

export interface LeadReference {
  crm: CrmType
  leadId: string
}
