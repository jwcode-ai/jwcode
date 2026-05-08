# JWCode 分层多Agent架构规范

> 本文档定义 JWCode 项目中所有AI协作的架构规范。
> 所有场景必须严格执行：**主Agent拆解调度，子Agent执行工作**。

---

## 1. 四层架构总览

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
│  策略：SMART / AGGRESSIVE / MINIMAL                          │
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
| **Tester** | worker | 测试用例设计、测试编写、执行 | 可运行编译和测试命令 |
| **Documenter** | worker | 文档编写、README、API文档 | 读写文件，不执行命令 |
| **Explorer** | worker | 代码库调研、结构分析、技术债务 | **只读模式**，纯调研 |
| **Architect** | worker | 架构设计、接口定义、技术选型 | 输出设计文档和代码骨架 |
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
| **策略** | SMART(智能压缩, 保留尾部8条+摘要) / AGGRESSIVE(激进压缩, 保留尾部4条+摘要) / MINIMAL(最小压缩, 仅移除噪声) |
| **触发方式** | Token水位线自动触发 / 用户手动触发(/compact) / Agent主动请求 / 检查点前 / 子任务完成时 / 会话超限 |

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
  1. 派 ExploreAgent 分析 parser 现有架构
  2. 派 ArchitectAgent 设计 JSON 导出接口
  3. 等 1、2 完成后，派 CoderAgent 实现核心逻辑
  4. 等 3 完成后，并行：
     - 派 Tester 编写并执行单元测试
     - 派 Reviewer 审查代码质量
  5. 等 4 完成后，派 Documenter 更新README和API文档
  6. 整合所有结果，汇报给用户
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
- Token使用率超过70%时自动触发压缩
- 子任务完成时自动清理上下文
- 支持手动 `/compact` 命令触发激进压缩

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

## 13. 相关文件

| 文件 | 说明 |
|------|------|
| `jwcode-core/src/main/java/com/jwcode/core/agent/OrchestratorAgent.java` | 主Agent实现 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/EnhancedOrchestratorAgent.java` | 增强型主Agent（PDCA循环） |
| `jwcode-core/src/main/java/com/jwcode/core/agent/CoderAgent.java` | 代码专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/DebugAgent.java` | 调试专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/ReviewerAgent.java` | 审查专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/TestAgent.java` | 测试专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/DocAgent.java` | 文档专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/ExploreAgent.java` | 探索专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/ArchitectAgent.java` | 架构专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/CompactorAgent.java` | 上下文压缩专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/CompactorTrigger.java` | 压缩触发策略 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/AgentRegistry.java` | Agent注册表 |
| `jwcode-core/src/main/java/com/jwcode/core/tool/ToolAgent.java` | 工具执行Agent（第3层） |
| `jwcode-core/src/main/java/com/jwcode/core/tool/ToolAgentResult.java` | 工具执行结果 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/model/ErrorSummary.java` | 错误摘要模型 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/model/StepStatus.java` | 步骤状态枚举 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/model/TaskLifecycle.java` | 任务生命周期 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/model/RetryPolicy.java` | 重试策略配置 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/retry/RetryStrategy.java` | 重试策略算法 |
| `jwcode-core/src/main/java/com/jwcode/core/a2a/retry/RetryOrchestrator.java` | 分层重试编排器 |
| `jwcode-core/src/main/java/com/jwcode/core/planner/TaskPlanner.java` | 任务规划器 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/SubTaskSplitter.java` | 子任务拆分器 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/parallel/ParallelAgentExecutor.java` | 并行执行器 |
| `jwcode-core/src/main/java/com/jwcode/core/planner/checkpoint/CheckpointManager.java` | 检查点管理器 |
| `jwcode-core/src/main/java/com/jwcode/core/planner/checkpoint/SharedContextBus.java` | 共享上下文总线 |
| `jwcode-core/src/test/java/com/jwcode/core/a2a/FourLayerIntegrationTest.java` | 四层架构集成测试（25个测试用例） |
| `.jwcode/team_members.json` | 团队配置 |
