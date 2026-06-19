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

## Model Binding System (v3.2)

**Architecture:** 三级优先级模型选择 + 按 ModelRef 缓存 LLMService：

```
                    ┌─────────────────────────────┐
                    │      ModelResolver          │
                    │  resolveForAgent(agentId,   │
                    │    executionMode)            │
                    └──────────┬──────────────────┘
                               │
          ┌────────────────────┼────────────────────┐
          ▼                    ▼                    ▼
   Agent 指定模型       模式默认模型          全局默认模型
  (mode=specified)    (plan/act default)    (global default)
          │                    │                    │
          └────────────┬───────┘────────────────────┘
                       ▼
              LLMFactory.getLLMService(modelRef)
                       │
              ┌────────┴────────┐
              ▼                 ▼
      LLMService[Map]    LLMService[Map]
       (per ModelRef       (per ModelRef
         cache)              cache)
```

**Config YAML format (新增字段):**
```yaml
providers:
  deepseek:
    # ... existing provider config ...
    models:
      - id: deepseek-chat
        max-tokens: 8192

# 三级默认模型（全局/Plan/Act），格式: "provider:modelId"
default-models:
  global: "deepseek:deepseek-chat"
  plan: "deepseek:deepseek-chat"
  act: "deepseek:deepseek-chat"

# Agent 级模型绑定（可选）
agent-model-bindings:
  orchestrator:
    mode: specified          # "mode-default" | "specified"
    model-ref: "deepseek:deepseek-chat"
  coder:
    mode: mode-default
```

**Selection priority:**
| Priority | Level | Description |
|----------|-------|-------------|
| 1 (highest) | Agent specified | Agent 的 `model-binding.mode=specified` + `model-ref` |
| 2 | Mode default | 当前执行模式（Plan/Act）的默认模型 |
| 3 (fallback) | Global default | 全局默认模型，模式默认或 Agent 指定不可用时的最终回退 |

**Fallback chain:**
1. Agent 指定模型不可用 → 尝试模式默认模型 → 尝试全局默认模型
2. 模式默认不可用 → 尝试全局默认模型
3. 全局默认不可用 → 返回错误，要求用户重新配置
4. 删除/禁用默认模型 → API 拒绝（返回 400）

**Key classes:**
| Class | File | Purpose |
|-------|------|---------|
| `ModelResolver` | `llm/ModelResolver.java` | 按 agentId + executionMode 解析模型；validate model availability (provider/key/enabled) |
| `ResolvedModel` | `llm/ResolvedModel.java` | 解析结果：modelRef, usable, fallback, fallbackReason, error |
| `AgentModelBinding` | `config/JwcodeConfig.java` (inner) | Agent 模型绑定配置：mode, modelRef |
| `LLMFactory` | `llm/LLMFactory.java` | 改爲 `Map<String,LLMService>` 按 ModelRef 缓存；新增 `getLLMService(modelRef)` |

**How the resolution flows at runtime:**
```
StreamingWebSocketHandler (on user message)
  → PlanModeManager.isPlanMode() → executionMode = "plan" | "act"
  → Session.getCurrentAgentId() → agentId
  → LLMFactory.getModelResolver().resolveForAgent(agentId, executionMode)
    → check agent-model-bindings → check default-models.{plan|act} → check default-models.global
    → returns ResolvedModel (with fallback info)
  → LLMFactory.createQueryEngine(session, toolRegistry, toolExecutor, agentRegistry, modelRef)
    → LLMFactory.getLLMService(modelRef) → cached or create new LLMService
    → LLMQueryEngine uses the resolved LLMService
```

**REST API:**
| Endpoint | Method | Purpose |
|----------|--------|---------|
| `GET /api/models` | GET | 返回 `isGlobalDefault`, `isPlanDefault`, `isActDefault` 标记 |
| `POST /api/models/defaults` | POST | 设置默认模型 `{ "global": "p:m", "plan": "p:m", "act": "p:m" }` |
| `POST /api/models/delete` | POST | 拒绝删除默认模型（返回 400） |
| `POST /api/models/toggle` | POST | 拒绝禁用默认模型（返回 400） |
| `GET /api/agents` | GET | 返回 `modelBinding` + `effectivePlanModel`/`effectiveActModel`（含 fallback 状态） |
| `POST /api/agents/{id}/model` | POST | 设置 Agent 模型绑定 `{ "mode": "specified", "modelRef": "p:m" }` |

**Frontend components:**
| Component | File | Purpose |
|-----------|------|---------|
| `ModelsView` | `components/Models/ModelsView.tsx` | 模型行显示三色默认徽章（全局绿/Plan蓝/Act黄）及 G/P/A 快速设置按钮 |
| `AgentsView` | `components/Agents/AgentsView.tsx` | Agent 模型选择器：跟随默认/指定模型下拉、Plan/Act 生效模型及降级警示 |

**Backward compatibility:** 旧 config.yaml 不含 `default-models` 或 `agent-model-bindings` 时，`ensureDefaultsInitialized()` 自动从 `default-provider` 第一个启用模型初始化三个默认值。配置保存时自动补齐。


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
- **Graph channels:** `jwcode-core/.../graph/channel/` — LastValueChannel, BinaryOpChannel, TopicChannel, EphemeralChannel (typed state slots for the agent graph)
- **Messaging channels:** `jwcode-core/.../channel/` — ChannelAdapter, ChannelRegistry, ChannelMessageDispatcher, `wechat/` (external messaging integrations: WeChat now, Feishu/DingTalk pluggable)
- **Checkpoint storage:** `jwcode-core/.../checkpoint/` — CheckpointStorage, InMemoryCheckpointStorage, SqliteCheckpointStorage
- **Tools:** `jwcode-core/.../tool/` — 47 tools: `execution/`, `shell/`, `analysis/`, `permission/`, `io/`
- **WebSocket handler:** `jwcode-web/.../stream/StreamingWebSocketHandler.java` (3062 lines)
- **React frontend:** `jwcode-web/src/` — Vite+React SPA, zustand stores, Tailwind CSS
- **TS CLI:** `ts-cli/src/` — esbuild-bundled Ink/React TUI, `ws` for WebSocket
- **REST API:** `FilesHandler.java` — `GET /api/files?path=` for directory listing (returns `FileNode[]` tree), `GET /api/files/read?path=` for file content (returns plain text), `POST/PUT/DELETE /api/files` for CRUD
- **Config:** `~/.jwcode/config.yaml` (backend_url, ws_url, ws_auth_token); `~/.jwcode/channels.json` (messaging channel configs)
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

Registered via `CommandRegistry.createDefault()` — user-facing commands:
| Command | Purpose |
|---------|---------|
| `/doctor` | System diagnostic: Java env, OS, session, Docker check |
| `/cost` | Token usage + estimated cost by model (prompt/completion/total) |

In the web frontend these are surfaced both inline (type `/` in chat → `SlashCommandMenu`) and app-wide via the `Ctrl/Cmd+K` CommandPalette (`jwcode-web/src/components/CommandPalette.tsx`), which reuses the same `useSlashCommands` command set (navigation + local + backend commands).

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
| `src/App.tsx` | Root component: WS event wiring, generation elapsed timer, token rate computation, keyboard, command execution. `useEffect` installs BSU/ESU wrapper from `terminalSync.ts` |
| `src/components/TextInput.tsx` | Text input with paste support (CJK-safe), ↑↓ input history (30 entries), token/char counter |
| `src/components/ChatArea.tsx` | Virtual message window with scrollOffset, `[X/Y]` indicator; live elapsed timers for running tool calls, duration for completed. `StreamingMessage` has custom `memo` comparator + `useCallback` toggle functions to avoid wasted re-renders during step/tool_call events |
| `src/components/StatusLine.tsx` | Model, prompt+completion token breakdown, token rate (t/s), generation elapsed, Plan/Act/Auto tags, █░ bar |
| `src/components/ApprovalModal.tsx` | CRITICAL/HIGH/MEDIUM/LOW risk classification, 15s countdown auto-approval, tool preview panel, y/n/1/2 shortcuts |
| `src/components/CommandPalette.tsx` | `/` filterable popup with PgUp/PgDn page navigation |
| `src/components/FilePalette.tsx` | `@` file reference popup: fuzzy search, ↑↓/PgUp/PgDn nav, Enter to insert path, Esc close |
| `src/store.ts` | `createStore` defers listener notification via `queueMicrotask`; multiple setState calls in the same task coalesce into one notification (handles the 4 boundary events that previously caused double-renders) |
| `src/terminalSync.ts` | DEC 2026 BSU/ESU atomic output wrapper. Wraps `process.stdout.write` so each Ink 5 frame is committed atomically — eliminates intra-frame tearing / flicker on Windows Terminal, iTerm2, WezTerm, ghostty, VS Code, kitty, alacritty, foot. Bypassed on tmux and unsupported terminals |
| `src/theme.ts` | Color constants, `JWCODE_THEME=dark\|light` env var support |

**Text input features:** ↑↓ browses last 30 inputs (sessionStorage). CJK ≈ 1.5 chars/token, English ≈ 4 chars/token. Warning at >100K tokens.

**Streaming flicker mitigation (Ink 5 has no cell diff / damage tracking / BSU/ESU):**
- `src/terminalSync.ts` wraps `process.stdout.write` with BSU/ESU (`\x1b[?2026h` ... `\x1b[?2026l`) so the terminal commits each frame atomically. Detected via `WT_SESSION` / `TERM_PROGRAM` / kitty / foot; bypassed on tmux.
- `src/store.ts` batches listener notifications on `queueMicrotask` so the 4 double-`updateAppState` boundary events (`start` / `complete` / `plan_start` / `plan_complete`) collapse to one render.
- `useStreamHandlers.ts` `scheduleStreamFlush` is adaptive: first flush immediate, content > 100 chars immediate, else 200ms.
- `ChatArea.tsx` `StreamingMessage` has explicit `memo` comparator + `useCallback` toggles.

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

## Messaging Channels (jwcode-core/.../channel/)

External messaging channels (WeChat now; Feishu/DingTalk pluggable) let users submit task commands and receive status, progress, and final reports back. Channels are configured/managed from the web "渠道" tab.

**Design principle:** reuses `LLMQueryEngine.StepCallback` — a transport-agnostic interface. `StreamingWebSocketHandler` uses it to push events to the WebSocket; the channel subsystem implements the same interface to push events to WeChat/Feishu. Core agent execution is untouched.

**Flow:**
```
External channel (WeChat iLink long-poll / Feishu WS / DingTalk webhook)
  → ChannelAdapter (unified interface) → ChannelRegistry
  → ChannelMessageDispatcher (polls adapters every 100ms)
      → per (channelId:senderId) Session → LLMQueryEngine.queryStream(...)
      → StepCallback: onStepStart→"📍", onContentChunk→buffer, complete→final report
      → ChannelRegistry.send() → adapter.send() (auto-segments >2000 chars)
```

**Core files (`jwcode-core/.../channel/`):**
| File | Purpose |
|------|---------|
| `ChannelAdapter` | Interface: `getChannelType` / `initialize` / `shutdown` / `isConnected` / `send` / `poll` / `getConfig` |
| `ChannelConfig` | POJO: id, name, type, appId, appSecret, token, encodingAESKey, enabled, extra map |
| `InboundChannelMessage` | Inbound model: channelId, channelType, senderId, text, receivedAt |
| `ChannelRegistry` | Runtime registry + adapter lifecycle; persists configs to `~/.jwcode/channels.json`; type→factory map |
| `ChannelMessageDispatcher` | Poll loop + per-user Session map + task thread pool (4-16); bridges inbound → `LLMQueryEngine` + `StepCallback` |
| `wechat/WechatApiClient` | iLink Bot API: `getUpdates` (35s long-poll), `sendText`, `getQrCode`, `getQrCodeStatus`; Bearer token + random `X-WECHAT-UIN` |
| `wechat/WechatChannelAdapter` | WeChat adapter: polling thread → `BlockingQueue`, syncBuf persisted for reconnect, per-user context_token cache |

**REST API (`ChannelsHandler.java`, registered at `/api/channels` in `WebServer.java`):**
| Endpoint | Purpose |
|----------|---------|
| `GET /api/channels` | List all (appSecret/token/encodingAESKey masked as `***`) |
| `POST /api/channels` | Create channel (starts adapter if enabled) |
| `PUT /api/channels/{id}` | Update (restarts adapter) |
| `DELETE /api/channels/{id}` | Delete (shuts down adapter) |
| `PATCH /api/channels/{id}/toggle` | Enable/disable |
| `POST /api/channels/{id}/test` | Connectivity check |
| `GET /api/channels/{id}/wechat/qrcode` | Get iLink login QR code |
| `GET /api/channels/{id}/wechat/qrcode/status` | Poll QR scan status (confirmed → saves bot_token, starts polling) |

**Frontend files (`jwcode-web/src/`):**
| File | Purpose |
|------|---------|
| `stores/channelsStore.ts` | zustand: load/create/update/remove/toggle + form state |
| `components/Channels/ChannelConfigView.tsx` | Container: toolbar (新建渠道), error banner, table + drawer |
| `components/Channels/ChannelTable.tsx` | List with type badge, enable toggle, edit/delete actions |
| `components/Channels/ChannelDrawer.tsx` | Right-side edit drawer; dynamic fields per type; WeChat QR scan panel with 2s status polling |
| `services/api/index.ts` | `api.channels.*` methods incl. `wechat.qrcode` / `wechat.qrcodeStatus` |

**Config storage:** `~/.jwcode/channels.json` — array of `ChannelConfig`. WeChat runtime state (bot_token, sync_buf, per-user context tokens) stored in `config.extra` map.

**Adding a new channel (Feishu/DingTalk):** (1) implement `ChannelAdapter`; (2) register factory in `WebServer.java`: `channelRegistry.registerFactory("feishu", cfg -> new FeishuChannelAdapter())`. `ChannelMessageDispatcher` and the frontend need no changes — the drawer's field set is driven by the channel-type enum.

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
| `CommandPalette.tsx` | App-level `Ctrl/Cmd+K` palette: reuses `useSlashCommands` (navigation + local/backend commands), centered modal, ↑↓ nav, Enter run, Esc close, toast feedback for backend commands |
| `ShortcutsHelp.tsx` | `Ctrl/Cmd+/` keyboard-shortcuts reference modal (built on `common/Modal`); ⌘ vs Ctrl label per platform |
| `common/Skeleton.tsx` | Skeleton/SkeletonCard/SkeletonList + LoadingSpinner/PageLoading; `shimmer` prop (sweeping highlight) and `role="status"` on spinners |

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

**Branding, shortcuts & accessibility (UI polish):**
- **Brand assets:** `jwcode-web/public/favicon.svg` + `public/logo.svg` (blue→purple gradient `</>` mark). Vite serves `public/` at root, so `index.html` references `/favicon.svg`; the header (`App.tsx`) and chat welcome empty-state (`ChatPanel.tsx`) render `/logo.svg`. App/brand name is **JWCode** (title, header, i18n `chat.welcome`).
- **Global keyboard shortcuts** (wired in `App.tsx` global `keydown` effect; text-edit-safe):
  | Shortcut | Action |
  |----------|--------|
  | `Ctrl/Cmd + K` | Toggle CommandPalette |
  | `Ctrl/Cmd + L` | Clear logs (toast feedback); skipped while editing text |
  | `Ctrl/Cmd + /` | Toggle ShortcutsHelp |
  | `Ctrl/Cmd + Enter` | Submit chat input (explicit, IME-safe; handled in `ChatPanel` textarea) |
  | `Esc` | Closes palette → help → mobile menu, else falls through to existing pause/stop (double-Esc terminates) |
- **Interaction states** (`styles/globals.css`): `.btn` unified `disabled:` state; chat textarea `focus:ring`; `shimmer` keyframe + `.skeleton-shimmer` utility (opt-in via Skeleton `shimmer` prop).
- **Accessibility:** icon-only buttons get `aria-label` (i18n `a11y.*`) with decorative icons `aria-hidden`; spinners use `role="status"`; connection indicator is a labeled `role="status"`. Existing dark/light palette already meets WCAG AA contrast (body ≈12:1, muted ≈5.8:1) — no color changes needed.
- **i18n keys:** `shortcuts.*`, `palette.*`, `a11y.*`, `chat.kbdHint` added to both `zh-CN.json` and `en.json`.
