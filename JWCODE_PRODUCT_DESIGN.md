# JwCode 产品设计文档

> **版本**: 2.0 | **更新日期**: 2026-04-27 | **用途**: AI 编码助手了解产品设计与现状

---

## 目录

1. [产品概述](#1-产品概述)
2. [项目架构](#2-项目架构)
3. [模块详解](#3-模块详解)
4. [配置系统](#4-配置系统)
5. [模型系统](#5-模型系统)
6. [Agent 系统](#6-agent-系统)
7. [UI 系统](#7-ui-系统)
8. [当前状态与待办](#8-当前状态与待办)

---

## 1. 产品概述

JwCode 是一个用 **Java 17+** 重构的终端 AI 编码工具，对标 TypeScript 版 Claude Code。它提供一个交互式终端环境，让 AI 助手能够直接操作文件、执行命令、搜索代码、管理任务等。

### 1.1 核心能力

| 能力 | 说明 |
|------|------|
| 🤖 **多模型支持** | 支持 OpenAI 兼容 API，可配置多个模型提供商及密钥轮询 |
| 🛠️ **40+ 工具** | 文件操作、代码搜索、Web 搜索、任务管理、Shell 执行等 |
| 📝 **50+ 命令** | 丰富的 CLI 命令，支持交互式操作、管道、历史记录 |
| 💬 **流式响应** | 实时显示 AI 生成内容和思考过程 |
| 🌐 **Web 界面** | 内置 Web 管理界面，可实时查看模型状态 |
| 🧠 **Agent Swarm** | 智能体集群，支持任务分解、并行执行、依赖调度 |
| 🔄 **密钥轮询** | 多 API Key 自动轮询和故障转移 |
| 📋 **活动日志** | 实时显示 AI 操作过程和执行时间 |

### 1.2 技术栈

| 技术 | 版本/说明 |
|------|----------|
| Java | 17+ |
| 构建工具 | Maven 3.8+ |
| 终端框架 | JLine (交互式 CLI) |
| HTTP 客户端 | OkHttp |
| JSON 处理 | Jackson |
| Web 服务 | Spring Boot (内置) |
| 许可协议 | Apache 2.0 |

---

## 2. 项目架构

### 2.1 顶层架构图

```
┌─────────────────────────────────────────────────────────────┐
│                       JWCode (CLI)                          │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────────┐  │
│  │  jwcode-cli  │  │  jwcode-ui   │  │   jwcode-web     │  │
│  │  (终端交互)  │  │  (显示层)    │  │  (Web 管理界面)  │  │
│  └──────┬───────┘  └──────┬───────┘  └────────┬─────────┘  │
│         └─────────────────┼───────────────────┘             │
│                            ▼                                │
│  ┌──────────────────────────────────────────────────────┐   │
│  │                    jwcode-core                       │   │
│  │  ┌──────────┐ ┌──────────┐ ┌────────────────────┐   │   │
│  │  │ ApiClient│ │ToolSystem│ │  ModelPoolManager  │   │   │
│  │  │ (API调用)│ │(工具系统)│ │  (模型池化管理)    │   │   │
│  │  └──────────┘ └──────────┘ └────────────────────┘   │   │
│  └──────────────────────────────────────────────────────┘   │
│                            ▲                                │
│  ┌──────────────────────────────────────────────────────┐   │
│  │  jwcode-common  │  jwcode-mcp  │  jwcode-parser     │   │
│  │  (公共工具)     │  (MCP协议)   │  (代码解析)        │   │
│  └──────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 组件定位

| 层级 | 模块 | 职责 |
|------|------|------|
| **应用层** | `jwcode-cli` | 命令行交互界面，命令解析、REPL 循环 |
| **应用层** | `jwcode-web` | 内置 Web 服务器，提供模型状态查看等 API |
| **显示层** | `jwcode-ui` | UI 渲染组件，活动日志显示 |
| **核心层** | `jwcode-core` | AI 客户端、工具系统、模型池、会话管理 |
| **基础层** | `jwcode-common` | 公共工具类、常量、枚举 |
| **扩展层** | `jwcode-mcp` | MCP (Model Context Protocol) 桥接 |
| **扩展层** | `jwcode-parser` | 代码解析与分析 |
| **运行时** | `jwcode-repl` | REPL (Read-Eval-Print-Loop) 交互式会话管理 |
| **分发** | `jwcode-distribution` | 打包和分发的 Maven 配置 |
| **父 POM** | `jwcode-parent` | 公共 Maven 依赖和插件管理 |

### 2.3 Maven 模块结构

```
jwcode/                      # 根 POM (pom)
├── jwcode-parent/           # 父 POM，统一依赖和插件版本
├── jwcode-common/           # 公共模块
├── jwcode-core/             # 核心业务逻辑
├── jwcode-cli/              # CLI 终端界面
├── jwcode-ui/               # UI 显示组件
├── jwcode-web/              # Web 管理界面
├── jwcode-mcp/              # MCP 协议扩展
├── jwcode-parser/           # 代码解析
├── jwcode-repl/             # REPL 会话
└── jwcode-distribution/     # 打包分发
```

---

## 3. 模块详解

### 3.1 jwcode-core（核心模块）

核心模块是 JwCode 的大脑，包含以下子系统：

```
com.jwcode.core/
├── api/              # API 客户端 (ApiClient)
│   ├── ApiClient.java        # 主要 AI API 调用客户端
│   ├── ChatSession.java      # 对话会话管理
│   └── StreamingHandler.java # 流式响应处理
├── model/            # 模型池
│   ├── ModelPoolManager.java # 模型池管理器（负载均衡、健康检查、故障转移）
│   ├── ModelInstance.java    # 模型实例
│   ├── ProviderType.java     # 提供商类型枚举
│   └── LoadBalanceStrategy.java # 负载均衡策略
├── tool/             # 工具系统
│   ├── ToolRegistry.java     # 工具注册中心
│   ├── ToolExecutor.java     # 工具执行引擎
│   └── tools/                # 具体工具实现（40+ 个）
├── config/           # 配置管理
│   ├── ConfigManager.java    # 配置管理器
│   └── YamlConfigLoader.java # YAML 配置加载
└── session/          # 会话管理
    ├── SessionManager.java   # 会话管理器
    └── Conversation.java     # 对话记录
```

### 3.2 jwcode-cli（CLI 模块）

CLI 模块提供终端交互界面，支持 50+ 命令。

**主要组件：**
- `JwCodeRepl.java` - 主 REPL 循环
- `CommandRegistry.java` - 命令注册
- `CommandExecutor.java` - 命令执行
- `completer/` - 自动补全
- `highlight/` - 语法高亮
- `log/` - 日志显示（含活动日志 ActivityLogger）

**关键特性：**
- JLine 驱动的交互式终端
- Tab 键自动补全
- 命令历史记录
- 流式响应实时显示
- 活动日志实时追踪 AI 操作

### 3.3 jwcode-ui（UI 模块）

提供终端 UI 渲染组件。

**主要组件：**
- `ActivityLogger.java` - AI 活动实时日志（类似 KimiCode 体验）
- `ProgressBar.java` - 进度条显示
- `ColorFormatter.java` - 颜色格式化
- `StatusBar.java` - 状态栏

**活动日志示例：**
```
[14:32:10] ▶️ 📄 读取文件 读取 src/main/java 256 行 (45ms)
[14:32:11] ▶️ 🔍 搜索代码 搜索: class ActivityLogger 5 个匹配 (23ms)
[14:32:12] ▶️ ⚡ 执行命令 执行: mvn clean compile BUILD SUCCESS (1.2s)
```

### 3.4 jwcode-web（Web 模块）

内置 Web 管理界面，基于 Spring Boot。

**API 端点：**

| 端点 | 方法 | 说明 |
|------|------|------|
| `/api/models/status` | GET | 获取模型池整体健康状态 |
| `/api/models` | GET | 获取所有模型实例列表 |
| `/api/models/{id}/toggle` | POST | 启停指定模型实例 |
| `/api/config` | GET/PUT | 读取/更新运行时配置 |

**Web 界面功能：**
- 模型池整体健康状态看板
- 各个模型实例的详细信息（成功率、延迟、请求数）
- 模型启停控制
- 实时统计信息

### 3.5 jwcode-mcp（MCP 模块）

MCP (Model Context Protocol) 桥接模块，支持标准化工具协议，用于与外部 AI 系统集成。

### 3.6 jwcode-parser（解析模块）

代码解析与分析模块，用于对 Java 源码进行 AST 解析和语义分析。

---

## 4. 配置系统

### 4.1 配置文件位置

| 优先级 | 位置 | 说明 |
|--------|------|------|
| 低 | `~/.jwcode/config.yaml` | 用户级全局配置 |
| 高 | `./.jwcode/config.yaml` | 项目级配置，覆盖用户级 |

### 4.2 配置格式（YAML）

```yaml
# JWCode 配置文件

# 默认使用的提供商
default-provider: moonshot

# 提供商配置
providers:
  moonshot:
    base-url: https://api.moonshot.cn/v1
    api-type: openai-completions
    api-keys:
      - sk-your-first-api-key
      - sk-your-second-api-key   # 支持多密钥轮询
    key-rotation:
      strategy: round_robin      # 轮询策略: round_robin, random
      failover-enabled: true     # 启用故障转移
      max-retries: 3             # 最大重试次数
      cooldown-ms: 60000         # 冷却时间(毫秒)

  minimax:
    base-url: https://api.minimaxi.com/v1
    api-type: openai-completions
    api-keys:
      - sk-your-minimax-key

# 模型映射（将逻辑名称映射到实际模型）
model-mappings:
  default: "moonshot/MiniMax-M2.7"
  coding: "moonshot/MiniMax-M2.7-highspeed"

# UI 设置
ui:
  theme: dark                     # 主题: dark, light
  activity-log: true             # 是否显示活动日志
  activity-log-mode: compact     # 日志模式: compact, detailed
  stream-response: true          # 流式响应
  color-enabled: true            # 颜色输出
```

### 4.3 运行时状态目录 (`.jwcode/`)

```
.jwcode/
├── config.yaml       # 项目级配置
├── agents/           # Agent 配置和状态
├── audit/            # 审计日志
├── logs/             # 运行日志
├── memory/           # AI 长期记忆
├── skills/           # 技能定义
├── system-prompt/    # 系统提示词
├── system-prompt.md  # 当前系统提示词
├── tasks.json        # 任务状态
├── todos.txt         # 待办事项
└── workspace/        # 工作空间文件
```

---

## 5. 模型系统

### 5.1 架构演进

JwCode 的模型系统经历了从多提供商模式到统一 OpenAI 兼容模式的演进：

```
【旧架构】多提供商模式                          【新架构】OpenAI 兼容模式
ProviderType: KIMI, ANTHROPIC,               ProviderType: OPENAI_COMPATIBLE (单一模式)
              MINIMAX, OPENAI, AZURE,                  ↓
              OLLAMA, CUSTOM                   统一的 OpenAI 格式
         ↓                                      简化架构，易于维护
不同的请求/响应格式转换
         ↓
复杂的兼容性检查
```

### 5.2 模型池架构 (ModelPool)

```
┌─────────────────────────────────────────────────────────────┐
│                    ModelPoolManager                         │
│              (负载均衡、健康检查、故障转移)                    │
├─────────────────────────────────────────────────────────────┤
│                                                             │
│  ┌───────────────────┐    ┌───────────────────┐            │
│  │  ModelInstance 1  │    │  ModelInstance 2  │   ...      │
│  │  (MiniMax-M2.7)   │    │  (MiniMax-M2.7-   │            │
│  │   primary         │    │   highspeed)      │            │
│  │                   │    │   secondary       │            │
│  └────────┬──────────┘    └────────┬──────────┘            │
│           │                        │                       │
│           └────────────┬───────────┘                       │
│                        ▼                                   │
│  ┌──────────────────────────────────────────────────┐      │
│  │           LoadBalanceStrategy                     │      │
│  │  round_robin | random | adaptive | failover      │      │
│  └──────────────────────────────────────────────────┘      │
│                                                             │
│  ┌──────────────────────────────────────────────────┐      │
│  │            HealthCheck 定时任务                    │      │
│  │  每 60s 检查模型实例健康状态                         │      │
│  └──────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### 5.3 模型实例配置

每个模型实例包含：

| 属性 | 说明 |
|------|------|
| `id` | 实例唯一标识 (如 primary, secondary) |
| `name` | 模型名称 (如 MiniMax-M2.7) |
| `provider` | 提供商 (如 minimax, moonshot) |
| `baseUrl` | API 端点 URL |
| `apiKey` | API 密钥 |
| `weight` | 负载均衡权重 |
| `enabled` | 是否启用 |
| `status` | 当前状态 (healthy, unhealthy, unknown) |
| `metrics` | 统计指标（请求数、成功率、延迟等） |

### 5.4 支持的模型提供商

| 提供商 | 基础 URL | 模型示例 |
|--------|---------|---------|
| MiniMax | `https://api.minimaxi.com/v1` | MiniMax-M2.7, M2.7-highspeed, M2.5, M2.1, M2 |
| Moonshot | `https://api.moonshot.cn/v1` | moonshot-v1-auto |
| Anthropic (兼容) | 通过 MiniMax 兼容层 | MiniMax-M2.7 (Anthropic 格式) |

### 5.5 MiniMax 模型系列（通过 Anthropic 兼容格式）

| 模型名称 | 上下文窗口 | 输出速度 | 说明 |
|---------|-----------|---------|------|
| MiniMax-M2.7 | 204,800 | ~60 TPS | 自我迭代能力 |
| MiniMax-M2.7-highspeed | 204,800 | ~100 TPS | M2.7 极速版 |
| MiniMax-M2.5 | 204,800 | ~60 TPS | 顶尖性能与性价比 |
| MiniMax-M2.5-highspeed | 204,800 | ~100 TPS | M2.5 极速版 |
| MiniMax-M2.1 | 204,800 | ~60 TPS | 强大多语言编程能力 |
| MiniMax-M2.1-highspeed | 204,800 | ~100 TPS | M2.1 极速版 |
| MiniMax-M2 | 204,800 | ~60 TPS | 基础模型 |
| MiniMax-M2-highspeed | 204,800 | ~100 TPS | M2 极速版 |

> **注意**: 通过 Anthropic SDK 兼容方式接入时，需使用 MiniMax 专门提供的 API 端点和密钥。

### 5.6 密钥轮询机制

```yaml
key-rotation:
  strategy: round_robin      # 轮询策略: round_robin, random
  failover-enabled: true     # 启用故障转移
  max-retries: 3             # 最大重试次数
  cooldown-ms: 60000         # 失败后冷却时间(毫秒)
```

- **round_robin**: 依次轮询使用 API Key
- **random**: 随机选择 API Key
- **failover**: 当前 Key 失败后自动切换到下一个

### 5.7 模型兼容性

系统已统一使用 OpenAI 兼容的 API 格式：
- **端点**: `/v1/chat/completions`
- **请求格式**: OpenAI Chat Completions API 格式
- **响应格式**: OpenAI Chat Completions API 格式
- **工具调用**: OpenAI Function Calling 格式

---

## 6. Agent 系统

### 6.1 AI 工程规范（AGENTS.md）

JwCode 定义了 AI 编码助手的工程规范，核心原则：

**角色定位：**
- AI = 受雇的软件工程师
- 用户 = 技术负责人/工程经理
- AI 直接交付可编译、可测试、可合并的工程产物

**反模式黑名单（必须避免）：**
| 反模式 | 替代方案 |
|--------|---------|
| 过度道歉或谦卑 | 直接陈述事实 |
| 过度解释简单操作 | 只说明关键决策理由 |
| 无意义修饰语 | 干练表达 |
| 开放式闲聊 | 专注技术任务 |

**编码规范：**
- JDK 17+ 特性优先使用
- 遵循既有代码风格
- Windows PowerShell 为主要执行环境
- 禁止破坏现有 API 兼容性

### 6.2 Agent Swarm（智能体集群）

Agent Swarm 是 JwCode 的高级功能，实现智能体集群能力。

**已实现功能：**

| 功能 | 状态 | 说明 |
|------|------|------|
| 任务分解 | ✅ 自动 | 根据预定义分解策略自动拆分复杂任务 |
| 动态 Agent 创建 | ✅ 自动 | 按任务类型创建专业 Agent |
| 并行执行 | ✅ 自动 | 多线程并行处理子任务 |
| 依赖调度 | ✅ 自动 | 按依赖关系正确排序执行 |
| 结果聚合 | ✅ 自动 | 自动汇总所有子任务结果 |
| 复杂度分析 | ✅ 自动 | 自动评估任务复杂度 |

**使用方式：**

```bash
# 方式 1: 显式调用 Swarm
jwcode> advanced swarm "重构所有 API 为异步模式"

# 方式 2: 自动检测模式
jwcode> advanced auto
jwcode> refactor all API calls to async  # 自动使用 Swarm

# 方式 3: 先分析再决定
jwcode> advanced analyze "升级 Spring Boot 版本"
```

**Swarm 工作流程：**
```
用户输入任务
     │
     ▼
┌─────────────┐
│ 复杂度分析   │ ← 自动评估任务复杂度
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 任务分解     │ ← 按关键词匹配分解策略
└──────┬──────┘
       │
       ▼
┌──────────────────┐
│ Agent 集群创建    │ ← 为每个子任务创建 Agent
│ ┌──┐ ┌──┐ ┌──┐  │
│ │A1│ │A2│ │A3│  │
│ └──┘ └──┘ └──┘  │
└──────┬───────────┘
       │
       ▼
┌─────────────┐
│ 并行/依赖执行 │ ← 按依赖关系调度
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ 结果聚合     │ ← 汇总所有子任务结果
└─────────────┘
```

---

## 7. UI 系统

### 7.1 CLI 界面

基于 JLine 的交互式终端，提供：
- 命令自动补全（Tab 键）
- 命令历史记录（上下箭头）
- 语法高亮
- 流式响应实时显示
- 多行输入支持

### 7.2 活动日志系统

实时显示 AI 正在执行的操作：

```
[14:32:10] ▶️ 📄 读取文件 读取 src/main/java 256 行 (45ms)
[14:32:11] ▶️ 🔍 搜索代码 搜索: class ActivityLogger 5 个匹配 (23ms)
[14:32:12] ▶️ ⚡ 执行命令 执行: mvn clean compile BUILD SUCCESS (1.2s)
```

**核心特性：**
- ✅ 实时活动显示（文件操作、代码搜索、命令执行等）
- ✅ 进度追踪（显示操作进度百分比）
- ✅ 执行时间（每个操作的耗时）
- ✅ 成功/失败状态标识（✓ / ✗）
- ✅ 多彩输出（颜色和图标增强可读性）
- ✅ 两种显示模式：紧凑（单行）和详细（多行）
- ✅ 工具执行追踪（自动追踪所有工具调用）
- ✅ 活动历史（记录所有活动供后续查看）

**组件结构：**
```
jwcode-cli/src/main/java/com/jwcode/cli/log/
├── ActivityType.java         # 活动类型枚举（文件操作、代码操作、API 调用等）
├── ActivityLogger.java       # 活动日志记录器
├── ActivityEvent.java        # 活动事件
├── ActivityRenderer.java     # 活动渲染器
└── ActivityHistory.java      # 活动历史记录
```

### 7.3 Web 管理界面

内置 Web 服务器提供模型状态可视化看板：

**页面功能：**
- 模型池整体健康状态（健康率、可用实例数）
- 各个模型实例详情（状态、请求数、成功率、平均延迟）
- 模型启停控制
- 实时统计仪表盘

**API 接口：**

```
GET  /api/models/status    → 模型池整体状态
GET  /api/models           → 所有模型实例详情
POST /api/models/{id}/toggle → 启停模型
GET  /api/config           → 获取运行时配置
PUT  /api/config           → 更新运行时配置
```

---

## 8. 当前状态与待办

### 8.1 已实现功能状态

| 功能模块 | 状态 | 说明 |
|---------|------|------|
| CLI 交互界面 | ✅ 完成 | JLine 驱动的 REPL，50+ 命令 |
| 工具系统 | ✅ 完成 | 40+ 工具，覆盖文件、代码、Shell、Web 等 |
| 模型池 | ✅ 完成 | 多实例管理、负载均衡、健康检查、故障转移 |
| OpenAI 兼容 | ✅ 完成 | 统一 OpenAI 格式，简化架构 |
| 密钥轮询 | ✅ 完成 | 多 Key 轮询、随机、故障转移 |
| 活动日志 | ✅ 完成 | 实时 AI 操作追踪与显示 |
| Web 管理界面 | ✅ 完成 | 模型状态看板 |
| Agent Swarm | ✅ 完成 | 智能体集群，任务分解与并行执行 |
| YAML 配置 | ✅ 完成 | 灵活的分层配置系统 |
| MCP 协议 | ✅ 完成 | 标准化工具协议集成 |
| 代码解析器 | ✅ 完成 | Java 源码 AST 解析 |
| 会话管理 | ✅ 完成 | 多会话支持，上下文保持 |

### 8.2 项目文件结构

```
jwcode/
├── pom.xml                          # 根 POM
├── README.md                        # 项目主文档
├── JWCODE_PRODUCT_DESIGN.md         # 本文档（产品设计总览）
├── AGENTS.md                        # AI 工程规范
├── AGENT_SWARM_GUIDE.md             # Agent Swarm 用户指南
├── ANTHROPIC_API_SETUP.md           # Anthropic SDK 兼容方式接入指南
├── YAML_CONFIG_GUIDE.md             # YAML 配置指南
├── ACTIVITY_LOGGER_README.md        # 活动日志系统文档
├── OPENAI_COMPATIBLE_MODEL_LOADER.md # OpenAI 兼容模型加载器文档
├── WEB_MODEL_UI.md                  # Web 模型状态展示文档
├── MODEL_POOL_IMPLEMENTATION.md     # 模型池化架构实现文档
│
├── .jwcode/                         # 运行时状态
│   ├── config.yaml                  # 项目级配置
│   ├── agents/                      # Agent 配置和状态
│   ├── audit/                       # 审计日志
│   ├── logs/                        # 运行日志
│   ├── memory/                      # AI 长期记忆
│   ├── skills/                      # 技能定义
│   ├── tasks.json                   # 任务状态
│   ├── todos.txt                    # 待办事项
│   └── workspace/                   # 工作空间文件
│
├── jwcode-parent/                   # 父 POM
├── jwcode-common/                   # 公共模块
├── jwcode-core/                     # 核心模块
├── jwcode-cli/                      # CLI 模块
├── jwcode-ui/                       # UI 模块
├── jwcode-web/                      # Web 模块
├── jwcode-mcp/                      # MCP 模块
├── jwcode-parser/                   # 解析模块
├── jwcode-repl/                     # REPL 模块
├── jwcode-distribution/             # 分发模块
│
├── docs/                            # 开发文档
├── md/                              # 归档文档
└── test_report/                     # 测试报告归档
```

---

> **本文档由 JwCode 产品设计文档 + AGENTS.md + AGENT_SWARM_GUIDE.md + ANTHROPIC_API_SETUP.md + YAML_CONFIG_GUIDE.md + ACTIVITY_LOGGER_README.md + OPENAI_COMPATIBLE_MODEL_LOADER.md + WEB_MODEL_UI.md + MODEL_POOL_IMPLEMENTATION.md 整合而成**
