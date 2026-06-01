# JWCode Hook 生命周期拦截体系 v3.0

> **Hook = 生命周期的"切面"，StateMachine = 生命周期的"骨架"，TransitionGuard = 两者的交汇点**
>
> v3.0 对标 Claude Code 事件模型，扩展至 28 种生命周期事件 + Matcher 匹配器系统。

---

## 目录

1. [架构概览](#1-架构概览)
2. [事件模型（28种）](#2-事件模型28种)
3. [Matcher 匹配器系统](#3-matcher-匹配器系统)
4. [决策语义](#4-决策语义)
5. [Hook 实现形态](#5-hook-实现形态)
6. [优先级与冲突裁决](#6-优先级与冲突裁决)
7. [配置](#7-配置)
8. [新增服务：上下文工程](#8-新增服务上下文工程)
9. [新增服务：记忆工程](#9-新增服务记忆工程)
10. [新增服务：CLAUDE.md 多级加载](#10-新增服务claudemd-多级加载)
11. [编程接口](#11-编程接口)
12. [状态机集成](#12-状态机集成)

---

## 1. 架构概览

Hook 系统位于 **Governance Layer**（横向切面），横切所有层。

```
┌──────────────────────────────────────────────────────────────────┐
│                     Governance Layer（横向切面）                    │
│                                                                  │
│  ┌──────────────┐   ┌──────────────┐   ┌─────────────────────┐  │
│  │  Hook System  │   │ StateMachine │   │  A2A Coordination   │  │
│  │  (28事件拦截)  │◄──┤ (状态定义)    │──►│  (跨Agent远程拦截)    │  │
│  └──────┬────────┘   └──────┬───────┘   └──────────┬──────────┘  │
│         │                   │                       │             │
│         ▼                   ▼                       ▼             │
│  PRE_TOOL_USE        TransitionGuard         TASK_DISPATCH       │
│  POST_TOOL_USE       Checkpoint/Rollback     SUBAGENT_START/STOP │
│  PRE_COMPACT         STATE_TRANSITION        A2A_REMOTE_INTERCEPT│
│  STOP/STOP_FAILURE   CONFIG_CHANGE           PERMISSION_DENIED   │
└──────────────────────────────────────────────────────────────────┘
```

### v3.0 新增文件

```
jwcode-core/src/main/java/com/jwcode/core/
├── hook/
│   └── HookEventType.java              # 更新: 12→28 事件 + matcher
├── service/
│   ├── ContextAnalysisService.java      # 新增: Token分布统计
│   ├── ContextSuggestionsService.java   # 新增: 5类上下文建议
│   └── ClaudeMdLoader.java             # 新增: 多级CLAUDE.md加载
├── compact/
│   └── AutoCompactCircuitBreaker.java   # 新增: 压缩断路器
├── memory/
│   ├── ExtractMemoriesService.java      # 新增: 自动记忆提取
│   └── SessionMemoryService.java        # 新增: 9节会话记忆
└── config/
    └── SystemPromptAssembler.java       # 更新: 优先级组装+记忆类型注入

jwcode-web/src/components/Chat/
└── ContextAnalysisBanner.tsx            # 新增: 前端上下文警告
```

---

## 2. 事件模型（28种）

### 2.1 全部生命周期事件

| 类别 | 事件 | 触发点 | Matcher | v3.0 |
|------|------|--------|---------|------|
| **Session** | `SESSION_START` | 会话创建 | source ∈ {startup,resume,clear,compact} | |
| | `SESSION_END` | 会话销毁 | reason ∈ {clear,logout,prompt_input_exit,other} | |
| | `STOP` | 响应结束前 | — | **新增** |
| | `STOP_FAILURE` | API错误结束 | error ∈ {rate_limit,auth_failed,...} | **新增** |
| | `NOTIFICATION` | 通知发送 | notification_type | |
| **Tool** | `PRE_TOOL_USE` | 工具执行前 | tool_name | |
| | `POST_TOOL_USE` | 工具成功后 | tool_name | |
| | `POST_TOOL_USE_FAILURE` | 工具失败后 | tool_name | |
| | `PERMISSION_DENIED` | 权限拒绝后 | tool_name | **新增** |
| | `PERMISSION_REQUEST` | 权限请求显示 | tool_name | **新增** |
| **Context** | `PRE_COMPACT` | 压缩前 | trigger ∈ {manual,auto} | |
| | `POST_COMPACT` | 压缩后 | trigger ∈ {manual,auto} | **新增** |
| | `PRE_CONTEXT_RESET` | 上下文重置前 | — | |
| | `POST_CONTEXT_RESET` | 上下文重置后 | — | |
| **StateMachine** | `STATE_TRANSITION` | 状态转换前 | — | |
| | `STATE_ENTERED` | 进入新状态 | — | |
| **Task** | `USER_PROMPT_SUBMIT` | 用户提交提示词 | — | |
| | `SUBAGENT_START` | 子Agent启动 | agent_type | |
| | `SUBAGENT_STOP` | 子Agent完成 | agent_type | |
| | `TASK_CREATED` | 任务创建 | — | **新增** |
| | `TASK_COMPLETED` | 任务完成 | — | **新增** |
| | `TEAMMATE_IDLE` | 队友空闲 | — | **新增** |
| **A2A** | `TASK_DISPATCH` | A2A任务下发前 | — | |
| | `A2A_REMOTE_INTERCEPT` | A2A远程拦截 | — | |
| **Config** | `CONFIG_CHANGE` | 配置文件变更 | source ∈ {user_settings,project_settings,...} | **新增** |
| | `INSTRUCTIONS_LOADED` | 指令文件加载 | load_reason ∈ {session_start,nested_traversal,...} | **新增** |
| **FileSystem** | `CWD_CHANGED` | 工作目录变更 | — | **新增** |
| | `FILE_CHANGED` | 监听文件变更 | — | **新增** |

### 2.2 事件分层分布

```
Orchestrator (Layer 1) → SESSION_START/END, USER_PROMPT_SUBMIT, STOP
DomainAgent (Layer 2)  → SUBAGENT_START/STOP, TASK_DISPATCH, TASK_CREATED/COMPLETED
ToolAgent (Layer 3)    → PRE_TOOL_USE, POST_TOOL_USE, POST_TOOL_USE_FAILURE, PERMISSION_DENIED/REQUEST
StateMachine (横向)     → STATE_TRANSITION, STATE_ENTERED
LLMQueryEngine (横向)  → PRE_COMPACT, POST_COMPACT, CONFIG_CHANGE
```

---

## 3. Matcher 匹配器系统

### 3.1 设计原理

对标 Claude Code `hooksConfigManager.ts` 的二级分组：

```typescript
// Claude Code: groupHooksByEventAndMatcher()
Record<HookEvent, Record<string, IndividualHookConfig[]>>
```

每个事件的 matcher 定义：
- `matcherField` — 用于匹配的字段名（如 `tool_name`、`trigger`、`source`）
- `matcherValues` — 合法匹配值列表（空列表 = 运行时动态确定）

### 3.2 匹配器执行流程

```
HookChain.execute(context)
    │
    ├─ 1. 获取事件类型 eventType
    ├─ 2. 从 registry 获取匹配的 Hook 执行器
    ├─ 3. 按 matcher 值过滤（二级分组）
    │     ├─ PRE_TOOL_USE + "Bash"  → 只匹配 Bash 的 Hook
    │     ├─ PRE_TOOL_USE + "Read"  → 只匹配 Read 的 Hook
    │     ├─ PRE_COMPACT + "auto"   → 只匹配自动压缩的 Hook
    │     └─ PRE_COMPACT + "manual" → 只匹配手动压缩的 Hook
    ├─ 4. 按优先级排序（降序）
    ├─ 5. 串行执行，DENY/VOID 短路
    └─ 6. 冲突裁决 → 最终决策
```

---

## 4. 决策语义

| 决策 | 含义 | 携带数据 | 终止性 |
|------|------|----------|--------|
| `ALLOW` | 直接放行 | — | 否 |
| `DENY` | 拒绝执行 | `reason` | ✅ |
| `ASK` | 请求用户确认 | `askPayload` | 否 |
| `MODIFY` | 修改输入后放行 | `modifiedInput` | 否 |
| `DEFER` | 延迟执行 | `deferToken` | 否 |
| `VOID` | 取消并回退 | `rollbackAction` | ✅ |

### 冲突裁决规则

```
规则 1: DENY/VOID 最高优先 → 任一拒绝即拒绝
规则 2: MODIFY 链式传递   → 高优先级先修改，低优先级基于新输入
规则 3: ASK 覆盖 ALLOW    → 只要有 Hook 需要确认，最终就需要确认
规则 4: DEFER 聚合        → 等待所有审批完成
```

---

## 5. Hook 实现形态

| 形态 | 原理 | 适用场景 | 默认超时 | Fail模式 |
|------|------|----------|----------|----------|
| **Shell** | stdin JSON → 脚本 → stdout 决策 | 本地安全脚本 | 30s | fail-open |
| **HTTP** | POST JSON → REST API → JSON 决策 | 企业审批流 | 10s | fail-open |
| **Prompt** | 模板变量 → LLM Prompt → AI 决策 | 动态风险评估 | 15s | fail-open |
| **Agent** | 子Agent 深度调查 → 结构化决策 | 复杂审查 | 60s | fail-open |

### Shell Hook 脚本契约

```json
// stdin 输入
{
  "eventType": "PRE_TOOL_USE",
  "toolName": "Bash",
  "toolInput": { "command": "rm -rf /" },
  "sessionId": "session-123"
}

// stdout 输出
{
  "decision": "DENY",
  "reason": "危险命令：rm -rf /"
}
```

### 新增: Stop Hook 退出码语义

| 退出码 | 行为 |
|--------|------|
| 0 | stdout/stderr 不显示给模型 |
| 2 | stderr 显示给模型，继续对话 |
| 其他 | stderr 仅显示给用户 |

### 新增: PreCompact Hook 退出码语义

| 退出码 | 行为 |
|--------|------|
| 0 | stdout 追加为自定义压缩指令 |
| 2 | 阻止压缩 |
| 其他 | stderr 显示给用户，压缩继续 |

---

## 6. 优先级与冲突裁决

| 优先级 | 数值 | 说明 | Fail策略 |
|--------|------|------|----------|
| `SYSTEM` | 100 | 内置关键拦截（Token预算保护） | fail-closed |
| `SECURITY` | 80 | 安全审计与合规检查 | fail-closed |
| `PROJECT` | 60 | 项目级别策略 | fail-open |
| `USER` | 40 | 用户自定义规则 | fail-open |
| `PLUGIN` | 20 | 第三方扩展 | fail-open |

### 短路机制

Hook 链按优先级从高到低串行执行。遇到以下情况立即短路：
- `DENY` → 不再执行后续 Hook
- `VOID` → 不再执行后续 Hook，触发回退

### 新增: Fire-and-Forget 事件

以下事件为 fire-and-forget 类型，输出和退出码被忽略（仅用于观测）：
- `STOP_FAILURE`
- `INSTRUCTIONS_LOADED`
- `CWD_CHANGED`
- `FILE_CHANGED`

---

## 7. 配置

### 7.1 配置文件 (v3.0)

位置：`.jwcode/hooks.json`

```json
{
  "hooks": [
    {
      "name": "bash-validator",
      "description": "Bash/PowerShell 命令安全检查",
      "events": ["PreToolUse"],
      "matcher": "Bash|PowerShell",
      "priority": "SYSTEM",
      "implementation": {
        "type": "command",
        "command": "python .jwcode/hooks/bash-validator.py",
        "timeout": 30
      }
    },
    {
      "name": "compact-warning",
      "description": "压缩前信息保护",
      "events": ["PreCompact"],
      "matcher": "manual|auto",
      "priority": "HIGH",
      "implementation": {
        "type": "command",
        "command": "python .jwcode/hooks/session-context.py",
        "timeout": 10
      }
    },
    {
      "name": "stop-hook",
      "description": "响应结束前—自动提取记忆",
      "events": ["Stop"],
      "matcher": "",
      "priority": "MEDIUM",
      "implementation": {
        "type": "prompt",
        "prompt": "Extract any new durable memories from the session.",
        "timeout": 30
      }
    },
    {
      "name": "config-change-reloader",
      "description": "配置文件变更时热重载",
      "events": ["ConfigChange"],
      "matcher": "project_settings|local_settings",
      "priority": "MEDIUM",
      "implementation": {
        "type": "command",
        "command": "echo 'Config changed, reload triggered'",
        "timeout": 5
      }
    }
  ]
}
```

### 7.2 配置字段说明 (v3.0 更新)

| 字段 | 类型 | 必需 | 说明 |
|------|------|------|------|
| `name` | string | ✅ | Hook 唯一名称 |
| `description` | string | | Hook 描述 |
| `events` | string[] | ✅ | 监听的事件类型（支持 28 种） |
| `matcher` | string | | **新增**: matcher 值（如 "Bash\|PowerShell"、"auto"） |
| `implementation.type` | enum | ✅ | command / http / prompt / agent |
| `implementation.command` | string | command时 | 脚本命令 |
| `implementation.url` | string | http时 | 端点 URL |
| `implementation.prompt` | string | prompt时 | Prompt 模板 |
| `implementation.agentName` | string | agent时 | 子 Agent 名称 |
| `priority` | enum | | SYSTEM / HIGH / MEDIUM / LOW / PLUGIN |
| `timeout` | number | | 超时（秒） |
| `enabled` | boolean | | 是否启用，默认 true |

---

## 8. 新增服务：上下文工程

### 8.1 ContextAnalysisService

对标 Claude Code `contextAnalysis.ts`，分析会话消息 Token 分布。

```java
ContextAnalysisService analyzer = new ContextAnalysisService();
TokenStats stats = analyzer.analyze(messages, tokenEstimator);

// 输出:
// toolRequests: Bash=5.2K Read=3.1K
// toolResults: Bash=12.4K Read=8.2K
// humanMsgs=1.2K, assistantMsgs=3.5K
// duplicateFileReads: src/main/App.tsx(x3, wasted=2.1K)
```

**核心指标:**
- `toolRequests` / `toolResults` — 各工具调用的 Token
- `duplicateFileReads` — 重复文件读取检测（同一文件被多次 Read）
- `humanMessages` / `assistantMessages` — 用户/AI 消息占比

### 8.2 ContextSuggestionsService

对标 Claude Code `contextSuggestions.ts`，5 类自动检测建议。

| 检测类型 | 触发条件 | 建议操作 | 预估节省 |
|----------|----------|----------|----------|
| NearCapacity | ≥80% 容量 | /compact 手动压缩 | — |
| LargeBashOutput | ≥15% 或 ≥10K tokens | head/tail/grep 过滤 | 50% |
| LargeReadOutput | ≥15% | offset/limit 分页 | 30% |
| FileReadBloat | ≥5% 且 ≥10K tokens | 引用之前读取结果 | 30% |
| MemoryBloat | ≥5% 或 ≥5K tokens | /memory 清理 | 30% |

### 8.3 ContextAnalysisBanner (前端)

React 组件，在 ChatPanel 顶部展示：
- 上下文使用进度条（绿→蓝→黄→红 颜色编码）
- 可展开/关闭的建议卡片
- Token 分布摘要行

### 8.4 AutoCompactCircuitBreaker

对标 Claude Code `autoCompact.ts` 电路断路器。

```
状态机: CLOSED → [3 consecutive failures] → OPEN → [success] → CLOSED
```

- 连续失败 ≥ 3 次 → 跳闸
- 本会话不再尝试自动压缩（手动 /compact 仍可用）
- Claude Code 数据：1,279 个会话 50+ 次连续失败，每天浪费 ~250K API 调用

---

## 9. 新增服务：记忆工程

### 9.1 ExtractMemoriesService

对标 Claude Code `extractMemories.ts`，在 query loop 结束时自动提取持久记忆。

**核心设计:**
```
每次 query loop 结束
    │
    ├─ 1. 检查 sinceUuid → 只处理新消息 (cursor 跟踪)
    ├─ 2. 互斥检测 → 主Agent 已写 memory 则跳过
    ├─ 3. 节流 → 每 N 个 turn 运行一次
    ├─ 4. Fork Agent → 只读文件 + memory目录写入
    ├─ 5. 提取记忆 → 写入 .jwcode/memory/
    ├─ 6. 更新 MEMORY.md 索引
    ├─ 7. Trailing extraction → stash 上下文追跑
    └─ 8. 更新 cursor → lastProcessedUuid
```

**四种记忆类型:**

| 类型 | 说明 | 作用域 | 示例 |
|------|------|--------|------|
| `user` | 用户角色/偏好/知识背景 | 私人 | "我是数据分析师" |
| `feedback` | 用户反馈/纠正/确认 | 私人/团队 | "不要 mock 数据库" |
| `project` | 项目目标/决策/约束 | 偏团队 | "周四 merge freeze" |
| `reference` | 外部系统引用 | 偏团队 | "bugs 在 Linear INGEST" |

**不应保存的记忆:**
- 代码模式、架构、文件路径 — 可从代码推导
- Git 历史 — git log 是权威参考
- 已在 CLAUDE.md 中的内容
- 临时任务细节

### 9.2 SessionMemoryService

对标 Claude Code `SessionMemory`，9 节模板的会话记忆。

```
# Session Title       — 5-10词描述性标题
# Current State       — 当前正在处理的内容
# Task specification  — 用户要求构建的内容
# Files and Functions — 重要文件和函数
# Workflow            — 常用命令和执行顺序
# Errors & Fixes      — 错误及修复
# Codebase and System — 系统组件及关系
# Learnings           — 经验教训
# Key results         — 用户要求的精确输出
# Worklog             — 步骤级摘要
```

**约束:**
- 每节最大 2000 tokens
- 总文件最大 12000 tokens
- 超限按节截断，保留 Current State 和 Errors & Fixes 精度
- 支持自定义模板 (`~/.jwcode/session-memory/template.md`)

---

## 10. 新增服务：CLAUDE.md 多级加载

### 10.1 ClaudeMdLoader

对标 Claude Code `claudemd.ts`，4 级优先级加载。

```
优先级: Managed → User → Project → Local

1. ~/.claude/rules/        — 托管规则（团队策略）
2. ~/.claude/CLAUDE.md     — 用户级指令
3. ./CLAUDE.md             — 项目根目录指令
4. ./.claude/CLAUDE.md     — 项目隐藏指令
5. ./.claude/rules/*.md    — 项目规则文件
6. ./CLAUDE.local.md       — 本地指令（不提交git）
```

**特性:**
- `@include` 指令 + 循环引用检测
- `paths:` frontmatter glob 匹配
- 5 种加载原因：session_start / nested_traversal / path_glob_match / include / compact

### 10.2 SystemPromptAssembler (v3.0 更新)

优先级组装：`OVERRIDE > AGENT > CUSTOM > DEFAULT` + `append`

默认 prompt 包含:
1. core.md — AI 身份定义
2. rules.md — 行为规则
3. CLAUDE.md 内容 — 项目指令
4. protocols/*.md — 协议定义
5. 工具自描述 — 从 ToolRegistry 自动聚合
6. **记忆类型定义** — 对标 Claude Code memoryTypes.ts
7. 记忆清单 — MEMORY.md 索引
8. 环境信息 — OS、时间、工作目录

---

## 11. 编程接口

### 11.1 初始化 (v3.0)

```java
// 1. 创建 Hook 注册表
HookRegistry registry = new HookRegistry();

// 2. 注册执行器
registry.register(new ShellHookExecutor("audit", "...", HookPriority.SYSTEM, 30_000, true, true));

// 3. 创建 Hook 链
HookChain hookChain = new HookChain(registry, auditLogger);

// 4. 初始化上下文服务
ContextAnalysisService ctxAnalyzer = new ContextAnalysisService();
ContextSuggestionsService ctxSuggestions = new ContextSuggestionsService();

// 5. 初始化记忆服务
ExtractMemoriesService extractMemories = new ExtractMemoriesService(memoryDir, forkExecutor);
SessionMemoryService sessionMemory = new SessionMemoryService(notesPath);

// 6. 初始化 CLAUDE.md 加载器
ClaudeMdLoader claudeMdLoader = new ClaudeMdLoader(workspaceRoot);
List<LoadResult> instructions = claudeMdLoader.loadAll();

// 7. 组装 System Prompt
String systemPrompt = new SystemPromptAssembler(promptDir, toolRegistry)
    .withClaudeMdContents(claudeMdLoader.getMemoryContext())
    .withMemoryManifest(memoryManifest)
    .includeMemoryTypes(true)
    .assemble();
```

### 11.2 Stop Hook — 自动记忆提取

```java
// 对标 Claude Code executeExtractMemories() — Stop hook 中 fire-and-forget
hookChain.register(new PromptHookExecutor(
    "stop-memory-extractor",
    "Extract any new durable memories from the session...",
    HookPriority.MEDIUM
));
```

### 11.3 PreCompact Hook — 会话记忆保护

```java
// 对标 Claude Code trySessionMemoryCompaction()
TruncationResult truncated = sessionMemory.readForCompact();
if (truncated.wasTruncated()) {
    logger.info("Session memory truncated for compact");
}
```

---

## 12. 状态机集成

### 转换保护点

| 转换 | Hook 审批 | 自动 Checkpoint |
|------|----------|----------------|
| `IDLE → PLANNING` | 否 | — |
| `PLANNING → EXECUTING` | 否 | ✅ |
| `EXECUTING → REVIEWING` | **是** | ✅ |
| `REVIEWING → IDLE` | **是** | ✅ |
| `任意 → WAITING_INPUT` | 否 | ✅ |

### 回退策略

```java
RollbackAction.RETRY                  // 重试当前步骤
RollbackAction.SKIP                   // 跳过当前步骤
RollbackAction.ROLLBACK_TO_CHECKPOINT // 回退到最近检查点
RollbackAction.ABORT_TASK             // 终止任务
RollbackAction.REQUEST_HUMAN          // 请求人工介入
```

---

## 附录 A: 与 Claude Code 的映射关系

| Claude Code 源码文件 | jwcode 对应实现 | 说明 |
|---------------------|----------------|------|
| `utils/hooks/hooksConfigManager.ts` | `HookChain.java` + 新 matcher 系统 | Hook 配置管理 + 二级分组 |
| `utils/hooks/hookEvents.ts` | `HookEventType.java` | 25→28 事件类型 |
| `utils/contextAnalysis.ts` | `ContextAnalysisService.java` | Token 统计分析 |
| `utils/contextSuggestions.ts` | `ContextSuggestionsService.java` | 5 类建议 |
| `services/compact/autoCompact.ts` | `AutoCompactCircuitBreaker.java` | 断路器 |
| `services/extractMemories/extractMemories.ts` | `ExtractMemoriesService.java` | 自动记忆提取 |
| `services/SessionMemory/sessionMemory.ts` | `SessionMemoryService.java` | 9 节模板 |
| `utils/claudemd.ts` | `ClaudeMdLoader.java` | 多级 CLAUDE.md |
| `utils/systemPrompt.ts` | `SystemPromptAssembler.java` | 优先级组装 |
| `memdir/memoryTypes.ts` | `MEMORY_TYPES_PROMPT` 常量 | 记忆类型定义 |
| `services/SessionMemory/prompts.ts` | `SessionMemoryService.buildUpdatePrompt()` | 更新 prompt |

## 附录 B: Fail-Open vs Fail-Closed

| 优先级 | 默认策略 | 含义 |
|--------|----------|------|
| SYSTEM | fail-closed | Hook 异常时拒绝操作（安全第一） |
| SECURITY | fail-closed | 安全审计不可绕过 |
| HIGH/MEDIUM/LOW/PLUGIN | fail-open | Hook 异常时放行（不阻塞业务） |

## 附录 C: 性能基准

| 操作 | 预期延迟 |
|------|----------|
| 无 Hook | < 1ms |
| Shell Hook（本地） | 5-50ms |
| HTTP Hook（本地） | 10-100ms |
| Prompt Hook | 500-3000ms |
| Agent Hook | 2-60s |
| ContextAnalysis | < 5ms (内存计算) |
| ExtractMemories | 2-10s (异步) |
| SessionMemory | < 1ms (文件 I/O) |
