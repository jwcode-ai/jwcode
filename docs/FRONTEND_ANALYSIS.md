# JWCode 前端架构分析报告：Web UI & TS-CLI

> 基于五篇"智能体上下文工程"深度技术文章 + JWCode 源码静态审查
> 2026-06-01 | 版本 1.0

---

## 一、总体评价

### 当前状态分级

| 维度 | Web UI | TS-CLI | 综合评价 |
|------|--------|--------|----------|
| 功能完整度 | 7/10 | 6/10 | 两者的核心对话流都已接通 |
| 架构健康度 | 6/10 | 5/10 | 双代码库零共享，维护成本高 |
| 上下文工程对应 | 5/10 | 5/10 | 核心机制大部分未可视化 |
| 代码质量 | 7/10 | 6/10 | Web UI 的 Zustand 使用规范，ts-cli 较简陋 |
| 错误处理 | 5/10 | 5/10 | 缺乏 API 层与工具层错误的可视化区分 |

---

## 二、Web UI 详细审查

### 2.1 架构总览

```
jwcode-web/src/
├── App.tsx                    ← 单文件 ~950 行（过大）
├── main.tsx                   ← Vite 入口
├── types/index.ts             ← ~320 行类型定义
├── services/
│   ├── websocket.ts           ← WebSocket 单例（心跳/重连/会话恢复）
│   └── api/client.ts          ← REST API 封装（fetch 包装）
├── stores/                    ← Zustand stores（聊条/会话/终端/Token/计划等）
│   ├── chatStore.ts           ← 多会话消息管理，流式追加，防抖写入
│   ├── sessionStore.ts        ← SessionTab + 会话历史 + 任务黑板
│   ├── planStore.ts           ← Plan/Act/Normal 三模式状态机
│   ├── terminalStore.ts       ← 终端视图状态
│   ├── settingsStore.ts       ← 主题/字体/语言/自定义颜色
│   ├── tokenStore.ts          ← Token 用量 + 压缩状态
│   ├── toastStore.ts          ← Toast 通知队列
│   ├── useHookApprovalStore.ts ← Hook 审批状态
│   ├── contractStore.ts       ← （未使用）
│   └── commandStore.ts        ← 斜杠命令注册
├── hooks/
│   ├── useWebSocket.ts        ← WS 消息路由（核心分发器）
│   ├── handlers/
│   │   ├── streamHandlers.ts  ← 流式消息：content/thinking/tool_call/…
│   │   ├── planHandlers.ts    ← Plan 消息：plan_start/plan_tasks/step_prompt…
│   │   ├── systemHandlers.ts  ← 系统消息：token_update/log/auth/workspace_changed
│   │   └── interactionHandlers.ts ← hook_ask/task_update/step_*/todo_*
│   ├── useAutoScroll.ts
│   ├── useInputHistory.ts
│   └── useSlashCommands.ts
└── components/
    ├── Chat/                  ← 核心聊天组件群（~15 个）
    ├── Plan/PlanPanel.tsx
    ├── Models/ModelsView.tsx
    ├── Tools/ToolsView.tsx
    ├── Skills/SkillsView.tsx
    ├── Agents/AgentsView.tsx
    ├── Terminal/TerminalView.tsx
    ├── FileTree/FileTreeView.tsx
    ├── Settings/SettingsPanel.tsx
    ├── Logs/LogsPanel.tsx
    ├── Toast/ToastContainer.tsx
    ├── Hook/HookApprovalModal.tsx
    └── common/               ← Avatar/Badge/MarkdownRenderer/Modal/Progress/Skeleton/Table
```

### 2.2 做得好的地方

1. **状态管理规范** — Zustand + persist 中间件，session 级隔离清晰；chatStore 的 debouncedStorage 防止流式高频写入堵塞主线程，是经过思考的设计
2. **WebSocket 健壮性** — wsService 有心跳检测（20s ping / 100s 超时）、指数退避重连、重连时携带 sessionId 恢复映射，还有 `isManualClose` 防止误重连
3. **消息分发架构清晰** — `useWebSocket.ts` 按类别路由到四个 handler 文件，职责分明
4. **Hook 审批流程完整** — 支持弹窗模态 + 内联对话卡片两种审批方式，有 `isSessionAllowed` 快捷放行
5. **会话管理丰富** — 多 Tab 会话、会话历史、自动命名、工作目录绑定，Max 6 个活动 Tab + 50 条历史，边际控制到位
6. **任务黑板 (SessionTaskBoard)** — 按状态分组（running/pending/completed/failed），有进度条和展开详情，对 AI 任务执行的可观测性较好
7. **自定义主题** — 支持完整 CSS 变量覆盖的暗/亮/自定义三套主题，持久化到 localStorage
8. **ESC 双段控制** — 单次暂停、双击终止生成，500ms 时间窗判断

### 2.3 问题清单

#### 严重（影响核心功能）

**S1 — ChatHandler.java 是死桩**
`jwcode-web/src/main/java/com/jwcode/web/ChatHandler.java:44`

REST `/api/chat` POST 只返回 `"收到: {message}"`，没有连接 LLM 管道。真实聊天完全走 WebSocket。这意味着没有 REST fallback——如果 WS 不可用，用户没有任何替代路径。而且 `GET /api/chat` 返回硬编码的 "你好"/"你好!我是 JwCode Web"，是开发阶段遗留的 mock。

**建议**: 至少让 ChatHandler POST 转发到 `StreamingWebSocketHandler` 或内部 LLM 管道，或明确标注 chat 通过 WS 通信，REST 返回 426 Upgrade Required。

**S2 — Plan Panel 未集成到 Tab 系统**
`jwcode-web/src/components/Plan/PlanPanel.tsx:1`

PlanPanel.tsx 是完整组件但 TABS 配置中没有 `plan` 这个 tabId（虽然 types 里有 `'plan'`）。Plan 模式切换逻辑散布在 App.tsx 内联代码中，`planModeChange` 只影响消息发送的 type 字段（`chat` vs `plan`），UI 上没有任何 Plan Mode 锁定反馈。而且 PlanPanel 仅在 `enterPlanMode` 被调用时通过 `switch-tab` 自定义事件显示——但这个事件只在 `planHandlers.ts` 中发出，链路过长。

**建议**: 将 PlanPanel 纳入主 Tab 系统，与文章 04 中 Plan Mode 的"物理隔离"理念对齐：Plan 模式下禁用 Write/Edit/Bash 工具的用户输入，UI 要有明显视觉提示（如顶部 banner、输入框灰化）。

#### 中等（影响用户体验）

**M1 — 两个前端的协议类型独立维护**
- Web UI: `jwcode-web/src/types/index.ts` 定义了 `WSMessageType`（~60 种）
- TS-CLI: `ts-cli/src/protocol.ts` 单独定义了 `EVENT_TYPES`（~40 种）

两者有大量重复（如 `plan_start`, `tool_call`, `token_update`），但 TS-CLI 多了 `degradation_update`、`step_prompt`、`plan_task_*` 系列而 Web UI 多了 `subscribe_logs`、`toggle_workspace_guard`。两边不一致意味着：后端可能发出某种消息而某一端根本不会处理。

**建议**: 抽取为共享包 `@jwcode/protocol-types`，或至少从 Java `MessageType` 枚举自动生成 TypeScript 类型。

**M2 — App.tsx 过大（~950 行）**
`jwcode-web/src/App.tsx:1`

单文件包含：Tab 配置、全局错误捕获、主题注入、WS 连接状态、ESC 键盘处理、会话操作回调、发送消息逻辑（含 Plan/Act 分支）、工作目录切换（含 store 重置）、TaskListPanel 定义（内联组件 ~120 行）、renderTabContent 路由、整个 JSX 布局。这个文件是典型的 "God Component"。

**建议**: 拆分为 `<AppShell>`, `<TopNav>`, `<RightSidebar>`, `<MainContent>`, `<TaskListPanel>` 等独立组件。

**M3 — 缺少 `degradation_update` 处理**
`jwcode-web/src/hooks/useWebSocket.ts:52`

在 useWebSocket 的消息路由中，`degradation_update` 不在任何路由分支中，意味着即使后端发出了降级通知，Web UI 完全不感知。TS-CLI 是有处理的（`useStreamHandlers.ts` 中），这是一个明显的不一致。

**建议**: 在 `systemHandlers.ts` 中增加 `degradation_update` 处理，至少通过 toast 通知用户当前处于降级模式。

**M4 — Token 用量只显示数字，缺乏水位线可视化**
`jwcode-web/src/components/Chat/StatusLine.tsx`

StatusLine 显示模型名称和 Token 数，但没有文章 07 中描述的软阈值（70%）/硬阈值（90%）可视化。用户不知道什么时候会触发压缩。压缩发生时只有一个 toast 弹窗（3 秒消失），没有持久化的压缩历史记录。

**建议**: 在 StatusLine 增加 Token 百分比进度条，颜色从绿→黄→红渐变。压缩后显示压缩比例和节省的 Token 数（类似 TS-CLI 的做法）。

**M5 — 右侧边栏的"后台日志"和"日志 Tab"重复**
App.tsx 右边栏有折叠的后台日志区域，同时又有一个独立的"日志"Tab (`LogsPanel`)。两者展示完全相同的 `logs` 状态，数据源重复，UI 重复。

**建议**: 右侧边栏保留实时日志流（紧凑模式），独立的日志 Tab 改为"系统监控"视图，展示健康度/模型状态/压缩历史/重试次数等聚合信息。

**M6 — ToolCall 配对断裂恢复不完善**
`jwcode-web/src/hooks/handlers/streamHandlers.ts`

当 `complete` 消息到达时，`chatStore.endGeneration` 会将所有仍 `running` 的工具标记为 error。但如果 WebSocket 断连后重连，已发出但未收到 `tool_result` 的 `tool_call` 会永远处于 "running" 状态。没有主动的超时检测或心跳对齐机制。

**建议**: 为每个 tool_call 设置前端超时计时器（如 120s），超时自动标记为 error + "工具执行超时"。

#### 轻微（技术债务）

**T1 — 消息类型定义中有未使用的类型**
`jwcode-web/src/types/index.ts`

`planTask_start`, `planTask_update`, `planTask_result` 在 `WSMessageType` 中未定义（ts-cli 的 protocol 里是 `plan_task_start` 等驼峰命名），且 `plan_mode_enter` / `plan_mode_exit` 定义了但 `useWebSocket.ts` 没有对应的 handler 分支。

**T2 — contractStore 是僵尸代码**
`jwcode-web/src/stores/contractStore.ts`

定义了 `ContractState` 接口、`contractDefinitions`、`validateUsage`、`checkContract` 等方法，但在整个代码库中没有被任何组件 import 或使用。

**T3 — chatStore.appendToLastToolCallArgs 函数未使用**
`jwcode-web/src/stores/chatStore.ts:200`

这个方法约 50 行代码，功能是流式追加 tool_call 的参数（应对分块传输的 args JSON），但 `streamHandlers.ts` 中处理 `tool_call` 时用的是 `addToolCall` + `updateToolCall`，从未调用 `appendToLastToolCallArgs`。

**T4 — 重复的 getLatestStep 逻辑**
在 `useWebSocket.ts:ensureStep` 和 `chatStore.addStep` 中都有"获取最后一步"的查找逻辑，但实现略有不同（一个用 `getLatestStep` 函数，一个直接索引 messages）。

---

## 三、TS-CLI 详细审查

### 3.1 架构总览

```
ts-cli/src/
├── App.tsx                   ← Ink 根组件 ~160 行
├── client.ts                 ← WebSocket 客户端（ws 库）
├── config.ts                 ← 配置加载
├── launcher.ts               ← 启动器
├── main.ts                   ← 入口
├── protocol.ts               ← 类型定义 + createMessage
├── store.ts                  ← 手写简易响应式 store（~30 行）
├── theme.ts                  ← 主题配置
├── commands/index.ts         ← 斜杠命令注册
├── hooks/
│   ├── useAppState.ts        ← 全局状态 + selector hooks
│   ├── useCommandHandler.ts  ← 命令分发
│   ├── useKeyboardInput.ts   ← 键盘处理
│   ├── useStreamHandlers.ts  ← WS 事件处理（~250 行）
│   └── useWebSocket.ts       ← client 单例注入
├── components/
│   ├── ChatArea.tsx          ← 消息渲染
│   ├── TextInput.tsx         ← 输入框
│   ├── StatusLine.tsx        ← 状态栏
│   ├── CommandPalette.tsx    ← 斜杠命令菜单
│   ├── PlanTaskBoard.tsx     ← 任务看板
│   └── ApprovalModal.tsx     ← Hook 审批弹窗
└── __tests__/
    ├── store.test.ts
    ├── theme.test.ts
    └── tokenEstimate.test.ts
```

### 3.2 做得好的地方

1. **流式批处理聪明** — `useStreamHandlers.ts` 使用 `queueMicrotask` + 16ms fallback `setTimeout` 实现微任务级批处理，content/thinking/tool_call 合并到同一帧更新，减少 Ink 渲染次数
2. **Token 速率计算** — `tokenRate` 使用 EMA（指数移动平均）`prevRate * 0.6 + instantRate * 0.4`，比 Web UI 的纯累计更有工程感
3. **`cleanArgs` 函数处理嵌套 JSON** — tool_call 参数有时是 `{"command": {"command": "..."}}` 的嵌套结构，这个递归解包逻辑是实战中踩坑后加的
4. **Plan 任务流完整** — 完整处理 `plan_start/thinking/tasks/task_start/task_update/task_result/complete` 全系列事件，比 Web UI 更全
5. **AutoMode 时自动放行 Hook** — `autoMode` 为 true 时 `approveHook` 自动调用，不需要弹窗
6. **键盘输入处理精细** — PgUp/PgDn 翻页、方向键滚动、Tab 补全、Ctrl+C 中断、Esc 关闭弹窗
7. **client 类方法完整** — 提供了 20+ 语义化快捷方法（`planConfirm()`, `doctor()`, `compact()`, `switchModel()`, `approveHook()` 等），比 Web UI 直接 `wsService.send({type: ...})` 更规范

### 3.3 问题清单

#### 严重

**S3 — 手写 store 无类型推断优化的 selector**
`ts-cli/src/store.ts`

用的是最简单的 `Set<Listener>` + `subscribe` 模式，没有 selector 级别的订阅优化。`useAppState.ts` 中的 selector hooks（如 `useAppConnected`）通过 `subscribe` + `useState` + `useEffect` 模拟，每次任意状态变更都会触发所有 selector 的 getState 调用。没有 bailout 机制，在 Ink 每个 32ms 帧渲染的压力下可能造成多余的 re-render。

**建议**: 考虑引入轻量的 Zustand 或为 Ink 适配的 `useSyncExternalStore` 的 selector 版本。

**S4 — TS-CLI 和 Web UI 是两个平行宇宙**
两个前端：
- 有不同的 WebSocket 消息类型定义
- 有不同的状态管理方案（Zustand vs 手写 store）
- 有不同的组件实现
- 甚至处理的 WS 事件集合都不一致

这意味着每个新功能需要在两个代码库中分别实现。当前仅 13 个组件/6 个组件的规模还能承受，但增长 2-3 倍后维护成本就会失控。

**建议**: 抽取共享包至少包含：`protocol.ts` 类型定义、WS 消息解析/序列化、公用的工具函数（如 `cleanArgs`）。长期考虑 monorepo 共享 React hooks。

#### 中等

**M7 — `useStreamHandlers` 中的闭包陷阱**
`ts-cli/src/hooks/useStreamHandlers.ts`

`wireHandlers` 是一个 `useCallback` 返回的函数，内部通过闭包持有 `setShowApproval`。但这个函数在 `useEffect` 中被调用后创建了大量 `client.on(...)` 闭包，这些闭包持有 `flushNow`、`_pendingContent` 等变量。如果组件因其他原因重渲染导致 `wireHandlers` 重新创建，新的 `client.on` 会叠加在旧的上面，造成事件重复处理。

当前通过 `useCallback` 的依赖 `[setShowApproval]`（React setState 稳定引用）避免了这个风险，但这是一个脆弱的假设——如果将来增加任何非稳定的依赖，就会出现重复订阅 bug。

**建议**: 在 `wireHandlers` 开头增加 `client.offAll()` 或在 useEffect 的 cleanup 中移除所有事件监听。

**M8 — 错误处理后端区分不足**
TS-CLI 的 `error` 事件只是简单显示 `statusText`。从文章 10 的角度看，应该区分：
- 工具层错误（`tool_result` 中 `result.startsWith('Error:')`）→ 交给 LLM 决策，只显示摘要
- API 层错误（`error` 事件）→ 是基础设施异常，应该显示重试状态和降级信息
- 压缩失败 → 应显示 `fallbackTruncate` 等降级策略的启用

当前这三种错误在 TUI 中都只显示同样的红色错误文本。

**M9 — Plan 任务与 Todo 任务混淆**
TS-CLI 的 `PlanTaskBoard` 和 Web UI 的 `TaskListPanel` / `SessionTaskBoard` 使用的是不同的数据模型：
- TS-CLI: `PlanTask`（有 phase/agentType/dependencies/status）
- Web UI: `SessionTask`（有 completed/backendId/planStatus，更细粒度）

但两者都缺少文章 04 中 TodoWrite 的核心约束——"任意时刻恰好一个 in_progress"。当前的 Todo 只是普通的 checkbox 列表，没有强制执行这个状态机。

#### 轻微

**T5 — TS-CLI 没有持久化**
`ts-cli/src/store.ts` 的 store 是纯内存的，没有 localStorage 或文件系统持久化。关闭终端后所有状态（会话历史、消息、设置）全部丢失。Web UI 在这方面做得好很多（Zustand persist）。

**T6 — 命令注册只有 7 个粗粒度命令**
`ts-cli/src/commands/index.ts`

`/chat`, `/plan`, `/stop`, `/pause`, `/resume`, `/compact`, `/doctor`, `/rewind` 等基础命令。缺少：
- `/model` 查看/切换模型
- `/agents` 列出可用的 agent
- `/hooks` 查看 hook 配置和状态
- `/tokens` 查看 token 使用详情
- `/config` 查看/修改配置
- `/mcp` MCP 服务器管理

---

## 四、跨前端架构问题

### 4.1 数据流不统一

```
用户输入 → WebSocket → Java 后端 → LLM
                ↑
        两个前端独立连接
        各自维护独立的会话状态
```

两个前端之间没有任何共享状态。用户在 TS-CLI 中创建的会话在 Web UI 中不可见，反过来也一样。这是因为会话状态存储在前端内存/localStorage，而不是后端。如果需要真正的多端同步体验，会话至少应该有一个后端持久化层。

### 4.2 缺少共享组件库

| 功能 | Web UI 实现 | TS-CLI 实现 | 代码复用 |
|------|------------|------------|---------|
| Markdown 渲染 | `MarkdownRenderer.tsx` | 无（纯文本 + Ink 语法高亮片段） | 0% |
| 工具调用展示 | `ToolCallItem.tsx` | ChatArea 内联 | 0% |
| 步骤展示 | `StepItem.tsx` | ChatArea 内联 | 0% |
| 消息气泡 | `MessageBubble.tsx` | ChatArea 内联 | 0% |
| Hook 审批 | HookApprovalModal + HookApprovalCard | ApprovalModal | 0% |

### 4.3 WebSocket 消息协议演进不一致

从 Java `MessageType` 枚举看，后端已支持的事件两个前端并没有全部处理：

| 消息类型 | Web UI | TS-CLI | 备注 |
|----------|--------|--------|------|
| `step_prompt` | ✅ (planHandlers) | ❌ | 两端的协议类型命名不同 |
| `doctor_result` | ❌ | ✅ | Web UI 只定义了类型，未处理 |
| `degradation_update` | ❌ | ✅ | Web UI 完全未处理 |
| `plan_mode_enter` | ❌ | ❌ | 两端都定义了但未处理 |
| `plan_mode_exit` | ❌ | ❌ | 同上 |
| `todo_item_done` | ✅ | ❌ | 仅 Web UI 处理 |
| `context_compressed` | ✅ | ✅ | 都处理了 |
| `token_update` | ✅ | ✅ | 都处理了 |

---

## 五、对照"智能体上下文工程"五篇文章的差距分析

### 5.1 文章 04 — Plan Mode 与 Todo 的状态机

| 文章要求 | JWCode 现状 | 差距 |
|----------|-----------|------|
| Plan Mode 禁用所有写操作 | 仅通过 `wsService.send({type: 'plan'})` 发送不同消息类型 | **后端需要在 Plan 模式下禁用写工具，前端需要锁定输入区域** |
| ExitPlanMode 不接受 plan 内容作为参数 | 未实现，`exitPlanMode` 只是模式切换 | **需要强制从文件读取 plan 内容** |
| TodoWrite: 恰好一个 in_progress | TaskListPanel 只是普通 checkbox 列表 | **需要在 sessionStore 中增加状态机约束** |
| Todo 的 content/activeForm 双形式 | 仅 `title` + `completed` | **需要增加 content（命令式）和 activeForm（进行式）两个字段** |
| 抗压缩锚点设计 | Plan 文件和 Todo 都存储在前端 | **关键的 Plan 内容和 Todo 应在压缩后仍可见，需要透传到前端** |

### 5.2 文章 05 — Hooks 与外部信号注入

| 文章要求 | JWCode 现状 | 差距 |
|----------|-----------|------|
| Hook 输出被视为用户级信任 | Hook 审批弹窗正确实现了 ASK 语义 | ✅ 做得好 |
| `SessionStart` Hook 注入动态状态 | 未在前端可见 | **前端应显示 SessionStart Hook 的注入结果（如 git status）** |
| `PreToolUse` 权限检查 | 后端 HookChain 实现，前端被动接收 ASK | ✅ 做得好 |
| `PostToolUse` 自动反馈环 | `post-tool-use-lint` hook 已注册 | **lint 结果应该回显在聊天中** |
| `PreCompact` 保存关键状态 | 未在前端体现 | **压缩前应该有一个"保存中..."的指示** |
| Skill 名称从 harness 注入，不猜测 | 前端 SkillsView 是从 `/api/skills` REST 拉取的 | ✅ 合规 |

### 5.3 文章 07 — 压缩与拼接算法

| 文章要求 | JWCode 现状 | 差距 |
|----------|-----------|------|
| 软阈值 70% / 硬阈值 90% | tokenStore 有 usageRatio 但没有阈值可视化 | **StatusLine 需要水位线指示器** |
| 压缩使用独立小模型 | 后端 CompactorAgent 有独立 compactionModelId | ✅ 后端合规 |
| 压缩摘要 block 不二次压缩 | 后端 compactionGeneration=-1 | ✅ 后端合规 |
| 压缩后的状态锚点恢复 | 压缩后前端只有 toast，关键信息可能丢失 | **压缩后应向聊天流注入摘要（<compaction_summary>），让用户知道丢失了什么** |
| Cache 命中率监控 | 完全不可见 | **前端应有 cache hit rate 指示器或诊断面板** |

### 5.4 文章 08 — 工具描述本身就是上下文

| 文章要求 | JWCode 现状 | 差距 |
|----------|-----------|------|
| 每个工具有 "When NOT to use" | 后端 `Tool.java` 新增了 `getNegativeGuidance()` 方法 | ✅ 后端已跟进 |
| 工具描述占 prompt 60% | 前端 ToolsView 可能只显示简短描述 | **ToolsView 应展示完整的参数 schema、禁用规则和示例** |
| 强制纪律用大写 | 后端通过 `getDisciplineRules()` 返回 | ✅ 后端已跟进 |
| 工具描述参与 cache 前缀 | 不可见 | **前端可以展示"Cache 前缀占用估算"** |

### 5.5 文章 10 — 错误恢复与上下文修复

| 文章要求 | JWCode 现状 | 差距 |
|----------|-----------|------|
| 区分工具层错误和基础设施错误 | 后端 ErrorSummary 有区分，前端不展示 | **前端需要为 API 层错误显示"重试中"状态，工具层错误显示为正常 tool_result** |
| 早跳出：2-3 次同类失败改策略 | 后端 RetryOrchestrator 实现，前端不可见 | **前端可以显示"第 N 次重试"的进度** |
| 降级要可见 | TS-CLI 有 degradation_update，Web UI 缺失 | **Web UI 需要补充 degradation_update 处理** |
| 子 Agent 失败 trust-but-verify | 前端无子 Agent 状态视图 | **AgentsView 应展示每个子 Agent 的任务成功率、最近失败原因** |
| 压缩失败降级为截断 | 后端 fallbackTruncate，前端不可见 | **截断发生时前端应有警告** |

---

## 六、建议新增功能

### 6.1 高优先级（直接提升可用性）

**F1 — System Health Dashboard（系统健康面板）**

独立的视图，替代当前重复的"后台日志"区域。包含：
- API 连接状态 + WebSocket 连接状态
- 当前模型名称 + 提供商
- Token 用量仪表盘（含 70%/90% 阈值线）
- 压缩历史（最近 5 次压缩的时间、压缩比、节省 Token）
- Hook 审批历史
- 子 Agent 活跃状态
- 降级模式指示器

**F2 — Plan Mode 物理锁定**

进入 Plan Mode 时：
- 输入框锁定为"只读分析模式"，文字提示
- 文件修改类工具在 UI 上灰显
- 顶部 Banner 显示亮色横幅"PLAN MODE — 文件不会被修改"
- 退出时需确认（ExitPlanMode 的 plan 内容验证）

**F3 — Todo 状态机可视化**

将当前的 TaskListPanel 升级为符合 TodoWrite 规范的看板：
- 每个 Todo 显示 `content`（任务描述）和 `activeForm`（进行中显示"正在做..."）
- 强制执行"仅一个 in_progress"
- 已完成/运行中/待处理的视觉区分
- 折叠已完成的 Todo

**F4 — Token 预算水位线**

在 StatusLine 增加：
- 彩色进度条（绿 0-50% / 黄 50-70% / 橙 70-90% / 红 90%+）
- 鼠标悬浮显示提示文本建议策略（"可继续"、"建议压缩"、"即将压缩"、"紧急压缩"）
- 压缩后动画过渡

### 6.2 中优先级

**F5 — 错误恢复状态视图**

当 API 层错误（超时/429/5xx）发生时：
- 显示重试倒计时和尝试次数（如 "重试中 (3/5)，等待 8s...")
- 降级时显示"已切换到备用模型/区域"
- 压缩失败时显示"压缩失败，已截断上下文"

**F6 — 子 Agent 状态面板**

AgentsView 的升级版：
- 每个 Agent 显示 id/类型/状态/最近任务/成功率
- 点击展开查看最近 10 次任务的时间线和结果
- 红色标记"已失败 >3 次"的 Agent

**F7 — 上下文压缩预览**

压缩前（PreCompact Hook 触发时）：
- 显示"上下文即将压缩"通知
- 展示将被压缩的消息数量
- 用户可选择"立即压缩"/"延迟"/"手动清理"

**F8 — Cache 监控面板**

Settings 的 Advanced 区域或独立的监控面板：
- 显示 cache_control 锚点命中率
- 展示 System Prompt / 工具 Schema / 对话历史的 Token 分布
- 异常检测（连续多轮 miss 的 thrashing 诊断）

### 6.3 低优先级 / 长期

**F9 — Memory 浏览器**

可视化 `.jwcode/memory/` 下的记忆文件：
- 分类展示（项目模式/洞察/偏好/计划上下文）
- 支持编辑和删除
- 显示每条记忆的创建时间和最后访问时间

**F10 — 配置热编辑**

在 Settings 面板中直接编辑配置文件：
- YAML 配置语法高亮编辑器
- `hooks.json` 的声明式编辑
- `team_members.json` 的可视化管理
- 修改后自动重载

**F11 — 协议一致性检查**

编译时脚本检查：
- Java `MessageType` 枚举 → 生成 TypeScript 类型
- 自动检测前端 unhandled 的消息类型
- CI 中报警协议差异

---

## 七、改进路线图

### 第一阶段（本周可做）

1. 给 Web UI 补充 `degradation_update` 处理（1 小时）
2. 统一 Web UI 和 TS-CLI 的协议类型（抽取共享 `protocol.ts`）（2 小时）
3. 修复 ChatHandler.java 的 stub 状态（2 小时）
4. 给 StatusLine 增加 Token 水位线进度条（2 小时）
5. 在 "后台日志" 区域增加 doctor_result 展示（1 小时）

### 第二阶段（1-2 周）

1. 实现 System Health Dashboard（替代日志重复）（4 小时）
2. Plan Mode 物理锁定 — 前端锁定输入 + 后端禁止写工具（6 小时）
3. Todo 状态机可视化升级（4 小时）
4. App.tsx 拆分重构（4 小时）
5. 错误恢复状态视图（3 小时）

### 第三阶段（月度目标）

1. 抽取共享包 `@jwcode/protocol-types` + `@jwcode/shared-hooks`（8 小时）
2. 子 Agent 状态面板升级（6 小时）
3. 上下文压缩预览（4 小时）
4. Config/Hook 热编辑（6 小时）
5. 协议一致性 CI 检查（2 小时）

---

## 八、总结一句话

> JWCode 的两个前端在会话流、WebSocket 通信和状态管理上都有了扎实的基础（特别是 Web UI 的 Zustand 架构和 TS-CLI 的流式批处理），但**上下文工程五篇文章中描述的核心机制（Plan 物理隔离、Todo 状态机、压缩水位线、错误分层可视化）在 UI 层几乎全部缺失**，导致用户无法感知 Agent 系统的内部运行状态——这恰恰是"可观测性"（R.E.S.T 的 L4 层）最需要解决的问题。

---

*分析基于 jwcode-web (8b19e2d+) 和 ts-cli (c4f3a1d+) 源码静态审查。*
*对照文献：智能体上下文工程 04/05/07/08/10 — 状态机/Hooks/压缩算法/工具描述/错误恢复。*

