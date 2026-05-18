# 测试覆盖率缺失映射（源码 → 测试文件映射）

## 说明
此文档将每个源文件夹中**零测试覆盖率**的类映射到对应的待建测试文件。

---

## Core 模块（核心：28 个 test 文件 vs ~50+ 源包）

### 一、tool/execution 包（2 个源类，0 个测试）
| 源文件 | 测试文件 | 优先级 |
|--------|---------|-------|
| `ToolExecutionStateMachine.java` | `ToolExecutionStateMachineCompleteTransitionTest.java` | **P0** |
| `ToolExecutionState.java` | （Test 上述 StateMachine 时一并覆盖） | P0 |
| **新增测试包**: `src/test/java/com/jwcode/core/tool/execution/` | | |

### 二、plan 包（1 个源类，0 个测试）
| 源文件 | 测试文件 | 优先级 |
|--------|---------|-------|
| `PlanModeManager.java` | `PlanModeManagerIntegrationTest.java` | **P0** |
| **新增测试包**: `src/test/java/com/jwcode/core/plan/` | | |

### 三、service 包（3 个源类，0 个测试）
| 源文件 | 测试文件 | 优先级 |
|--------|---------|-------|
| `ToolExecutionService.java` | `ToolExecutionServiceIntegrationTest.java` | **P0** |
| `ContextWindowCompactionService.java` | `ContextWindowCompactionIntegrationTest.java` | P1 |
| `A2AAgentService.java` | `AgentCollaborationIntegrationTest.java` | P1 |
| **新增测试包**: `src/test/java/com/jwcode/core/service/` | | |

### 四、permission 包（1 个源类，0 个测试）
| 源文件 | 测试文件 | 优先级 |
|--------|---------|-------|
| `PermissionManager.java` | `PermissionManagerIntegrationTest.java` | **P0** |
| **新增测试包**: `src/test/java/com/jwcode/core/permission/` | | |

### 五、session 包（1 个源类，0 个测试）
| 源文件 | 测试文件 | 优先级 |
|--------|---------|-------|
| `Session.java` 及相关 | `SessionLifecycleIntegrationTest.java` | P1 |
| **新增测试包**: `src/test/java/com/jwcode/core/session/` | | |

### 六、config 包（1 个源类，0 个测试）
| 源文件 | 测试文件 | 优先级 |
|--------|---------|-------|
| `ConfigManager.java` | `ConfigLoadingHotReloadIntegrationTest.java` | P2 |
| **新增测试包**: `src/test/java/com/jwcode/core/config/` | | |

### 七、checkpoint 包（1 个源类，0 个测试）
| 源文件 | 测试文件 | 优先级 |
|--------|---------|-------|
| `CheckpointManager.java` | `CheckpointRestoreIntegrationTest.java` | P2 |
| **新增测试包**: `src/test/java/com/jwcode/core/checkpoint/` | | |

### 八、skill 包（1 个源类，0 个测试）
| 源文件 | 测试文件 | 优先级 |
|--------|---------|-------|
| `SkillManager.java` | `SkillSystemIntegrationTest.java` | P2 |
| **新增测试包**: `src/test/java/com/jwcode/core/skill/` | | |

### 九、auth 包（1 个源类，0 个测试）
| 源文件 | 测试文件 | 优先级 |
|--------|---------|-------|
| `AuthService.java` | `AuthFlowIntegrationTest.java` | P3 |
| **新增测试包**: `src/test/java/com/jwcode/core/auth/` | | |

### 十、plugin 包（1 个源类，0 个测试）
| 源文件 | 测试文件 | 优先级 |
|--------|---------|-------|
| `PluginManager.java` | `PluginLoadingIntegrationTest.java` | P3 |
| **新增测试包**: `src/test/java/com/jwcode/core/plugin/` | | |

### 十一、report 包（1 个源类，0 个测试）
| 源文件 | 测试文件 | 优先级 |
|--------|---------|-------|
| `ReportGenerator.java` | `ReportGenerationTest.java` | P3 |
| **新增测试包**: `src/test/java/com/jwcode/core/report/` | | |

### 十二、resilience 包（~3 个源类，0 个测试）
| 源文件 | 测试文件 | 优先级 |
|--------|---------|-------|
| `RetryPolicy.java` | `ResilienceRetryCircuitBreakerIntegrationTest.java` | P2 |
| `CircuitBreaker.java` | （同上） | P2 |
| `FallbackStrategy.java` | （同上） | P2 |
| **新增测试包**: `src/test/java/com/jwcode/core/resilience/` | | |

---

## Tool 模块增强

### 现有测试覆盖情况
| 工具包 | 测试 | 状态 |
|-------|------|------|
| `tool/bash` | BashToolTest.java | ✅ 已有 |
| `tool/file` | FileToolTest.java | ✅ 已有 |
| `tool/git` | GitToolTest.java | ✅ 已有 |
| `tool/web` | WebToolTest.java | ✅ 已有 |
| `tool/*` 共 11 个 | 各有一个测试 | ✅ 已有 |

### 缺失增强
| 缺失点 | 建议 | 优先级 |
|-------|------|-------|
| 工具并发测试 | 新增 `ToolConcurrencyTest.java` | P2 |
| 工具链式调用测试 | 在 `ToolExecutionServiceIntegrationTest.java` 覆盖 | P0 |
| 工具注册完整性 | 在 `IntegrationTest.java` 增强 | P0 |

---

## Parser 模块

| 包 | 源文件 | 测试 | 优先级 |
|----|-------|------|-------|
| `parser/json` | `JSONSchemaParser` + `JSONSchemaGenerator` | ✅ 有 | - |
| `parser/xml` | `XMLSchemaParser` + `XMLSchemaGenerator` | ❌ 缺 | P1 |
| `parser/markdown` | `MarkdownSchemaParser` + `MarkdownSchemaGenerator` | ❌ 缺 | P1 |
| `parser/yaml` | `YAMLSchemaParser` + `YAMLSchemaGenerator` | ❌ 缺 | P1 |
| **新增**: `MultiFormatParserIntegrationTest.java` | 格式间互转测试 | ❌ 缺 | P1 |

---

## CLI 模块

| 源文件 | 测试 | 优先级 |
|-------|------|-------|
| `jwcode-cli` 入口 | 有基础测试 | ✅ |
| CLI 命令参数解析 | ❌ 缺 | P2 |
| CLI 交互模式 | ❌ 缺 | P2 |

---

## 其他完全零测试模块

| 模块 | 源文件数 | 测试数 | 优先级 |
|------|---------|-------|-------|
| `jwcode-repl` | 5 | **0** | P3 |
| `jwcode-mcp` | 1 | **0** | P2 |
| `jwcode-web` | 8 | **0** | P3 |
| `jwcode-ui` | ~20+ | **0** | P3 |

---

## 汇总：待建测试文件（按优先级）

### P0（5 个）— 核心基础设施
1. `ToolExecutionStateMachineCompleteTransitionTest.java`
2. `PlanModeManagerIntegrationTest.java`
3. `ToolExecutionServiceIntegrationTest.java`
4. `PermissionManagerIntegrationTest.java`
5. `IntegrationTest.java`（增强）

### P1（6 个）— 核心业务
6. `SessionLifecycleIntegrationTest.java`
7. `ContextWindowCompactionIntegrationTest.java`
8. `LlmToToolExecutionIntegrationTest.java`
9. `AgentCollaborationIntegrationTest.java`
10. `GitWorktreeIntegrationTest.java`
11. `MultiFormatParserIntegrationTest.java`

### P2（5 个）— 功能增强
12. `ConfigLoadingHotReloadIntegrationTest.java`
13. `CodeAnalysisTreeSitterIntegrationTest.java`
14. `ResilienceRetryCircuitBreakerIntegrationTest.java`
15. `CheckpointRestoreIntegrationTest.java`
16. `SkillSystemIntegrationTest.java`

### P3（5 个）— 外围功能
17. `AuthFlowIntegrationTest.java`
18. `PluginLoadingIntegrationTest.java`
19. `NotebookImportExportTest.java`
20. `ReportGenerationTest.java`
21. `UtilToolClassesTest.java`

---

## 快速启动指南

执行以下命令创建 P0 的测试包目录结构和空文件：

```bash
# P0 测试目录
mkdir -p jwcode-core/src/test/java/com/jwcode/core/tool/execution
mkdir -p jwcode-core/src/test/java/com/jwcode/core/plan
mkdir -p jwcode-core/src/test/java/com/jwcode/core/permission
mkdir -p jwcode-core/src/test/java/com/jwcode/core/service

# P1 测试目录
mkdir -p jwcode-core/src/test/java/com/jwcode/core/session
mkdir -p jwcode-core/src/test/java/com/jwcode/core/llm
mkdir -p jwcode-core/src/test/java/com/jwcode/core/a2a
mkdir -p jwcode-core/src/test/java/com/jwcode/core/parser

# P2 测试目录
mkdir -p jwcode-core/src/test/java/com/jwcode/core/config
mkdir -p jwcode-core/src/test/java/com/jwcode/core/checkpoint
mkdir -p jwcode-core/src/test/java/com/jwcode/core/skill
mkdir -p jwcode-core/src/test/java/com/jwcode/core/resilience

# P3 测试目录
mkdir -p jwcode-core/src/test/java/com/jwcode/core/auth
mkdir -p jwcode-core/src/test/java/com/jwcode/core/plugin
mkdir -p jwcode-core/src/test/java/com/jwcode/core/report
```
