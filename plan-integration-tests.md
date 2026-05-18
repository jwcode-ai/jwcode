# 缺失集成测试 & 任务完整性测试 — 实施计划

## 当前项目测试概览

| 指标 | 数值 |
|------|------|
| Main 源包总数 | **43 个** |
| 有测试文件的包 | **14 个**（35 个测试文件） |
| **完全零测试的包** | **29 个（67%）** |
| 核心端到端集成测试 | **0 个** |
| Plan/Act 模式测试 | **0 个** |
| 状态机完整性测试 | **0 个** |
| 现有测试主力 | `tool` 包（11 个）+ `core/` 根（3 个） |

---

## 阶段一：P0 — 核心基础设施集成测试（5 个任务）

### 任务 1.1：ToolExecutionStateMachine 状态机完整性测试
**目录**: `src/test/java/com/jwcode/core/tool/execution/`
**新建文件**: `ToolExecutionStateMachineCompleteTransitionTest.java`

#### 测试场景
| 测试方法 | 描述 | 验证点 |
|---------|------|-------|
| `testInitialStateIsParse()` | 初始状态为 PARSE | `getCurrentState() == PARSE` |
| `testParseSuccessToValidate()` | PARSE 正确JSON → VALIDATE | 转移成功 |
| `testParseFailureToCorrection()` | PARSE JSON非法 → CORRECTION | 错误消息含"JSON 解析失败" |
| `testValidateSuccessToExecute()` | VALIDATE 字段齐全 → EXECUTE | 转移成功 |
| `testValidateFieldMissingToCorrection()` | VALIDATE 字段缺失 → CORRECTION | 返回正例模板 |
| `testExecuteTimeoutToCorrection()` | EXECUTE 超时 → CORRECTION | 返回错误摘要 |
| `testCorrectionFirstTimeRetry()` | 第1次CORRECTION → 回到PARSE | `canCorrect() == true`, 尝试次数=1 |
| `testCorrectionSecondTimeRetry()` | 第2次CORRECTION → 回到PARSE | `canCorrect() == true`, 尝试次数=2 |
| `testCorrectionExceededToFailed()` | 第3次CORRECTION → FAILED | `canCorrect() == false`, `getCurrentState() == FAILED` |
| `testReportSuccessToDone()` | REPORT 成功 → DONE | 触发 Consumer |
| `testFullHappyPathFlow()` | **完整正向流**: PARSE→VALIDATE→EXECUTE→REPORT→DONE | 全路径正确 |
| `testFullErrorRecoveryFlow()` | **完整纠错流**: PARSE→CORRECTION→PARSE→VALIDATE→...→DONE | 纠错后恢复 |

#### Mock 策略
- `Consumer<ToolProgress>` — mock 回调验证 REPORT→DONE 结果写入
- 模拟 `ErrorType` 枚举：`INVALID_JSON`、`FIELD_MISSING`、`TIMEOUT`、`NON_ZERO_EXIT`

---

### 任务 1.2：PlanModeManager 模式切换 + 工具权限隔离测试
**目录**: `src/test/java/com/jwcode/core/plan/`
**新建文件**: `PlanModeManagerIntegrationTest.java`

#### 测试场景
| 测试方法 | 描述 | 验证点 |
|---------|------|-------|
| `testInitialModeIsNormal()` | 初始化模式为 NORMAL | `getCurrentMode() == NORMAL` |
| `testSwitchToPlanMode()` | NORMAL → PLAN | 模式切换成功，监听器被调用 |
| `testSwitchToActMode()` | NORMAL → ACT | 模式切换成功 |
| `testSwitchBackToNormal()` | PLAN → NORMAL | 可以切回 |
| `testPlanModeBlocksWriteTools()` | PLAN 模式下写工具被阻止 | `isToolAllowed(FileWriteTool)` 返回 false |
| `testPlanModeAllowsReadTools()` | PLAN 模式下读工具被允许 | `isToolAllowed(GlobTool)` 返回 true |
| `testPlanModeBlocksAllWriteTools()` | **批量验证全部写工具被阻止** | Bash、PowerShell、REPL、FileWrite、FileEdit、NotebookEdit、Git、RemoteTrigger、ScheduleCron、SendMessage、TeamCreate、TeamDelete、McpAuth — 全部返回 false |
| `testActModeAllowsAllTools()` | ACT 模式下所有工具可用 | 读/写工具均返回 true |
| `testNormalModeAllowsAllTools()` | NORMAL 模式下所有工具可用 | 读/写工具均返回 true |
| `testModePersistenceToFile()` | 模式持久化到文件 | 文件内容包含模式状态 |
| `testModeRestoreFromFile()` | 从文件恢复模式 | 重启后模式保持 |
| `testModeChangeListenerNotification()` | 模式切换通知监听器 | 监听器被准确调用 |
| `testModeChangeHistoryRecorded()` | 模式切换历史记录 | history.size() 正确 |

#### Mock/基础设施
- 使用 `@TempDir` 创建临时目录存储 `.jwcoplan` 文件
- 测试后 cleanup

---

### 任务 1.3：ToolExecutionService 端到端链路集成测试
**目录**: `src/test/java/com/jwcode/core/service/`
**新建文件**: `ToolExecutionServiceIntegrationTest.java`

#### 测试场景
| 测试方法 | 描述 | 验证点 |
|---------|------|-------|
| `testToolRegistration()` | 工具注册到服务 | `getTool(name)` 返回正确定义 |
| `testToolUnregistration()` | 工具反注册 | 注册列表减少 |
| `testExecuteToolSuccess()` | 执行工具成功 | 返回结果正常 |
| `testExecuteToolWithPermissions()` | 带权限校验的执行 | PermissionManager 被调用 |
| `testExecuteToolLifecycle()` | **完整生命周期**: 注册→执行→完成→历史记录 | history 中有记录 |
| `testConcurrentToolExecution()` | 并发执行多个工具 | 线程安全，结果正确 |
| `testToolExecutionTimeout()` | 工具执行超时处理 | 超时异常正确抛出 |
| `testToolExecutionHistory()` | 执行历史查询 | 历史记录完整 |
| `testMultipleToolChainedExecution()` | **链式调用**: 工具A结果作为工具B参数 | 链式调用结果正确 |
| `testToolExecutionWithContext()` | 带上下文执行 | 上下文正确传递 |

#### Mock 策略
- `ToolDefinition` — 自定义 Mock 工具
- `PermissionManager` — mock 权限校验
- `CompletableFuture` — 模拟异步执行

---

### 任务 1.4：工具注册 + 完整性验证测试（增强已有 IntegrationTest）
**目录**: `src/test/java/com/jwcode/core/`
**编辑文件**: `IntegrationTest.java`

#### 新增测试
| 测试方法 | 描述 | 验证点 |
|---------|------|-------|
| `testAllToolsHaveUniqueNames()` | 所有工具名唯一 | 无重复 |
| `testAllToolsHaveDescription()` | 所有工具有描述 | description 非空 |
| `testAllToolsHavePrompt()` | 所有工具有 Prompt | prompt 非空 |
| `testToolCategoryAssignment()` | 工具分类正确 | 每个工具都有合法 Category |
| `testSideEffectAnnotation()` | SideEffect 注解正确 | 写操作工具标记正确 |
| `testToolCountIsComplete()` | Phase 1-8 工具总数完整 | 总数 >= 70 |
| `testPhase1To8AllPhasesCovered()` | **验证全部 Phase 都有对应工具** | 每个 Phase 至少 5 个工具 |

---

### 任务 1.5：PermissionManager + 工具执行集成测试
**目录**: `src/test/java/com/jwcode/core/permission/`
**新建文件**: `PermissionManagerIntegrationTest.java`

#### 测试场景
| 测试方法 | 描述 | 验证点 |
|---------|------|-------|
| `testPermissionCheckBeforeExecute()` | 权限校验在工具执行前触发 | 拦截违规调用 |
| `testPermissionDeniedBlocksExecution()` | 权限不足阻止执行 | 抛出 SecurityException |
| `testPermissionGrantedAllowsExecution()` | 权限允许顺利执行 | 正常返回结果 |
| `testRoleBasedPermission()` | 基于角色的权限控制 | 不同角色不同权限 |
| `testPermissionCache()` | 权限缓存 | 重复校验不走重复逻辑 |

---

## 阶段二：P1 — 核心业务集成测试（6 个任务）

### 任务 2.1：会话管理完整生命周期测试
**目录**: `src/test/java/com/jwcode/core/session/`
**新建文件**: `SessionLifecycleIntegrationTest.java`

#### 测试场景
- Session 创建 → 设置参数 → 活跃 → 超时 → 自动回收
- 多 Session 隔离性（并发访问互不干扰）
- Session 参数持久化 → 恢复 → 继续使用
- 上下文窗口触发压缩策略

---

### 任务 2.2：上下文窗口 + 压缩策略集成测试
**目录**: `src/test/java/com/jwcode/core/service/`
**新建文件**: `ContextWindowCompactionIntegrationTest.java`

#### 测试场景
- 上下文累积达到阈值 → 自动触发压缩
- SimpleCompactionStrategy 压缩后保留关键消息
- StructuredCompactionStrategy 结构化压缩
- 压缩后 Token 计数减少验证
- 压缩前后上下文一致性

---

### 任务 2.3：LLM → 工具解析 → 执行 端到端链路测试
**目录**: `src/test/java/com/jwcode/core/llm/`
**新建文件**: `LlmToToolExecutionIntegrationTest.java`

#### 测试场景
| 测试方法 | 描述 |
|---------|------|
| `testLlmResponseParsedIntoToolCall()` | LLM JSON 响应解析为工具调用 |
| `testLlmResponseWithMultipleTools()` | 一次 LLM 响应包含多个工具调用 |
| `testToolCallExecutedInSequence()` | 多个工具按顺序串行执行 |
| `testToolExecutionResultReturnedToLlm()` | 工具执行结果返回到 LLM 上下文 |
| `testStreamingResponseDuringToolExecution()` | 流式响应处理 |
| `testLlmRetryOnToolFailure()` | LLM 重试机制 |

---

### 任务 2.4：A2A Agent 协作集成测试（增强已有）
**目录**: `src/test/java/com/jwcode/core/a2a/`
**新建文件**: `AgentCollaborationIntegrationTest.java`

#### 增强测试
- Master Agent → 子 Agent 任务分配 + 结果聚合
- 多 Agent 并行执行
- Agent 间消息路由延迟
- Agent 发现/注册/健康检查
- 错误传播与隔离

---

### 任务 2.5：Git + 工作树集成测试
**目录**: `src/test/java/com/jwcode/core/git/`
**新建文件**: `GitWorktreeIntegrationTest.java`

#### 测试场景
- Git 提交 → 分支切换 → 合并
- 工作树创建/进入/退出
- 文件修改 → git status → commit 完整流程

---

### 任务 2.6：Parser 多格式集成测试
**目录**: `src/test/java/com/jwcode/core/parser/`
**新建文件**: `MultiFormatParserIntegrationTest.java`

#### 测试场景
- JSON 解析 + 验证
- XML 解析
- Markdown 解析
- YAML 解析
- 格式间互转
- 错误恢复

---

## 阶段三：P2 — 功能增强集成测试（5 个任务）

### 任务 3.1：Config 加载 + 热更新集成测试
- 多级配置合并（系统级/用户级/项目级）
- 热更新监听
- 配置持久化

### 任务 3.2：Code Analysis + Tree-sitter 端到端测试
- 代码解析 → AST 生成 → 查询 → 修改 → 写入

### 任务 3.3：Resilience 容错集成测试
- 重试机制
- 熔断器
- 降级策略

### 任务 3.4：Checkpoint 检查点 + 恢复测试
- 执行状态保存
- 进程重启后恢复

### 任务 3.5：Skill 技能系统集成测试
- 技能注册/发现/执行

---

## 阶段四：P3 — 外围功能集成测试（5 个任务）

### 任务 4.1：AuthService 认证流程测试
### 任务 4.2：Plugin 插件系统加载测试
### 任务 4.3：Notebook 导入/导出测试
### 任务 4.4：Report 报告生成测试
### 任务 4.5：Util 工具类 + Exception 测试

---

## 实施路线图

```
第1周：P0 任务 1.1 (ToolExecutionStateMachine) + 1.5 (PermissionManager)
第2周：P0 任务 1.2 (PlanModeManager) 
第3周：P0 任务 1.3 (ToolExecutionService) + 1.4 (IntegrationTest 增强)
第4周：P1 任务 2.1 (Session) + 2.2 (ContextWindow)
第5周：P1 任务 2.3 (LLM→Tool) + 2.4 (A2A 增强)
第6周：P1 任务 2.5 (Git) + 2.6 (Parser)
第7周：P2 任务 3.1-3.3
第8周：P2 任务 3.4-3.5 + P3 任务 4.1-4.5
```

---

## 测试基础设施需求

1. ✅ **JUnit 5 (Jupiter)** — 已存在
2. ✅ **Mockito** — 已存在（pom.xml依赖）
3. ⚠️ **测试覆盖率工具** — 建议添加 JaCoCo
4. ⚠️ **集成测试隔离** — 建议使用 `@Tag("integration")` 区分单元/集成测试
5. ⚠️ **TempDir** — 建议系统性地使用 `@TempDir` 避免文件污染

---

## 验收标准

- **P0 完成**: 核心链路 (StateMachine + PlanMode + ToolExecutionService) 100% 覆盖
- **P1 完成**: 所有业务模块集成场景 >= 80% 覆盖
- **P2 完成**: 增强功能 >= 60% 覆盖
- **P3 完成**: 外围功能 >= 40% 覆盖
- **所有测试**: 不依赖外部服务（DB/网络），可独立运行
- **构建**: `mvn test` 全量通过
