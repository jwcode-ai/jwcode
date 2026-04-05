# JWCode 功能提升方案

> **目标**: 达到 TypeScript (Claude Code) 版本功能完整度  
> **最后更新**: 2026-04-01  
> **状态**: 开发中

---

## 一、方案概述

本提升方案旨在将 JWCode (Java 版本) 完善到与 TypeScript 版本 (Claude Code) 功能对等的水平。方案分为 **4 个阶段**，预计需要 **12 周** 完成。

### 当前状态评估

| 维度 | TypeScript 版本 | JWCode 当前状态 | 完成度 |
|------|----------------|----------------|--------|
| 工具系统 | ~40 个工具 + 完整 UI | 33 个工具 (无 UI) | 82% |
| 命令系统 | ~80 个命令 | 25 个命令 | 31% |
| 服务层 | ~50 个服务 | 7 个服务 | 14% |
| UI/UX 组件 | ~200+ 组件 | 5 个基础组件 | 2.5% |
| 测试覆盖 | 完整测试套件 | 3 个测试类 | 6% |
| 文档 | 完整文档 | 基础文档 | 30% |
| **总体** | **100%** | **~35%** | |

---

## 二、详细提升计划

### 第一阶段：核心功能补齐 (周 1-4)

#### 1.1 工具系统完善 (Week 1)

**目标**: 补齐缺失的工具和工具 UI 组件

| 任务 | 优先级 | 预计工时 | TypeScript 参考 | 状态 |
|------|--------|----------|----------------|------|
| FileWriteTool | P0 | 2 天 | src/tools/FileWriteTool/ | ⬜ |
| NotebookEditTool | P0 | 1 天 | src/tools/NotebookEditTool/ | ⬜ |
| MultiPlanTool | P1 | 1 天 | src/tools/MultiPlanTool/ | ⬜ |
| 工具 UI 框架 | P0 | 3 天 | src/tools/*/UI.tsx | ⬜ |
| BashTool 增强 | P0 | 2 天 | src/tools/BashTool/ | ⬜ |

**交付物**:
- `jwcode-core/src/main/java/com/jwcode/core/tool/FileWriteTool.java`
- `jwcode-core/src/main/java/com/jwcode/core/tool/NotebookEditTool.java`
- `jwcode-core/src/main/java/com/jwcode/core/tool/ui/` (工具 UI 组件包)
- `jwcode-core/src/main/java/com/jwcode/core/tool/bash/` (Bash 工具增强包)

#### 1.2 命令系统扩展 (Week 2)

**目标**: 实现缺失的核心命令

| 命令 | 优先级 | 预计工时 | TypeScript 参考 | 状态 |
|------|--------|----------|----------------|------|
| /doctor | P0 | 1 天 | src/commands/doctor/ | ⬜ |
| /export | P0 | 1 天 | src/commands/export/ | ⬜ |
| /feedback | P1 | 0.5 天 | src/commands/feedback/ | ⬜ |
| /files | P0 | 1 天 | src/commands/files/ | ⬜ |
| /copy | P1 | 0.5 天 | src/commands/copy/ | ⬜ |
| /cost | P0 | 1 天 | src/commands/cost/ | ⬜ |
| /diff | P0 | 1 天 | src/commands/diff/ | ⬜ |
| /stats | P1 | 1 天 | src/commands/stats/ | ⬜ |
| /share | P1 | 1 天 | src/commands/share/ | ⬜ |
| /summary | P0 | 1 天 | src/commands/summary/ | ⬜ |
| /upgrade | P1 | 0.5 天 | src/commands/upgrade/ | ⬜ |
| /plugin | P0 | 2 天 | src/commands/plugin/ | ⬜ |

**交付物**:
- `jwcode-cli/src/main/java/com/jwcode/cli/commands/DoctorCommand.java`
- `jwcode-cli/src/main/java/com/jwcode/cli/commands/ExportCommand.java`
- `jwcode-cli/src/main/java/com/jwcode/cli/commands/FilesCommand.java`
- `jwcode-cli/src/main/java/com/jwcode/cli/commands/CostCommand.java`
- `jwcode-cli/src/main/java/com/jwcode/cli/commands/DiffCommand.java`
- `jwcode-cli/src/main/java/com/jwcode/cli/commands/StatsCommand.java`
- `jwcode-cli/src/main/java/com/jwcode/cli/commands/PluginCommand.java`
- ... (共 12 个新命令类)

#### 1.3 服务层基础建设 (Week 3-4)

**目标**: 建立核心服务层框架

##### Week 3: 分析服务 + 插件服务

| 服务 | 优先级 | 预计工时 | TypeScript 参考 | 状态 |
|------|--------|----------|----------------|------|
| AnalyticsService | P0 | 2 天 | src/services/analytics/ | ⬜ |
| PluginService | P0 | 3 天 | src/services/plugins/ | ⬜ |

**交付物**:
- `jwcode-core/src/main/java/com/jwcode/core/service/AnalyticsService.java`
- `jwcode-core/src/main/java/com/jwcode/core/service/PluginInstallationService.java`
- `jwcode-core/src/main/java/com/jwcode/core/analytics/` (分析包)
- `jwcode-core/src/main/java/com/jwcode/core/plugins/` (插件包)

##### Week 4: LSP 服务增强 + MCP 服务增强

| 服务 | 优先级 | 预计工时 | TypeScript 参考 | 状态 |
|------|--------|----------|----------------|------|
| LSP 诊断注册表 | P0 | 1 天 | src/services/lsp/LSPDiagnosticRegistry.ts | ⬜ |
| LSP 服务器管理 | P0 | 2 天 | src/services/lsp/LSPServerManager.ts | ⬜ |
| MCP 通道权限 | P0 | 1 天 | src/services/mcp/channelPermissions.ts | ⬜ |
| MCP OAuth | P1 | 1 天 | src/services/mcp/oauthPort.ts | ⬜ |
| MCP 官方注册表 | P1 | 1 天 | src/services/mcp/officialRegistry.ts | ⬜ |

**交付物**:
- `jwcode-core/src/main/java/com/jwcode/core/lsp/LspDiagnosticRegistry.java`
- `jwcode-core/src/main/java/com/jwcode/core/lsp/LspServerManager.java`
- `jwcode-core/src/main/java/com/jwcode/core/mcp/McpChannelPermissions.java`
- `jwcode-core/src/main/java/com/jwcode/core/mcp/McpOAuthHandler.java`
- `jwcode-core/src/main/java/com/jwcode/core/mcp/McpServerRegistry.java`

---

### 第二阶段：UI/UX 提升 (周 5-7)

#### 2.1 终端 UI 框架 (Week 5)

**目标**: 建立 Java 版终端 UI 框架 (类似 Ink)

**技术方案**: 使用 Java Curses 库 (如 jexer 或 lanterna)

| 组件 | 优先级 | 预计工时 | 状态 |
|------|--------|----------|------|
| UI 框架基础 | P0 | 2 天 | ⬜ |
| Box 容器 | P0 | 1 天 | ⬜ |
| Text 文本 | P0 | 0.5 天 | ⬜ |
| 键盘事件处理 | P0 | 1.5 天 | ⬜ |

**交付物**:
- `jwcode-ui/pom.xml` (新增 UI 模块)
- `jwcode-ui/src/main/java/com/jwcode/ui/terminal/` (终端 UI 包)
- `jwcode-ui/src/main/java/com/jwcode/ui/components/` (基础组件包)

#### 2.2 设计系统组件 (Week 6)

**目标**: 实现基础设计系统组件

| 组件 | 优先级 | 预计工时 | TypeScript 参考 | 状态 |
|------|--------|----------|----------------|------|
| Dialog | P0 | 2 天 | src/components/design-system/Dialog.tsx | ⬜ |
| Tabs | P0 | 1 天 | src/components/design-system/Tabs.tsx | ⬜ |
| ProgressBar | P0 | 1 天 | src/components/design-system/ProgressBar.tsx | ⬜ |
| LoadingState | P0 | 1 天 | src/components/design-system/LoadingState.tsx | ⬜ |
| Divider | P1 | 0.5 天 | src/components/design-system/Divider.tsx | ⬜ |
| Spinner | P0 | 0.5 天 | src/components/spinner/ | ⬜ |

**交付物**:
- `jwcode-ui/src/main/java/com/jwcode/ui/components/Dialog.java`
- `jwcode-ui/src/main/java/com/jwcode/ui/components/Tabs.java`
- `jwcode-ui/src/main/java/com/jwcode/ui/components/ProgressBar.java`
- `jwcode-ui/src/main/java/com/jwcode/ui/components/LoadingState.java`
- `jwcode-ui/src/main/java/com/jwcode/ui/components/Spinner.java`

#### 2.3 消息和工具 UI (Week 7)

**目标**: 实现消息显示和工具 UI 组件

| 组件 | 优先级 | 预计工时 | TypeScript 参考 | 状态 |
|------|--------|----------|----------------|------|
| Message | P0 | 1 天 | src/components/Message.tsx | ⬜ |
| Messages | P0 | 1 天 | src/components/Messages.tsx | ⬜ |
| ToolUseLoader | P0 | 1 天 | src/components/ToolUseLoader.tsx | ⬜ |
| FileEditToolDiff | P0 | 2 天 | src/components/FileEditToolDiff.tsx | ⬜ |
| StatusLine | P0 | 1 天 | src/components/StatusLine.tsx | ⬜ |

**交付物**:
- `jwcode-ui/src/main/java/com/jwcode/ui/components/Message.java`
- `jwcode-ui/src/main/java/com/jwcode/ui/components/Messages.java`
- `jwcode-ui/src/main/java/com/jwcode/ui/components/ToolUseLoader.java`
- `jwcode-ui/src/main/java/com/jwcode/ui/components/FileEditDiff.java`
- `jwcode-ui/src/main/java/com/jwcode/ui/components/StatusLine.java`

---

### 第三阶段：高级功能 (周 8-10)

#### 3.1 Agent 系统完善 (Week 8)

**目标**: 完善 Agent 多代理协作系统

| 功能 | 优先级 | 预计工时 | TypeScript 参考 | 状态 |
|------|--------|----------|----------------|------|
| Agent 颜色管理 | P1 | 0.5 天 | src/tools/AgentTool/agentColorManager.ts | ⬜ |
| Agent 显示系统 | P0 | 1 天 | src/tools/AgentTool/agentDisplay.ts | ⬜ |
| Agent 内存 | P0 | 1 天 | src/tools/AgentTool/agentMemory.ts | ⬜ |
| 内置 Agent | P1 | 1 天 | src/tools/AgentTool/builtInAgents.ts | ⬜ |
| Agent UI | P0 | 1.5 天 | src/tools/AgentTool/UI.tsx | ⬜ |

**交付物**:
- `jwcode-core/src/main/java/com/jwcode/core/agent/AgentColorManager.java`
- `jwcode-core/src/main/java/com/jwcode/core/agent/AgentDisplay.java`
- `jwcode-core/src/main/java/com/jwcode/core/agent/AgentMemory.java`
- `jwcode-core/src/main/java/com/jwcode/core/agent/BuiltInAgents.java`
- `jwcode-core/src/main/java/com/jwcode/core/tool/AgentTool.java` (增强版)

#### 3.2 Buddy 伙伴系统 (Week 9)

**目标**: 实现 Buddy 伙伴精灵系统

| 功能 | 优先级 | 预计工时 | TypeScript 参考 | 状态 |
|------|--------|----------|----------------|------|
| 伙伴精灵 | P1 | 2 天 | src/buddy/CompanionSprite.tsx | ⬜ |
| 伙伴提示 | P1 | 1 天 | src/buddy/prompt.ts | ⬜ |
| 伙伴通知 | P1 | 1 天 | src/buddy/useBuddyNotification.tsx | ⬜ |
| 精灵系统 | P1 | 1 天 | src/buddy/sprites.ts | ⬜ |

**交付物**:
- `jwcode-core/src/main/java/com/jwcode/core/buddy/Companion.java`
- `jwcode-core/src/main/java/com/jwcode/core/buddy/CompanionSprite.java`
- `jwcode-core/src/main/java/com/jwcode/core/buddy/CompanionPrompt.java`
- `jwcode-core/src/main/java/com/jwcode/core/buddy/CompanionNotification.java`

#### 3.3 会话管理增强 (Week 10)

**目标**: 实现会话压缩、内存管理等功能

| 功能 | 优先级 | 预计工时 | TypeScript 参考 | 状态 |
|------|--------|----------|----------------|------|
| 自动压缩 | P0 | 2 天 | src/services/compact/autoCompact.ts | ⬜ |
| 会话内存 | P1 | 1 天 | src/services/SessionMemory/ | ⬜ |
| 提取内存 | P1 | 1 天 | src/services/extractMemories/ | ⬜ |
| Prompt 建议 | P1 | 1 天 | src/services/PromptSuggestion/ | ⬜ |

**交付物**:
- `jwcode-core/src/main/java/com/jwcode/core/service/SessionCompactService.java`
- `jwcode-core/src/main/java/com/jwcode/core/service/SessionMemoryService.java`
- `jwcode-core/src/main/java/com/jwcode/core/service/PromptSuggestionService.java`

---

### 第四阶段：测试与文档 (周 11-12)

#### 4.1 测试覆盖 (Week 11)

**目标**: 建立完整的测试套件

| 测试类型 | 优先级 | 预计工时 | 目标覆盖率 | 状态 |
|----------|--------|----------|-----------|------|
| 工具单元测试 | P0 | 2 天 | 80% | ⬜ |
| 命令单元测试 | P0 | 1 天 | 80% | ⬜ |
| 服务单元测试 | P0 | 2 天 | 70% | ⬜ |
| UI 组件测试 | P1 | 1 天 | 60% | ⬜ |
| 集成测试 | P0 | 2 天 | 核心流程 | ⬜ |
| E2E 测试 | P1 | 2 天 | 关键路径 | ⬜ |

**交付物**:
- `jwcode-core/src/test/java/` (完整测试套件)
- `jwcode-cli/src/test/java/` (完整测试套件)
- `jwcode-ui/src/test/java/` (完整测试套件)
- `jwcode-core/src/integration-test/` (集成测试)

#### 4.2 文档完善 (Week 12)

**目标**: 建立完整的文档体系

| 文档类型 | 优先级 | 预计工时 | 内容 | 状态 |
|----------|--------|----------|------|------|
| API 文档 | P0 | 2 天 | Javadoc 完整注释 | ⬜ |
| 工具参考 | P0 | 1 天 | 所有工具使用说明 | ⬜ |
| 命令参考 | P0 | 1 天 | 所有命令使用说明 | ⬜ |
| 服务文档 | P0 | 1 天 | 服务层架构说明 | ⬜ |
| 故障排查 | P1 | 1 天 | 常见问题解决 | ⬜ |
| 最佳实践 | P1 | 1 天 | 开发最佳实践 | ⬜ |
| 部署指南 | P0 | 1 天 | 部署和配置指南 | ⬜ |

**交付物**:
- `jwcode/docs/api-reference.md`
- `jwcode/docs/tools-reference.md`
- `jwcode/docs/commands-reference.md`
- `jwcode/docs/services-architecture.md`
- `jwcode/docs/troubleshooting.md`
- `jwcode/docs/best-practices.md`
- `jwcode/docs/deployment-guide.md`

---

## 三、新增模块结构

### 3.1 jwcode-ui 模块

```
jwcode-ui/
├── pom.xml
└── src/
    ├── main/
    │   └── java/
    │       └── com/jwcode/ui/
    │           ├── terminal/          # 终端 UI 框架
    │           │   ├── TerminalRenderer.java
    │           │   ├── TerminalInput.java
    │           │   └── TerminalBuffer.java
    │           ├── components/        # UI 组件
    │           │   ├── Box.java
    │           │   ├── Text.java
    │           │   ├── Dialog.java
    │           │   ├── Tabs.java
    │           │   ├── ProgressBar.java
    │           │   ├── LoadingState.java
    │           │   ├── Spinner.java
    │           │   ├── Message.java
    │           │   ├── Messages.java
    │           │   ├── StatusLine.java
    │           │   └── ...
    │           └── theme/             # 主题系统
    │               ├── Theme.java
    │               └── Colors.java
    └── test/
        └── java/
            └── com/jwcode/ui/
                └── components/
```

### 3.2 新增服务包结构

```
jwcode-core/src/main/java/com/jwcode/core/
├── analytics/           # [新增] 分析服务
│   ├── AnalyticsService.java
│   ├── EventCollector.java
│   ├── EventExporter.java
│   └── UsageTracker.java
├── plugins/             # [新增] 插件服务
│   ├── PluginService.java
│   ├── PluginInstallationManager.java
│   ├── PluginLoader.java
│   └── PluginRegistry.java
├── compact/             # [新增] 会话压缩
│   ├── CompactService.java
│   ├── AutoCompactTrigger.java
│   └── SessionSummarizer.java
├── agent/               # [新增] Agent 管理
│   ├── AgentManager.java
│   ├── AgentColorManager.java
│   ├── AgentDisplay.java
│   └── AgentMemory.java
└── buddy/               # [新增] Buddy 伙伴
    ├── Companion.java
    ├── CompanionSprite.java
    ├── CompanionPrompt.java
    └── CompanionNotification.java
```

---

## 三、新增模块结构

### 3.1 jwcode-ui 模块

```
jwcode-ui/
├── pom.xml
└── src/
    ├── main/
    │   └── java/
    │       └── com/jwcode/ui/
    │           ├── terminal/          # 终端 UI 框架
    │           │   ├── TerminalRenderer.java
    │           │   ├── TerminalInput.java
    │           │   └── TerminalBuffer.java
    │           ├── components/        # UI 组件
    │           │   ├── Box.java
    │           │   ├── Text.java
    │           │   ├── Dialog.java
    │           │   ├── Tabs.java
    │           │   ├── ProgressBar.java
    │           │   ├── LoadingState.java
    │           │   ├── Spinner.java
    │           │   ├── Message.java
    │           │   ├── Messages.java
    │           │   ├── StatusLine.java
    │           │   └── ...
    │           └── theme/             # 主题系统
    │               ├── Theme.java
    │               └── Colors.java
    └── test/
        └── java/
            └── com/jwcode/ui/
                └── components/
```

### 3.2 新增服务包结构

```
jwcode-core/src/main/java/com/jwcode/core/
├── analytics/           # [新增] 分析服务
│   ├── AnalyticsService.java
│   ├── EventCollector.java
│   ├── EventExporter.java
│   └── UsageTracker.java
├── plugins/             # [新增] 插件服务
│   ├── PluginService.java
│   ├── PluginInstallationManager.java
│   ├── PluginLoader.java
│   └── PluginRegistry.java
├── compact/             # [新增] 会话压缩
│   ├── CompactService.java
│   ├── AutoCompactTrigger.java
│   └── SessionSummarizer.java
├── agent/               # [新增] Agent 管理
│   ├── AgentManager.java
│   ├── AgentColorManager.java
│   ├── AgentDisplay.java
│   └── AgentMemory.java
└── buddy/               # [新增] Buddy 伙伴
    ├── Companion.java
    ├── CompanionSprite.java
    ├── CompanionPrompt.java
    └── CompanionNotification.java
```

---

## 四、依赖配置

### 4.1 新增 Maven 依赖

```xml
<!-- jwcode-ui 模块依赖 -->
<dependency>
    <groupId>com.googlecode.lanterna</groupId>
    <artifactId>lanterna</artifactId>
    <version>3.1.1</version>
</dependency>

<!-- 测试依赖 -->
<dependency>
    <groupId>org.mockito</groupId>
    <artifactId>mockito-core</artifactId>
    <version>5.5.0</version>
    <scope>test</scope>
</dependency>

<!-- 分析服务依赖 (可选) -->
<dependency>
    <groupId>com.datadoghq</groupId>
    <artifactId>dd-trace-api</artifactId>
    <version>1.20.0</version>
    <optional>true</optional>
</dependency>
```

### 4.2 模块依赖关系

```xml
<!-- 在 jwcode-core/pom.xml 中添加 -->
<dependency>
    <groupId>com.jwcode</groupId>
    <artifactId>jwcode-ui</artifactId>
    <version>1.0.0</version>
</dependency>
```

---

## 五、里程碑与验收标准

### 里程碑 1 (Week 4): 核心功能完成

**验收标准**:
- [ ] 所有缺失工具已实现
- [ ] 所有核心命令已实现
- [ ] 服务层框架已建立
- [ ] LSP 和 MCP 服务已增强

### 里程碑 2 (Week 7): UI/UX 完成

**验收标准**:
- [ ] jwcode-ui 模块已创建
- [ ] 所有设计系统组件已实现
- [ ] 消息和工具 UI 已实现
- [ ] 终端 UI 框架可运行

### 里程碑 3 (Week 10): 高级功能完成

**验收标准**:
- [ ] Agent 系统已完善
- [ ] Buddy 伙伴系统已实现
- [ ] 会话管理增强已完成
- [ ] 自动压缩功能可用

### 里程碑 4 (Week 12): 测试与文档完成

**验收标准**:
- [ ] 单元测试覆盖率 > 80%
- [ ] 集成测试覆盖核心流程
- [ ] 所有文档已完成
- [ ] API 文档完整

---

## 六、风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 终端 UI 库选择困难 | 高 | 中 | 提前评估 lanterna 和 jexer |
| LSP 协议复杂性 | 高 | 中 | 使用现有 LSP4J 库 |
| 测试工作量超预期 | 中 | 高 | 引入测试自动生成工具 |
| 时间延期 | 高 | 中 | 分阶段交付，优先核心功能 |

---

## 七、开发进度追踪

### 周进度记录

| 周次 | 日期范围 | 完成内容 | 完成度 | 备注 |
|------|---------|---------|--------|------|
| 第 1 周 | 2026-04-01 ~ 2026-04-07 | FileWriteTool, NotebookEditTool, MultiPlanTool, DoctorCommand, ExportCommand, AnalyticsService | 60% | 工具系统完善 |
| 第 2 周 | 2026-04-08 ~ 2026-04-14 | 待开发 | 0% | 命令系统扩展 |
| 第 3 周 | 2026-04-15 ~ 2026-04-21 | 待开发 | 0% | 分析服务 + 插件服务 |
| 第 4 周 | 2026-04-22 ~ 2026-04-28 | 待开发 | 0% | LSP+MCP 服务增强 |
| 第 5 周 | 2026-04-29 ~ 2026-05-05 | 待开发 | 0% | 终端 UI 框架 |
| 第 6 周 | 2026-05-06 ~ 2026-05-12 | 待开发 | 0% | 设计系统组件 |
| 第 7 周 | 2026-05-13 ~ 2026-05-19 | 待开发 | 0% | 消息和工具 UI |
| 第 8 周 | 2026-05-20 ~ 2026-05-26 | 待开发 | 0% | Agent 系统完善 |
| 第 9 周 | 2026-05-27 ~ 2026-06-02 | 待开发 | 0% | Buddy 伙伴系统 |
| 第 10 周 | 2026-06-03 ~ 2026-06-09 | 待开发 | 0% | 会话管理增强 |
| 第 11 周 | 2026-06-10 ~ 2026-06-16 | 待开发 | 0% | 测试覆盖 |
| 第 12 周 | 2026-06-17 ~ 2026-06-23 | 待开发 | 0% | 文档完善 |

---

## 八、总结

本提升方案将 JWCode 从当前 **35%** 的功能完整度提升到 **100%**，达到与 TypeScript 版本对等的水平。

**关键数据**:
- **新增代码量**: ~15,000 行
- **新增类**: ~80 个
- **新增测试**: ~50 个测试类
- **新增文档**: ~10 个文档文件
- **总工时**: 12 周 (60 个工作日)

---

*文档创建时间：2026-04-01*