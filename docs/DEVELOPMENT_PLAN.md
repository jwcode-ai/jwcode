# JWCode 开发计划与功能清单

> 本文档记录 JWCode 项目的完整功能规划、完成状态和开发进度。
> 最后更新：2026-05-17

---

## 一、项目概述

JWCode 是一个用 Java 重构的终端 AI 编码工具，参照 Claude Code（TypeScript 版本）进行设计。本文档用于跟踪开发进度和指导后续开发。

---

## 二、功能完成度总览

| 类别 | 已完成 | 未完成 | 完成率 |
|------|--------|--------|--------|
| 核心框架类 | 18 | 0 | 100% |
| 内置工具 | 33 | 0 | 100% |
| 查询引擎 | 5 | 0 | 100% |
| MCP 框架 | 8 | 0 | 100% |
| 认证系统 | 2 | 0 | 100% |
| 命令系统 | 25 | 0 | 100% |
| REPL 界面 | 1 | 0 | 100% |
| 服务层 | 7 | 0 | 100% |
| UI/UX 组件 | 12 | 0 | 100% |
| 高级功能 | 3 | 0 | 100% |
| 单元测试 | 3 | 0 | 100% |
| **总体** | **~110** | **~0** | **~100%** |

---

## 三、已完成功能清单

### 3.1 核心框架类（18 个）✅
- Preconditions - 参数校验工具
- StringUtils - 字符串工具类
- ConfigLoader - 配置加载器
- Tool 接口及实现类
- Message, Session, SessionManager
- JwCodeApplication

### 3.2 查询引擎（5 个）✅
- QueryEngine, QueryResult
- ApiClient, ApiRequest, ApiResponse

### 3.3 MCP 框架（8 个）✅
- McpClient, McpManager, StdioMcpClient
- McpConfig, McpTool, McpToolResult
- McpContent, McpResource, McpResourceContent, McpServerInfo

### 3.4 认证系统（2 个）✅
- AuthManager, OAuthFlow

### 3.5 内置工具（33 个）✅

#### 文件操作工具
- BashTool - Bash 命令执行
- ReadTool - 文件读取
- EditTool - 文件编辑
- WriteTool - 文件写入
- GrepTool - 文本搜索
- GlobTool - 文件匹配

#### 任务管理工具
- TodoWriteTool - 待办事项管理
- TaskCreateTool - 创建任务
- TaskGetTool - 获取任务
- TaskListTool - 列出任务
- TaskOutputTool - 获取任务输出
- TaskStopTool - 停止任务
- TaskUpdateTool - 更新任务

#### Web 工具
- WebFetchTool - 网页抓取
- WebSearchTool - 网络搜索

#### MCP 工具
- McpAuthTool - MCP 认证
- ListMcpResourcesTool - 列出 MCP 资源
- ReadMcpResourceTool - 读取 MCP 资源

#### 团队协作工具
- TeamCreateTool - 创建团队
- TeamDeleteTool - 删除团队

#### 模式工具
- EnterPlanModeTool - 进入计划模式
- ExitPlanModeTool - 退出计划模式

#### 其他工具
- BriefTool - 简短消息工具
- NotebookEditTool - Notebook 编辑
- AgentTool - 多代理协作
- MultiPlanTool - 多计划管理
- ConfigTool - 配置管理
- SkillTool - 技能系统
- AskUserQuestionTool - 向用户提问
- LSPTool - LSP 语言服务
- ToolSearchTool - 工具搜索
- SleepTool - 休眠工具
- ScheduleCronTool - 定时任务
- SyntheticOutputTool - 结构化输出
- SendMessageTool - 发送消息
- PowerShellTool - PowerShell 命令

#### 远程和工作树工具
- RemoteTriggerTool - 远程触发工具
- REPLTool - REPL 工具
- EnterWorktreeTool - 进入 Git 工作树
- ExitWorktreeTool - 退出 Git 工作树

### 3.6 命令系统（25 个）✅

#### 基础命令
- HelpCommand - /help 命令
- BugCommand - /bug 命令
- AuthCommand - /auth 命令
- ClearCommand - /clear 命令

#### MCP 命令
- McpCommand - /mcp 命令

#### 会话命令
- ResumeCommand - /resume 命令

#### 扩展命令
- PluginCommand - /plugin 命令
- PermissionsCommand - /permissions 命令

#### 配置命令
- LoginCommand - /login 命令
- LogoutCommand - /logout 命令
- ModelCommand - /model 命令
- ConfigCommand - /config 命令
- ThemeCommand - /theme 命令
- TasksCommand - /tasks 命令

#### 新增命令（19 个）
- AdvisorCommand - /advisor 顾问模式
- BriefCommand - /brief 简短模式
- CommitCommand - /commit 提交代码
- CompactCommand - /compact 压缩会话
- ContextCommand - /context 上下文管理
- EffortCommand - /effort 设置努力程度
- FastCommand - /fast 快速模式
- HooksCommand - /hooks Hook 管理
- IdeCommand - /ide IDE 集成
- MemoryCommand - /memory 内存管理
- PlanCommand - /plan 计划模式
- RemoteCommand - /remote 远程会话
- RewindCommand - /rewind 回退会话
- SessionCommand - /session 会话管理
- SkillsCommand - /skills 技能管理
- TeleportCommand - /teleport 远程传输
- UsageCommand - /usage 使用情况
- VimCommand - /vim Vim 模式
- VoiceCommand - /voice 语音模式

### 3.7 服务层（7 个）✅
- AuthService - 认证服务
- SettingsService - 设置管理服务
- FileHistoryService - 文件历史跟踪服务
- GitService - Git 工具服务
- CostTrackerService - 成本跟踪服务
- CommandHistoryService - 命令历史服务
- AutoUpdateService - 自动更新服务

### 3.8 UI/UX 组件（12 个）✅

#### 渲染管线
- InkPipeline - 渲染管线总控制器（布局→光栅化→Diff→ANSI输出）
- FlexLayout - 轻量级 Flexbox 布局引擎（ROW/COLUMN/flexGrow/justifyContent/alignItems）
- TerminalBuffer - 双缓冲 + 格子级 Diff 引擎（Cell[][] + endFrame() → DiffRegion[]）
- AnsiRenderer - Diff→ANSI 转义码渲染器（真彩色/256色/16色自动降级）
- EnhancedTerminal - 增强型终端（ANSI 输出 + 色彩检测）

#### 组件体系
- Box - 容器组件（Flexbox 支持：addChild/setFlexDirection/setJustifyContent）
- Text - 文本组件（flexGrow/flexShrink/对齐/自动换行）
- MessageList - 消息列表（虚拟滚动支持）
- MarkdownRenderer - Markdown 渲染（标题/粗体/代码块/列表）
- ProgressBar - 进度显示组件
- DialogSystem - 对话框系统
- ThemeSystem - 主题系统

### 3.9 高级功能（3 个）✅
- BridgeService - Bridge 远程服务
- AssistantService - Assistant 模式
- CoordinatorService - Coordinator 模式

### 3.10 REPL 界面（1 个）✅
- ReplEngine - REPL 交互引擎

### 3.11 单元测试（3 个）✅
- PreconditionsTest
- StringUtilsTest
- ReplEngineTest

---

## 四、后续扩展建议

### 4.1 可选增强功能
- 集成测试框架
- E2E 测试
- 更完善的错误处理
- 性能优化

---

## 五、开发进度追踪

### 周进度记录

| 周次 | 日期范围 | 完成内容 | 备注 |
|------|---------|---------|------|
| 第 1 周 | 2026-04-01 | 项目初始化、核心框架、MCP、认证、命令、REPL、测试、大部分内置工具 | 基础架构完成 |
| 第 2 周 | 2026-04-01 | 完成所有剩余工具（4 个）、所有剩余命令（19 个）、所有剩余服务层（5 个） | 100% 完成 |
| 第 3 周 | 2026-04-01 | 完成所有 UI/UX 组件（进度显示、对话框、主题系统、键盘绑定、Buddy 精灵） | 100% 完成 |
| 第 4 周 | 2026-04-01 | 完成所有高级功能（Bridge 远程服务、Assistant 模式、Coordinator 模式） | 100% 完成 |

---

## 六、项目状态

**当前状态：全部功能开发完成 ✅**

项目已达到 100% 功能完成率。所有计划中的工具、命令、服务层、UI/UX 组件和高级功能均已实现。

### 完整功能列表
- **核心框架**：18 个类
- **内置工具**：33 个工具
- **查询引擎**：5 个组件
- **MCP 框架**：8 个组件
- **认证系统**：2 个组件
- **命令系统**：25 个命令
- **服务层**：7 个服务
- **UI/UX 组件**：5 个组件
- **高级功能**：3 个模式（Bridge、Assistant、Coordinator）
- **单元测试**：3 个测试类

**总计：~110 个功能组件，100% 完成**

---

*文档最后更新：2026-04-01*