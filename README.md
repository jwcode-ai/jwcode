<div align="center">
  <img src="jwcode-web/public/logo.svg" width="120" alt="JWCode" />
  <h1 align="center">JWCode</h1>
  <p align="center">
    <strong>Java 生态原生的 AI 编程助手 · 自托管 · 多 LLM 支持</strong>
  </p>
  <p align="center">
    <em>Java-Native AI Coding Agent · Self-Hosted · Multi-LLM Support</em>
  </p>
  <p align="center">
    <a href="#quick-start">快速开始</a> ·
    <a href="#features">功能特性</a> ·
    <a href="#architecture">架构</a> ·
    <a href="#modules">模块</a> ·
    <a href="#comparison">对比</a> ·
    <a href="#roadmap">路线图</a>
  </p>
  <p align="center">
    <img src="https://img.shields.io/badge/Java-17%2B-blue?logo=openjdk" alt="Java 17+" />
    <img src="https://img.shields.io/badge/Node-18%2B-green?logo=node.js" alt="Node 18+" />
    <img src="https://img.shields.io/badge/license-Apache%202.0-red" alt="License" />
    <img src="https://img.shields.io/badge/agents-16-orange" alt="16 Agents" />
    <img src="https://img.shields.io/badge/tools-50%2B-purple" alt="50+ Tools" />
    <img src="https://img.shields.io/badge/Java%20files-889-yellow" alt="889 Java files" />
  </p>
</div>

---

> **JWCode** 是一个完全用 Java 17 构建的 AI 编程助手。它提供 Web UI 和终端 TUI 双界面，支持 DeepSeek / Claude / OpenAI 等多种 LLM，自带 Agent 图编排引擎、50+ 内置工具、插件 API 和 Docker 沙箱安全体系。
>
> 与 OpenAI Codex CLI（仅 CLI 壳开源，核心闭源）不同，**JWCode 的 Agent 核心完全开源**——你可以学习、修改、自行部署。
>
> ---
>
> **JWCode** is a fully open-source AI coding agent built with Java 17. It features both a Web UI and a terminal TUI, supports multiple LLM providers (DeepSeek / Claude / OpenAI), includes a LangGraph-like agent graph engine, 50+ built-in tools, a plugin API, and Docker sandbox security.
>
> Unlike OpenAI Codex CLI (which only open-sourced the shell — the core agent is closed), **JWCode's entire agent system is fully open-source**.

---

## 快速开始 / Quick Start

### 一键安装 / One-Click Install

```bash
# macOS / Linux
curl -fsSL https://raw.githubusercontent.com/jwcode/main/install.sh | sh

# Windows
powershell -ExecutionPolicy ByPass -c "irm https://raw.githubusercontent.com/jwcode/main/install.ps1 | iex"
```

### 手动启动 / Manual Start

```bash
# 1. 构建后端 / Build backend
mvn install -pl jwcode-core,jwcode-web -am -DskipTests

# 2. 启动后端（HTTP :8080, WS :8081）/ Start backend
mvn exec:java -pl jwcode-web -Dexec.mainClass=com.jwcode.web.WebLauncher -Dexec.args="8080 8081"

# 3. 启动 Web UI（另一个终端）/ Start Web UI
cd jwcode-web && npm run dev

# 4. 或者使用终端 TUI / Or use the terminal TUI
cd ts-cli && npm run go
```

### 配置 LLM / Configure LLM

编辑 `~/.jwcode/config.yaml`：

```yaml
providers:
  deepseek:
    base-url: https://api.deepseek.com
    api-type: openai-completions
    api-keys: [ sk-xxx ]
    models:
      - id: deepseek-chat
        max-tokens: 8192

  claude:
    base-url: https://api.anthropic.com
    api-type: anthropic-messages
    api-keys: [ sk-ant-xxx ]
    models:
      - id: claude-sonnet-4-20250514
        max-tokens: 8192

default-provider: deepseek
```

---

## 功能特性 / Features

| 特性 | 说明 | Feature |
|------|------|---------|
| 🤖 **16 种 Agent** | Orchestrator、Coder、Architect、Debug、Reviewer 等 | 16 specialized agents for different tasks |
| 🔧 **50+ 内置工具** | 文件编辑、Bash、Git、Web、浏览器、MCP 等 | 50+ built-in tools |
| 🧠 **Agent Graph 引擎** | LangGraph 风格的 DAG 图编排 + BSP 执行 | LangGraph-like DAG orchestration engine |
| 🌐 **多 LLM 支持** | DeepSeek / Claude / OpenAI / 兼容任意 OpenAI API | Support any OpenAI-compatible API |
| 🖥️ **双界面** | Web SPA (React) + 终端 TUI (Ink) | Web SPA + Terminal TUI |
| 🔒 **安全体系** | Docker 沙箱、命令注入检测、7 层权限分类 | Docker sandbox, injection detection, permission system |
| 🔌 **插件 API** | `jwcode-plugin-api` 扩展自定义 Agent 和 Tool | Plugin API for custom agents and tools |
| 📡 **渠道集成** | 微信消息通道（可扩展飞书/钉钉） | WeChat integration (extensible) |
| 📋 **任务黑板** | 黑板模式任务管理 + AI 自动分解 | Blackboard-pattern task management |
| 🎯 **Plan/Act/Goal 模式** | 三模式权限隔离，AI 行为可控 | Three-mode permission isolation |

---

## 架构 / Architecture

```
                    ┌──────────────────────────────────────┐
                    │          Web UI (React + Tailwind)     │
                    │         Terminal TUI (Ink + React)     │
                    └──────────────────┬───────────────────┘
                                       │ WS / REST
                    ┌──────────────────▼───────────────────┐
                    │        WebServer :8080 / WS :8081     │
                    │  (com.sun.net.httpserver)              │
                    └──────────────────┬───────────────────┘
                                       │
          ┌────────────────────────────┼────────────────────────────┐
          ▼                            ▼                            ▼
   ┌─────────────┐           ┌─────────────────┐          ┌────────────────┐
   │  Agent 系统  │           │  Agent Graph     │          │   LLM 服务层    │
   │  16 Agents   │◄──────────│  LangGraph-DAG   │─────────►│  Multi-Provider │
   │  + Swarm     │           │  Pregel BSP      │          │  DeepSeek       │
   └──────┬──────┘           └────────┬────────┘          │  Claude         │
          │                           │                    │  OpenAI         │
          ▼                           ▼                    └────────────────┘
   ┌──────────────┐          ┌─────────────────┐
   │  50+ Tools    │          │  Task 黑板系统    │
   │  ToolRegistry │          │  TaskStore       │
   │  Permission   │          │  ActiveTask      │
   │  安全管道      │          │  AIPlanner       │
   └──────────────┘          └─────────────────┘
          │                           │
          ▼                           ▼
   ┌─────────────────────────────────────────────┐
   │  安全层 / Security Layer                     │
   │  DockerSandbox · CommandInjectionDetector    │
   │  AutoPermissionClassifier · HookChain        │
   └─────────────────────────────────────────────┘
```

### Agent Graph 引擎（类 LangGraph）

```
┌─────────────────────────────────────────────┐
│  OrchestratorGraphFactory                    │
│                                              │
│   addNode("planner") → addEdge("start")      │
│        ↓                                     │
│   addNode("coder")                           │
│     ↙       ↘                                 │
│   review     debug     ← conditional edges   │
│     ↓         ↓                               │
│   addNode("finish")                          │
│                                              │
│  Channel System:                              │
│   LastValueChannel  - 最后写入者胜出           │
│   BinaryOpChannel   - Reducer 合并            │
│   TopicChannel      - 发布/订阅               │
│   EphemeralChannel  - 不持久化                │
└─────────────────────────────────────────────┘
```

---

## Agent 清单 / Agent Catalog

| Agent | 职责 | Responsibility |
|-------|------|---------------|
| `OrchestratorAgent` | 任务分解与子 Agent 编排 | Task decomposition and sub-agent orchestration |
| `EnhancedOrchestratorAgent` | 增强版编排，支持图路由 | Enhanced orchestration with graph routing |
| `CoderAgent` | 代码生成与修改 | Code generation and modification |
| `ArchitectAgent` | 架构设计与技术方案 | Architecture design and tech planning |
| `DebugAgent` | 调试与错误分析 | Debugging and error analysis |
| `ReviewerAgent` | 代码审查 | Code review |
| `ExplorerAgent` | 代码库探索阅读 | Codebase exploration (read-only) |
| `EvaluatorAgent` | 方案评估与决策 | Solution evaluation and decision making |
| `TestAgent` | 测试编写与执行 | Test writing and execution |
| `DocAgent` | 文档生成 | Documentation generation |
| `CompactorAgent` | 上下文压缩 | Context compaction |
| `MemoryAgent` | 记忆管理 | Memory management |
| `TaskAgent` | 任务执行 | Task execution |
| `ConfigurableAgent` | 可动态配置的通用 Agent | Configurable generic agent |
| `DefaultAgent` | 默认兜底 Agent | Default fallback agent |
| `AgentSwarm` | 多 Agent 群体协作 | Multi-agent swarm collaboration |

---

## Tool 清单 / Tool Catalog

50+ 工具按类别划分：

| 类别 | Tools |
|------|-------|
| **文件操作** | FileRead, FileWrite, FileEdit, Edit, Glob, Grep, BatchRead, MergeFiles |
| **命令执行** | Bash, PowerShell, REPL |
| **搜索** | WebSearch, WebFetch, SemanticSearch, Grep |
| **浏览器** | Browser (Playwright) |
| **Git** | Git |
| **LSP** | LSP (language server protocol) |
| **MCP** | MCP, ListMcpResources, ReadMcpResource |
| **任务管理** | TaskCreate, TaskGet, TaskList, TaskUpdate, TaskOutput, TaskStop |
| **团队** | TeamCreate, TeamList, TeamDelete |
| **AI 协作** | SendMessage, Agent, ToolSearch |
| **编辑器** | NotebookEdit, SmartAnalyze |
| **工具** | Sleep, ScheduleCron, Download, Config, TodoWrite, ChangeDirectory |
| **对话管理** | EnterPlanMode, ExitPlanModeV2, EnterWorktree, ExitWorktree, WorktreeList, AskUserQuestion, Pattern |
| **技能** | SkillManageTool |
| **调试** | ContextDumpTool |

---

## 模块 / Modules

| 模块 | 说明 | Description | 文件数 |
|------|------|-------------|--------|
| `jwcode-common` | 共享工具：认证、配置、辅助工具 | Shared utilities: auth, config, helpers | 6 |
| `jwcode-core` | **核心引擎**：Agent、Tool、LLM 编排、图引擎、安全 | Core engine: agents, tools, LLM, graph, security | ~630 |
| `jwcode-web` | HTTP/WS 服务器 + React SPA 前端 + TS CLI | HTTP/WS server + React SPA + TS CLI | 104 前端 |
| `jwcode-mcp` | MCP 客户端接口 | MCP client interface | 1 |
| `jwcode-plugin-api` | 插件开发 API，第三方扩展入口 | Plugin API for third-party extensions | 4 |

- **Java 源文件**: 889 | **前端文件**: 104 | **配置文件**: 30+

---

## 为什么用 Java 写 AI 编程助手？ / Why Java?

> "AI 编程工具都用 Python——但 JWCode 偏要用 Java。"

| 优势 | 说明 |
|------|------|
| 🏭 **企业就绪** | Java 在金融、电商、制造业的部署生态无可替代 |
| ⚡ **性能** | JIT + 虚拟线程 (Project Loom) + 低延迟 GC |
| 🛡️ **安全** | 强类型 + 沙箱 + 成熟的权限模型 |
| 📚 **学习价值** | 完整覆盖 Java 17 新特性、设计模式、并发、网络、安全编程 |
| 🔌 **生态** | Maven 中央仓库百万级依赖可用 |

---

## 对比 / Comparison

| 特性 | JWCode | OpenAI Codex CLI | Claude Code | Cursor |
|------|--------|-----------------|-------------|--------|
| **开源核心** | ✅ 完全开源 | ❌ 仅 CLI 壳 | ❌ 闭源 | ❌ 闭源 |
| **后端语言** | **Java 17** | Rust | TypeScript | TypeScript |
| **自托管** | ✅ 完全自托管 | ❌ 依赖云端 | ❌ 依赖云端 | ❌ 依赖云端 |
| **多 LLM** | ✅ DeepSeek/Claude/OpenAI | ❌ 仅 OpenAI | ❌ 仅 Claude | ✅ 多模型 |
| **Agent 系统** | ✅ 16 Agent + 图编排 | ❌ 单一 Agent | ✅ 内置 | ✅ 内置 |
| **Web UI** | ✅ React SPA | ❌ CLI only | ✅ CLI only | ✅ IDE |
| **终端 TUI** | ✅ Ink TUI | ✅ Ink TUI | ❌ | ❌ |
| **Docker 沙箱** | ✅ | ✅ | ❌ | ❌ |
| **插件 API** | ✅ jwcode-plugin-api | ❌ | ❌ | ✅ 扩展 |
| **Java 学习价值** | ⭐⭐⭐⭐⭐ | ⭐ | ⭐ | ⭐ |

---

## 路线图 / Roadmap

- [x] v1.0 基础 Agent 系统 + Tool 框架
- [x] v2.0 多 LLM 适配层 + WebSocket 流式通信
- [x] v3.0 Plan/Act/Goal 模式 + Agent Graph 图引擎
- [x] v3.1 任务黑板系统 + 渠道集成（微信）
- [x] v3.2 三级模型绑定 + 插件 API
- [ ] **v4.0 VS Code 扩展** — 直接在编辑器中连接 JWCode 后端
- [ ] **v4.0 JetBrains 插件** — IDEA 原生集成
- [ ] **v4.1 云服务** — 托管版 JWCode Cloud
- [ ] **v4.2 企业版** — SSO、RBAC、审计日志

---

## 构建状态 / Build Status

```bash
# 完整构建
mvn install -pl jwcode-core,jwcode-web -am -DskipTests

# 仅构建前端
cd jwcode-web && npm run build

# TS CLI 构建
cd ts-cli && npm run go
```

---

## 开源协议 / License

[Apache License 2.0](LICENSE) — 商业友好，可自由使用和修改。

---

## 支持 / Support

- 📖 [完整文档](docs/)
- 💬 微信群 / Discord（建设中）
- 🐛 [Issues](https://github.com/jwcode/jwcode/issues)
- ⭐ Star 我们——让更多 Java 开发者看到！

---

<div align="center">
  <sub>Built with ❤️ and Java 17</sub>
  <br />
  <sub>JWCode — Java-Native AI Coding Agent</sub>
</div>
