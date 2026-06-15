/**
 * useApprovalQueue — approval queue state for hook_ask events.
 * Solid version — uses createSignal for the queue and the session allow-list.
 */
import { createSignal, createMemo } from "solid-js"
import { useClient } from "./ClientProvider"
import type { ApprovalItem } from "./AppStateProvider"

export interface ApprovalQueueAPI {
  queue: ApprovalItem[]
  currentApproval: ApprovalItem | null
  sessionAllowList: Set<string>
  addToQueue: (item: ApprovalItem) => void
  approveCurrent: () => void
  denyCurrent: () => void
  allowSession: () => void
  clearQueue: () => void
}

export function createApprovalQueue(sessionAllowRef?: { current: Set<string> }): ApprovalQueueAPI {
  const [queue, setQueue] = createSignal<ApprovalItem[]>([])
  const [sessionAllowList, setSessionAllowList] = createSignal<Set<string>>(new Set())

  const currentApproval = createMemo(() => {
    const q = queue()
    return q.length > 0 ? q[0] : null
  })

  function shift() {
    setQueue((prev) => (prev.length > 1 ? prev.slice(1) : []))
  }

  return {
    get queue() {
      return queue()
    },
    get currentApproval() {
      return currentApproval()
    },
    get sessionAllowList() {
      return sessionAllowList()
    },
    addToQueue(item: ApprovalItem) {
      setQueue((prev) => {
        if (prev.some((a) => a.approvalId === item.approvalId)) return prev
        return [...prev, item]
      })
    },
    approveCurrent() {
      const current = currentApproval()
      if (!current) return
      try {
        const client = useClient()
        client.approveHook(current.approvalId)
      } catch {
        // No client context available
      }
      shift()
    },
    denyCurrent() {
      const current = currentApproval()
      if (!current) return
      try {
        const client = useClient()
        client.denyHook(current.approvalId)
      } catch {
        // No client context available
      }
      shift()
    },
    allowSession() {
      const current = currentApproval()
      if (!current) return
      if (sessionAllowRef) {
        sessionAllowRef.current = new Set(sessionAllowRef.current).add(current.toolName)
      }
      setSessionAllowList((prev) => new Set(prev).add(current.toolName))
      try {
        const client = useClient()
        client.approveHook(current.approvalId)
      } catch {
        // No client context available
      }
      shift()
    },
    clearQueue() {
      setQueue([])
    },
  }
}
