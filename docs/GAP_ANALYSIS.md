# JWCode 功能差距分析与剩余开发计划

> 文档生成时间：2026/4/1
> 最后更新：2026/4/1 (新增工具和命令)
> 目标：达到 TypeScript (Claude Code) 版本功能完整度

---

## 一、当前状态总览

### 1.1 功能对比表

| 模块 | TypeScript 版本 | JWCode 当前 | 完成度 | 优先级 |
|------|----------------|-------------|--------|--------|
| **工具系统** | ~40 个工具 | 40 个工具 | 100% | P0 |
| **命令系统** | ~80 个命令 | 36 个命令 | 45% | P0 |
| **服务层** | ~50 个服务 | 8 个服务 | 16% | P0 |
| **UI 组件** | ~200+ 组件 | 5 个组件 | 2.5% | P0 |
| **Buddy 系统** | 完整 | 部分实现 | 40% | P1 |
| **Agent 系统** | 完整 | 缺失 | 0% | P0 |
| **LSP 增强** | 完整 | 基础实现 | 20% | P0 |
| **MCP 增强** | 完整 | 基础实现 | 30% | P0 |
| **测试覆盖** | 80%+ | 3 个测试类 | 6% | P0 |
| **文档** | 完整 | 基础文档 | 30% | P1 |

---

## 二、详细差距分析

### 2.1 工具系统差距 (Tools)

#### 已实现工具 (33 个)
```
✅ AskUserQuestionTool
✅ BriefTool
✅ ConfigTool
✅ EditTool
✅ EnterPlanModeTool
✅ EnterWorktreeTool
✅ ExitPlanModeTool
✅ ExitWorktreeTool
✅ FileWriteTool
✅ ListMcpResourcesTool
✅ LSPTool
✅ McpAuthTool
✅ MultiPlanTool
✅ NotebookEditTool
✅ PowerShellTool
✅ ReadMcpResourceTool
✅ RemoteTriggerTool
✅ REPLTool
✅ ScheduleCronTool
✅ SendMessageTool
✅ SleepTool
✅ SyntheticOutputTool
✅ TaskCreateTool
✅ TaskGetTool
✅ TaskListTool
✅ TaskOutputTool
✅ TaskStopTool
✅ TaskUpdateTool
✅ TeamCreateTool
✅ TeamDeleteTool
✅ TodoWriteTool
✅ ToolSearchTool
✅ WebFetchTool
```

#### 缺失工具 (需要新增 2 个)

| 工具名称 | 优先级 | TypeScript 参考 | 说明 | 状态 |
|----------|--------|----------------|------|------|
| `FileReadTool` | P0 | src/tools/FileReadTool/ | 文件读取工具 | ✅ 已实现 |
| `FileEditTool` | P0 | src/tools/FileEditTool/ | 文件编辑工具 (diff 模式) | ✅ 已实现 |
| `GlobTool` | P0 | src/tools/GlobTool/ | 文件搜索工具 | ✅ 已实现 |
| `GrepTool` | P0 | src/tools/GrepTool/ | 文本搜索工具 | ✅ 已实现 |
| `MCPTool` | P0 | src/tools/MCPTool/ | MCP 调用工具 | ✅ 已实现 |
| `BashTool` | P0 | src/tools/BashTool/ | Shell 命令工具 (增强版) | ✅ 已实现 |
| `AgentTool` | P0 | src/tools/AgentTool/ | Agent 管理工具 | ✅ 已实现 |
| `SkillTool` | P1 | src/tools/SkillTool/ | 技能调用工具 | ⏳ 待实现 |
| `WebSearchTool` | P1 | src/tools/WebSearchTool/ | 网络搜索工具 | ⏳ 待实现 |
| `ForkSubagentTool` | P1 | src/tools/AgentTool/forkSubagent.ts | 子代理分叉工具 | ⏳ 待实现 |

#### 工具 UI 组件缺失

每个工具需要对应的 UI.tsx 组件，目前全部缺失：

```
需要新增的 UI 组件:
- FileReadTool/UI.tsx
- FileEditTool/UI.tsx
- BashTool/UI.tsx
- GlobTool/UI.tsx
- GrepTool/UI.tsx
- MCPTool/UI.tsx
- AgentTool/UI.tsx
- ... (共 12+ 个工具 UI 组件)
```

---

### 2.2 命令系统差距 (Commands)

#### 已实现命令 (27 个)
```
✅ AdvisorCommand
✅ BriefCommand
✅ CommitCommand
✅ CompactCommand
✅ ConfigCommand
✅ ContextCommand
✅ DoctorCommand
✅ EffortCommand
✅ ExportCommand
✅ FastCommand
✅ HelpCommand
✅ HooksCommand
✅ IdeCommand
✅ MemoryCommand
✅ PermissionsCommand
✅ PlanCommand
✅ RemoteCommand
✅ ResumeCommand
✅ RewindCommand
✅ SessionCommand
✅ SkillsCommand
✅ TasksCommand
✅ TeleportCommand
✅ ThemeCommand
✅ UsageCommand
✅ VimCommand
✅ VoiceCommand
```

#### 缺失命令 (需要新增 31 个)

| 命令名称 | 优先级 | TypeScript 参考 | 说明 | 状态 |
|----------|--------|----------------|------|------|
| `/copy` | P0 | src/commands/copy/ | 复制内容到剪贴板 | ✅ 已实现 |
| `/cost` | P0 | src/commands/cost/ | 查看成本统计 | ✅ 已实现 |
| `/diff` | P0 | src/commands/diff/ | 查看代码差异 | ✅ 已实现 |
| `/files` | P0 | src/commands/files/ | 文件列表 | ✅ 已实现 |
| `/summary` | P0 | src/commands/summary/ | 会话摘要 | ✅ 已实现 |
| `/clear` | P0 | src/commands/clear/ | 清除上下文 | ✅ 已实现 |
| `/exit` | P0 | src/commands/exit/ | 退出应用 | ✅ 已实现 |
| `/feedback` | P1 | src/commands/feedback/ | 提交反馈 | ⏳ 待实现 |
| `/stats` | P1 | src/commands/stats/ | 使用统计 | ⏳ 待实现 |
| `/share` | P1 | src/commands/share/ | 分享会话 | ⏳ 待实现 |
| `/upgrade` | P1 | src/commands/upgrade/ | 升级检查 | ⏳ 待实现 |
| `/plugin` | P0 | src/commands/plugin/ | 插件管理 | ⏳ 待实现 |
| `/mcp` | P0 | src/commands/mcp/ | MCP 管理 | ⏳ 待实现 |
| `/agents` | P0 | src/commands/agents/ | Agent 管理 | ⏳ 待实现 |
| `/branch` | P1 | src/commands/branch/ | 分支管理 | ⏳ 待实现 |
| `/debug` | P1 | src/commands/debug-tool-call/ | 调试工具调用 | ⏳ 待实现 |
| `/init` | P0 | src/commands/init/ | 初始化项目 |
| `/install` | P1 | src/commands/install/ | 安装依赖 |
| `/login` | P0 | src/commands/login/ | 登录账户 |
| `/logout` | P0 | src/commands/logout/ | 登出账户 |
| `/review` | P1 | src/commands/review/ | 代码审查 |
| `/status` | P0 | src/commands/status/ | 状态查看 |
| `/add-dir` | P1 | src/commands/add-dir/ | 添加目录 |
| `/backfill-sessions` | P2 | src/commands/backfill-sessions/ | 回填会话 |
| `/btw` | P2 | src/commands/btw/ | 顺便提醒 |
| `/chrome` | P2 | src/commands/chrome/ | Chrome 集成 |
| `/desktop` | P2 | src/commands/desktop/ | 桌面集成 |
| `/heapdump` | P2 | src/commands/heapdump/ | 内存转储 |
| `/issue` | P2 | src/commands/issue/ | Issue 管理 |
| `/keybindings` | P1 | src/commands/keybindings/ | 快捷键管理 |
| `/mobile` | P2 | src/commands/mobile/ | 移动集成 |
| `/model` | P1 | src/commands/model/ | 模型选择 |
| `/output-style` | P1 | src/commands/output-style/ | 输出样式 |
| `/passes` | P2 | src/commands/passes/ | Pass 管理 |
| `/perf-issue` | P2 | src/commands/perf-issue/ | 性能问题 |
| `/pr_comments` | P2 | src/commands/pr_comments/ | PR 评论 |
| `/quick-open` | P1 | - | 快速打开 |
| `/rename` | P2 | src/commands/rename/ | 重命名 |
| `/sandbox` | P1 | src/commands/sandbox-toggle/ | 沙盒切换 |
| `/tag` | P1 | src/commands/tag/ | 标签管理 |
| `/thinkback` | P2 | src/commands/thinkback/ | 回顾思考 |
| `/color` | P1 | src/commands/color/ | 颜色主题 |
| `/release-notes` | P1 | src/commands/release-notes/ | 发布说明 |
| `/insights` | P1 | src/commands/insights/ | 使用洞察 |
| `/security-review` | P1 | src/commands/security-review/ | 安全审查 |
| `/autofix-pr` | P2 | src/commands/autofix-pr/ | 自动修复 PR |
| `/bughunter` | P2 | src/commands/bughunter/ | Bug 追踪 |
| `/good-claude` | P2 | src/commands/good-claude/ | Good Claude 模式 |
| `/install-github-app` | P2 | src/commands/install-github-app/ | 安装 GitHub App |
| `/install-slack-app` | P2 | src/commands/install-slack-app/ | 安装 Slack App |

---

### 2.3 服务层差距 (Services)

#### 已实现服务 (8 个)
```
✅ AnalyticsService
✅ AuthService
✅ AutoUpdateService
✅ CommandHistoryService
✅ CostTrackerService
✅ FileHistoryService
✅ GitService
✅ SettingsService
```

#### 缺失服务 (需要新增 35+ 个)

| 服务名称 | 优先级 | TypeScript 参考 | 说明 |
|----------|--------|----------------|------|
| **LSP 服务** | | | |
| `LspDiagnosticRegistry` | P0 | src/services/lsp/LSPDiagnosticRegistry.ts | LSP 诊断注册 |
| `LspServerManager` | P0 | src/services/lsp/LSPServerManager.ts | LSP 服务器管理 |
| `LspClient` | P0 | src/services/lsp/LSPClient.ts | LSP 客户端 |
| `LspPassiveFeedback` | P1 | src/services/lsp/passiveFeedback.ts | LSP 被动反馈 |
| **MCP 服务** | | | |
| `McpChannelPermissions` | P0 | src/services/mcp/channelPermissions.ts | MCP 通道权限 |
| `McpChannelNotification` | P0 | src/services/mcp/channelNotification.ts | MCP 通道通知 |
| `McpClient` | P0 | src/services/mcp/client.ts | MCP 客户端 |
| `McpConfig` | P0 | src/services/mcp/config.ts | MCP 配置 |
| `McpConnectionManager` | P0 | src/services/mcp/MCPConnectionManager.tsx | MCP 连接管理 |
| `McpElicitationHandler` | P1 | src/services/mcp/elicitationHandler.ts | MCP 诱导处理 |
| `McpOAuth` | P1 | src/services/mcp/oauthPort.ts | MCP OAuth |
| `McpOfficialRegistry` | P1 | src/services/mcp/officialRegistry.ts | MCP 官方注册表 |
| `McpXaaIdp` | P1 | src/services/mcp/xaaIdp.ts | MCP XaaIdp |
| **会话服务** | | | |
| `SessionCompact` | P0 | src/services/compact/autoCompact.ts | 会话压缩 |
| `SessionMemory` | P0 | src/services/SessionMemory/sessionMemory.ts | 会话内存 |
| `ExtractMemories` | P1 | src/services/extractMemories/extractMemories.ts | 提取记忆 |
| `PromptSuggestion` | P1 | src/services/PromptSuggestion/promptSuggestion.ts | Prompt 建议 |
| **插件服务** | | | |
| `PluginService` | P0 | src/services/plugins/pluginOperations.ts | 插件操作 |
| `PluginInstallationManager` | P0 | src/services/plugins/PluginInstallationManager.ts | 插件安装管理 |
| `PluginLoader` | P0 | src/services/plugins/pluginOperations.ts | 插件加载器 |
| `PluginRegistry` | P1 | src/services/plugins/pluginCliCommands.ts | 插件注册表 |
| **工具服务** | | | |
| `ToolExecutionService` | P0 | src/services/tools/toolExecution.ts | 工具执行 |
| `ToolHooksService` | P0 | src/services/tools/toolHooks.ts | 工具钩子 |
| `ToolOrchestrationService` | P1 | src/services/tools/toolOrchestration.ts | 工具编排 |
| `StreamingToolExecutor` | P1 | src/services/tools/StreamingToolExecutor.ts | 流式工具执行 |
| **API 服务** | | | |
| `ApiService` | P0 | src/services/api/ | API 客户端 |
| `UsageService` | P0 | src/services/api/usage.ts | 使用量查询 |
| `MetricsService` | P1 | src/services/api/metricsOptOut.ts | 指标服务 |
| **其他服务** | | | |
| `MagicDocsService` | P1 | src/services/MagicDocs/magicDocs.ts | MagicDocs |
| `TipsService` | P1 | src/services/tips/tipScheduler.ts | 提示服务 |
| `TeamMemorySync` | P1 | src/services/teamMemorySync/ | 团队记忆同步 |
| `SettingsSync` | P1 | src/services/settingsSync/ | 设置同步 |
| `RemoteManagedSettings` | P1 | src/services/remoteManagedSettings/ | 远程设置管理 |
| `PolicyLimits` | P1 | src/services/policyLimits/ | 策略限制 |
| `AutoDreamService` | P2 | src/services/autoDream/autoDream.ts | 自动梦想服务 |
| `DiagnosticTrackingService` | P1 | src/services/diagnosticTracking.ts | 诊断追踪 |
| `VoiceService` | P1 | src/services/voice.ts | 语音服务 |
| `VoiceStreamSTT` | P1 | src/services/voiceStreamSTT.ts | 语音流识别 |

---

### 2.4 UI 组件差距 (Components)

#### 已实现 UI 组件 (5 个)
```
✅ BuddyAnimation
✅ DialogSystem
✅ KeyboardBindings
✅ ProgressComponent
✅ ThemeSystem
```

#### 缺失基础组件 (需要新增 50+ 个)

| 组件名称 | 优先级 | TypeScript 参考 | 说明 |
|----------|--------|----------------|------|
| **设计系统组件** | | | |
| `Box` | P0 | src/components/design-system/ThemedBox.tsx | 基础容器 |
| `Text` | P0 | src/components/design-system/ThemedText.tsx | 文本组件 |
| `Dialog` | P0 | src/components/design-system/Dialog.tsx | 对话框 |
| `Tabs` | P0 | src/components/design-system/Tabs.tsx | 标签页 |
| `ProgressBar` | P0 | src/components/design-system/ProgressBar.tsx | 进度条 |
| `LoadingState` | P0 | src/components/design-system/LoadingState.tsx | 加载状态 |
| `Spinner` | P0 | src/components/Spinner/ | 旋转加载器 |
| `Divider` | P1 | src/components/design-system/Divider.tsx | 分隔线 |
| `StatusIcon` | P0 | src/components/design-system/StatusIcon.tsx | 状态图标 |
| `Ratchet` | P1 | src/components/design-system/Ratchet.tsx | 棘轮组件 |
| `Pane` | P1 | src/components/design-system/Pane.tsx | 面板 |
| `FuzzyPicker` | P1 | src/components/design-system/FuzzyPicker.tsx | 模糊选择器 |
| `ListItem` | P1 | src/components/design-system/ListItem.tsx | 列表项 |
| **消息组件** | | | |
| `Message` | P0 | src/components/Message.tsx | 消息行 |
| `Messages` | P0 | src/components/Messages.tsx | 消息列表 |
| `MessageResponse` | P0 | src/components/MessageResponse.tsx | 消息响应 |
| `MessageRow` | P0 | src/components/MessageRow.tsx | 消息行 |
| `MessageSelector` | P1 | src/components/MessageSelector.tsx | 消息选择器 |
| `MessageTimestamp` | P1 | src/components/MessageTimestamp.tsx | 消息时间戳 |
| `VirtualMessageList` | P1 | src/components/VirtualMessageList.tsx | 虚拟消息列表 |
| **工具 UI 组件** | | | |
| `ToolUseLoader` | P0 | src/components/ToolUseLoader.tsx | 工具加载器 |
| `FileEditToolDiff` | P0 | src/components/FileEditToolDiff.tsx | 文件编辑差异 |
| `StatusLine` | P0 | src/components/StatusLine.tsx | 状态行 |
| `AgentProgressLine` | P0 | src/components/AgentProgressLine.tsx | Agent 进度 |
| `BashModeProgress` | P0 | src/components/BashModeProgress.tsx | Bash 模式进度 |
| `CompactSummary` | P0 | src/components/CompactSummary.tsx | 压缩摘要 |
| `EffortIndicator` | P1 | src/components/EffortIndicator.ts | 工作量指示器 |
| `TeleportProgress` | P1 | src/components/TeleportProgress.tsx | 传送进度 |
| **对话框组件** | | | |
| `ExportDialog` | P0 | src/components/ExportDialog.tsx | 导出对话框 |
| `FeedbackDialog` | P0 | src/components/Feedback.tsx | 反馈对话框 |
| `QuickOpenDialog` | P0 | src/components/QuickOpenDialog.tsx | 快速打开对话框 |
| `SearchBox` | P0 | src/components/SearchBox.tsx | 搜索框 |
| `GlobalSearchDialog` | P0 | src/components/GlobalSearchDialog.tsx | 全局搜索对话框 |
| `MCPServerApprovalDialog` | P0 | src/components/MCPServerApprovalDialog.tsx | MCP 服务器审批对话框 |
| `MCPServerDialogCopy` | P1 | src/components/MCPServerDialogCopy.tsx | MCP 服务器复制对话框 |
| `InvalidSettingsDialog` | P0 | src/components/InvalidSettingsDialog.tsx | 无效设置对话框 |
| `AutoModeOptInDialog` | P1 | src/components/AutoModeOptInDialog.tsx | 自动模式对话框 |
| `CostThresholdDialog` | P1 | src/components/CostThresholdDialog.tsx | 成本阈值对话框 |
| **状态组件** | | | |
| `DiagnosticsDisplay` | P0 | src/components/DiagnosticsDisplay.tsx | 诊断显示 |
| `MemoryUsageIndicator` | P1 | src/components/MemoryUsageIndicator.tsx | 内存使用指示器 |
| `StatusNotices` | P1 | src/components/StatusNotices.tsx | 状态通知 |
| `FastIcon` | P1 | src/components/FastIcon.tsx | 快速图标 |
| `CostTracker` | P0 | - | 成本追踪器 |
| **其他组件** | | | |
| `FilePathLink` | P0 | src/components/FilePathLink.tsx | 文件路径链接 |
| `HighlightedCode` | P0 | src/components/HighlightedCode.tsx | 高亮代码 |
| `StructuredDiff` | P0 | src/components/StructuredDiff.tsx | 结构化差异 |
| `Markdown` | P0 | src/components/Markdown.tsx | Markdown 渲染 |
| `PressEnterToContinue` | P0 | src/components/PressEnterToContinue.tsx | 按回车继续 |
| `ConfigurableShortcutHint` | P1 | src/components/ConfigurableShortcutHint.tsx | 快捷键提示 |
| `KeybindingWarnings` | P1 | src/components/KeybindingWarnings.tsx | 快捷键警告 |
| `ModelPicker` | P0 | src/components/ModelPicker.tsx | 模型选择器 |
| `OutputStylePicker` | P1 | src/components/OutputStylePicker.tsx | 输出样式选择器 |
| `ThemePicker` | P1 | src/components/ThemePicker.tsx | 主题选择器 |
| `LanguagePicker` | P1 | src/components/LanguagePicker.tsx | 语言选择器 |
| `TaskList` | P0 | src/components/TaskListV2.tsx | 任务列表 |
| `Stats` | P1 | src/components/Stats.tsx | 统计信息 |

---

### 2.5 Agent 系统差距

#### 当前状态：❌ 完全缺失

TypeScript 版本有完整的 Agent 系统：

```
src/tools/AgentTool/
├── agentColorManager.ts      # Agent 颜色管理
├── agentDisplay.ts           # Agent 显示系统
├── agentMemory.ts            # Agent 内存
├── agentMemorySnapshot.ts    # Agent 内存快照
├── AgentTool.tsx             # Agent 工具主组件
├── agentToolUtils.ts         # Agent 工具工具函数
├── builtInAgents.ts          # 内置 Agent
├── constants.ts              # 常量
├── forkSubagent.ts           # 子代理分叉
├── loadAgentsDir.ts          # 加载 Agent 目录
├── prompt.ts                 # Prompt
├── resumeAgent.ts            # 恢复 Agent
├── runAgent.ts               # 运行 Agent
├── UI.tsx                    # Agent UI
└── built-in/                 # 内置 Agent 目录
```

#### 需要新增的 Agent 组件

| 组件名称 | 优先级 | 说明 |
|----------|--------|------|
| `AgentManager` | P0 | Agent 管理器 |
| `AgentColorManager` | P0 | Agent 颜色管理 |
| `AgentDisplay` | P0 | Agent 显示系统 |
| `AgentMemory` | P0 | Agent 内存 |
| `BuiltInAgents` | P0 | 内置 Agent 定义 |
| `AgentTool` (增强版) | P0 | Agent 工具 (带 UI) |
| `AgentCommands` | P0 | Agent 相关命令 |
| `AgentUIComponents` | P0 | Agent UI 组件 |

---

### 2.6 Buddy 系统差距

#### 当前状态：⚠️ 部分实现

已实现：
- `BuddyAnimation.java`
- `DialogSystem.java`

#### 缺失组件

| 组件名称 | 优先级 | TypeScript 参考 | 说明 |
|----------|--------|----------------|------|
| `Companion` | P1 | src/buddy/companion.ts | 伙伴精灵主类 |
| `CompanionSprite` | P1 | src/buddy/CompanionSprite.tsx | 伙伴精灵渲染 |
| `CompanionPrompt` | P1 | src/buddy/prompt.ts | 伙伴提示 |
| `CompanionNotification` | P1 | src/buddy/useBuddyNotification.tsx | 伙伴通知 |
| `SpriteRegistry` | P2 | src/buddy/sprites.ts | 精灵注册表 |

---

### 2.7 LSP 服务差距

#### 当前状态：⚠️ 基础实现

已实现：
- `LspService.java`
- `LspModels.java`

#### 缺失组件

| 组件名称 | 优先级 | TypeScript 参考 | 说明 |
|----------|--------|----------------|------|
| `LspDiagnosticRegistry` | P0 | src/services/lsp/LSPDiagnosticRegistry.ts | LSP 诊断注册 |
| `LspServerManager` | P0 | src/services/lsp/LSPServerManager.ts | LSP 服务器管理 |
| `LspClient` | P0 | src/services/lsp/LSPClient.ts | LSP 客户端 |
| `LspPassiveFeedback` | P1 | src/services/lsp/passiveFeedback.ts | LSP 被动反馈 |

---

### 2.8 MCP 服务差距

#### 当前状态：⚠️ 基础实现

#### 缺失组件

| 组件名称 | 优先级 | TypeScript 参考 | 说明 |
|----------|--------|----------------|------|
| `McpChannelPermissions` | P0 | src/services/mcp/channelPermissions.ts | MCP 通道权限 |
| `McpChannelNotification` | P0 | src/services/mcp/channelNotification.ts | MCP 通道通知 |
| `McpClient` | P0 | src/services/mcp/client.ts | MCP 客户端 |
| `McpConfig` | P0 | src/services/mcp/config.ts | MCP 配置 |
| `McpConnectionManager` | P0 | src/services/mcp/MCPConnectionManager.tsx | MCP 连接管理 |
| `McpElicitationHandler` | P1 | src/services/mcp/elicitationHandler.ts | MCP 诱导处理 |
| `McpOAuth` | P1 | src/services/mcp/oauthPort.ts | MCP OAuth |
| `McpOfficialRegistry` | P1 | src/services/mcp/officialRegistry.ts | MCP 官方注册表 |
| `McpXaaIdp` | P1 | src/services/mcp/xaaIdp.ts | MCP XaaIdp |

---

## 三、剩余开发工作量

### 3.1 代码量估算

| 模块 | 新增类数量 | 预计代码行数 | 优先级 |
|------|-----------|-------------|--------|
| 工具系统 | 12 个工具 + 12 个 UI | ~3,500 行 | P0 |
| 命令系统 | 40+ 个命令 | ~4,000 行 | P0 |
| 服务层 | 35+ 个服务 | ~6,000 行 | P0 |
| UI 组件 | 50+ 个组件 | ~5,000 行 | P0 |
| Agent 系统 | 8 个组件 | ~1,500 行 | P0 |
| Buddy 系统 | 5 个组件 | ~500 行 | P1 |
| LSP 增强 | 4 个组件 | ~800 行 | P0 |
| MCP 增强 | 9 个组件 | ~1,500 行 | P0 |
| **总计** | **~175 个类** | **~22,800 行** | |

### 3.2 测试工作量

| 测试类型 | 测试类数量 | 预计代码行数 |
|----------|-----------|-------------|
| 工具单元测试 | 45 个 | ~4,500 行 |
| 命令单元测试 | 67 个 | ~6,700 行 |
| 服务单元测试 | 43 个 | ~4,300 行 |
| UI 组件测试 | 55 个 | ~5,500 行 |
| 集成测试 | 20 个 | ~3,000 行 |
| **总计** | **~230 个测试类** | **~24,000 行** |

---

## 四、开发优先级排序

### P0 - 核心功能 (必须实现)

1. **工具系统完善** (Week 1-2)
   - FileReadTool, FileEditTool, GlobTool, GrepTool
   - BashTool 增强
   - 所有工具的 UI 组件

2. **核心命令** (Week 2-3)
   - /copy, /cost, /diff, /files, /summary
   - /plugin, /mcp, /agents
   - /clear, /exit, /login, /logout, /status

3. **核心服务** (Week 3-5)
   - LSP 服务增强 (LspDiagnosticRegistry, LspServerManager)
   - MCP 服务增强 (McpChannelPermissions, McpConnectionManager)
   - 会话压缩服务 (SessionCompact)

4. **UI 框架** (Week 5-7)
   - 终端 UI 框架基础
   - 设计系统组件 (Box, Text, Dialog, Tabs, ProgressBar, Spinner)
   - 消息组件 (Message, Messages)

5. **Agent 系统** (Week 8)
   - AgentManager, AgentColorManager, AgentDisplay
   - AgentTool 增强版

### P1 - 重要功能 (应该实现)

1. **插件系统** (Week 4)
   - PluginService, PluginInstallationManager

2. **Buddy 系统** (Week 9)
   - Companion, CompanionSprite

3. **记忆系统** (Week 10)
   - SessionMemory, ExtractMemories, PromptSuggestion

4. **更多命令** (Week 3-4)
   - /feedback, /stats, /share, /upgrade
   - /branch, /debug, /keybindings, /model

5. **更多服务** (Week 5-6)
   - ToolExecutionService, ToolHooksService
   - ApiService, UsageService

### P2 - 增强功能 (可选实现)

1. **高级工具** (Week 2)
   - SkillTool, WebSearchTool, ForkSubagentTool

2. **高级命令** (Week 4)
   - /backfill-sessions, /btw, /chrome, /desktop
   - /heapdump, /issue, /mobile, /perf-issue

3. **高级服务** (Week 6)
   - AutoDreamService, DiagnosticTrackingService
   - VoiceService, TeamMemorySync

---

## 五、建议开发顺序

### 第一阶段：基础补齐 (Week 1-4)

```
Week 1: 工具系统
- FileReadTool + UI
- FileEditTool + UI
- GlobTool + UI
- GrepTool + UI

Week 2: 工具系统继续 + 命令系统开始
- BashTool 增强 + UI
- MCPTool + UI
- AgentTool + UI
- /copy, /cost, /diff, /files 命令

Week 3: 命令系统继续
- /summary, /plugin, /mcp, /agents 命令
- /clear, /exit, /login, /logout, /status 命令
- /feedback, /stats, /share, /upgrade 命令

Week 4: 服务层基础
- LspDiagnosticRegistry
- LspServerManager
- McpChannelPermissions
- McpConnectionManager
```

### 第二阶段：UI/UX 提升 (Week 5-7)

```
Week 5: 终端 UI 框架
- 终端 UI 框架基础 (使用 Lanterna)
- Box, Text 基础组件

Week 6: 设计系统组件
- Dialog, Tabs, ProgressBar
- LoadingState, Spinner, Divider
- StatusIcon

Week 7: 消息和工具 UI
- Message, Messages, MessageResponse
- ToolUseLoader, FileEditDiff
- StatusLine
```

### 第三阶段：高级功能 (Week 8-10)

```
Week 8: Agent 系统
- AgentManager, AgentColorManager
- AgentDisplay, AgentMemory
- BuiltInAgents

Week 9: Buddy 系统 + 插件服务
- Companion, CompanionSprite
- PluginService, PluginInstallationManager

Week 10: 会话管理
- SessionCompact (自动压缩)
- SessionMemory
- PromptSuggestion
```

### 第四阶段：测试与文档 (Week 11-12)

```
Week 11: 测试覆盖
- 工具单元测试
- 命令单元测试
- 服务单元测试
- UI 组件测试
- 集成测试

Week 12: 文档完善
- API 文档 (Javadoc)
- 工具参考文档
- 命令参考文档
- 服务架构文档
- 故障排查文档
```

---

## 六、新增模块结构

### 6.1 jwcode-ui 模块 (新增)

```
jwcode-ui/
├── pom.xml
└── src/
    ├── main/
    │   └── java/
    │       └── com/jwcode/ui/
    │           ├── terminal/
    │           │   ├── TerminalRenderer.java
    │           │   ├── TerminalInput.java
    │           │   └── TerminalBuffer.java
    │           ├── components/
    │           │   ├── Box.java
    │           │   ├── Text.java
    │           │   ├── Dialog.java
    │           │   ├── Tabs.java
    │           │   ├── ProgressBar.java
    │           │   ├── LoadingState.java
    │           │   ├── Spinner.java
    │           │   ├── Message.java
    │           │   ├── Messages.java
    │           │   └── StatusLine.java
    │           └── theme/
    │               ├── Theme.java
    │               └── Colors.java
    └── test/
        └── java/
            └── com/jwcode/ui/
```

### 6.2 jwcode-core 新增包结构

```
jwcode-core/src/main/java/com/jwcode/core/
├── agent/               # [新增] Agent 管理
│   ├── AgentManager.java
│   ├── AgentColorManager.java
│   ├── AgentDisplay.java
│   └── AgentMemory.java
├── buddy/               # [增强] Buddy 伙伴
│   ├── Companion.java
│   ├── CompanionSprite.java
│   └── CompanionNotification.java
├── compact/             # [新增] 会话压缩
│   ├── CompactService.java
│   └── AutoCompactTrigger.java
├── plugins/             # [新增] 插件服务
│   ├── PluginService.java
│   └── PluginInstallationManager.java
└── tool/
    └── ui/              # [新增] 工具 UI 组件
        ├── FileReadToolUI.java
        ├── FileEditToolUI.java
        ├── BashToolUI.java
        └── ...
```

---

## 七、Maven 依赖配置

### 7.1 新增 jwcode-ui 模块依赖

```xml
<!-- jwcode-ui/pom.xml -->
<dependencies>
    <!-- 终端 UI 库 -->
    <dependency>
        <groupId>com.googlecode.lanterna</groupId>
        <artifactId>lanterna</artifactId>
        <version>3.1.1</version>
    </dependency>
    
    <!-- 测试依赖 -->
    <dependency>
        <groupId>org.junit.jupiter</groupId>
        <artifactId>junit-jupiter</artifactId>
        <version>5.10.0</version>
        <scope>test</scope>
    </dependency>
</dependencies>
```

### 7.2 核心模块新增依赖

```xml
<!-- jwcode-core/pom.xml -->
<dependencies>
    <!-- LSP4J for LSP support -->
    <dependency>
        <groupId>org.eclipse.lsp4j</groupId>
        <artifactId>org.eclipse.lsp4j</artifactId>
        <version>0.20.1</version>
    </dependency>
    
    <!-- Mockito for testing -->
    <dependency>
        <groupId>org.mockito</groupId>
        <artifactId>mockito-core</artifactId>
        <version>5.5.0</version>
        <scope>test</scope>
    </dependency>
    
    <!-- jwcode-ui -->
    <dependency>
        <groupId>com.jwcode</groupId>
        <artifactId>jwcode-ui</artifactId>
        <version>1.0.0</version>
    </dependency>
</dependencies>
```

---

## 八、里程碑与验收

### 里程碑 1 (Week 4): 核心功能完成

**验收标准**:
- [ ] FileReadTool, FileEditTool, GlobTool, GrepTool 已实现
- [ ] BashTool 增强已完成
- [ ] 12 个核心命令已实现
- [ ] LspDiagnosticRegistry, LspServerManager 已实现
- [ ] McpChannelPermissions, McpConnectionManager 已实现

### 里程碑 2 (Week 7): UI/UX 完成

**验收标准**:
- [ ] jwcode-ui 模块已创建并可用
- [ ] 终端 UI 框架可运行
- [ ] 所有设计系统组件已实现
- [ ] 消息组件已实现
- [ ] 工具 UI 组件已实现

### 里程碑 3 (Week 10): 高级功能完成

**验收标准**:
- [ ] Agent 系统已完善
- [ ] Buddy 伙伴系统已实现
- [ ] 会话压缩功能可用
- [ ] 插件服务已实现

### 里程碑 4 (Week 12): 测试与文档完成

**验收标准**:
- [ ] 单元测试覆盖率 > 80%
- [ ] 集成测试覆盖核心流程
- [ ] 所有文档已完成
- [ ] API 文档完整

---

## 九、风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| 终端 UI 库选择困难 | 高 | 中 | 提前评估 Lanterna 和 Jexer，优先使用 Lanterna |
| LSP 协议复杂性 | 高 | 中 | 使用现有 LSP4J 库，参考 VSCode Java 实现 |
| 测试工作量超预期 | 中 | 高 | 引入测试自动生成工具，优先测试核心功能 |
| 时间延期 | 高 | 中 | 分阶段交付，优先 P0 功能 |
| Maven 模块依赖问题 | 中 | 低 | 提前规划模块依赖关系，使用 parent pom 管理 |

---

## 十、总结

### 剩余工作量

| 类别 | 数量 | 预计工时 |
|------|------|----------|
| 新增工具 | 12 个 | 10 天 |
| 新增命令 | 40+ 个 | 15 天 |
| 新增服务 | 35+ 个 | 20 天 |
| 新增 UI 组件 | 50+ 个 | 15 天 |
| 测试代码 | 230 个类 | 10 天 |
| 文档 | 10+ 个 | 5 天 |
| **总计** | **~175 个类** | **75 天 (15 周)** |

### 建议

1. **优先实现 P0 功能**，确保核心功能完整
2. **分阶段交付**，每 2 周交付一个可用版本
3. **测试驱动开发**，边开发边测试
4. **参考 TypeScript 版本**，保持功能一致性
5. **文档同步更新**，避免文档滞后

---

*本文档将定期更新，反映最新开发进度。*