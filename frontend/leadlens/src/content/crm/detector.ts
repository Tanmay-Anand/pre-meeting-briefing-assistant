import type { CrmAdapter } from './types'
import { leadratAdapter } from './leadrat'
import { leadratBuilderAdapter } from './leadratBuilder'
import { leadscrmAdapter } from './leadscrm'
import { demoAdapter } from './demo'
import { genericAdapter } from './generic'

/**
 * Registry of known CRM adapters. To support another CRM, add a new
 * adapter file next to this one and register it here — nothing else in the
 * extension (content script, background worker, UI) needs to change.
 */
const adapters: CrmAdapter[] = [leadratAdapter, leadratBuilderAdapter, leadscrmAdapter, demoAdapter]

export function detectCrm(url: string): CrmAdapter {
  const { hostname, port } = safeUrlParts(url)
  return adapters.find((adapter) => adapter.matchesHost(hostname, port)) ?? genericAdapter
}

function safeUrlParts(url: string): { hostname: string; port: string } {
  try {
    const parsed = new URL(url)
    return { hostname: parsed.hostname, port: parsed.port }
  } catch {
    return { hostname: '', port: '' }
  }
}
