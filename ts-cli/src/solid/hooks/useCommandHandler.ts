/**
 * Command execution hook — handles /commands and normal chat dispatch.
 * Framework-agnostic (no React/Solid dependencies).
 */
import type { JwCodeClient } from "../../client"
import { createMessage } from "../../protocol"
import { SLASH_COMMANDS } from "../../commands"
import type { SetStoreFunction } from "solid-js/store"
import type { AppState } from "./AppStateProvider"

interface CommandHandlerOptions {
  client: JwCodeClient
  planMode: () => boolean
  setState: SetStoreFunction<AppState>
  setInput: (v: string) => void
  setShowHelp: (v: boolean) => void
  setShowPalette: (v: boolean) => void
  onExit: () => void
}

export function createCommandHandler(opts: CommandHandlerOptions) {
  const { client, planMode, setState, setInput, setShowHelp, setShowPalette, onExit } = opts

  return function executeCommand(value: string) {
    const text = value.trim()
    if (!text) return
    setInput("")
    setShowHelp(false)
    setShowPalette(false)

    const parts = text.startsWith("/") ? text.split(/\s+/) : []
    const cmd = parts[0] || null
    const cmdArg = parts.slice(1).join(" ")

    if (cmd && cmd in SLASH_COMMANDS) {
      const def = SLASH_COMMANDS[cmd]
      if (def === null) {
        setShowHelp(true)
        return
      }
      const { action, needsArg } = def

      switch (action) {
        case "__exit__":
          onExit()
          return
        case "__confirm_plan":
          setState((prev) => {
            if (!prev.planWaiting) return prev
            client.planConfirm()
            return { ...prev, planWaiting: false }
          })
          return
        case "__cancel_plan":
          setState("planWaiting", false)
          return
        case "plan_mode":
          setState("planMode", (v) => !v)
          return
        case "auto_mode":
          setState("autoMode", (v) => !v)
          return
        case "clear":
          setState((prev) => ({
            ...prev,
            messages: [],
            currentMessage: null,
          }))
          return
        case "model_change":
          if (needsArg && cmdArg) client.switchModel(cmdArg)
          else client.switchModel(cmdArg || "")
          return
        case "show_context":
          setState((prev) => ({
            ...prev,
            messages: [...prev.messages],
            statusText: `会话消息: ${prev.messages.length} | 模式: ${prev.planMode ? "规划" : "执行"} | 自动: ${prev.autoMode ? "开" : "关"} | 模型: ${prev.modelName || "未连接"}`,
          }))
          return
        case "show_config":
          setState("showConfigPanel", (v: boolean) => !v)
          return
        case "init_project":
          setState("initProgress", { stage: "scanning", message: "Scanning project directory...", percent: 0 })
          ;(async () => {
            try {
              const { scanProject, generateAgentMd, writeAgentMd } = await import("../util/initProject")
              const { loadConfig } = await import("../../config")
              const wsDir = loadConfig().workspace_dir || process.cwd()
              const meta = await scanProject(wsDir, (p: { stage: string; message: string; percent: number }) => setState("initProgress", { ...p }))
              setState("initProgress", { stage: "generating", message: "Generating agent.md...", percent: 90 })
              const content = generateAgentMd(meta)
              const filePath = await writeAgentMd(wsDir, content)
              setState("initProgress", { stage: "complete", message: `agent.md → ${filePath}`, percent: 100 })
              setTimeout(() => setState("initProgress", null), 5000)
            } catch (err) {
              setState("initProgress", { stage: "error", message: `Init failed: ${(err as Error).message}`, percent: 0, error: (err as Error).message })
              setTimeout(() => setState("initProgress", null), 8000)
            }
          })()
          return
        case "stop": client.stop(); return
        case "pause": client.pause(); return
        case "resume": client.resume(); return
        case "doctor": client.doctor(); return
        case "rewind": client.rewind(); return
        case "compact": client.compact(); return
        case "init": client.init(); return
        case "effort": if (cmdArg) client.effort(cmdArg); return
        case "branch": if (cmdArg) client.branch(cmdArg); return
        case "mcp": if (cmdArg) client.mcp(cmdArg); return
        case "skills": client.skills(); return
        case "agents": client.agents(); return
        case "config": if (cmdArg) client.config(cmdArg); return
        case "plugin": if (cmdArg) client.plugin(cmdArg); return
        case "tokens": client.send("tokens"); return
        case "memory": client.send("memory"); return
        case "export": if (cmdArg) client.send("export", undefined, { path: cmdArg }); return
        case "checkpoint": client.send("checkpoint"); return
        case "test": client.send("test"); return
        case "lint": client.send("lint"); return
        case "search": if (cmdArg) client.send("search", undefined, { query: cmdArg }); return
        case "project": client.send("project"); return
        case "setup_wizard": setState("statusText", "Setup wizard not yet implemented"); return
      }
      return
    }

    // Normal chat — ignore unmatched / prefixes
    if (text.startsWith("/") && !(cmd && cmd in SLASH_COMMANDS)) return

    const msg = createMessage("user", text)
    setState((prev) => ({ ...prev, messages: [...prev.messages, msg] }))
    client.chat(text, planMode())
  }
}
