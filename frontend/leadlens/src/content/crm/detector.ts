import type { CrmAdapter } from './types'
import { leadratAdapter } from './leadrat'
import { leadratBuilderAdapter } from './leadratBuilder'
import { genericAdapter } from './generic'

/**
 * Registry of known CRM adapters. To support another CRM, add a new
 * adapter file next to this one and register it here — nothing else in the
 * extension (content script, background worker, UI) needs to change.
 */
const adapters: CrmAdapter[] = [leadratAdapter, leadratBuilderAdapter]

export function detectCrm(url: string): CrmAdapter {
  const hostname = safeHostname(url)
  return adapters.find((adapter) => adapter.matchesHost(hostname)) ?? genericAdapter
}

function safeHostname(url: string): string {
  try {
    return new URL(url).hostname
  } catch {
    return ''
  }
}
