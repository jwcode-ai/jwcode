# JWCode 分层多Agent架构规范

> 本文档定义 JWCode 项目中所有AI协作的架构规范。
> 所有场景必须严格执行：**主Agent拆解调度，子Agent执行工作**。

## 1. 架构总览

```
┌─────────────────────────────────────────────────────────────┐
│                     用户（User）                              │
└──────────────────────┬──────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│  🎛️ Orchestrator Agent（主Agent / 指挥家）                    │
│  ─────────────────────────────────────────────────────────  │
│  职责：意图识别 → 任务拆解 → Agent调度 → 结果验收 → 整合输出   │
│  禁止：直接读写文件、直接修改代码、直接执行命令               │
│  工具：AgentTool / SmartAnalyzeTool / AskUserQuestionTool   │
└──────────────────────┬──────────────────────────────────────┘
                       │ 派发任务
          ┌────────────┼────────────┐
          ▼            ▼            ▼
    ┌─────────┐  ┌─────────┐  ┌─────────┐
    │  Coder  │  │  Debug  │  │ Reviewer│
    └─────────┘  └─────────┘  └─────────┘
    ┌─────────┐  ┌─────────┐  ┌─────────┐
    │  Test   │  │   Doc   │  │ Explore │
    └─────────┘  └─────────┘  └─────────┘
    ┌─────────┐  ┌─────────┐
    │Architect│  │ Default │
    └─────────┘  └─────────┘
```

## 2. 角色定义

### 2.1 Orchestrator（主Agent）

| 维度 | 说明 |
|------|------|
| **定位** | 唯一用户入口，任务指挥家 |
| **职责** | 意图识别、复杂度评估、任务拆解、Agent选型、并行编排、结果验收、整合输出 |
| **可用工具** | `AgentTool`（核心）、`SmartAnalyzeTool`（宏观分析）、`AskUserQuestionTool`（需求澄清） |
| **禁止工具** | `FileReadTool`、`FileWriteTool`、`FileEditTool`、`BashTool`、`PowerShellTool`、`GlobTool`、`GrepTool` 等所有业务执行工具 |
| **关键原则** | 绝不自己动手。哪怕是一个简单的文件读取，也必须派给子Agent。 |

### 2.2 子Agent（Worker）

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

## 3. 工作流标准

### 3.1 任务处理流程

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

### 3.2 典型场景示例

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

## 4. 上下文传递规范

### 4.1 最小必要原则
Orchestrator 只向子Agent传递完成任务所需的最小上下文：
- **代码任务**：相关文件路径 + 接口契约 + 约束条件
- **测试任务**：被测代码路径 + 已有测试参考 + 覆盖率要求
- **文档任务**：代码变更摘要 + 目标读者 + 格式规范
- **调研任务**：调研范围 + 关注维度 + 输出格式

### 4.2 引用而非复制
- 大文件传路径，让子Agent自己读取
- 代码片段只传关键部分（<50行）
- 禁止把整个代码库上下文塞给子Agent

### 4.3 成果传递
- 上游任务输出作为下游任务的 `context` 注入
- 使用 SharedContextBus 共享中间成果

## 5. 质量与约束

### 5.1 Orchestrator 红线（禁止行为）
- ❌ 直接调用 `FileReadTool` / `FileWriteTool` / `FileEditTool`
- ❌ 直接调用 `BashTool` / `PowerShellTool` / `REPLTool`
- ❌ 直接调用 `GlobTool` / `GrepTool` 搜索代码库
- ❌ 编写任何代码、修改任何配置
- ❌ 越过 `AgentTool` 直接"自己动手"

### 5.2 子Agent 约束
- **Reviewer / Explorer**：只读模式，禁止修改任何文件
- **Coder / Tester / Doc / Architect**：可读写文件，但需在职责范围内
- **所有子Agent**：不可再创建子Agent（防止递归），AgentTool 在子Agent执行时已被排除

### 5.3 质量检查清单
Orchestrator 在整合输出前必须确认：
- [ ] 所有子任务已完成（或已记录失败原因）
- [ ] 代码类任务有对应的 review 或 test 结果
- [ ] 文档类任务与代码变更保持一致
- [ ] 无遗漏的边界情况
- [ ] 输出格式统一、无冲突

## 6. 故障处理

### 6.1 子Agent 失败
```
子Agent失败
    ↓
Orchestrator判断失败类型
    ├── 输入不清 → 重新分解任务，补充上下文
    ├── 技术错误 → 换子Agent重试 / 降级处理（如Coder失败换Default）
    └── 无法解决 → 向用户汇报失败原因和建议
```

### 6.2 过度拆解保护
- 简单任务（如改一个变量名）直接指派给单个子Agent，不要拆成多步
- 预估子任务执行时间 < 30秒的，合并到同个子Agent中

### 6.3 循环依赖检测
- 任务依赖图必须是 DAG（有向无环图）
- Orchestrator 在拆解时检测并打破循环

## 7. 配置

### 7.1 团队配置
团队配置位于 `.jwcode/team_members.json`，定义了：
- 团队组成和角色
- Agent职责和能力
- 工作流规则（并行度、审批策略、升级策略）

### 7.2 Agent注册
Agent注册位于 `AgentRegistry.java`，所有Agent在系统启动时自动注册。

## 8. 扩展指南

### 8.1 添加新子Agent
1. 实现 `Agent` 接口
2. 定义系统提示词（明确职责边界）
3. 配置可用工具白名单
4. 在 `AgentRegistry.registerDefaultAgents()` 中注册
5. 更新 `.jwcode/team_members.json`

### 8.2 修改 Orchestrator 策略
Orchestrator 的拆解和调度逻辑可通过以下方式扩展：
- 修改 `TaskPlanner` 中的规则模板
- 调整 `SubTaskSplitter` 的启发式策略
- 自定义 `ParallelAgentExecutor` 的依赖图算法

## 9. 相关文件

| 文件 | 说明 |
|------|------|
| `jwcode-core/src/main/java/com/jwcode/core/agent/OrchestratorAgent.java` | 主Agent实现 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/CoderAgent.java` | 代码专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/DebugAgent.java` | 调试专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/ReviewerAgent.java` | 审查专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/TestAgent.java` | 测试专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/DocAgent.java` | 文档专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/ExploreAgent.java` | 探索专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/ArchitectAgent.java` | 架构专家 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/AgentRegistry.java` | Agent注册表 |
| `jwcode-core/src/main/java/com/jwcode/core/planner/TaskPlanner.java` | 任务规划器 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/SubTaskSplitter.java` | 子任务拆分器 |
| `jwcode-core/src/main/java/com/jwcode/core/agent/parallel/ParallelAgentExecutor.java` | 并行执行器 |
| `.jwcode/team_members.json` | 团队配置 |
