# JWCode 架构规范

> 本文档为 JWCode 项目的 AI 协作规范和 Agent 架构定义。
> 所有场景必须严格执行：**主Agent拆解调度，子Agent执行工作**。
> 基于 Harness Engineering 框架 (R.E.S.T 模型) 构建。
> 最后自动更新：2026-05-26 | 版本 1.0.0-SNAPSHOT | 5 模块 | 17 Agent | 47 Tool

---

## -1. 构建与运行

```bash
mvn compile -pl jwcode-core,jwcode-web -am -q   # 编译
mvn test -pl jwcode-core -am                      # 测试
./start.bat / ./start.sh                          # 一键启动
jwcode start                                      # Python CLI 全自动
```

**前后端**: Python CLI (Rich+Textual) / React Web UI (Vite+Tailwind, :8080) / VS Code 插件 (Ctrl+Shift+J)

---

## 0. Harness Engineering 四层架构 (v3.1)

jwcode 以 R.E.S.T 模型构建 Agent 驾驭体系：

```
L1 安全:  DockerSandbox → WorkspaceGuard → PermissionManager → HookChain → AuditLogger
L2 成本:  ModelRouter + CostTracker + Prompt Caching + TokenBudget 分区
L3 质量:  五级压缩保留 + AiRepair 自愈 + ZONE 注入边界 + 语义记忆 + 子代理隔离
L4 可观测: AnalyticsObserver + /doctor + /rewind + ProjectDocGenerator
```

**六大设计原则**: 为失败而设计 | 契约优先 | 默认安全 | 决策与执行分离 | 万物皆可度量 | 数据驱动进化

**禁止**: Orchestrator 直接执行工具 | 子 Agent 递归创建子 Agent | Reviewer/Explorer 修改文件 | LLM 维护跨轮次状态

### 关键文件速查

| 组件 | 文件 |
|------|------|
| LLM/Tool/Hook | `llm/LLMQueryEngine.java` `tool/ToolExecutor.java` `hook/HookChain.java` |
| Agent 系统 | `agent/EnhancedOrchestratorAgent.java` `agent/AgentRegistry.java` |
| 沙箱/路由 | `tool/shell/DockerSandboxExecutor.java` `llm/ModelRouter.java` |
| 成本/诊断 | `service/CostTrackerService.java` `service/DoctorService.java` |
| 压缩/自愈 | `service/SimpleCompactionStrategy.java` `resilience/RecoveryExecutor.java` |
| 文档/记忆 | `service/ProjectDocGenerator.java` `agent/WorkspaceMemoryStore.java` |
| WS/前端 | `jwcode-web/.../stream/StreamingWebSocketHandler.java` `python-cli/jwcode/main.py` |
| CI/插件 | `.github/workflows/ci.yml` `vscode-extension/src/extension.ts` |
| 配置 | `~/.jwcode/config.yaml` |

---

## 1. 部署架构 (v3.0)

```
┌──────────────────────────────────────────┐
│  Python CLI (Rich + Textual)              │  ← jwcode start
│  React Web UI (Vite + Tailwind)           │  ← http://localhost:8080
├──────────────────────────────────────────┤
│  WebSocket (8081/ws) + REST API (8080)    │  ← jwcode-web
├──────────────────────────────────────────┤
│  jwcode-core (LLM / Agent / Tool)         │
│  jwcode-common (Auth / Config)            │
│  jwcode-mcp / jwcode-parser               │
└──────────────────────────────────────────┘
```

**启动**: `jwcode start` (Python CLI, 自动编译并启动 Java 后端)
**Web UI**: `http://localhost:8080`

---

## 1. 四层 Agent 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                    用户层 (User Request)                      │
└───────────────────────┬─────────────────────────────────────┘
                        │
┌───────────────────────▼─────────────────────────────────────┐
│  第1层：主Agent (Orchestrator)                              │
│  职责：意图分析、任务分解、Agent发现(A2A Agent Card)、状态聚合   │
│  关键：不直接处理工具调用，只负责"派活"和"收结果"               │
│  模式：PDCA循环 (Plan → Do → Check → Act)                   │
└───────────────────────┬─────────────────────────────────────┘
                        │ A2A Task Submit (任务清单)
┌───────────────────────▼─────────────────────────────────────┐
│  第2层：专业Agent (Code/Debug/Domain Agent)                 │
│  职责：接收子任务、生成操作步骤、调用Tool Agent、步骤级重试      │
│  关键：维护本地步骤状态机，对主Agent屏蔽工具执行细节             │
└───────────────────────┬─────────────────────────────────────┘
                        │ A2A Task Submit (操作步骤)
┌───────────────────────▼─────────────────────────────────────┐
│  第3层：Tool Agent (Executor)                               │
│  职责：接收具体命令、调用MCP工具、执行、失败自诊断与修复         │
│  关键：3次自修复循环，失败返回结构化错误摘要(非原始堆栈)          │
└───────────────────────┬─────────────────────────────────────┘
                        │ MCP Protocol
┌───────────────────────▼─────────────────────────────────────┐
│  第4层：工具层 (Tools/Data via MCP Servers)                  │
│  职责：数据库、API、文件系统等原子操作                         │
└─────────────────────────────────────────────────────────────┘

横向服务层：
┌─────────────────────────────────────────────────────────────┐
│  CompactorAgent (上下文压缩专家)                              │
│  职责：为所有Agent提供上下文压缩服务，节省Token预算             │
│  策略：AICL_PRIORITY / STRUCTURED / SMART / AGGRESSIVE /     │
│        MINIMAL                                               │
│  协议：AICL (Agent Interaction Context Language) v1.0         │
│  核心：6级优先级 + 6状态生命周期 + Priority-LRU 淘汰引擎       │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│  AICL 协议引擎（v1.1 新增，结构化上下文生命周期管理）           │
│  ContextAssembler / ContextBlock / BlockPriority /           │
│  BlockLifecycle / AICLSerializer / AICLDeserializer          │
└─────────────────────────────────────────────────────────────┘
┌─────────────────────────────────────────────────────────────┐
│  Hook 拦截体系（v2.1 新增，生命周期拦截与运行时治理）          │
│  职责：在关键节点拦截、决策、修改执行流                          │
│  事件：12种生命周期事件 (Session/Tool/Context/StateMachine/   │
│        Task/A2A)                                             │
│  决策：6种决策语义 (ALLOW/DENY/ASK/MODIFY/DEFER/VOID)        │
│  形态：4种实现 (Shell/HTTP/Prompt/Agent Hook)                │
│  核心：HookChain拦截链 + TransitionGuard状态守护 +             │
│        A2A远程拦截 + Priority-LRU冲突裁决                     │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. 角色定义

### 2.1 Orchestrator（主Agent / 第1层）

| 维度 | 说明 |
|------|------|
| **定位** | 唯一用户入口，任务指挥家 |
| **职责** | 意图识别、复杂度评估、任务拆解、Agent选型、并行编排、结果验收、整合输出 |
| **可用工具** | `AgentTool`（核心）、`SmartAnalyzeTool`（宏观分析）、`AskUserQuestionTool`（需求澄清） |
| **禁止工具** | `FileReadTool`、`FileWriteTool`、`FileEditTool`、`BashTool`、`PowerShellTool`、`GlobTool`、`GrepTool` 等所有业务执行工具 |
| **关键原则** | 绝不自己动手。哪怕是一个简单的文件读取，也必须派给子Agent。 |
| **工作模式** | **PDCA循环**：Plan(任务清单) → Do(下发子任务) → Check(聚合状态) → Act(重规划/完成) |

### 2.2 专业Agent（Worker / 第2层）

| Agent | 类型 | 职责 | 特点 |
|-------|------|------|------|
| **Coder** | worker | 代码编写、重构、Bug修复 | 全工具权限（除危险工具） |
| **Debug** | worker | 错误排查、根因分析、修复验证 | 侧重分析类工具 |
| **Reviewer** | worker | 代码审查、安全扫描、风格检查 | **只读模式**，不修改文件 |
| **Evaluator** | worker | GAN式评估专家（判别器），4维加权评分+硬门槛否决，驱动生成-评估对抗循环 | **只读模式**，输出结构化评分JSON |
| **Tester** | worker | 测试用例设计、测试编写、执行 | 可运行编译和测试命令 |
| **Documenter** | worker | 文档编写、README、API文档 | 读写文件，不执行命令 |
| **Explorer** | worker | 代码库调研、结构分析、技术债务 | **只读模式**，纯调研 |
| **Architect** | worker | 架构设计、接口定义、技术选型 | 输出设计文档和代码骨架 |
| **TaskAgent** | internal | AI回复→结构化任务解析（阶段/模式/依赖） | 无外部工具，纯解析 |
| **TaskExecutionAgent** | internal | 结构化任务逐步执行（并发/串行调度） | 通过 A2AFacade 调度子Agent |
| **MemoryAgent** | internal | 工作目录级主动记忆（项目模式/洞察/偏好） | 文件读写（仅 .jwcode/memory/） |
| **Default** | worker | 通用任务、降级兜底 | 全工具权限 |

### 2.3 Tool Agent（执行器 / 第3层）

| 维度 | 说明 |
|------|------|
| **定位** | 工具执行专家，MCP协议调用者 |
| **职责** | 接收具体命令、调用MCP工具、执行、失败自诊断与修复 |
| **核心机制** | 3次自修复循环，失败返回结构化错误摘要（非原始堆栈） |
| **返回内容** | 错误类型 + 修复尝试次数 + 最终失败原因(1句话) |
| **不返回内容** | 原始命令、堆栈跟踪、工具详情 |

### 2.4 CompactorAgent（横向服务Agent / 第2.5层）

| 维度 | 说明 |
|------|------|
| **定位** | 上下文压缩专家，为所有Agent提供服务 |
| **职责** | 压缩对话历史，节省Token预算 |
| **策略** | AICL_PRIORITY(基于6级优先级+生命周期渐进淘汰, v1.1默认) / STRUCTURED(强制XML输出, 按优先级保留) / SMART(智能压缩, 保留尾部8条+摘要) / AGGRESSIVE(激进压缩, 保留尾部4条+摘要) / MINIMAL(最小压缩, 仅移除噪声) |
| **结构化输出** | `<current_focus>` / `<environment>` / `<completed_tasks>` / `<active_issues>` / `<code_state>` / `<design_decisions>` / `<todo_items>` |
| **AICL输出** | `<ctx:context>` 根 → `<ctx:control>`(预算/策略) + `<ctx:blocks>`(优先级块集合) + `<ctx:trace>`(校验) |
| **优先级体系** | 当前任务状态 > 错误与解决方案 > 代码最终版本 > 系统上下文 > 设计决策 > TODO项 |
| **AICL优先级** | pinned(永固) > critical(关键) > high(高) > medium(中) > low(低) > optional(可选) |
| **触发方式** | Token水位线自动触发 / 用户手动触发(/compact) / Agent主动请求 / 检查点前 / 子任务完成时 / 会话超限 |

### 2.5 ContextResetManager（v3.0 新增 / 横向基础设施）

Context Reset 协议实现"进程级重启+结构化交接"，用于解决 Agent 上下文焦虑问题。

| 维度 | 说明 |
|------|------|
| **定位** | 上下文重置专家，为所有Agent提供进程级重启服务 |
| **职责** | 检测上下文压力、生成交接文档(HandoffArtifact)、触发Agent重启 |
| **触发条件** | Token使用率>85%(硬阈值) / 迭代循环>3轮(软阈值) / 任务阶段切换 / Agent主动请求 |
| **核心机制** | 冻结状态 → 提取HandoffArtifact → 保存到`.jwcode/handoff/` → 杀死旧Agent → 启动新Agent → 读取恢复 |
| **与Compactor关系** | Compactor先尝试压缩，压缩不足以解决问题时建议升级为Reset |

**HandoffArtifact 结构**：
| 字段 | 说明 |
|------|------|
| `sessionId` | 会话ID |
| `taskId` | 任务ID |
| `phase` | 当前阶段 |
| `completedWork` | 已完成工作摘要 |
| `currentState` | 当前状态（代码状态、配置等） |
| `pendingItems` | 待办事项列表 |
| `decisions` | 关键决策记录 |
| `codeState` | 代码变更摘要 |
| `activeIssues` | 活跃问题列表 |
| `nextActions` | 下一步行动 |

### 2.6 AICL 协议引擎（v1.1 新增 / 横向基础设施）

AICL (Agent Interaction Context Language) 是上下文块的结构化生命周期管理协议。核心组件：

| 组件 | 文件 | 职责 |
|------|------|------|
| `BlockPriority` | `aicl/BlockPriority.java` | 6级优先级枚举（OPTIONAL→PINNED）+ 淘汰动作映射 |
| `BlockLifecycle` | `aicl/BlockLifecycle.java` | 6状态生命周期（active→compressed→summarized→archived→deprecated）+ pinned |
| `ContextBlock` | `aicl/ContextBlock.java` | AICL块模型（id/type/priority/state/ttl/lastAccess/generation） |
| `ContextControl` | `aicl/ContextControl.java` | 控制层（TokenBudget + EvictionConfig + LifecycleDefaults） |
| `ContextAssembler` | `aicl/ContextAssembler.java` | **核心淘汰引擎**：Priority-LRU逐级淘汰 |
| `AICLSerializer` | `aicl/AICLSerializer.java` | XML序列化（ContextBlock → AICL XML） |
| `AICLDeserializer` | `aicl/AICLDeserializer.java` | XML反序列化（AICL XML → AICLContext） |
| `AICLContext` | `aicl/AICLContext.java` | 完整上下文容器（sessionId/turn/control/blocks/checksum） |
| `AICLPromptBuilder` | `aicl/AICLPromptBuilder.java` | AI解析规则Prompt生成器 |

**淘汰算法（Priority-LRU）**：
```
当 used > total * threshold 时：
  1. 按 priority 分组，从 optional 开始处理
  2. 同优先级内按 last-access 排序（LRU）
  3. 依次执行：
     optional → 直接删除     low → 归档(保留元数据)
     medium → 摘要替换        high → 同义压缩(去冗余)
     critical → 仅删注释      pinned → 跳过
  4. 每处理一个块，重新计算 used
  5. 当 used <= total * stopThreshold 时停止
```

### 2.6 Hook 拦截体系（v2.1 新增 / 横向基础设施）

Hook 是生命周期的"切面"，StateMachine 是生命周期的"骨架"。
Hook 位于 Governance Layer，与 Permission Modes、Sandboxing 并列。

| 维度 | 说明 |
|------|------|
| **定位** | 生命周期拦截与运行时治理 |
| **职责** | 在关键节点拦截、决策、修改执行流（工具调用/状态转换/A2A任务分发） |
| **事件模型** | 12种生命周期事件：SESSION_START/END, PRE_TOOL_USE, POST_TOOL_USE, POST_TOOL_USE_FAILURE, PRE_COMPACT, STATE_TRANSITION, STATE_ENTERED, USER_PROMPT_SUBMIT, SUBAGENT_START, SUBAGENT_STOP, TASK_DISPATCH, A2A_REMOTE_INTERCEPT |
| **决策语义** | ALLOW(放行) / DENY(拒绝) / ASK(确认) / MODIFY(修改) / DEFER(延迟) / VOID(回退) |
| **实现形态** | SHELL(脚本) / HTTP(REST端点) / PROMPT(AI动态评估) / AGENT(子代理调查) |
| **优先级** | SYSTEM(100) > SECURITY(80) > PROJECT(60) > USER(40) > PLUGIN(20) |
| **核心机制** | HookChain拦截链(优先级排序→串行执行→短路→MODIFY链式传递) + TransitionGuard(状态转换前置审批) + ConflictResolver(冲突裁决) |
| **配置方式** | `.jwcode/hooks.json` 声明式配置，支持热加载 |

**核心组件**：

| 组件 | 文件 | 职责 |
|------|------|------|
| `HookDecision` | `hook/HookDecision.java` | 6种决策语义枚举 |
| `HookEventType` | `hook/HookEventType.java` | 12种事件类型枚举 |
| `HookChain` | `hook/HookChain.java` | **核心拦截编排引擎**：按优先级排序、串行执行、短路裁决 |
| `HookRegistry` | `hook/HookRegistry.java` | 配置加载（.jwcode/hooks.json）+ 热重载 + 事件索引 |
| `HookExecutor` | `hook/HookExecutor.java` | 执行器接口（Shell/HTTP/Prompt/Agent） |
| `HookContext` | `hook/HookContext.java` | 上下文数据模型（公共字段 + 事件专用字段） |
| `HookResult` | `hook/HookResult.java` | 决策结果模型（decision/reason/modifiedInput/askPayload） |
| `HookAuditLogger` | `hook/HookAuditLogger.java` | 审计日志（ConcurrentLinkedQueue + 统计摘要） |
| `ShellHookExecutor` | `hook/executor/ShellHookExecutor.java` | stdin JSON → 外部脚本 → stdout 决策 |
| `HttpHookExecutor` | `hook/executor/HttpHookExecutor.java` | POST JSON → REST API → JSON 决策 |
| `PromptHookExecutor` | `hook/executor/PromptHookExecutor.java` | 模板 → LLM Prompt → AI 动态风险评估 |
| `AgentHookExecutor` | `hook/executor/AgentHookExecutor.java` | 子Agent(只读)→深度调查→结构化决策 |
| `RollbackAction` | `hook/RollbackAction.java` | 回退策略（RETRY/SKIP/ROLLBACK_TO_CHECKPOINT/ABORT） |

**三大拦截点**：

```
ToolExecutor.execute()      → PRE_TOOL_USE / POST_TOOL_USE / POST_TOOL_USE_FAILURE
MainAgentStateMachine       → STATE_TRANSITION (TransitionGuard)
LocalAgentDispatcher        → SUBAGENT_START / SUBAGENT_STOP
```

**冲突裁决规则**：
```
当多个 Hook 返回不同决策时：
  1. DENY/VOID 最高优先 — 任一拒绝即拒绝
  2. MODIFY 链式传递 — 高优先级先修改，低优先级基于新输入
  3. ASK 覆盖 ALLOW — 只要有确认需求，最终就需要确认
  4. DEFER 聚合 — 等待所有审批完成
```

---

## 3. 错误隔离：三层摘要机制

| 层级 | 失败时返回内容 | 不返回内容 |
|------|---------------|-----------|
| **Tool Agent** | 错误类型 + 修复尝试次数 + 最终失败原因(1句话) | 原始命令、堆栈跟踪、工具详情 |
| **专业Agent** | 步骤失败摘要 + 建议的替代方案 | 单步重试过程、Tool Agent内部状态 |
| **主Agent** | 任务状态(Failed) + 业务级失败原因 + 是否需要人工介入 | 任何技术细节 |

### 3.1 错误摘要模型 (`ErrorSummary`)

```java
ErrorSummary {
    errorType: String,        // 错误类型 (TIMEOUT / PERMISSION_DENIED / NOT_FOUND / RATE_LIMIT / INVALID_INPUT / UNKNOWN)
    message: String,          // 一句话错误描述
    retryable: boolean,       // 是否可重试
    attemptCount: int,        // 已尝试次数
    maxRetries: int,          // 最大重试次数
    recoveryHint: String,     // 恢复建议（可选）
    criticalPath: boolean,    // 是否关键路径
    toBusinessSummary(): String  // 面向业务的一句话摘要
}
```

---

## 4. 状态追踪："任务-步骤"双层状态机

### 4.1 A2A标准任务生命周期

```
submitted → working → input-required → completed / failed / canceled
```

### 4.2 双层状态机

```
主Agent视角 (Task级):
  Task-001: working
    ├─ SubTask-A (CodeAgent): completed
    ├─ SubTask-B (DebugAgent): failed  ← 收到摘要后决策
    └─ SubTask-C (CodeAgent): pending

专业Agent视角 (Step级):
  SubTask-B: working
    ├─ Step-1: completed
    ├─ Step-2: failed (ToolAgent 3次修复均失败)
    └─ Step-3: pending  ← 根据失败摘要决定是否跳过/替代
```

### 4.3 步骤状态 (`StepStatus`)

```java
enum StepStatus {
    PENDING,      // 待执行
    WORKING,      // 执行中
    COMPLETED,    // 已完成
    FAILED,       // 失败
    SKIPPED,      // 跳过
    BLOCKED       // 阻塞
}
```

---

## 5. 重试策略：分层降级

### 5.1 三层重试

```
Tool Agent层 (自修复):
  执行命令 → 失败 → LLM分析错误 → 生成修复命令 → 重试
  ↓ 循环3次仍失败
  返回: {status: "FAILED", reason: "权限不足，无法访问xx资源", retryable: false}

专业Agent层 (步骤替代):
  收到Tool Agent失败 → 判断:
    - 如retryable=true: 换参数/换工具重试
    - 如retryable=false: 跳过该步骤 或 返回主Agent请求人工介入

主Agent层 (任务重排):
  收到子任务失败 → 判断:
    - 非关键路径: 标记部分完成，继续其他任务
    - 关键路径: 终止流程，返回用户失败原因
```

### 5.2 重试策略 (`RetryStrategy`)

| 策略 | 说明 | 适用场景 |
|------|------|---------|
| **指数退避** (默认) | 每次重试等待时间指数增长 | 通用场景 |
| **固定间隔** | 每次重试等待固定时间 | 资源竞争场景 |
| **立即重试** | 不等待直接重试 | 临时性错误（超时） |
| **不重试** (快速失败) | 立即返回失败 | 权限错误、无效输入 |
| **自适应** | 根据错误类型动态选择 | 综合场景 |

### 5.3 重试编排器 (`RetryOrchestrator`)

- 同步重试：`executeWithRetry(operation, policy, strategy)`
- 异步重试：`executeWithRetryAsync(operation, policy, strategy)`
- 步骤级决策：`decideStepAction(lifecycle, stepId, error, policy, strategy)`
- 任务级决策：`decideTaskAction(lifecycle, error)`

---

## 6. 主Agent的PDCA决策闭环

```
1. Plan: 分析用户请求，生成任务清单(Task List)
   └─→ 意图识别 → 复杂度评估 → 任务拆解 → 依赖分析

2. Do: 通过A2A并行/串行下发子任务
   └─→ Agent发现(Agent Card) → 任务提交 → 状态追踪

3. Check: 聚合各子任务状态(Artifacts)，检查是否满足用户原始意图
   └─→ 状态聚合 → 结果验证 → 意图对齐检查

4. Act:
   ├─→ 全部成功 → 生成最终回复
   ├─→ 部分失败 → 基于失败摘要重新规划（非简单重试），可能生成补偿任务
   └─→ 完全失败 → 返回结构化失败报告
```

---

## 7. 工作流标准

### 7.1 任务处理流程

```
1. 接收用户请求
   └─→ Orchestrator 启动

2. 意图识别
   └─→ 判断类型：开发 / 调试 / 重构 / 测试 / 文档 / 分析

3. 复杂度评估
   ├─→ 简单（1-2步）：直接指派1个子Agent
   ├─→ 中等（3-5步）：拆为2-3个子任务，可并行
   └─→ 复杂（>5步）：先派 ExploreAgent 调研，再制定完整计划

4. 任务拆解（结构化）
   每个子任务必须包含：
   - task_id: 唯一标识
   - task_type: code / review / test / doc / explore / debug / architect
   - description: 详细描述（做什么、为什么、边界）
   - acceptance_criteria: 验收标准
   - dependencies: 依赖的其他任务ID
   - context_scope: 需提供的上下文范围
   - estimated_effort: low / medium / high

5. Agent调度
   └─→ 用 AgentTool 创建/分配/执行子Agent

6. 并行编排
   ├─→ 无依赖的任务 → 并行执行
   └─→ 有依赖的任务 → 拓扑排序后串行执行

7. 结果验收
   └─→ 检查每个子Agent输出是否满足验收标准

8. 整合输出
   └─→ 合并结果，生成给用户的一致、完整回复
```

### 7.2 典型场景示例

#### 场景A：开发新功能
```yaml
用户: "给parser模块加JSON导出功能"

Orchestrator:
  # Plan 模式: AI 生成计划 → TaskAgent 解析为结构化任务
  0. AI 生成执行计划 (Plan Mode)
  0a. 用户确认 → processConfirmedPlan()
  0b. TaskAgent 解析AI回复 → StructuredTask列表
      - Phase 1: 调研 (Explorer, 串行)
      - Phase 2: 设计 (Architect, 串行)
      - Phase 3: 实现 (Coder+Tester, 并发)
      - Phase 4: 审查 (Reviewer, 串行)
      - Phase 5: GAN迭代 (Generator⇄Evaluator, 串行, 最大3轮)
  0c. WebSocket 广播 → 前端"结构化"视图展示
  # Act 模式: 使用 TaskExecutionAgent 逐步执行
  1. TaskExecutionAgent 按阶段顺序执行
  2. Phase 3 并发: Coder + Tester 线程池并行
  3. 等 3 完成后 → Reviewer 审查
  4. Phase 5: GAN迭代循环:
     - Generator 根据反馈修改
     - Evaluator 4维加权评分
     - 未通过则注入反馈继续循环
     - 通过或达最大轮数后终止
  5. 每个任务完成后 → MemoryAgent 自动记忆
  6. 整合所有结果，汇报给用户
```

#### 场景F：跨任务记忆
```yaml
用户: "分析所有Java文件的依赖关系"

Orchestrator:
  # 首次执行
  1. 派 Explorer 分析整个项目结构
  2. 任务完成后 → MemoryAgent 记录:
     - 项目类型: maven
     - 语言: Java, TypeScript
     - 关键模块: jwcode-core, jwcode-web
     - 洞察: "所有模块通过 pom.xml 父子关系管理"
  3. regeneratePlanContext() → .jwcode/memory/plan_context.md

  # 下一次任务
  用户: "重构 jwcode-core 的 agent 包"
  4. Plan 模式 → MemoryAgent 自动注入:
     "已知模块结构、依赖关系、编码规范..."
  5. AI 基于记忆做出更精准的任务规划
```

#### 场景B：修复Bug
```yaml
用户: "修复登录模块的NPE问题"

Orchestrator:
  1. 派 DebugAgent 复现问题、定位根因
  2. 等 1 完成后，派 CoderAgent 实施修复
  3. 等 2 完成后，并行：
     - 派 Tester 编写回归测试并验证
     - 派 Reviewer 审查修复方案
  4. 整合结果，汇报修复详情
```

#### 场景C：重构代码
```yaml
用户: "重构auth模块，提取公共逻辑"

Orchestrator:
  1. 派 ExploreAgent 分析 auth 模块现状
  2. 派 ArchitectAgent 制定重构计划
  3. 等 1、2 完成后，派 CoderAgent 按步骤执行重构
  4. 等 3 完成后，并行：
     - 派 Tester 运行全量测试验证
     - 派 Reviewer 审查重构质量
  5. 整合结果
```

#### 场景D：代码审查
```yaml
用户: "审查这个PR的代码"

Orchestrator:
  1. 派 ExploreAgent 获取PR变更范围和相关上下文
  2. 等 1 完成后，派 Reviewer 执行详细审查
  3. 如 Reviewer 发现严重问题，派 DebugAgent 验证
  4. 整合审查报告
```

#### 场景E：编写文档
```yaml
用户: "更新API文档，反映v2接口变化"

Orchestrator:
  1. 派 ExploreAgent 扫描v2接口定义
  2. 等 1 完成后，派 Documenter 编写文档
  3. 等 2 完成后，派 Reviewer 检查文档准确性
  4. 整合输出
```

---

## 8. 上下文传递规范

### 8.1 最小必要原则
Orchestrator 只向子Agent传递完成任务所需的最小上下文：
- **代码任务**：相关文件路径 + 接口契约 + 约束条件
- **测试任务**：被测代码路径 + 已有测试参考 + 覆盖率要求
- **文档任务**：代码变更摘要 + 目标读者 + 格式规范
- **调研任务**：调研范围 + 关注维度 + 输出格式

### 8.2 引用而非复制
- 大文件传路径，让子Agent自己读取
- 代码片段只传关键部分（<50行）
- 禁止把整个代码库上下文塞给子Agent

### 8.3 成果传递
- 上游任务输出作为下游任务的 `context` 注入
- 使用 SharedContextBus 共享中间成果

### 8.4 上下文压缩
- Token使用率超过80%时自动触发 AICL Priority-LRU 淘汰
- 子任务完成时自动清理上下文
- 支持手动 `/compact` 命令触发激进压缩
- 每轮对话结束自动触发 TTL 衰减和代际检查

### 8.5 AICL 上下文协议（v1.1）
- 所有上下文块统一包装为 `<ctx:block>` 元素，携带 `priority`、`state`、`ttl` 等生命周期属性
- Assembler 组装时自动执行分级淘汰，确保 Token 预算内信息密度最大化
- AI 通过注入的 AICL 解析规则识别优先级与状态，调整阅读策略
- 完整规范见 `docs/AICL_SPEC.md`

---

## 9. 质量与约束

### 9.1 Orchestrator 红线（禁止行为）
- ❌ 直接调用 `FileReadTool` / `FileWriteTool` / `FileEditTool`
- ❌ 直接调用 `BashTool` / `PowerShellTool` / `REPLTool`
- ❌ 直接调用 `GlobTool` / `GrepTool` 搜索代码库
- ❌ 编写任何代码、修改任何配置
- ❌ 越过 `AgentTool` 直接"自己动手"

### 9.2 子Agent 约束
- **Reviewer / Explorer**：只读模式，禁止修改任何文件
- **Coder / Tester / Doc / Architect**：可读写文件，但需在职责范围内
- **所有子Agent**：不可再创建子Agent（防止递归），AgentTool 在子Agent执行时已被排除

### 9.3 质量检查清单
Orchestrator 在整合输出前必须确认：
- [ ] 所有子任务已完成（或已记录失败原因）
- [ ] 代码类任务有对应的 review 或 test 结果
- [ ] 文档类任务与代码变更保持一致
- [ ] 无遗漏的边界情况
- [ ] 输出格式统一、无冲突

---

## 10. 故障处理

### 10.1 子Agent 失败
```
子Agent失败
    ↓
Orchestrator判断失败类型
    ├── 输入不清 → 重新分解任务，补充上下文
    ├── 技术错误 → 换子Agent重试 / 降级处理（如Coder失败换Default）
    └── 无法解决 → 向用户汇报失败原因和建议
```

### 10.2 过度拆解保护
- 简单任务（如改一个变量名）直接指派给单个子Agent，不要拆成多步
- 预估子任务执行时间 < 30秒的，合并到同个子Agent中

### 10.3 循环依赖检测
- 任务依赖图必须是 DAG（有向无环图）
- Orchestrator 在拆解时检测并打破循环

---

## 11. 配置

### 11.1 团队配置
团队配置位于 `.jwcode/team_members.json`，定义了：
- 团队组成和角色
- Agent职责和能力
- 工作流规则（并行度、审批策略、升级策略）

### 11.2 Agent注册
Agent注册位于 `AgentRegistry.java`，所有Agent在系统启动时自动注册。

**内部服务Agent**（TaskAgent / TaskExecutionAgent / MemoryAgent）由 `EnhancedOrchestratorAgent` 
直接实例化和调用，不需要通过 AgentRegistry 注册，也不暴露给外部 A2A 协议。
MemoryAgent 按工作目录实例化（每个项目一个实例），数据存储在 `.jwcode/memory/` 下。

---

## 12. 扩展指南

### 12.1 添加新子Agent
1. 实现 `Agent` 接口
2. 定义系统提示词（明确职责边界）
3. 配置可用工具白名单
4. 在 `AgentRegistry.registerDefaultAgents()` 中注册
5. 更新 `.jwcode/team_members.json`

### 12.2 修改 Orchestrator 策略
Orchestrator 的拆解和调度逻辑可通过以下方式扩展：
- 修改 `TaskPlanner` 中的规则模板
- 调整 `SubTaskSplitter` 的启发式策略
- 自定义 `ParallelAgentExecutor` 的依赖图算法

---

## 13. 文档索引

| 文档 | 说明 |
|------|------|
| `AGENTS.md` | AI 协作规范 + Agent 架构 (本文档) |
| `README.md` | 项目概述 (自动生成) |
| `docs/ARCHITECTURE_V2.md` | 架构演进记录 (v1→v2→v3→v3.1) |
| `docs/JWCODE_PRODUCT_DESIGN.md` | 产品设计文档 |
| `docs/AICL_SPEC.md` | AICL 上下文协议规范 |
| `docs/CONFIG_GUIDE.md` | 配置指南 |
| `docs/HOOK_SYSTEM_GUIDE.md` | Hook 生命周期拦截体系 |
| `docs/developer-guide.md` | 开发者文档 |
| `docs/agent-bridge-guide.md` | Agent 桥接模式指南 |
## 14. 相关文件

| 文件 | 说明 |
|------|------|
| `jwcode-core/src/main/java/com/jwcode/core/agent/OrchestratorAgent.java` | 主Agent实现 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/EnhancedOrchestratorAgent.java` | 增强型主Agent（PDCA循环，集成TaskAgent+TaskExecutionAgent） |
| `jwcode-core/src/main/java/com/jwcode/core/agent/CoderAgent.java` | 代码专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/DebugAgent.java` | 调试专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/ReviewerAgent.java` | 审查专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/TestAgent.java` | 测试专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/DocAgent.java` | 文档专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/ExploreAgent.java` | 探索专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/ArchitectAgent.java` | 架构专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/TaskAgent.java` | 任务结构化Agent（AI回复→结构化任务列表） |
| `jwcode-core/src/main/java/com/jwcode/core/agent/TaskExecutionAgent.java` | 任务执行Agent（并发/串行逐步调度子Agent） |
| `jwcode-core/src/main/java/com/jwcode/core/agent/MemoryAgent.java` | 工作目录记忆Agent（项目模式/洞察/偏好主动记忆） |
| `jwcode-core/src/main/java/com/jwcode/core/agent/WorkspaceMemoryStore.java` | 工作目录记忆持久化存储（.jwcode/memory/） |
| `jwcode-core/src/main/java/com/jwcode/core/agent/CompactorAgent.java` | 上下文压缩专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/CompactorTrigger.java` | 压缩触发策略 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/EvaluatorAgent.java` | GAN式评估专家（4维加权评分+硬门槛否决） |
| `jwcode-core/src/main/java/com/jwcode/core/service/StructuredCompactionStrategy.java` | 强制XML结构化压缩策略（优先级截断） |
| `jwcode-core/src/main/java/com/jwcode/core/service/IterativeSprintOrchestrator.java` | GAN迭代循环仲裁器（Generator⇄Evaluator反馈闭环） |
| `jwcode-core/src/main/java/com/jwcode/core/service/ContextResetManager.java` | 上下文重置管理器（进程级重启+结构化交接） |
| `jwcode-core/src/main/java/com/jwcode/core/aicl/BlockPriority.java` | AICL 6级优先级枚举 |
| `jwcode-core/src/main/java/com/jwcode/core/aicl/BlockLifecycle.java` | AICL 6状态生命周期枚举 |
| `jwcode-core/src/main/java/com/jwcode/core/aicl/ContextBlock.java` | AICL 上下文块模型 |
| `jwcode-core/src/main/java/com/jwcode/core/aicl/ContextControl.java` | AICL 控制层（预算+策略） |
| `jwcode-core/src/main/java/com/jwcode/core/aicl/ContextAssembler.java` | AICL Priority-LRU 淘汰引擎 |
| `jwcode-core/src/main/java/com/jwcode/core/aicl/AICLSerializer.java` | AICL XML 序列化器 |
| `jwcode-core/src/main/java/com/jwcode/core/aicl/AICLDeserializer.java` | AICL XML 反序列化器 |
| `jwcode-core/src/main/java/com/jwcode/core/aicl/AICLContext.java` | AICL 完整上下文容器 |
| `jwcode-core/src/main/java/com/jwcode/core/aicl/AICLPromptBuilder.java` | AICL AI解析规则Prompt生成器 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/AgentRegistry.java` | Agent注册表 |
| `jwcode-core/src/main/java/com/jwcode/core/model/StructuredTask.java` | 结构化任务模型（执行模式+阶段+并发组+contractId） |
| `jwcode-core/src/main/java/com/jwcode/core/model/SprintContract.java` | Sprint合同模型（DRAFT→NEGOTIATING→SIGNED→EXECUTING） |
| `jwcode-core/src/main/java/com/jwcode/core/model/EvaluationScore.java` | 4维加权评分模型（产品深度/功能/视觉/代码质量） |
| `jwcode-core/src/main/java/com/jwcode/core/model/EvaluationReport.java` | 评估报告模型（含verdict+门槛检查+反馈摘要） |
| `jwcode-core/src/main/java/com/jwcode/core/model/HandoffArtifact.java` | 交接文档模型（Context Reset协议核心） |
| `jwcode-core/src/main/java/com/jwcode/core/tool/ToolAgent.java` | 工具执行Agent（第3层） |
| `jwcode-core/src/main/java/com/jwcode/core/tool/ToolAgentResult.java` | 工具执行结果 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/model/ErrorSummary.java` | 错误摘要模型 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/model/StepStatus.java` | 步骤状态枚举 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/model/TaskLifecycle.java` | 任务生命周期 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/model/RetryPolicy.java` | 重试策略配置 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/retry/RetryStrategy.java` | 重试策略算法 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/retry/RetryOrchestrator.java` | 分层重试编排器 |
| `jwcode-core/src/main/java/com/jwcode/core/hook/HookDecision.java` | Hook决策语义枚举（v2.1） |
| `jwcode-core/src/main/java/com/jwcode/core/hook/HookEventType.java` | Hook事件类型枚举（v2.1） |
| `jwcode-core/src/main/java/com/jwcode/core/hook/HookChain.java` | Hook拦截链编排引擎（v2.1） |
| `jwcode-core/src/main/java/com/jwcode/core/hook/HookRegistry.java` | Hook配置加载与热重载（v2.1） |
| `jwcode-core/src/main/java/com/jwcode/core/hook/HookExecutor.java` | Hook执行器接口（v2.1） |
| `jwcode-core/src/main/java/com/jwcode/core/hook/executor/ShellHookExecutor.java` | Shell脚本Hook（v2.1） |
| `jwcode-core/src/main/java/com/jwcode/core/hook/executor/HttpHookExecutor.java` | HTTP端点Hook（v2.1） |
| `jwcode-core/src/main/java/com/jwcode/core/hook/executor/PromptHookExecutor.java` | LLM Prompt Hook（v2.1） |
| `jwcode-core/src/main/java/com/jwcode/core/hook/executor/AgentHookExecutor.java` | Agent调查Hook（v2.1） |
| `jwcode-core/src/main/java/com/jwcode/core/hook/HookAuditLogger.java` | Hook审计日志（v2.1） |
| `jwcode-core/src/main/java/com/jwcode/core/hook/RollbackAction.java` | 回退策略枚举（v2.1） |
| `jwcode-core/src/main/java/com/jwcode/core/planner/TaskPlanner.java` | 任务规划器 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/SubTaskSplitter.java` | 子任务拆分器 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/parallel/ParallelAgentExecutor.java` | 并行执行器 |
| `jwcode-core/src/main/java/com/jwcode/core/planner/checkpoint/CheckpointManager.java` | 检查点管理器 |
| `jwcode-core/src/main/java/com/jwcode/core/planner/checkpoint/SharedContextBus.java` | 共享上下文总线 |
| `jwcode-web/src/components/Plan/StructuredTaskView.tsx` | 前端结构化任务视图组件 |
| `jwcode-web/src/stores/planStore.ts` | 前端 Plan 状态管理（含结构化任务） |
| `jwcode-core/src/test/java/com/jwcode/core/a2a/FourLayerIntegrationTest.java` | 四层架构集成测试（25个测试用例） |
| `.jwcode/team_members.json` | 团队配置 |

---

## 15. Harness Engineering（v3.0 新增 / 横向基础设施）

Harness Engineering 是 Agent 的"缰绳"体系，确保 AI 从"野马"变为"千里马"。
四层递进：L1 安全 → L2 成本 → L3 质量 → L4 可观测。

### L1: 沙箱安全

| 组件 | 文件 | 职责 |
|------|------|------|
| `DockerSandboxExecutor` | `tool/shell/DockerSandboxExecutor.java` | Docker 容器隔离执行（`--network=none --memory=512m :ro`） |
| `WorkspaceGuard` | `tool/WorkspaceGuard.java` | 文件系统边界校验（TOCTOU 防护） |
| `HookAuditLogger` | `hook/HookAuditLogger.java` | 操作审计日志 |
| `PermissionManager` | `permission/PermissionManager.java` | 五级权限控制 |

### L2: 成本与路由

| 组件 | 文件 | 职责 |
|------|------|------|
| `ModelRouter` | `llm/ModelRouter.java` | 按任务特征动态选择模型（对话→轻量 / 推理→旗舰） |
| `CostTrackerService` | `service/CostTrackerService.java` | 实时成本追踪（已接入 LLMQueryEngine） |
| `TokenBudget` | `llm/TokenBudget.java` | Token 预算管理 |
| `ContextWindowManager.zonePriority()` | `service/ContextWindowManager.java` | 五级 ZONE 截断优先级 |

### L3: 质量与记忆

| 组件 | 文件 | 职责 |
|------|------|------|
| `RecoveryExecutor` (AiRepair) | `resilience/RecoveryExecutor.java` | LLM 分析错误→生成修复方案→重试 |
| `RecoveryProtocol.AiRepair` | `resilience/RecoveryProtocol.java` | 三阶段恢复协议（AutoRetry→AiRepair→HumanEscalation） |
| `SimpleCompactionStrategy` (五级分层) | `service/SimpleCompactionStrategy.java` | 错误/文件修改/读取/命令 分层保留 |
| `WorkspaceMemoryStore.semanticSearch()` | `agent/WorkspaceMemoryStore.java` | Embedding 语义检索 + 关键词降级 |
| `LLMService.embed()` | `llm/LLMService.java` | 文本嵌入接口 |
| Prompt Caching | `llm/LLMMessage.java` (CacheControl) | `cache_control: ephemeral` 减少 ~70% 重复 token |

### L4: 可观测

| 组件 | 文件 | 职责 |
|------|------|------|
| `ObservationPipeline` | `observability/ObservationPipeline.java` | 事件管道（12 种事件类型） |
| `AnalyticsObserver` | `observability/AnalyticsObserver.java` | 聚合统计 |

### 安全执行管道

```
ToolExecutor.execute()
  → PermissionManager.isDestructive()     // 权限
  → WorkspaceGuard.validatePath()         // 路径
  → HookChain.execute(PRE_TOOL_USE)       // Hook
  → DockerSandboxExecutor.execute()       // 沙箱
  → HookChain.execute(POST_TOOL_USE)      // 审计
  → HookAuditLogger.record()              // 日志
```
