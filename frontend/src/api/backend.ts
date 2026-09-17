import type { Request, ResponseFor } from '../types/messages'

/**
 * Typed client used by the panel and content script. Every call is a message to the
 * background service worker, which owns the actual network I/O (I.4).
 */
export function send<R extends Request>(request: R): Promise<ResponseFor<R>> {
  return chrome.runtime.sendMessage(request) as Promise<ResponseFor<R>>
}
