# 任务：分析并补充缺失的集成测试 / 任务完整性测试

## 任务状态
- 当前测试文件总数: **43**
- 已覆盖顶层包: **19/45**
- 缺失顶层包: **26**
- 完整分析报告见: `summary_integration_tests_analysis.md`

---

## 核心发现

### 完全缺失测试的顶层包 (26个)
**高危**: aicl, api, assistant, coordinator, hook, lsp, repl, resilience
**中危**: bridge, buddy, checker, checkpoint, message, notebook, observability, plugins, report, search, skill, state, team, terminal, ui
**低危**: compact, exception, log, util

### 已有包中缺失的子包测试
- a2a: registry, router, server, service, retry 无测试
- agent: config, fork, parallel 无测试
- advanced: compression, indexing, swarm, thinking, yolo 无测试

### 缺失的关键集成测试 (P0)
1. CoordinatorIntegrationTest
2. A2AFullFlowIntegrationTest (含 registry→router→dispatcher→server)
3. ToolChainIntegrationTest
4. AssistantConversationFlowTest
5. AgentTaskForkJoinIntegrationTest

### 缺失的集成测试 (P1)
- LSPIntegrationTest, REPLIntegrationTest, HookSystemIntegrationTest
- ResilienceIntegrationTest, CheckpointSaveRestoreTest
- SessionPersistenceIntegrationTest, SearchIntegrationTest
- PluginSystemIntegrationTest

### 缺失的任务完整性测试
1. ToolExecutionTaskCompleteTest — 工具执行完整链路
2. AgentTaskLifecycleTest — Agent 任务全生命周期
3. A2AMessageRoundTripTest — A2A 消息完整往返
4. SessionFullLifecycleTest — 会话完整流程
5. UserRequestToResponseFlowTest — 用户请求全链路

---

## 行动清单

### □ 第一阶段 - 立即行动
- [ ] 为 coordinator, hook, lsp, repl, resilience 创建基础集成测试
- [ ] 补全 agent/config, agent/fork, agent/parallel 子包测试
- [ ] 为 aicl, api, assistant 核心包创建集成测试

### □ 第二阶段 - 本周内
- [ ] 为 a2a/registry, a2a/router, a2a/server, a2a/service 创建测试
- [ ] 创建 ToolChainIntegrationTest
- [ ] 创建 AgentTaskForkJoinIntegrationTest

### □ 第三阶段 - 本月内
- [ ] 为所有剩余缺失包创建至少一个基础测试
- [ ] 创建任务完整性测试覆盖核心用户场景
- [ ] 建立测试覆盖率门禁
