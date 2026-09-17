export type CrmType = 'leadrat' | 'leadrat-builder' | 'unknown'

export interface CrmContext {
  crm: CrmType
  url: string
  timestamp: string
}

export interface LeadReference {
  crm: CrmType
  leadId: string
}
