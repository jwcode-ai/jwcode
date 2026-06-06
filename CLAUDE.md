# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build Java backend only (Maven, skip tests for speed)
mvn compile -pl jwcode-core,jwcode-web -am

# Build with tests
mvn package -pl jwcode-core,jwcode-web -am

# Start Java backend (HTTP :8080, WS :8081) — install first to ensure jwcode-core in local Maven repo
mvn install -pl jwcode-core,jwcode-web -am -DskipTests && mvn exec:java -pl jwcode-web -Dexec.mainClass=com.jwcode.web.WebLauncher -Dexec.args="8080 8081"

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

## LLM Service Layer (base_url Dual-Format Support)

**Architecture:**
```
LLMFactory (via ServiceRegistry routing)
    |
ServiceRegistry
    |-- OpenAIServiceProvider  (api-type = "openai-completions")
    |-- AnthropicServiceProvider (api-type = "anthropic-messages")
            |                          |
        OpenAILLMService         AnthropicLLMService
            |                          |
            +------- AbstractHttpLLMService --------+
                (HttpClient, retry, error mapping, SSE)
```

**Key classes:**
| Class | Purpose |
|-------|---------|
| `AbstractHttpLLMService` | Shared HTTP base: HttpClient (60s timeout, HTTP/1.1), exponential-backoff retry (2s->4s->8s, max 3), error code mapping, SSE line reading, 5 subclass hooks (buildRequestBody, buildRequestUri, buildExtraHeaders, parseResponse, processStreamEvent) |
| `ServiceConfig` | Standalone config with apiType, anthropicVersion, baseUrl, apiKeys, model, timeout, contextWindow |
| `ServiceRegistry` | Runtime provider registry: register(provider) / createService(apiType, config) / getSupportedTypes() |
| `ServiceProvider` | Interface: getApiType() / createService(ServiceConfig) |
| `OpenAIServiceProvider` | `"openai-completions"` -> `OpenAILLMService` |
| `AnthropicServiceProvider` | `"anthropic-messages"` -> `AnthropicLLMService` |
| `AnthropicMessageConverter` | Pure conversion: internal messages <-> Anthropic Messages API format (system top-level, content blocks: text/tool_use/thinking/tool_result, input_schema) |
| `AnthropicLLMService` | Implements Anthropic Messages API: non-streaming + SSE streaming with state machine (message_start, content_block_start/delta/stop, message_delta/stop, ping), input_json_delta accumulation for tool_use input |

**Config YAML format:**
```yaml
providers:
  deepseek:
    base-url: https://api.deepseek.com
    api-type: openai-completions
    api-keys: [ sk-xxx ]
    models:
      - id: deepseek-chat
        max-tokens: 8192

  deepseek-anthropic:
    base-url: https://api.deepseek.com/anthropic
    api-type: anthropic-messages
    api-keys: [ sk-xxx ]
    anthropic-version: 2023-06-01
    models:
      - id: deepseek-chat
        max-tokens: 8192

  claude:
    base-url: https://api.anthropic.com
    api-type: anthropic-messages
    api-keys: [ sk-ant-xxx ]
    models:
      - id: claude-sonnet-4-20250514
        max-tokens: 8192

default-provider: deepseek
```

**Key files:** `jwcode-core/.../llm/` -- AbstractHttpLLMService, ServiceConfig, ServiceRegistry, ServiceProvider, OpenAILLMService, OpenAIServiceProvider, AnthropicMessageConverter, AnthropicLLMService, AnthropicServiceProvider


**Four-layer Harness Engineering (v3.2):**
- L4 Observability: CostTracker, ObservationPipeline, Doctor, analytics
- L3 Quality: Pregel BSP graph orchestration, 5-level context compaction, AI self-healing, semantic memory
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

**Security pipeline (L1):**
| Component | Purpose |
|-----------|---------|
| `bash/CommandInjectionDetector` | Detects backtick, $(), ${}, path traversal, reverse shell patterns |
| `bash/CommandReadOnlyValidator` | 150+ read-only prefixes across Unix/Windows/git/npm/docker/kubectl/go/cargo/pip; riskScore 0-10 |
| `bash/SedCommandValidator` | Validates `sed -i`, `s///e` exec flag, `w`/`W` file write commands |
| `permission/AutoPermissionClassifier` | 7-layer heuristic: alwaysSafe→injection→userApproved→userDenied→riskScore→rateLimit→default |
| `permission/PermissionManager` | Auto-mode switch delegating to AutoPermissionClassifier; session learning with approved/denied pattern sets |
| `DockerSandboxExecutor` | alpine:3.20, --pids-limit=100, --read-only, --tmpfs noexec/nosuid, --security-opt=no-new-privileges |

**Key design rules:**
- Orchestrator agents **never execute tools directly** — they decompose and delegate to sub-agents
- Sub-agents **cannot recursively spawn** more sub-agents
- Reviewer/Explorer agents are **read-only** (cannot modify files)
- LLM maintains **no cross-turn state** — framework manages all state
- All tool execution passes through `PermissionManager` → `HookChain` → execution
- Session recovery uses checkpoints + message replay buffer

**Agent Graph System (v3.2 — LangGraph-inspired):**
- Declarative DAG-based orchestration replacing hardcoded if-else agent pipelines
- Channel-based state with typed reducers (LastValue, BinaryOp, Topic, Ephemeral)
- BSP (Bulk Synchronous Parallel) execution: per-superstep plan → execute → write → checkpoint
- Channel version tracking for fine-grained "which node saw what" incremental execution
- Interrupt/resume with checkpoint persistence at any superstep boundary

| Component | Purpose |
|-----------|---------|
| `AgentGraph` | Builder DSL: `addNode` / `addEdge` / `addConditionalEdge` / `compile` |
| `CompiledAgentGraph` | PregelLoop execution engine with channel version tracking |
| `OrchestratorGraphFactory` | Pre-built workflows: featureDev, bugFix, review, refactor, conditional routing |
| `GraphState` | Channel collection + subscriber mapping per node |
| `GraphNode` | Agent wrapper with triggers, writes, retryPolicy, timeout |
| `GraphEdge` | Simple edges (A→B) or conditional edges (router function) |
| `Channel<T>` | Typed state slot: `get()` / `update()` / `checkpoint()` / `consume()` |

**Channel semantics (LangGraph equivalents):**
| Channel | LangGraph | Behavior |
|---------|-----------|----------|
| `LastValueChannel<T>` | `LastValue` | Last writer wins (default) |
| `BinaryOpChannel<T>` | `BinaryOperatorAggregate` | Reducer-merge (e.g. message list append) |
| `TopicChannel<E>` | `Topic` | Accumulating pub/sub, drained on consume |
| `EphemeralChannel<T>` | `EphemeralValue` | Not persisted in checkpoints |

**Checkpoint storage backends:**
| Backend | File | Use case |
|---------|------|----------|
| `InMemoryCheckpointStorage` | `checkpoint/InMemoryCheckpointStorage.java` | Ephemeral sessions, tests |
| `SqliteCheckpointStorage` | `checkpoint/SqliteCheckpointStorage.java` | Cross-restart durability, WAL mode |

SQLite schema: `checkpoints` (id, session, step, ts, source, channel_values) + `channel_versions` + `versions_seen` + `writes`. Database stored at `~/.jwcode/checkpoints/<sessionId>.db`.

## Key Paths

- **Entry point:** `jwcode-web/.../WebLauncher.java` (starts `WebServer` with `com.sun.net.httpserver`)
- **Agent system:** `jwcode-core/.../agent/` — 17 types: Orchestrator, Coder, Debugger, Architect, Reviewer, Explorer, etc.
- **Agent graph:** `jwcode-core/.../graph/` — AgentGraph builder, CompiledAgentGraph (PregelLoop), OrchestratorGraphFactory
- **Channels:** `jwcode-core/.../graph/channel/` — LastValueChannel, BinaryOpChannel, TopicChannel, EphemeralChannel
- **Checkpoint storage:** `jwcode-core/.../checkpoint/` — CheckpointStorage, InMemoryCheckpointStorage, SqliteCheckpointStorage
- **Tools:** `jwcode-core/.../tool/` — 47 tools: `execution/`, `shell/`, `analysis/`, `permission/`, `io/`
- **WebSocket handler:** `jwcode-web/.../stream/StreamingWebSocketHandler.java` (3062 lines)
- **React frontend:** `jwcode-web/src/` — Vite+React SPA, zustand stores, Tailwind CSS
- **TS CLI:** `ts-cli/src/` — esbuild-bundled Ink/React TUI, `ws` for WebSocket
- **REST API:** `FilesHandler.java` — `GET /api/files?path=` for directory listing (returns `FileNode[]` tree), `GET /api/files/read?path=` for file content (returns plain text), `POST/PUT/DELETE /api/files` for CRUD
- **Config:** `~/.jwcode/config.yaml` (backend_url, ws_url, ws_auth_token)
- **Hooks:** `~/.jwcode/hooks/` — user-defined lifecycle hook scripts
- **Memory:** `~/.claude/projects/.../memory/` — persistent Claude Code memory

## Bash Security (jwcode-core/.../tool/bash/)

Three-stage validation pipeline before shell execution:

| Stage | Class | Checks |
|-------|-------|--------|
| 1. Read-only detection | `CommandReadOnlyValidator` | 150+ read-only prefixes across Unix/Windows, git, npm, docker, kubectl, go, cargo, pip; `riskScore()` 0-10 |
| 2. Injection detection | `CommandInjectionDetector` | Backtick substitution, `$()` / `${}`, path traversal, encoded payloads, reverse shell patterns (`/dev/tcp`, `nc -e`) |
| 3. Sed validation | `SedCommandValidator` | `-i` in-place with backup detection, `s///e` exec flag, `w`/`W` file writes |

All three integrated into `BashTool.validate()` and `isReadOnly()`.

## Permission System

`PermissionManager` → `AutoPermissionClassifier` (7-layer heuristic):
alwaysSafe → injection detection → userApproved patterns → userDenied patterns → riskScore(0-10) → rateLimit(30/min) → default ASK.

Auto mode bypasses permission prompts; session learning accumulates approved/denied patterns.

## Slash Commands

Registered via `CommandRegistry.createDefault()` — 7 user-facing commands:
| Command | Purpose |
|---------|---------|
| `/doctor` | System diagnostic: Java env, OS, session, Docker check |
| `/cost` | Token usage + estimated cost by model (prompt/completion/total) |
| `/compact` | Context compaction: normal(30)/aggressive(10)/summary(20+summary) |
| `/review` | Code review: logic, security, performance, style, completeness |
| `/security-review` | OWASP Top 10 check + injection/path-traversal/privilege/sql risks |
| `/memory` | Persistent memory CRUD: list/add/delete/clear for user/feedback/project/reference |
| `/tasks` | Background task management: list/stop/output |

## Team Collaboration & Config

| Service | Purpose |
|---------|---------|
| `SharedMemoryService` | Team-level shared memory CRUD, type filtering, search, 30-day auto-cleanup, JSON per team |
| `SessionSharingService` | Session sharing with view/fork counting, tag/keyword search, popular ranking, per-team JSON |
| `ConfigMigrationManager` | Schema-versioned config (v0→v1→v2), auto-migration chain, backup/restore on failure |
| `PromptCacheOptimizer` | SHA-256 breakpoint detection, adaptive cache strategy (AGGRESSIVE/CONSERVATIVE/ADAPTIVE) |
| `AutoDreamService` | 30s idle threshold → codebaseInsight + todoDiscovery + cacheWarm on low-cost model |

## WebSocket Protocol

Messages: `{type, sessionId, message?, data?}` — 30+ event types (`start`, `content`, `thinking`, `tool_call`, `tool_result`, `step_*`, `plan_*`, `hook_ask`, `token_update`, `compaction_progress`, `error`, etc.)

Auth flow: connect → `auth_required` → client sends `{type:"auth", token:"default-token"}` → `auth_success` → `POST /api/sessions` → message exchange.

## TS CLI (ts-cli/)

Built with Ink 5 (React for terminal), `ws`, esbuild. Source in `src/`, output is single `dist/cli.js`. Protocol in `src/protocol.ts` must stay in sync with Java backend's `MessageType` enum. WebSocket reconnect logic in `src/client.ts` uses `_reconnecting` flag to prevent timer stacking.

**Key components:**
| File | Purpose |
|------|---------|
| `src/App.tsx` | Root component: WS event wiring, generation elapsed timer, token rate computation, keyboard, command execution |
| `src/components/TextInput.tsx` | Text input with paste support (CJK-safe), ↑↓ input history (30 entries), token/char counter |
| `src/components/ChatArea.tsx` | Virtual message window with scrollOffset, `[X/Y]` indicator; live elapsed timers for running tool calls, duration for completed |
| `src/components/StatusLine.tsx` | Model, prompt+completion token breakdown, token rate (t/s), generation elapsed, Plan/Act/Auto tags, █░ bar |
| `src/components/ApprovalModal.tsx` | CRITICAL/HIGH/MEDIUM/LOW risk classification, 15s countdown auto-approval, tool preview panel, y/n/1/2 shortcuts |
| `src/components/CommandPalette.tsx` | `/` filterable popup with PgUp/PgDn page navigation |
| `src/components/FilePalette.tsx` | `@` file reference popup: fuzzy search, ↑↓/PgUp/PgDn nav, Enter to insert path, Esc close |
| `src/theme.ts` | Color constants, `JWCODE_THEME=dark\|light` env var support |

**Text input features:** ↑↓ browses last 30 inputs (sessionStorage). CJK ≈ 1.5 chars/token, English ≈ 4 chars/token. Warning at >100K tokens.

**@ file reference (TS CLI):** Type `@` in the input → `findAtTrigger()` detects the last `@` not preceded by a word char → debounced file list via `GET /api/files` → `FilePalette` renders matching files (files only, not directories) → select inserts path → on send, `resolveAndSend()` reads each file via `GET /api/files/read`, wraps content in `<context ref="path">\n```ext\n...\n```\n</context>` blocks, removes raw paths from message text, and prepends contexts before the user prompt.

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
| `chatStore` | Per-session messages, generation state, steps + toolCalls CRUD | localStorage (debounced) |
| `sessionStore` | Session tabs, history, per-session tasks | localStorage |
| `planStore` | Plan mode state, structured tasks, mode history | no |
| `terminalStore` | Terminal session lifecycle (idle/running/error) | no |
| `toastStore` | Toast notifications with auto-dismiss | no |
| `tokenStore` | Token usage tracking, prompt/completion breakdown, EMA-smoothed token rate, session delta | no |
| `settingsStore` | Theme, workspace dir, feature toggles, workspaceGuardBypass | localStorage |
| `useHookApprovalStore` | Hook approval queue, auto-mode, per-session allow-list | partial (autoMode only) |

**Key UI components:**
| Component | Purpose |
|-----------|---------|
| `Chat/StatusLine.tsx` | Token breakdown (prompt+completion), split bar, t/s rate, elapsed timer, model, Plan/Act/Auto badges |
| `Chat/StepItem.tsx` | Step card with live elapsed timer (setInterval for running), animated status, collapsible thought/result/toolCalls |
| `Chat/ToolCallItem.tsx` | Tool call card with live elapsed timer, collapsible args/result, DiffPreview integration for file modifications |
| `Chat/HookApprovalCard.tsx` | Risk-level classification (CRITICAL/HIGH/MEDIUM/LOW), 15s countdown auto-approval, command preview, dropdown options |
| `Chat/DiffPreview.tsx` | Unified + side-by-side diff views, stats footer (+N/-M/hunks), language detection for 20+ languages |
| `Chat/ExpandableResult.tsx` | Expand/collapse long result text with truncation |
| `Chat/FileMentionMenu.tsx` | `@` file mention popup: filtered file list, ↑↓ select, Enter/Hover, yellow folder/file icons, path display |

**WebSocket handlers** (`src/hooks/handlers/`): 775-line `useWebSocket.ts` split into category modules:
| Module | Message types handled |
|--------|----------------------|
| `planHandlers.ts` | plan_start, plan_thinking, plan_tasks, plan_task_start/update/result, plan_complete, plan_error, plan_mode_change, step_prompt |
| `streamHandlers.ts` | start, content, thinking, tool_call, tool_result, complete, error, compaction_progress, context_compressed, generation_paused/resumed |
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
- Step cards with live elapsed timers, animated running indicators, collapsible thought/result
- Tool call cards with live elapsed timers, inline result preview, diff detection for file modifications
- Hook approval cards with CRITICAL/HIGH/MEDIUM/LOW risk classification, 15s countdown, command preview
- Token breakdown (prompt + completion) with split progress bar, EMA-smoothed tokens/sec rate
- Diff preview with unified + side-by-side views, stats footer, 20+ language detection
- CLI StatusLine with token breakdown, rate display, generation elapsed counter
- CLI ApprovalModal with risk levels, countdown auto-approval, y/n/1/2 shortcuts
- @ file reference in both web and TS CLI: type `@` to search/filter files, select to insert path, on send file content is read via REST API and attached as `<context>` blocks
