# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build Java backend only (Maven, skip tests for speed)
cd jwcode-parent && mvn compile -pl ../jwcode-core,../jwcode-web -am

# Build with tests
cd jwcode-parent && mvn package -pl ../jwcode-core,../jwcode-web -am

# Start Java backend (HTTP :8080, WS :8081)
mvn exec:java -pl jwcode-web -Dexec.mainClass=com.jwcode.web.WebLauncher -Dexec.args="8080 8081"

# Build frontend only (Vite → dist/)
cd jwcode-web && npm run build

# TypeScript CLI — one-command build + TUI
cd ts-cli
npm install       # first time only
npm run go        # bundle + connect to running backend
npm start         # bundle + start backend + TUI
```

The TS CLI bundles via esbuild into `dist/cli.js` (ESM, Node 18+). `npm run go` = `node build.mjs && node dist/cli.js run`.
Web frontend builds with `tsc && vite build` → `jwcode-web/dist/`, served from classpath by Java backend.

## Architecture

**Four-layer Harness Engineering (v3.1):**
- L4 Observability: CostTracker, ObservationPipeline, Doctor, analytics
- L3 Quality: 5-level context compaction, AI self-healing, semantic memory
- L2 Cost: ModelRouter dynamic routing, token budget partitioning, prompt caching
- L1 Security: DockerSandbox, WorkspaceGuard, PermissionManager, HookChain interceptor

**Five Maven modules** (`jwcode-parent/pom.xml` aggregates all):
| Module | Purpose |
|--------|---------|
| `jwcode-common` | Shared utilities: auth, config, helpers (6 files) |
| `jwcode-core` | Core engine: 47 tools, 17 agents, LLM orchestration, planner, hooks (~630 files) |
| `jwcode-web` | HTTP/WS server (`com.sun.net.httpserver`) + React SPA frontend (Vite+Tailwind) |
| `jwcode-mcp` | MCP client interface (1 file) |
| `jwcode-parser` | Tree-sitter code analysis (8 files) |

**Key design rules:**
- Orchestrator agents **never execute tools directly** — they decompose and delegate to sub-agents
- Sub-agents **cannot recursively spawn** more sub-agents
- Reviewer/Explorer agents are **read-only** (cannot modify files)
- LLM maintains **no cross-turn state** — framework manages all state
- All tool execution passes through `PermissionManager` → `HookChain` → execution
- Session recovery uses checkpoints + message replay buffer

## Key Paths

- **Entry point:** `jwcode-web/.../WebLauncher.java` (starts `WebServer` with `com.sun.net.httpserver`)
- **Agent system:** `jwcode-core/.../agent/` — 17 types: Orchestrator, Coder, Debugger, Architect, Reviewer, Explorer, etc.
- **Tools:** `jwcode-core/.../tool/` — 47 tools: `execution/`, `shell/`, `analysis/`, `permission/`, `io/`
- **WebSocket handler:** `jwcode-web/.../stream/StreamingWebSocketHandler.java` (3062 lines)
- **React frontend:** `jwcode-web/src/` — Vite+React SPA, zustand stores, Tailwind CSS
- **TS CLI:** `ts-cli/src/` — esbuild-bundled Ink/React TUI, `ws` for WebSocket
- **Config:** `~/.jwcode/config.yaml` (backend_url, ws_url, ws_auth_token)
- **Hooks:** `~/.jwcode/hooks/` — user-defined lifecycle hook scripts
- **Memory:** `~/.claude/projects/.../memory/` — persistent Claude Code memory

## WebSocket Protocol

Messages: `{type, sessionId, message?, data?}` — 30+ event types (`start`, `content`, `thinking`, `tool_call`, `tool_result`, `step_*`, `plan_*`, `hook_ask`, `token_update`, `error`, etc.)

Auth flow: connect → `auth_required` → client sends `{type:"auth", token:"default-token"}` → `auth_success` → `POST /api/sessions` → message exchange.

## TS CLI (ts-cli/)

Built with Ink 5 (React for terminal), `ws`, esbuild. Source in `src/`, output is single `dist/cli.js`. Protocol in `src/protocol.ts` must stay in sync with Java backend's `MessageType` enum. WebSocket reconnect logic in `src/client.ts` uses `_reconnecting` flag to prevent timer stacking.

**Key components:**
| File | Purpose |
|------|---------|
| `src/App.tsx` | Root component: WS event wiring, keyboard, command execution, layout |
| `src/components/TextInput.tsx` | Text input with paste support (CJK-safe), ↑↓ input history (30 entries), token/char counter |
| `src/components/ChatArea.tsx` | Virtual message window with scrollOffset, `[X/Y]` position indicator |
| `src/components/StatusLine.tsx` | Model, token bar, Plan/Act tag, Auto tag, message count, connection indicator |
| `src/components/CommandPalette.tsx` | `/` filterable popup with PgUp/PgDn page navigation |
| `src/theme.ts` | Color constants, `JWCODE_THEME=dark\|light` env var support |

**Text input features:** ↑↓ browses last 30 inputs (sessionStorage). CJK ≈ 1.5 chars/token, English ≈ 4 chars/token. Warning at >100K tokens.

## ttyd Terminal (Web)

The web terminal tab uses [ttyd](https://github.com/tsl0922/ttyd) as a PTY sidecar to provide a real terminal running `ts-cli` in the browser.

**Architecture:** Browser xterm.js + AttachAddon → `ws://127.0.0.1:{port}/ws` → ttyd → PTY → `node dist/cli.js run`

**Backend files:**
| File | Purpose |
|------|---------|
| `terminal/TerminalSession.java` | ttyd process lifecycle: spawn, port allocation, kill, PATH detection |
| `terminal/TerminalHandler.java` | REST API: `POST /api/terminal/start`, `POST /api/terminal/stop`, `GET /api/terminal/status` |

**Frontend files:**
| File | Purpose |
|------|---------|
| `stores/terminalStore.ts` | Session lifecycle state (idle/starting/running/error) |
| `components/Terminal/TerminalView.tsx` | xterm.js + AttachAddon, resize protocol, restart on error |

**API flow:** Frontend `POST /api/terminal/start {workspaceDir}` → Java finds free port (8090+), spawns `ttyd --port P --interface 127.0.0.1 --cwd DIR node dist/cli.js run` → returns `{ttydPort, wsUrl}` → xterm.js connects to ttyd WS directly.

**Requirements:** ttyd must be installed on PATH. Terminal tab is hidden when ttyd is not detected (checked via `GET /api/terminal/status` on startup).

## Web Frontend (jwcode-web/src/)

**Architecture:** Vite+React 18 SPA, Tailwind CSS, zustand stores, tab-based navigation (no React Router).

**Key stores (all zustand):**
| Store | Purpose | Persist? |
|-------|---------|----------|
| `chatStore` | Per-session messages, generation state | localStorage (debounced) |
| `sessionStore` | Session tabs, history, per-session tasks | localStorage |
| `planStore` | Plan mode state, structured tasks, mode history | no |
| `terminalStore` | Terminal session lifecycle (idle/running/error) | no |
| `toastStore` | Toast notifications with auto-dismiss | no |
| `tokenStore` | Token usage tracking | no |
| `settingsStore` | Theme, workspace dir, feature toggles | localStorage |
| `useHookApprovalStore` | Hook approval queue, auto-mode | no |

**WebSocket handlers** (`src/hooks/handlers/`): 775-line `useWebSocket.ts` split into category modules:
| Module | Message types handled |
|--------|----------------------|
| `planHandlers.ts` | plan_start, plan_thinking, plan_tasks, plan_task_start/update/result, plan_complete, plan_error, plan_mode_change, step_prompt |
| `streamHandlers.ts` | start, content, thinking, tool_call, tool_result, complete, error, generation_paused/resumed |
| `systemHandlers.ts` | token_update, auth_*, log, commands_list, ping, workspace_changed |
| `interactionHandlers.ts` | hook_ask, task_update, step_*, todo_update, todo_item_done |

**Recent optimizations:**
- ChatPanel uses `react-virtuoso` for virtualized message list with `followOutput`
- WebSocket infinite reconnect (exponential backoff, no max attempts)
- FileTreeView split-pane with Monaco Editor for file preview/edit + save
- Toast notification system for connection, error, save events
- ErrorBoundary wrapping lazy-loaded tabs
- Plan/Act/Auto mode toggles in StatusLine with colored badges
- Collapsible sidebar panels with React state (no DOM hacks)
- Terminal tab conditionally hidden when ttyd unavailable
- Messages capped at 200/session with debounced localStorage persistence
