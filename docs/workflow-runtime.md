# Durable Workflow Runtime

JWCode's durable workflow path is `Workflow IR -> EffectVM -> WorkflowLedger`.
It is the default production path for complex workflow requests that need replay,
status, cancellation, pause, and checkpoint visibility.

## Runtime Boundaries

- Normal chat streaming stays on the existing chat WebSocket path.
- Workflow execution uses the dedicated HTTP and WebSocket workflow paths.
- Workflow IR v1 accepts JSON IR only. JS-like workflow text is not parsed.
- Writer/checkpoint behavior remains local through `MemoryLayer.writeCheckpoint`.
- `EnhancedOrchestratorAgent`, A2A local dispatch, `SharedContextBus`, and
  `ParallelAgentExecutor` remain legacy/compatibility paths. They are not
  removed, but new durable execution behavior should target `Workflow IR + EffectVM`.

## Compatibility Paths

The workflow runtime is additive. The following paths stay available for
existing callers and should not be deleted as part of durable workflow work:

- `EnhancedOrchestratorAgent` for confirmed-plan orchestration.
- A2A local dispatch for legacy sub-agent task routing.
- `SharedContextBus` for older multi-agent coordination state.
- `ParallelAgentExecutor` for legacy AgentTool execution and fallback.

New durable behavior should not be implemented by extending those paths first.
The durable contract is ledger-backed execution through `Workflow IR`,
`EffectVM`, `WorkflowLedger`, and persisted run artifacts.

## HTTP API

All HTTP workflow status is ledger-backed. A fresh `WorkflowHandler` instance
can answer `status` and `events` from the run directory alone.

- `POST /api/workflows/start`
  - Body may include `runId`, `sessionId`, `workflow`, `input`,
    `memoryEnabled`, `checkpointPolicy`.
  - Returns immediately with `status=RUNNING`, `runId`, and `workflowRoot`.
  - Execution continues in the background and writes results to the ledger.
  - Background failures are not returned on the original request. Query
    `status` or `events` to observe the final `FAILED` state.
- `POST /api/workflows/{runId}/resume`
  - Reuses saved `ir.json` when `workflow` is omitted.
  - Cancelled runs reject resume unless `forceResume=true`.
  - Paused runs can resume normally.
- `GET /api/workflows/{runId}/status`
  - Replays `events.jsonl` and returns status plus progress counters.
- `GET /api/workflows/{runId}/events`
  - Returns the persisted ledger event stream.
- `POST /api/workflows/{runId}/pause`
  - Appends `run.paused`.
- `POST /api/workflows/{runId}/cancel`
  - Appends `run.cancelled`.

## WebSocket Messages

Client to server:

- `workflow_start`
- `workflow_resume`
- `workflow_status`
- `workflow_pause`
- `workflow_cancel`

Server to client:

- `workflow_started`
- `workflow_event`
- `workflow_progress`
- `workflow_finished`
- `workflow_error`

Workflow messages are handled by the frontend workflow store and must not be
converted into normal assistant content.

Queued or replayed workflow messages must include `sessionId`; the frontend
uses that id instead of the currently active chat session when updating
`workflowStore`.

## AgentTool Contract

`AgentTool` uses the durable workflow runtime by default for `execute` actions.

- `agent_tool.workflow.enabled=false` keeps the legacy executor path active.
- Workflow mode must preserve the existing result surface:
  `success`, `content`, and `metadata` remain populated.
- Workflow metadata includes:
  - `workflow_runtime=true`
  - `workflow_run_id`
  - `workflow_status`
- Parallel best-effort AgentTool execution compiles to `ErrorMode.NULL`.
- Sequential fail-fast AgentTool execution compiles to `ErrorMode.FAIL_FAST`.

If the workflow runtime cannot build or execute the IR, AgentTool may fall back
to the legacy executor. That fallback must not add workflow metadata.

## Permission And Session Isolation

Workflow agent/tool effects must preserve existing safety boundaries.

- Explorer role defaults to read-only/search tools and must not get write tool
  access through the whitelist.
- Coder role may use write/execute tools, but only through `ToolExecutor` and
  the hook chain.
- Verifier role may use diagnostic and test-related tools.
- Tool whitelists are thread-local and must not leak across concurrent agents.
- Child sessions are scoped as `parentSessionId:effectId`.
- Child sessions are not written back to the parent session.

## Pause And Cancel Semantics

Pause and cancel are scheduling controls.

- `run.paused` stops scheduling before the next node/effect.
- An already-running LLM or tool effect is not forcibly killed by pause.
- After the current effect finishes, `EffectVM` returns `PAUSED` and does not
  schedule more effects.
- `run.cancelled` stops scheduling and returns `CANCELLED`.
- Cancelled runs reject resume by default; `forceResume=true` opts into resume.

## Storage Configuration

- `jwcode.workflow.root`
  - Root directory for workflow run folders.
  - Each run stores `ir.json`, `events.jsonl`, artifacts, and derived state.
- `jwcode.memory.root`
  - Root directory for local workflow checkpoints.

Tests should set both properties to temporary directories. Production defaults
resolve under the user's `.jwcode` directory.

## Test Coverage

The workflow runtime contract is covered by:

- Core workflow/runtime tests for pause, cancel, resume, replay, budgets,
  progress events, artifact storage, memory checkpoints, and AgentTool IR shape.
- HTTP API tests for start/status/events/pause/cancel/resume, handler-instance
  replay, saved `ir.json`, cancelled resume rejection, forced resume, and
  async failure visibility through ledger queries.
- WebSocket workflow tests for start/status/pause/cancel/resume, emitted
  `workflow_event`, `workflow_progress`, `workflow_finished`, and resume
  behavior after cancellation.
- Frontend workflow tests for `workflow_started`, `workflow_event`,
  `workflow_progress`, `workflow_finished`, `workflow_error`, replayed
  messages, and isolation from normal assistant chat content.

Recommended focused commands:

```bash
mvn -pl jwcode-core -Dtest="com.jwcode.core.memory.*Test,com.jwcode.core.workflow.*Test,com.jwcode.core.runtime.*Test,com.jwcode.core.tool.*Workflow*Test,com.jwcode.core.hands.*Test" test
mvn -pl jwcode-web -am -Dtest="com.jwcode.web.WorkflowHandlerTest,com.jwcode.web.stream.StreamingWebSocketWorkflowTest" "-Dsurefire.failIfNoSpecifiedTests=false" "-Dfrontend.skip=true" test
cd jwcode-web && npm run test -- src/stores/__tests__/workflowStore.test.ts src/hooks/__tests__/useWebSocket.workflow.test.tsx
cd jwcode-web && npm run build
```
