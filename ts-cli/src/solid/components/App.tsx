/**
 * App — root Solid+OpenTUI component.
 * Replaces Ink/React App.tsx. Ties together all providers, components,
 * keyboard handling, @ file references, command execution, and state.
 */
import { createSignal, createMemo, onCleanup, onMount } from "solid-js"
import { useKeyboard, useRenderer, useTerminalDimensions } from "@opentui/solid"
import { MouseButton } from "@opentui/core"
import { useTheme } from "../context/theme"
import * as Clipboard from "../util/clipboard"
import { useAppStateContext } from "../hooks/AppStateProvider"
import { useClient } from "../hooks/ClientProvider"
import { createCommandHandler } from "../hooks/useCommandHandler"
import { wireStreamHandlers } from "../hooks/useStreamHandlers"
import { createApprovalQueue } from "../hooks/useApprovalQueue"
import { TextInput } from "./TextInput"
import { ChatArea } from "./ChatArea"
import { StatusLine } from "./StatusLine"
import { CommandPalette } from "./CommandPalette"
import { FilePalette } from "./FilePalette"
import { SessionPicker, type SessionInfo } from "./SessionPicker"
import { ApprovalModal } from "./ApprovalModal"
import { ConfigPanel } from "./ConfigPanel"
import { PlanTaskBoard } from "./PlanTaskBoard"
import { HELP_TEXT } from "../../commands"
import { createMessage } from "../../protocol"
import { recordMessage } from "../../messageStore"
import { registerDefaults, matchBinding, setActiveContexts, type KeybindingContext, type KeyAction } from "../hooks/useKeybinding"
import { useSelection } from "../hooks/useSelection"
import { ToolPalette } from "./ToolPalette"
import { scanSystemTools } from "../util/toolScanner"
import { loadHistory, saveHistory, dedupPush } from "../util/history"

// ---- Constants ----

const RESERVED_ROWS = 5
const INPUT_HISTORY_MAX = 30

const WELCOME_TIPS = [
  "输入 / 查看命令列表，@ 引用文件",
  "Ctrl+L 清屏 | Ctrl+S 暂停 | Ctrl+N 新建会话 | Ctrl+R 历史会话",
  "F1 查看帮助 | Tab 切换规划/执行模式 | ESC ESC 停止生成",
  "/plan 先规划再执行 | /auto 自动批准工具执行",
  "支持多模型切换: /model <模型名>",
  "支持 @ 文件引用，自动读取文件内容作为上下文",
  "支持 /compact 压缩上下文 | /rewind 撤销到上一步",
]

// ---- Input history (module-level, persisted to ~/.jwcode/history.json) ----
let inputHistory: string[] = loadHistory()
let historyIdx = -1

/** Find last @ not preceded by a word char (to exclude emails). Returns index or -1. */
function findAtTrigger(value: string): number {
  for (let i = value.length - 1; i >= 0; i--) {
    if (value[i] === "@") {
      if (i > 0 && /\w/.test(value[i - 1])) continue
      return i
    }
  }
  return -1
}

interface Props {
  backendUrl: string
  wsUrl: string
  onExit: () => void
}

export function App(props: Props) {
  const { theme } = useTheme()
  const renderer = useRenderer()
  const dims = useTerminalDimensions()
  const terminalCols = () => dims().width
  const terminalRows = () => dims().height

  // ---- App state via store ----
  const {
    state, setState,
    messages, currentMessage, isGenerating, planMode, autoMode,
    connected, statusText,
  } = useAppStateContext()

  // ---- Client ----
  const client = useClient()

  // ---- Session allow ref (shared between approval queue and stream handlers) ----
  const sessionAllowRef = { current: new Set<string>() }

  // ---- Approval queue ----
  const approvalQueue = createApprovalQueue(sessionAllowRef)

  // ---- Wire stream handlers + keybinding defaults ----
  onMount(() => {
    const unsubStream = wireStreamHandlers(client, setState, {
      onApproval: (item) => approvalQueue.addToQueue(item),
      sessionAllowRef,
    })
    const unsubKeys = registerDefaults()
    onCleanup(() => { unsubStream(); unsubKeys() })

    // Scan system tools on startup (async to avoid blocking render)
    setTimeout(() => {
      try {
        const tools = scanSystemTools()
        setSystemTools(tools)
        if (tools.length > 0) {
          setState("statusText", `Loaded ${tools.length} system tools (type ! to use)`)
        }
      } catch { /* ignore scanner failures */ }
    }, 100)
  })

  // ---- UI signals ----
  const [input, setInput] = createSignal("")
  const [showPalette, setShowPalette] = createSignal(false)
  const [showFilePalette, setShowFilePalette] = createSignal(false)
  const [fileQuery, setFileQuery] = createSignal("")
  const [fileList, setFileList] = createSignal<string[]>([])
  const [showHelp, setShowHelp] = createSignal(false)
  const [showCheatsheet, setShowCheatsheet] = createSignal(false)
  const [helpScroll, setHelpScroll] = createSignal(0)
  const [currentTip] = createSignal(Math.floor(Math.random() * WELCOME_TIPS.length))
  const [showSessionPicker, setShowSessionPicker] = createSignal(false)
  const [sessionList, setSessionList] = createSignal<SessionInfo[]>([])

  // ---- Tool palette state ----
  const [showToolPalette, setShowToolPalette] = createSignal(false)
  const [toolQuery, setToolQuery] = createSignal("")
  const [systemTools, setSystemTools] = createSignal<string[]>([])

  // ---- Selection hook (复制由右键处理器完成) ----
  const selection = useSelection(renderer)

  // ---- Derived state ----
  const paletteActive = () => showPalette() || showFilePalette() || showSessionPicker() || showToolPalette()
  const viewportHeight = () => Math.max(3, terminalRows() - RESERVED_ROWS)
  const msgCount = () => messages().length
  const planTasks = () => state.planTasks
  // Rows reserved below text input: border top/bottom (2) + content (1) + char count (1) + StatusLine (1-2)
  const textInputBottomReserved = () => 4 + (state.degradation?.active || state.compactionProgress ? 2 : 1)

  // ---- File debounce ref (module var) ----
  let fileDebounceTimer: ReturnType<typeof setTimeout> | null = null

  // ---- Command execution ----
  // Wrap onExit to send exit signal to backend first
  const handleExit = () => {
    try {
      client.send("exit")
      client.close()
    } catch { /* ignore */ }
    props.onExit()
  }

  const commandHandler = createCommandHandler({
    client,
    planMode,
    setState,
    setInput: (v: string) => setInput(v),
    setShowHelp: (v: boolean) => setShowHelp(v),
    setShowPalette: (v: boolean) => setShowPalette(v),
    onExit: handleExit,
  })

  // ---- @ file reference ----
  async function handleFileSelect(path: string | null) {
    if (path) {
      const atIdx = findAtTrigger(input())
      if (atIdx >= 0) {
        const newValue = input().slice(0, atIdx) + path + " "
        setInput(newValue)
      }
    }
    setShowFilePalette(false)
    setFileQuery("")
    setFileList([])
  }

  function handleToolSelect(tool: string | null) {
    if (tool) {
      // Replace !query in input with selected tool name
      const bangIdx = input().lastIndexOf("!")
      if (bangIdx >= 0) {
        const before = input().slice(0, bangIdx)
        setInput(before + tool + " ")
      } else {
        setInput(tool + " ")
      }
      setShowToolPalette(false)
    } else {
      setShowToolPalette(false)
    }
  }

  function handlePaletteSelect(cmd: string | null) {
    if (cmd) {
      setInput(cmd)
      setShowPalette(false)
    } else {
      setShowPalette(false)
      setInput("")
    }
  }

  async function resolveAndSend(text: string) {
    let finalText = text
    const fileCtxs: string[] = []
    const seenPaths = new Set<string>()

    // Parse @-referenced files and fetch their content
    const atRe = /(?:^|\s)@(\S+)/g
    let match: RegExpExecArray | null
    while ((match = atRe.exec(finalText)) !== null) {
      const raw = match[0]
      const path = match[1].replace(/[^a-zA-Z0-9_\-./\\]/g, "")
      if (path && path.length < 500 && !seenPaths.has(path)) {
        seenPaths.add(path)
        try {
          const content = await client.readFileContent(path)
          if (content != null) {
            const ext = path.includes(".") ? path.split(".").pop() || "" : ""
            fileCtxs.push(`<context ref="${path}">\n\`\`\`${ext}\n${content}\n\`\`\`\n</context>`)
          }
        } catch { /* skip files that can't be read */ }
      }
      // Remove the @reference from the text
      finalText = finalText.replace(raw, "")
    }

    // Prepend contexts before user message
    if (fileCtxs.length > 0) {
      finalText = fileCtxs.join("\n") + "\n" + finalText.trim()
    }

    const msg = createMessage("user", finalText)
    setState((prev) => ({
      ...prev,
      messages: [...prev.messages, msg],
    }))
    recordMessage("user", finalText)
    client.chat(finalText, planMode())
  }

  // ---- ! trigger detection ----
  function findBangTrigger(value: string): number {
    for (let i = value.length - 1; i >= 0; i--) {
      if (value[i] === "!") {
        // Must be at start of input or after whitespace
        if (i === 0 || /\s/.test(value[i - 1])) return i
        continue
      }
    }
    return -1
  }

  // ---- Input change handler ----
  function handleChange(value: string) {
    setInput(value)
    setShowPalette(value.startsWith("/"))

    // Close tool palette if ! is no longer present
    if (showToolPalette()) {
      const bangIdx = findBangTrigger(value)
      if (bangIdx < 0) {
        setShowToolPalette(false)
      } else {
        const q = value.slice(bangIdx + 1)
        setToolQuery(q)
      }
    }

    const atIdx = findAtTrigger(value)
    if (atIdx >= 0) {
      const query = value.slice(atIdx + 1)
      if (/^[\w.\-\\\/\s]*$/.test(query) && query.length < 200) {
        setFileQuery(query)
        setShowFilePalette(true)
        if (fileDebounceTimer) clearTimeout(fileDebounceTimer)
        fileDebounceTimer = setTimeout(async () => {
          try {
            const files = await client.listFiles(query.trim() || undefined)
            setFileList(files)
          } catch { /* ignore */ }
        }, 150)
        return
      }
    }
    setShowFilePalette(false)
    setFileQuery("")

    // Check for ! trigger (system tool palette)
    const bangIdx = findBangTrigger(value)
    if (bangIdx >= 0) {
      const query = value.slice(bangIdx + 1)
      if (query.length < 100) {
        setToolQuery(query)
        setShowToolPalette(true)
        return
      }
    }
    setShowToolPalette(false)
    setToolQuery("")
  }

  // ---- Submit handler ----
  function handleSubmit(value: string) {
    if (showPalette() || showFilePalette() || showToolPalette()) return
    if (!value.trim()) return

    // Save to input history (dedup + cap + persist)
    inputHistory = dedupPush(inputHistory, value)
    if (inputHistory.length > INPUT_HISTORY_MAX) {
      inputHistory = inputHistory.slice(-INPUT_HISTORY_MAX)
    }
    saveHistory(inputHistory)
    historyIdx = -1

    commandHandler(value)
  }

  // ---- Session handlers ----
  async function handleNewSession() {
    setState((prev) => ({
      ...prev,
      messages: [],
      currentMessage: null,
      planTasks: [],
      pdcaPhase: "",
      statusText: "New session started",
    }))
    client.send("create_session")
  }

  async function handleOpenSessionHistory() {
    try {
      const sessions = await client.listSessions()
      setSessionList(sessions)
      setShowSessionPicker(true)
    } catch { /* ignore */ }
  }

  async function handleSessionSelect(session: SessionInfo | null) {
    setShowSessionPicker(false)
    if (!session) return
    try {
      const msgs = await client.getSessionMessages(session.id)
      const mapped = (msgs || []).map((m: any) =>
        createMessage(
          (m.type === "chat" || m.type === "plan") ? "user" : "assistant",
          m.data || m.message || JSON.stringify(m),
        ),
      )
      setState((prev) => ({
        ...prev,
        messages: mapped.length > 0 ? mapped : prev.messages,
        statusText: `Loaded session: ${session.id} (${mapped.length} msgs)`,
      }))
    } catch { /* ignore */ }
  }

  async function handleSessionDelete(sessionId: string) {
    try {
      await client.deleteSession(sessionId)
      const sessions = await client.listSessions()
      setSessionList(sessions)
    } catch { /* ignore */ }
  }

  // ---- Help toggle ----
  function handleToggleHelp() {
    if (showHelp()) {
      setShowHelp(false)
    } else {
      setShowHelp(true)
      setHelpScroll(0)
    }
  }

  // ---- Keyboard shortcuts (global, non-input) ----
  let lastEscTime = 0

  useKeyboard((key: any) => {
    const keyName = key.name ?? ""
    const ctrl = !!key.ctrl

    // Skip when modals are open
    if (showPalette() || showFilePalette() || showSessionPicker() || showToolPalette()) return

    // App-specific overrides (before keybinding dispatch)
    // Ctrl+P: Command palette
    if (ctrl && keyName === "p") {
      setShowPalette((v) => !v)
      return
    }
    // Ctrl+R: Session history
    if (ctrl && keyName === "r") {
      handleOpenSessionHistory()
      return
    }
    // Ctrl+E / Ctrl+O: Toggle tool call expand
    if ((ctrl && keyName === "e") || (ctrl && keyName === "o")) {
      setState("toolCallsExpanded", (v) => !v)
      return
    }
    // ?: Toggle keyboard cheatsheet overlay
    if (keyName === "?" || key.sequence === "?") {
      setShowCheatsheet((v) => !v)
      return
    }
    // Input history: Up/Down
    if (keyName === "up" || keyName === "down") {
      if (inputHistory.length === 0) return
      if (keyName === "up") {
        historyIdx = Math.min(historyIdx + 1, inputHistory.length - 1)
      } else {
        historyIdx = Math.max(historyIdx - 1, -1)
      }
      setInput(historyIdx >= 0 ? inputHistory[inputHistory.length - 1 - historyIdx] : "")
      return
    }

    // Dispatch via keybinding registry for standard actions
    const match = matchBinding(keyName, ctrl, false)
    if (match) {
      switch (match.action) {
        case "help":
          handleToggleHelp()
          break
        case "new-session":
          handleNewSession()
          break
        case "clear":
          setState((prev) => ({
            ...prev,
            messages: [],
            currentMessage: null,
            planTasks: [],
            pdcaPhase: "",
            statusText: "Screen cleared",
          }))
          break
        case "config-panel":
          setState("showConfigPanel", (v: boolean) => !v)
          break
        case "toggle-plan":
          setState((prev) => ({ ...prev, planMode: !prev.planMode, planWaiting: false }))
          break
        case "stop":
          if (showHelp()) { setShowHelp(false); break }
          if (isGenerating()) {
            const now = Date.now()
            const prev = lastEscTime
            lastEscTime = now
            if (prev > 0 && now - prev < 500) {
              client.stop()
              setState("statusText", "Stopped (ESC×2)")
            } else {
              client.pause()
              setState("statusText", "Paused — press ESC again to stop")
            }
          }
          break
        case "pause":
          client.pause()
          setState("statusText", "Paused. Press again to resume.")
          break
        case "toggle-auto":
          setState((prev) => ({ ...prev, autoMode: !prev.autoMode }))
          break
        // ── Scroll actions ──
        case "scroll-up":
          setState("scrollOffset", (prev: number) => prev + 1)
          break
        case "scroll-down":
          setState("scrollOffset", (prev: number) => Math.max(0, prev - 1))
          break
        case "page-up":
          setState("scrollOffset", (prev: number) => prev + 5)
          break
        case "page-down":
          setState("scrollOffset", (prev: number) => Math.max(0, prev - 5))
          break
        case "scroll-top":
          // Scroll to top: set a very large offset (clamped in ChatArea)
          setState("scrollOffset", 99999)
          break
        case "scroll-bottom":
          setState("scrollOffset", 0)
          break
      }
    }
  })

  // ---- Render ----

  // Welcome screen
  const showWelcome = () => msgCount() === 0 && !isGenerating() && !!state.modelName

  // Help text lines
  const helpLines = createMemo(() => HELP_TEXT.split("\n"))
  const helpMax = () => Math.max(5, Math.min(terminalRows() - 12, 10))
  const helpEnd = () => Math.max(0, helpLines().length - helpScroll())
  const helpStart = () => Math.max(0, helpEnd() - helpMax())
  const visibleHelp = () => helpLines().slice(helpStart(), helpEnd())

  // Cheatsheet: grouped by category. Pressing ? toggles the overlay.
  const CHEATSHEET: Array<[string, string, "Global" | "Session" | "Input" | "View" | "Help"]> = [
    ["Ctrl+L", "清屏", "Global"],
    ["Ctrl+S", "暂停/继续", "Global"],
    ["Ctrl+N", "新建会话", "Session"],
    ["Ctrl+R", "会话历史", "Session"],
    ["Ctrl+P", "命令面板", "Input"],
    ["Tab", "切换规划/执行", "Input"],
    ["@", "引用文件", "Input"],
    ["!", "系统工具", "Input"],
    ["↑ / ↓", "浏览历史", "Input"],
    ["Ctrl+E", "展开工具调用", "View"],
    ["PgUp/PgDn", "滚动消息", "View"],
    ["?", "本速查", "Help"],
    ["Esc Esc", "停止生成", "Global"],
  ]
  const groupColor = (g: string): string => {
    const t2 = theme as any
    if (g === "Input") return t2.primary
    if (g === "Session") return t2.success
    if (g === "View") return t2.info
    if (g === "Help") return t2.warning
    return t2.text
  }

  return (
    <box flexDirection="column" width="100%"
      onMouseDown={(evt: any) => {
        if (evt.button === MouseButton.RIGHT) {
          const sel = renderer.getSelection()
          const text = sel ? sel.getSelectedText() : ""
          if (text) {
            Clipboard.copy(text).catch(() => {})
            setState("statusText", "Copied " + text.length + " chars")
            renderer.clearSelection?.()
          } else {
            // 无选区 → 尝试粘贴剪贴板文本
            Clipboard.readText().then(pasted => {
              if (pasted) {
                setInput(v => v + pasted)
                setState("statusText", "Pasted " + pasted.length + " chars")
              }
            }).catch(() => {})
          }
          return
        }
        selection.handleMouseDown(evt)
      }}
      onMouseMove={selection.handleMouseMove}
      onMouseUp={selection.handleMouseUp}
    >
      {connected() ? (
        <box flexDirection="column">
          {/* Welcome message — two-column layout with vertical divider */}
          {showWelcome() && (
            <box borderStyle="single" border={true} borderColor={theme.primary} flexDirection="column" marginBottom={1}>
              <box flexDirection="row">
                {/* Left column: version, model, workspace */}
                <box flexDirection="column" width="50%" paddingX={1}>
                  <box>
                    <text attributes={1} fg={theme.primary}>{">_ JWCode v3.0.0"}</text>
                  </box>
                  <box>
                    <text fg={theme.textMuted}>  模型: </text>
                    <text fg={theme.success}>{state.modelName || "connecting..."}</text>
                    <text fg={theme.textMuted}>  /model 切换</text>
                  </box>
                  <box>
                    <text fg={theme.textMuted}>  目录: </text>
                    <text fg={theme.warning}>{process.cwd()}</text>
                  </box>
                </box>
                {/* Vertical divider */}
                <text fg={theme.border}>│</text>
                {/* Right column: tips and commands */}
                <box flexDirection="column" width="50%" paddingX={1}>
                  <box>
                    <text attributes={1} fg={theme.primary}>Quick Commands</text>
                  </box>
                  <box>
                    <text fg={theme.info}>  /plan 先规划再执行  /auto 自动批准  PgUp/PgDn 滚动</text>
                  </box>
                  <box>
                    <text fg={theme.textMuted}>  Tips: </text>
                    <text fg={theme.info}>{WELCOME_TIPS[currentTip()]}</text>
                  </box>
                </box>
              </box>
            </box>
          )}

          {/* Chat area */}
          <box flexGrow={paletteActive() ? 0 : 1} flexDirection="column">
            <ChatArea
              terminalCols={terminalCols()}
              terminalRows={terminalRows()}
            />
          </box>

          {/* Plan task board (if plan tasks exist) */}
          {planTasks().length > 0 && (
            <PlanTaskBoard tasks={planTasks()} terminalCols={terminalCols()} />
          )}

          {/* Input box — bordered area */}
          <box
            borderStyle="round"
            borderColor={theme.border}
            backgroundColor={theme.inputBackground}
            flexDirection="column"
            marginTop={1}
            marginX={1}
          >
            <box paddingLeft={1}>
              <text fg={theme.success} attributes={1}>{"> "}</text>
              <TextInput
                value={input()}
                onChange={handleChange}
                onSubmit={handleSubmit}
                placeholder="Type a message or / for commands..."
                disabled={approvalQueue.currentApproval !== null}
                bottomReserved={textInputBottomReserved()}
              />
            </box>
          </box>

          {/* Init progress */}
          {state.initProgress && state.initProgress.percent < 100 && (
            <box paddingX={1} height={1}>
              <text fg={theme.warning}>{state.initProgress.message}</text>
            </box>
          )}
          {state.initProgress?.stage === "complete" && (
            <box paddingX={1} height={1}>
              <text fg={theme.success}>✓ {state.initProgress.message}</text>
            </box>
          )}
          {state.initProgress?.stage === "error" && (
            <box paddingX={1} height={1}>
              <text fg={theme.error}>✗ {state.initProgress.message}</text>
            </box>
          )}

          {/* Palettes / modals */}
          <box flexDirection="column">
            {showPalette() && (
              <CommandPalette filter={input()} onSelect={handlePaletteSelect} />
            )}
            {showFilePalette() && (
              <FilePalette query={fileQuery()} files={fileList()} onSelect={handleFileSelect} />
            )}
            {showSessionPicker() && (
              <SessionPicker
                sessions={sessionList()}
                onSelect={handleSessionSelect}
                onDelete={handleSessionDelete}
              />
            )}
            {showToolPalette() && (
              <ToolPalette tools={systemTools()} filter={toolQuery()} onSelect={handleToolSelect} />
            )}
            {showHelp() && (
              <box borderStyle="single" border={true} borderColor={theme.primary} flexDirection="column" paddingX={1}>
                {helpLines().length > helpMax() && (
                  <box>
                    <text fg={theme.textMuted}>
                      {"  " + (helpStart() + 1) + "-" + helpEnd() + " / " + helpLines().length + "  PgUp/PgDn scroll / Esc close"}
                    </text>
                  </box>
                )}
                {visibleHelp().map((line, i) => (
                  <text fg={theme.primary}>{line}</text>
                ))}
              </box>
            )}
            {showCheatsheet() && (
              <box borderStyle="round" border={true} borderColor={theme.warning} flexDirection="column" paddingX={1}>
                <box marginBottom={1}>
                  <text fg={theme.warning} attributes={1}>Keyboard Shortcuts</text>
                  <text fg={theme.textMuted}>  (press ? to close)</text>
                </box>
                {CHEATSHEET.map(([key2, desc, group]) => (
                  <box>
                    <text fg={groupColor(group)}>{group.padEnd(8)}</text>
                    <text fg={theme.textMuted}>  {key2.padEnd(12)}</text>
                    <text fg={theme.text}>{desc}</text>
                  </box>
                ))}
              </box>
            )}
            {state.showConfigPanel && (
              <ConfigPanel onClose={() => setState("showConfigPanel", false)} />
            )}
            {approvalQueue.currentApproval && (
              <ApprovalModal
                toolName={approvalQueue.currentApproval.toolName}
                payload={approvalQueue.currentApproval.payload}
                onAllow={() => approvalQueue.approveCurrent()}
                onDeny={() => approvalQueue.denyCurrent()}
                onAllowSession={() => approvalQueue.allowSession()}
                onAutoMode={() => setState("autoMode", true)}
                queuePosition={{ current: 1, total: approvalQueue.queue.length }}
              />
            )}
          </box>
        </box>
      ) : (
        <box flexDirection="column">
          <box flexGrow={1} flexDirection="column">
            <ChatArea terminalCols={terminalCols()} terminalRows={terminalRows()} />
          </box>
          <box
            borderStyle="round"
            borderColor={theme.border}
            backgroundColor={theme.inputBackground}
            paddingLeft={1}
            marginX={1}
          >
            <text fg={theme.textMuted}>Connecting...</text>
          </box>
        </box>
      )}

      {/* Status line */}
      <StatusLine
        selectionActive={selection.selection().active}
        selectionChars={selection.selection().text.length}
        selectedText={selection.selection().text}
      />

      {/* Disconnected warning */}
      {!connected() && (
        <box height={1}>
          <text fg={theme.error}>Backend not connected -- WebSocket reconnecting.</text>
        </box>
      )}

      {/* Plan waiting confirm */}
      {state.planWaiting && (
        <box height={1}>
          <text fg={theme.warning} attributes={1}>Plan ready -- /confirm to execute, /cancel to discard.</text>
        </box>
      )}
    </box>
  )
}