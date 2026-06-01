# JWCode 架构 v3.1 —— Harness Engineering

> 最后更新：2026-05-26 | 模块: jwcode-common, jwcode-core, jwcode-web, jwcode-mcp, jwcode-parser

---

## v2 → v3 → v3.1 变更

| v2 | v3 | v3.1 |
|----|----|------|
| Java CLI (JLine+Lant) | Python CLI (Rich+Textual) | — |
| 4 个前端模块 | 全部删除 | — |
| 渲染 bug (丢颜色) | Rich/Textual 原生 | — |
| CostTracker 死代码 | — | 接入 LLMQueryEngine |
| 固定模型 | — | ModelRouter 动态路由 |
| AiRepair 空占位 | — | LLM 分析→修复→重试 |
| 压缩丢工具结果 | 五级分层保留 | — |
| 无沙箱 | — | Docker 容器隔离 |
| 关键词记忆 | — | Embedding 语义检索 |
| 平铺上下文 | — | ZONE 注入边界 + 分区预算 |
| 9 个模块 | 5 个模块 | 5 个模块 |

---

## Harness 四层架构 (v3.1)

```
L4 可观测: CostTracker + ObservationPipeline + AnalyticsObserver
L3 质量:   五级压缩保留 + AiRepair自愈 + 语义记忆 + ZONE注入边界
L2 成本:   ModelRouter + TokenBudget分区 + Prompt Caching
L1 安全:   DockerSandbox + WorkspaceGuard + HookChain + 审计
─────────────────────────────────────────────────
```

---

## 模块结构

```
jwcode/
├── jwcode-core/          # 核心引擎 (LLM / Agent / Tool / Session)
├── jwcode-common/        # 基础工具
├── jwcode-web/           # HTTP + WebSocket 服务器 + React Web UI
├── jwcode-mcp/           # MCP 客户端
├── jwcode-parser/        # 代码解析
├── python-cli/           # Python CLI (Rich + Textual)
│   ├── jwcode/main.py    # 入口 (jwcode start / run / version)
│   ├── jwcode/app.py     # Textual App 布局
│   ├── jwcode/client.py  # WebSocket 客户端
│   ├── jwcode/launcher.py# 后端编译 + 启动
│   └── jwcode/widgets/   # 终端 UI 组件
├── start.bat / start.sh  # 一键启动脚本
└── README.md
```

**已删除的模块**: `jwcode-cli/` `jwcode-ui/` `jwcode-repl/` `jwcode-distribution/`

---

## 通信协议

所有前端（CLI / Web UI）通过统一的 WebSocket 协议与后端通信：

| 方向 | 消息类型 |
|------|---------|
| 客户端 → 服务端 | `chat`, `plan`, `plan_confirm`, `stop`, `pause`, `resume`, `hook_allow`, `hook_deny`, `model_change`, `workspace` |
| 服务端 → 客户端 | `start`, `content`, `thinking`, `tool_call`, `tool_result`, `step_start/thinking/action/complete`, `complete`, `hook_ask`, `token_update`, `error` |

---

## Python CLI 技术栈

| 库 | 版本 | 用途 |
|----|------|------|
| textual | >=2.0 | TUI 框架 (CSS 布局, async 事件循环) |
| rich | >=13.0 | Panel / Table / Syntax / Markdown / Tree 渲染 |
| aiohttp | >=3.9 | WebSocket 客户端 |
| typer | >=0.12 | CLI 参数解析 |
| pyyaml | >=6.0 | 配置加载 |

## Java 后端技术栈

| 库 | 版本 | 用途 |
|----|------|------|
| Java | 17 | 运行环境 |
| Maven | 3.8+ | 构建 |
| Jackson | 2.17.0 | JSON 序列化 |
| SLF4J + Logback | 2.0.13 / 1.5.6 | 日志 |
| okhttp | 4.12.0 | HTTP 客户端 |
| JUnit 5 + Mockito | 5.10.2 / 5.11.0 | 测试 |
