#!/usr/bin/env bun
/**
 * JWCode TypeScript CLI — entry point (OpenTUI/Solid).
 *
 * Commands:
 *   jwcode start [-p <port>] [-B] [-w <dir>]
 *   jwcode run [-b <url>] [--ws <url>]
 *   jwcode version
 *
 * jwcode (no subcommand) defaults to 'start' — launches backend
 * and opens the interactive TUI. The current working directory
 * becomes the workspace directory.
 */
import { render } from "@opentui/solid"
import { createCliRenderer } from "@opentui/core"
import { existsSync, appendFileSync } from "node:fs"
import { join } from "node:path"
import {
  findInstallDir, findJar, findMvn, buildBackend,
  startBackend, waitForBackend, cleanupBackend,
  findAvailablePorts, portInUse, killPort,
  findRunningDaemon, startDaemon, clearDaemonInfo, readDaemonInfo, writeDaemonInfo,
  isDaemonAlive,
} from "./launcher"
import { loadConfig } from "./config"
import type { ChildProcess } from "node:child_process"

// ---- OpenTUI imports ----
import { App } from "./solid/components/App"
import { SetupWizard } from "./solid/components/SetupWizard"
import { KVProvider } from "./solid/context/kv"
import { ThemeProvider } from "./solid/context/theme"
import { ClientProvider } from "./solid/hooks/ClientProvider"
import { AppStateProvider } from "./solid/hooks/AppStateProvider"
import { printStoredMessages } from "./messageStore"

const VERSION = "3.0.0";

// ---- EPIPE / ECONNRESET safety ----
["stdout", "stderr"].forEach((name) => {
  const stream = (process as any)[name]
  if (stream) {
    stream.on("error", (e: NodeJS.ErrnoException) => {
      if (e.code === "EPIPE" || e.code === "ECONNRESET") { /* suppress */ }
    })
  }
})

process.on("unhandledRejection", (reason: unknown) => {
  const err = reason instanceof Error ? reason : new Error(String(reason))
  try { appendFileSync("crash.log", `[${new Date().toISOString()}] UNHANDLED: ${err.message}\n${err.stack}\n`); } catch {}
  console.error("Unhandled rejection:", err.message, err.stack)
})

process.on("uncaughtException", (err: NodeJS.ErrnoException) => {
  if (err.code === "EPIPE" || err.code === "ECONNRESET") return
  try { appendFileSync("crash.log", `[${new Date().toISOString()}] FATAL: ${err.message}\n${err.stack}\n`); } catch {}
  console.error("Fatal error:", err.message, err.stack)
  process.exit(1)
})

// ---- Usage ----

function printUsage(): void {
  console.log(`JWCode CLI v${VERSION}`)
  console.log("")
  console.log("Usage:")
  console.log("  jwcode [options]              Start backend + interactive terminal (default)")
  console.log("  jwcode start [options]        Start backend + interactive terminal")
  console.log("  jwcode run [options]          Connect to existing backend")
  console.log("  jwcode stop                   Stop running daemon")
  console.log("  jwcode update                 Check for updates / update CLI")
  console.log("  jwcode version                Print version")
  console.log("")
  console.log("Options:")
  console.log("  -p, --port <port>             HTTP port (default: auto, first available from 8080)")
  console.log("  -w, --workspace <dir>         Workspace directory (default: current directory)")
  console.log("  -F, --force                   Kill existing process on target port before starting")
  console.log("  -B, --build                   Force rebuild backend (dev mode only)")
  console.log("  -b, --backend <url>           Backend URL (run mode, WS auto-derived)")
  console.log("  --ws <url>                    Override WebSocket URL")
  console.log("")
  console.log("Environment:")
  console.log("  JWCODE_THEME=dark|light       Color theme (default: dark)")
  console.log("  JWCODE_NO_MOUSE=1             Disable mouse capture, enable native text selection")
}

function parseArgs(): Record<string, string | boolean> {
  const args: Record<string, string | boolean> = {}
  const argv = process.argv.slice(2)
  for (let i = 0; i < argv.length; i++) {
    const arg = argv[i]
    if (arg === "--build" || arg === "-B") args.build = true
    else if (arg === "--force" || arg === "-F") args.force = true
    else if (arg === "--port" || arg === "-p") args.port = argv[++i]
    else if (arg === "--workspace" || arg === "-w") args.workspace = argv[++i]
    else if (arg === "--backend" || arg === "-b") args.backend = argv[++i]
    else if (arg === "--ws") args.ws = argv[++i]
    else if (!arg.startsWith("-")) args._cmd = arg
  }
  return args
}

// ---- OpenTUI render helpers ----

let _renderer: Awaited<ReturnType<typeof createCliRenderer>> | null = null

async function getRenderer() {
  if (!_renderer) {
    const noMouse = process.env.JWCODE_NO_MOUSE === "1" || process.env.JWCODE_NO_MOUSE === "true"
    _renderer = await createCliRenderer({
      targetFps: 30,
      exitOnCtrlC: true,
      useMouse: !noMouse,
    })
  }
  return _renderer
}

/** Render an app and wait for completion. */
/** Render an app and wait for completion.
 *  Pass a stop function into the component callback so child components
 *  (e.g. SetupWizard.onComplete) can terminate the render cycle.
 */
async function renderApp(component: (stop: () => void) => any) {
  const renderer = await getRenderer()
  const stop = () => { renderer.stop() }
  await render(() => component(stop), renderer)
}

async function renderAppWithCleanup(component: () => any): Promise<() => Promise<void>> {
  const renderer = await getRenderer()
  const p = render(component, renderer).catch((err) => {
    console.error("Render failed:", err)
  })
  return async () => {
    renderer.stop()
    await p
  }
}

// ---- TUI ----

function getThemeMode(): "dark" | "light" {
  const env = process.env.JWCODE_THEME || ""
  if (env === "light" || env === "dark") return env
  // Attempt to detect from terminal background
  return "dark" // default
}

async function startTUI(httpPort: number, wsPort: number, _workspaceDir: string, _installDir: string, onBeforeExit?: () => void): Promise<void> {
  const backendUrl = `http://localhost:${httpPort}`
  const wsUrl = `ws://localhost:${wsPort}/ws`

  // Enter alternate screen
  process.stdout.write("\x1b[?1049h\x1b[2J\x1b[H")

  // First-run: check if a provider is configured, show setup wizard if not
  let providerConfigured = false
  try {
    const statusRes = await fetch(`${backendUrl}/api/config/provider`)
    const status = await statusRes.json() as any
    providerConfigured = status?.data?.configured === true
  } catch { /* proceed to wizard if status check fails */ }

  if (!providerConfigured) {
    await renderApp((stop) => (
      <AppStateProvider>
        <KVProvider>
          <ThemeProvider mode={getThemeMode()}>
            <SetupWizard
              backendUrl={backendUrl}
              onComplete={() => { stop() }}
              mode="fullscreen"
            />
          </ThemeProvider>
        </KVProvider>
      </AppStateProvider>
    ))
  }

  // Main app
  const stop = await renderAppWithCleanup(() => (
    <KVProvider>
      <ThemeProvider mode={getThemeMode()}>
        <AppStateProvider>
          <ClientProvider backendUrl={backendUrl} wsUrl={wsUrl}>
            <App
              backendUrl={backendUrl}
              wsUrl={wsUrl}
              onExit={() => {
                // Exit alt screen first so output goes to main buffer
                process.stdout.write("\x1b[?1049l")
                if (onBeforeExit) onBeforeExit()
                printStoredMessages()
                process.exit(0)
              }}
            />
          </ClientProvider>
        </AppStateProvider>
      </ThemeProvider>
    </KVProvider>
  ))

  // Wait for exit signal
  await new Promise<void>((resolve) => {
    process.on("SIGINT", () => resolve())
    process.on("SIGTERM", () => resolve())
  })
  await stop()
  // Exit alternate screen first so output goes to main buffer
  process.stdout.write("\x1b[?1049l")
  // Then run cleanup (daemon kill) + print messages — visible in main buffer
  if (onBeforeExit) onBeforeExit()
  printStoredMessages()
  process.exit(0)
}

// ---- Commands ----

async function cmdStart(args: Record<string, string | boolean>): Promise<void> {
  const forceKill = !!args.force
  const build = !!args.build
  const workspaceDir = String(args.workspace || process.cwd())
  const installDir = findInstallDir()

  // Try existing daemon first
  const existingDaemon = findRunningDaemon(workspaceDir)
  if (existingDaemon) {
    const httpPort = existingDaemon.httpPort
    const wsPort = existingDaemon.wsPort
    console.log("[daemon] Reusing existing daemon (PID " + existingDaemon.pid + ") on ports " + httpPort + "/" + wsPort)
    writeDaemonInfo(existingDaemon.pid, httpPort, wsPort, workspaceDir)
    await startTUI(httpPort, wsPort, workspaceDir, installDir)
    return
  }

  // Determine ports
  let httpPort: number
  let wsPort: number
  if (args.port) {
    httpPort = parseInt(String(args.port), 10)
    wsPort = httpPort + 1
    if (!forceKill && (portInUse(httpPort) || portInUse(wsPort))) {
      console.error(`[launcher] Port ${httpPort} or ${wsPort} is already in use.`)
      console.error("[launcher] Use --force to replace the existing process, or omit -p for auto port selection.")
      process.exit(1)
    }
  } else {
    const ports = findAvailablePorts(8080)
    httpPort = ports.httpPort
    wsPort = ports.wsPort
  }

  console.log("╔══════════════════════════════════════╗")
  console.log("║   JWCode — Java AI Coding Tool       ║")
  console.log("╚══════════════════════════════════════╝")
  console.log(`  Workspace: ${workspaceDir}`)
  console.log(`  HTTP API:  http://localhost:${httpPort}`)
  console.log(`  WebSocket: ws://localhost:${wsPort}/ws`)

  // Build or find JAR
  if (build || !findJar(installDir)) {
    if (existsSync(join(installDir, "pom.xml"))) {
      buildBackend(installDir)
    } else if (!findJar(installDir)) {
      console.error("[launcher] Backend JAR not found and no pom.xml for build.")
      console.error("[launcher] Install via: npm install -g @jwcode/cli")
      process.exit(1)
    }
  }

  // Start daemon
  const daemonProc = startDaemon({ installDir, workspaceDir, port: httpPort, wsPort, forceKill, idleTimeout: 300 })

  // Ensure daemon is killed on any exit (Ctrl+C via exitOnCtrlC, crash, etc.)
  let daemonCleanedUp = false
  process.on("exit", () => {
    if (daemonCleanedUp) return
    daemonCleanedUp = true
    cleanupBackend(daemonProc)
    clearDaemonInfo()
  })

  await waitForBackend(httpPort)

  // Background update check
  import("./update").then(({ checkForUpdates }) => {
    checkForUpdates(VERSION).then((info) => {
      if (info?.updateAvailable) {
        console.log(`\n  Update available: v${info.current} → v${info.latest}`)
        console.log(`  Run 'jwcode update' (or '${info.url}') to upgrade.\n`)
      }
    }).catch(() => {})
  })

  // Daemon health monitor
  const healthTimer = setInterval(() => {
    import("./launcher").then(({ readDaemonInfo, isDaemonAlive, startDaemon, writeDaemonInfo }) => {
      const info = readDaemonInfo()
      if (info && !isDaemonAlive(info)) {
        console.error("\n[daemon] Daemon process died. Attempting auto-restart...")
        try {
          const dp = startDaemon({
            installDir, workspaceDir, port: httpPort, wsPort, forceKill: true, idleTimeout: 300,
          })
          writeDaemonInfo(dp.pid!, httpPort, wsPort, workspaceDir)
          console.error("[daemon] Daemon restarted successfully.")
        } catch (e) {
          console.error("[daemon] Auto-restart failed:", String(e))
        }
      }
    }).catch(() => {})
  }, 30_000)

  await startTUI(httpPort, wsPort, workspaceDir, installDir, () => {
    if (daemonCleanedUp) return
    daemonCleanedUp = true
    cleanupBackend(daemonProc)
    clearDaemonInfo()
  })
  clearInterval(healthTimer)
}

async function cmdRun(args: Record<string, string | boolean>): Promise<void> {
  if (!process.stdin.isTTY) {
    console.error("Error: jwcode requires a real terminal (TTY).")
    console.error("Please run from a terminal emulator like Windows Terminal, CMD, or PowerShell.")
    process.exit(1)
  }

  // Enable bracketed paste mode
  if (process.env.JWCODE_NO_BRACKETED_PASTE !== "1") {
    process.stdout.write("\x1b[?2004h")
  }

  const config = loadConfig()
  const backendUrl = String(args.backend || config.backend_url)
  const wsUrl = String(
    args.ws ||
    backendUrl.replace(/^http/, "ws").replace(/:(\d+)/, (_: string, p: string) => ":" + (parseInt(p) + 1)) + "/ws" ||
    config.ws_url,
  )

  // Enter alternate screen
  process.stdout.write("\x1b[?1049h\x1b[2J\x1b[H")

  const stop = await renderAppWithCleanup(() => (
    <KVProvider>
      <ThemeProvider mode={getThemeMode()}>
        <AppStateProvider>
          <ClientProvider backendUrl={backendUrl} wsUrl={wsUrl}>
            <App
              backendUrl={backendUrl}
              wsUrl={wsUrl}
              onExit={() => {
                process.stdout.write("\x1b[?1049l")
                printStoredMessages()
                process.exit(0)
              }}
            />
          </ClientProvider>
        </AppStateProvider>
      </ThemeProvider>
    </KVProvider>
  ))

  await new Promise<void>((resolve) => {
    process.on("SIGINT", () => resolve())
    process.on("SIGTERM", () => resolve())
  })
  await stop()
  process.stdout.write("\x1b[?1049l")
  printStoredMessages()
  process.exit(0)
}

async function cmdStop(): Promise<void> {
  const { execSync } = await import("node:child_process")
  const { readDaemonInfo, clearDaemonInfo, isDaemonAlive } = await import("./launcher")
  const info = readDaemonInfo()
  if (!info) {
    console.log("No daemon is running.")
    return
  }
  if (!isDaemonAlive(info)) {
    console.log("Daemon process is already dead. Cleaning up.")
    clearDaemonInfo()
    return
  }
  console.log("Stopping JWCode daemon (PID " + info.pid + ")...")
  try {
    if (process.platform === "win32") {
      execSync("taskkill /F /T /PID " + info.pid, { stdio: "ignore" })
    } else {
      process.kill(info.pid, "SIGTERM")
    }
    clearDaemonInfo()
    console.log("Daemon stopped.")
  } catch (e) {
    console.error("Failed to stop daemon:", String(e))
  }
}

async function cmdUpdate(): Promise<void> {
  const { checkForUpdates, runNpmUpdate } = await import("./update")
  console.log("Checking for updates...")
  const info = await checkForUpdates(VERSION)
  if (!info) {
    console.log("Could not check for updates. Try again later or install manually:")
    console.log("  npm install -g @jwcode/cli@latest")
    return
  }
  if (!info.updateAvailable) {
    console.log(`JWCode CLI is up to date (v${info.current}).`)
    return
  }
  console.log(`Update available: v${info.current} → v${info.latest}`)
  console.log("")
  if (info.body) {
    const lines = info.body.split("\n").filter((l: string) => l.trim()).slice(0, 3)
    for (const line of lines) console.log("  " + line)
    console.log("")
  }
  const readline = await import("node:readline")
  const rl = readline.createInterface({ input: process.stdin, output: process.stdout })
  rl.question("Update now? [Y/n] ", (answer: string) => {
    rl.close()
    if (answer.toLowerCase() === "n" || answer.toLowerCase() === "no") {
      console.log("Skipped. Run \"jwcode update\" later or: npm install -g @jwcode/cli@latest")
      process.exit(0)
      return
    }
    console.log("Updating...")
    const result = runNpmUpdate()
    if (result.ok) {
      console.log("Updated successfully. Restart jwcode to use the new version.")
    } else {
      console.error("Update failed:", result.message)
      console.log("Try manually: npm install -g @jwcode/cli@latest")
    }
    process.exit(result.ok ? 0 : 1)
  })
}

// ---- Main ----

async function main(): Promise<void> {
  if (process.argv.includes("--help") || process.argv.includes("-h") || process.argv.includes("help")) {
    printUsage()
    return
  }
  if (process.argv.includes("--version") || process.argv.includes("-v") || process.argv.includes("version")) {
    console.log(`JWCode CLI v${VERSION}`)
    return
  }

  const args = parseArgs()
  const cmd = (args._cmd as string) || "start"

  switch (cmd) {
    case "start":
      await cmdStart(args)
      break
    case "run":
      await cmdRun(args)
      break
    case "stop":
      await cmdStop()
      break
    case "update":
      await cmdUpdate()
      break
    default:
      console.error(`Unknown command: ${cmd}`)
      printUsage()
      process.exit(1)
  }
}

main().catch((err) => {
  console.error("Fatal error:", err)
  process.exit(1)
})
