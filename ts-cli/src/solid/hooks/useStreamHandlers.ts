/**
 * useStreamHandlers — wires JwCodeClient WS events to Solid app state.
 * Replaces React's useStreamHandlers.ts. Stream buffer state is closure-scoped.
 */
import { onCleanup } from "solid-js"
import type { SetStoreFunction } from "solid-js/store"
import type { JwCodeClient } from "../../client"
import { debugLog } from "../../client"
import { createMessage, parseData } from "../../protocol"
import { recordMessage } from "../../messageStore"
import type { WSMessage, Message, ToolCall, PlanTask, Step } from "../../protocol"
import type { AppState, ApprovalItem } from "./AppStateProvider"

const MAX_MESSAGES = 200

/** Append a message to the message list, capping at MAX_MESSAGES (FIFO trim).
 *  Exported for unit tests. Pure: returns a new AppState object. */
export function appendMessage(prev: AppState, msg: Message): AppState {
  const next = [...prev.messages, msg]
  if (next.length > MAX_MESSAGES) return { ...prev, messages: next.slice(next.length - MAX_MESSAGES) }
  return { ...prev, messages: next }
}

/** Best-effort extraction of a human-readable command string from tool-call args.
 *  Handles nested JSON, command-as-object wrappers. Exported for unit tests. */
export function cleanArgs(raw: unknown): string {
  let s: string = typeof raw === "string" ? raw : JSON.stringify(raw)
  for (let i = 0; i < 10; i++) {
    try {
      const obj = JSON.parse(s)
      if (obj && typeof obj === "object" && !Array.isArray(obj)) {
        if (typeof obj.command === "string") return obj.command
        if (typeof obj.command === "object") {
          s = JSON.stringify(obj.command)
          continue
        }
        return JSON.stringify(obj, null, 2)
      }
      return s
    } catch {
      return s
    }
  }
  return s
}

/**
 * Wire all stream event handlers to the store. Call once after mount.
 * Returns cleanup function to unsubscribe all handlers.
 */
export function wireStreamHandlers(
  client: JwCodeClient,
  setState: SetStoreFunction<AppState>,
  opts?: {
    sessionAllowRef?: { current: Set<string> }
    onApproval?: (item: ApprovalItem) => void
  },
): () => void {
  // ── Stream buffer (closure-scoped) ──
  let _pendingContent = ""
  let _pendingThinking = ""
  let _pendingToolFns: Array<(msg: Message) => Message> = []
  let _pendingStepFns: Array<(msg: Message) => Message> = []
  let _flushTimer: ReturnType<typeof setTimeout> | null = null
  let _flushScheduled = false

  function doStreamFlush() {
    _flushScheduled = false
    const c = _pendingContent
    _pendingContent = ""
    const t = _pendingThinking
    _pendingThinking = ""
    const fns = _pendingToolFns
    _pendingToolFns = []
    const sFns = _pendingStepFns
    _pendingStepFns = []
    if (!c && !t && fns.length === 0 && sFns.length === 0) return
    try {
      setState((prev) => {
        if (!prev.currentMessage) return prev
        let msg = prev.currentMessage
        if (c) msg = { ...msg, content: msg.content + c }
        if (t) msg = { ...msg, thinking: msg.thinking + t }
        for (const fn of fns) msg = fn(msg)
        for (const fn of sFns) msg = fn(msg)
        return { ...prev, currentMessage: msg }
      })
    } catch (e) {
      debugLog("flush", "Error in stream flush: " + (e instanceof Error ? e.message : String(e)))
    }
  }

  function scheduleStreamFlush() {
    if (_flushScheduled) return
    _flushScheduled = true
    _flushTimer = setTimeout(doStreamFlush, 32)
  }

  function flushNow() {
    if (_flushTimer) {
      clearTimeout(_flushTimer)
      _flushTimer = null
    }
    _flushScheduled = false
    doStreamFlush()
  }

  // ── Token update throttle ──
  let _lastTotal = 0
  let _lastTotalTs = 0
  let _firstTokenUpdate = true
  let _pendingToken: Record<string, unknown> | null = null
  let _tokenScheduled = false

  function flushToken() {
    _tokenScheduled = false
    const d = _pendingToken
    if (!d) return
    _pendingToken = null
    const promptTokens = Number(d.promptTokens) || 0
    const completionTokens = Number(d.completionTokens) || 0
    const totalTokens = Number(d.totalTokens) || 0
    const usageRatio = Number(d.usageRatio) || 0
    if (totalTokens <= 0) return
    const now = Date.now()
    let tokenRate = 0
    if (_lastTotalTs > 0 && _lastTotal > 0 && now > _lastTotalTs && totalTokens > _lastTotal) {
      const deltaTokens = totalTokens - _lastTotal
      const deltaSec = (now - _lastTotalTs) / 1000
      const instantRate = deltaTokens / deltaSec
      tokenRate = instantRate > 0 ? instantRate : 0
    }
    _lastTotal = totalTokens
    _lastTotalTs = now
    setState("usage", { promptTokens, completionTokens, totalTokens, usageRatio })
    if (d.model) setState("modelName", String(d.model))
    setState("tokenRate", tokenRate)
  }

  // ── Build all unsub functions ──
  const unsubs: (() => void)[] = []

  unsubs.push(
    client.on("start", () => {
      debugLog("evt", ">> STREAM START")
      flushNow()
      const msg = createMessage("assistant")
      setState((prev) => {
        let msgs = prev.messages
        // If the previous currentMessage had content but no "complete" arrived
        // (user sent a new message mid-stream), commit it to the messages array
        // before creating the new one.
        const prevCm = prev.currentMessage
        if (prevCm && prevCm.content) {
          let found = false
          for (let i = msgs.length - 1; i >= 0; i--) {
            if (msgs[i].type === "assistant" && msgs[i].id === prevCm.id) {
              const copy = [...msgs]
              copy[i] = prevCm
              msgs = copy
              found = true
              break
            }
          }
          if (!found) {
            msgs = [...msgs, prevCm]
          }
        }
        return {
          ...prev,
          currentMessage: msg,
          messages: appendMessage({ ...prev, messages: msgs }, msg).messages,
          scrollOffset: 0,
        }
      })
    }),
  )

  unsubs.push(
    client.on("content", (m: WSMessage) => {
      const text = typeof m.data === "string" ? m.data : m.data ? String(m.data) : ""
      _pendingContent += text
      scheduleStreamFlush()
    }),
  )

  unsubs.push(
    client.on("thinking", (m: WSMessage) => {
      _pendingThinking += typeof m.data === "string" ? m.data : ""
      scheduleStreamFlush()
    }),
  )

  unsubs.push(
    client.on("tool_call", (m: WSMessage) => {
      const d = parseData(m) as unknown as ToolCall
      debugLog("evt", "tool_call: " + (d.name || "?") + (d.complete ? " complete" : " running"))
      _pendingToolFns.push((msg: Message): Message => {
        const tcs = [...msg.toolCalls]
        let existingIdx = d.id ? tcs.findIndex((t) => t.id === d.id) : -1
        if (existingIdx < 0 && d.name) {
          existingIdx = tcs.findIndex((t) => t.name === d.name && t.status === "running")
        }
        if (existingIdx >= 0) {
          const existing = { ...tcs[existingIdx] }
          if (d.args) existing.args = cleanArgs(d.args)
          if (d.complete) existing.status = "complete"
          if (d.result) existing.result = d.result
          tcs[existingIdx] = existing
        } else {
          tcs.push({
            id: d.id || (d.name ? `${d.name}-${Date.now()}` : ""),
            name: d.name || "",
            args: d.args ? cleanArgs(d.args) : undefined,
            status: d.complete ? "complete" : "running",
            complete: !!d.complete,
            timestamp: Date.now(),
          })
        }
        return { ...msg, toolCalls: tcs }
      })
      scheduleStreamFlush()
    }),
  )

  unsubs.push(
    client.on("tool_result", (m: WSMessage) => {
      const d = parseData(m) as { toolName?: string; result?: string }
      _pendingToolFns.push((msg: Message): Message => {
        const tcs = [...msg.toolCalls]
        for (let i = tcs.length - 1; i >= 0; i--) {
          if (tcs[i].name === d.toolName && !tcs[i].result) {
            const tc = tcs[i]
            const duration = tc.timestamp ? Math.floor((Date.now() - tc.timestamp) / 1000) : undefined
            tcs[i] = { ...tc, result: d.result || "", status: "complete", duration }
            break
          }
        }
        return { ...msg, toolCalls: tcs }
      })
      scheduleStreamFlush()
    }),
  )

  unsubs.push(
    client.on("complete", () => {
      debugLog("evt", ">> STREAM COMPLETE")
      flushNow()
      setState((prev) => {
        if (!prev.currentMessage) return prev
        const msgs = [...prev.messages]
        const cm = prev.currentMessage
        for (let i = msgs.length - 1; i >= 0; i--) {
          if (msgs[i].type === "assistant" && msgs[i].id === cm.id) {
            msgs[i] = cm
            break
          }
        }
        if (cm.content) recordMessage("assistant", cm.content)
        return { ...prev, currentMessage: null, messages: msgs }
      })
    }),
  )

  // ── Step events ──

  unsubs.push(
    client.on("step_start", (m: WSMessage) => {
      const d = parseData(m)
      _pendingStepFns.push((msg: Message): Message => {
        const step: Step = {
          id: (d.id as string) || "step-" + Date.now(),
          title: (d.title as string) || (d.description as string) || "",
          thought: d.thought as string,
          action: d.action as string,
          status: "running",
          tools: [],
          timestamp: Date.now(),
        }
        return { ...msg, steps: [...msg.steps, step] }
      })
      scheduleStreamFlush()
    }),
  )

  unsubs.push(
    client.on("step_thinking", (m: WSMessage) => {
      const d = parseData(m)
      _pendingStepFns.push((msg: Message): Message => {
        const steps = [...msg.steps]
        const idx = d.id ? steps.findIndex((s) => s.id === d.id) : steps.length - 1
        if (idx >= 0) {
          steps[idx] = { ...steps[idx], status: "thinking", thought: (d.thought as string) || steps[idx].thought }
        }
        return { ...msg, steps }
      })
      scheduleStreamFlush()
    }),
  )

  unsubs.push(
    client.on("step_action", (m: WSMessage) => {
      const d = parseData(m)
      _pendingStepFns.push((msg: Message): Message => {
        const steps = [...msg.steps]
        const idx = d.id ? steps.findIndex((s) => s.id === d.id) : steps.length - 1
        if (idx >= 0) {
          steps[idx] = { ...steps[idx], status: "action", action: (d.action as string) || steps[idx].action }
        }
        return { ...msg, steps }
      })
      scheduleStreamFlush()
    }),
  )

  unsubs.push(
    client.on("step_complete", (m: WSMessage) => {
      const d = parseData(m)
      _pendingStepFns.push((msg: Message): Message => {
        const steps = [...msg.steps]
        const idx = d.id ? steps.findIndex((s) => s.id === d.id) : steps.length - 1
        if (idx >= 0) {
          const dur = d.duration
            ? Number(d.duration)
            : steps[idx].timestamp
              ? Math.floor((Date.now() - steps[idx].timestamp!) / 1000)
              : undefined
          steps[idx] = {
            ...steps[idx],
            status: (d.status as string) === "error" ? "error" : "success",
            result: d.result as string,
            duration: dur,
          }
        }
        return { ...msg, steps }
      })
      scheduleStreamFlush()
    }),
  )

  // ── Plan events ──
  unsubs.push(
    client.on("plan_start", () => {
      debugLog("evt", ">> PLAN START")
      flushNow()
      const msg = createMessage("assistant")
      setState((prev) => ({
        ...prev,
        planWaiting: false,
        currentMessage: msg,
        messages: appendMessage(prev, msg).messages,
        scrollOffset: 0,
      }))
    }),
  )

  unsubs.push(
    client.on("plan_thinking", (m: WSMessage) => {
      const text = typeof m.data === "string" ? m.data : m.data ? String(m.data) : ""
      _pendingThinking += text + "\n"
      scheduleStreamFlush()
    }),
  )

  unsubs.push(
    client.on("plan_tasks", () => {
      _pendingContent += "\nTask list generated\n"
      scheduleStreamFlush()
    }),
  )

  unsubs.push(
    client.on("plan_error", (m: WSMessage) => {
      const text = String(m.data || "Plan failed").slice(0, 120)
      setState((prev) => ({ ...prev, planWaiting: false, statusText: "Plan error: " + text }))
    }),
  )

  unsubs.push(
    client.on("plan_mode_enter", () => {
      debugLog("evt", "plan_mode_enter")
      setState((prev) => ({ ...prev, planMode: true, statusText: "Entered plan mode" }))
    }),
  )

  unsubs.push(
    client.on("plan_mode_exit", () => {
      debugLog("evt", "plan_mode_exit")
      setState((prev) => ({ ...prev, planMode: false, statusText: "Exited plan mode" }))
    }),
  )

  unsubs.push(
    client.on("plan_task_start", (m: WSMessage) => {
      const d = parseData(m) as unknown as PlanTask
      setState((prev) => {
        const tasks = [...prev.planTasks]
        const idx = tasks.findIndex((t) => t.id === d.id)
        if (idx >= 0) {
          tasks[idx] = { ...tasks[idx], ...d, status: "running", timestamp: Date.now() }
        } else {
          tasks.push({ ...d, status: "running", timestamp: Date.now() })
        }
        return { ...prev, planTasks: tasks, pdcaPhase: prev.pdcaPhase || "Do" }
      })
    }),
  )

  unsubs.push(
    client.on("plan_task_update", (m: WSMessage) => {
      const d = parseData(m) as unknown as PlanTask
      setState((prev) => {
        const tasks = [...prev.planTasks]
        const idx = tasks.findIndex((t) => t.id === d.id)
        if (idx >= 0) tasks[idx] = { ...tasks[idx], ...d }
        return { ...prev, planTasks: tasks }
      })
    }),
  )

  unsubs.push(
    client.on("plan_task_result", (m: WSMessage) => {
      const d = parseData(m) as unknown as PlanTask
      setState((prev) => {
        const tasks = [...prev.planTasks]
        const idx = tasks.findIndex((t) => t.id === d.id)
        if (idx >= 0) {
          const duration = tasks[idx].timestamp ? Math.floor((Date.now() - tasks[idx].timestamp!) / 1000) : undefined
          tasks[idx] = { ...tasks[idx], ...d, status: d.status || "completed", duration }
        } else {
          tasks.push({ ...d })
        }
        return { ...prev, planTasks: tasks }
      })
    }),
  )

  unsubs.push(
    client.on("plan_complete", (m: WSMessage) => {
      flushNow()
      const status = (m as any).status as string | undefined
      debugLog("evt", ">> PLAN COMPLETE status=" + (status || "none"))
      const planText = typeof m.data === "string" ? m.data : ""
      setState((prev) => {
        if (!prev.currentMessage) return prev
        const msgs = [...prev.messages]
        const cm = prev.currentMessage
        for (let i = msgs.length - 1; i >= 0; i--) {
          if (msgs[i].type === "assistant" && msgs[i].id === cm.id) {
            msgs[i] = { ...cm, content: planText || "Plan complete." }
            break
          }
        }
        const content = planText || "Plan complete."
        if (content) recordMessage("assistant", content)
        return { ...prev, currentMessage: null, messages: msgs, planWaiting: status === "waiting_confirm", pdcaPhase: "" }
      })
    }),
  )

  // ── Token & context events ──
  unsubs.push(
    client.on("token_update", (m: WSMessage) => {
      const d = parseData(m)
      _firstTokenUpdate = false
      const totalTokens = Number(d.totalTokens) || 0
      if (totalTokens > 0) {
        _pendingToken = d
        if (!_tokenScheduled) {
          _tokenScheduled = true
          setTimeout(flushToken, 100)
        }
      }
    }),
  )

  unsubs.push(
    client.on("compaction_progress", (m: WSMessage) => {
      const d = parseData(m)
      setState("compactionProgress", {
        stage: String(d.stage || ""),
        percent: Number(d.percent) || 0,
        message: String(d.message || ""),
      })
    }),
  )

  unsubs.push(
    client.on("context_compressed", (m: WSMessage) => {
      const d = parseData(m)
      const orig = Number(d.originalCount) || 0
      const comp = Number(d.compressedCount) || 0
      const saved = Number(d.tokensSaved) || 0
      const tokensStr = saved >= 1000 ? (saved / 1000).toFixed(1) + "K" : String(saved)
      setState((prev) => ({
        ...prev,
        statusText: "Context compressed " + orig + " to " + comp + " messages, freed " + tokensStr + " tokens",
        compactionProgress: null,
      }))
    }),
  )

  // ── Hook / approval ──
  unsubs.push(
    client.on("hook_ask", (m: WSMessage) => {
      const d = parseData(m)
      const tn = (d.toolName as string) || ""
      const approvalId = (d.approvalId as string) || ""
      const toolName = (d.toolName as string) || ""
      debugLog("evt", "hook_ask: " + tn)

      // Check auto-mode and session allow-list
      let currentAutoMode = false
      let currentAllowList: Set<string> | undefined
      setState((prev) => {
        currentAutoMode = prev.autoMode
        return prev
      })
      if (opts?.sessionAllowRef) currentAllowList = opts.sessionAllowRef.current

      if (currentAutoMode || (currentAllowList && currentAllowList.has(toolName))) {
        client.approveHook(approvalId)
        return
      }

      const item: ApprovalItem = {
        approvalId,
        toolName: (d.toolName as string) || "",
        payload: (d.askPayload as string) || (d.payload as string) || JSON.stringify(d),
      }

      if (opts?.onApproval) {
        opts.onApproval(item)
      } else {
        setState((prev) => {
          if (prev.approvalQueue.some((a) => a.approvalId === item.approvalId)) return prev
          return { ...prev, approvalQueue: [...prev.approvalQueue, item] }
        })
      }
    }),
  )

  // ── Doctor ──
  unsubs.push(
    client.on("doctor_result", (m: WSMessage) => {
      const text = String(m.data || "")
      const truncated = text.length > 300 ? text.slice(0, 300) + "..." : text
      recordMessage("assistant", truncated)
      const msg = createMessage("assistant", truncated)
      setState((prev) => ({
        ...prev,
        messages: appendMessage(prev, msg).messages,
        statusText: "Doctor diagnosis complete",
      }))
    }),
  )

  // ── Degradation ──
  unsubs.push(
    client.on("degradation_update", (m: WSMessage) => {
      const d = parseData(m)
      setState("degradation", {
        active: (d.active as boolean) || false,
        retryCount: Number(d.retryCount) || 0,
        maxRetries: Number(d.maxRetries) || 0,
        mode: ((d.mode as string) || "normal") as AppState["degradation"]["mode"],
        message: (d.message as string) || "",
      })
    }),
  )

  // ── TODO events ──
  unsubs.push(
    client.on("todo_update", (m: WSMessage) => {
      const text = String(m.data || "").slice(0, 100)
      setState("statusText", "TODO: " + text)
    }),
  )
  unsubs.push(
    client.on("todo_item_done", (m: WSMessage) => {
      const text = String(m.data || "").slice(0, 100)
      setState("statusText", "Done: " + text)
    }),
  )
  unsubs.push(
    client.on("todo_progress", (m: WSMessage) => {
      const text = String(m.data || "").slice(0, 100)
      if (text) setState((prev) => {
        if (prev.currentMessage) return prev
        return { ...prev, statusText: text }
      })
    }),
  )

  // ── Workspace ──
  unsubs.push(
    client.on("workspace_changed", (m: WSMessage) => {
      const text = String(m.data || "Workspace changed").slice(0, 100)
      setState("statusText", text)
    }),
  )

  // ── Generation state ──
  unsubs.push(
    client.on("generation_paused", () => {
      setState("statusText", "Generation paused — press ESC to resume or ESC ESC to stop")
    }),
  )
  unsubs.push(
    client.on("generation_resumed", () => {
      setState("statusText", "")
    }),
  )

  // ── Connection / session lifecycle ──
  unsubs.push(
    client.on("connected", (m: WSMessage) => {
      const d = parseData(m)
      debugLog("evt", "connected: " + (d.model as string || ""))
      setState((prev) => ({
        ...prev,
        connected: true,
        modelName: (d.model as string) || prev.modelName,
        statusText: "Connected",
      }))
    }),
  )

  unsubs.push(
    client.on("session_created", (m: WSMessage) => {
      const d = parseData(m)
      const sid = (d.sessionId as string) || (d.id as string) || ""
      if (sid) client.sessionId = sid
      setState("statusText", "Session: " + sid.slice(0, 12))
    }),
  )

  // ── Progress events ──
  unsubs.push(
    client.on("progress", (m: WSMessage) => {
      const text = String(m.data || m.message || "").slice(0, 120)
      // Skip during streaming - AgentProgressTree already shows tool progress
      if (text) setState((prev) => {
        if (prev.currentMessage) return prev
        return { ...prev, statusText: text }
      })
    }),
  )

  // ── Notification events (non-error info) ──
  unsubs.push(
    client.on("notification", (m: WSMessage) => {
      const text = String(m.data || m.message || "").slice(0, 120)
      if (text) setState((prev) => {
        if (prev.currentMessage) return prev
        return { ...prev, statusText: text }
      })
    }),
  )

  // ── Rewind result ──
  unsubs.push(
    client.on("rewind_result", (m: WSMessage) => {
      const d = parseData(m)
      const msgsRaw = (d.messages as unknown[]) || []
      if (msgsRaw.length > 0) {
        const rewound = msgsRaw.map((raw: any) => {
          const t = (raw.type === "chat" || raw.type === "plan") ? "user" : "assistant"
          return createMessage(t, raw.data || raw.message || JSON.stringify(raw))
        })
        setState((prev) => ({
          ...prev,
          messages: rewound,
          currentMessage: null,
          planTasks: [],
          pdcaPhase: "",
          statusText: `Rewound to ${rewound.length} messages`,
        }))
      } else {
        setState("statusText", "Rewind completed")
      }
    }),
  )

  // Return cleanup
  return () => {
    for (const unsub of unsubs) unsub()
    if (_flushTimer) clearTimeout(_flushTimer)
    if (_tokenScheduled) clearTimeout(flushToken as unknown as number)
  }
}
