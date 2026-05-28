# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build Java backend only (Maven)
cd jwcode-parent && mvn compile -pl ../jwcode-core,../jwcode-web -am

# Start Java backend (HTTP :8080, WS :8081)
mvn exec:java -pl jwcode-web -Dexec.mainClass=com.jwcode.web.WebLauncher -Dexec.args="8080 8081"

# TypeScript CLI — one-command build + TUI
cd ts-cli
npm install       # first time only
npm run go        # bundle + connect to running backend
npm start         # bundle + start backend + TUI
```

The TS CLI bundles via esbuild into `dist/cli.js` (ESM, Node 18+). `npm run go` = `node build.mjs && node dist/cli.js run`.

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

Built with Ink 5 (React for terminal), `ws`, esbuild. Source in `src/`, output is single `dist/cli.js`. Protocol in `src/protocol.ts` must stay in sync with Java backend's `MessageType` enum. Text input in `src/components/TextInput.tsx` uses ink's `useInput` hook — the `input.length === 1` filter was removed for CJK support. WebSocket reconnect logic in `src/client.ts` uses `_reconnecting` flag to prevent timer stacking.
