import type { LeadReference } from '../types/crm'

// Bridges the background service worker (which learns about a lead click
// first) and the React side panel (which needs to react to it). Backed by
// chrome.storage.session so it survives the panel opening after the message
// that triggered it, and so multiple lead clicks in a row all reach the UI.
const STORAGE_KEY = 'leadbrief:currentLead'

function hasSessionStorage(): boolean {
  return typeof chrome !== 'undefined' && !!chrome.storage?.session
}

export async function setCurrentLead(reference: LeadReference): Promise<void> {
  if (!hasSessionStorage()) return
  await chrome.storage.session.set({ [STORAGE_KEY]: reference })
}

/** Clears the stored lead - the LEAD_CLOSED counterpart to setCurrentLead, so the panel can
 *  tell "no lead is open" apart from "still waiting to hear about one". */
export async function clearCurrentLead(): Promise<void> {
  if (!hasSessionStorage()) return
  await chrome.storage.session.remove(STORAGE_KEY)
}

export async function getCurrentLeadReference(): Promise<LeadReference | null> {
  if (!hasSessionStorage()) return null
  const result = await chrome.storage.session.get(STORAGE_KEY)
  return (result[STORAGE_KEY] as LeadReference | undefined) ?? null
}

/** @param callback called with the new reference, or null when the lead was cleared
 *  (clearCurrentLead) rather than replaced with another one. */
export function subscribeToLeadChanges(callback: (reference: LeadReference | null) => void): () => void {
  if (!hasSessionStorage()) return () => {}

  const listener = (changes: Record<string, chrome.storage.StorageChange>, areaName: string) => {
    if (areaName !== 'session') return
    const change = changes[STORAGE_KEY]
    if (!change) return
    callback((change.newValue as LeadReference | undefined) ?? null)
  }

  chrome.storage.onChanged.addListener(listener)
  return () => chrome.storage.onChanged.removeListener(listener)
}
