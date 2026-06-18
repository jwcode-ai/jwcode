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


## LLM 服务层（v3.2 新增 — 双格式 base_url 支持）

```
LLMFactory (通过 Registry 路由)
    |
ServiceRegistry
    |-- OpenAIServiceProvider  (api-type = "openai-completions")
    |-- AnthropicServiceProvider (api-type = "anthropic-messages")
            |                          |
        OpenAILLMService         AnthropicLLMService
            |                          |
            +------- AbstractHttpLLMService --------+
    (共享：HttpClient 连接池、60s 超时、HTTP/1.1)
    (重试：指数退避 2s→4s→8s，最多 3 次，支持换 API key)
    (错误码映射：429→rate_limit, 5xx→server_error, 超时→timeout)
    (SSE 行读取、日志打标 [Provider=name])
```

### 新增文件一览

| 文件 | 职责 |
|------|------|
| `AbstractHttpLLMService.java` | 共享 HTTP 基类：5 个 hook 方法供子类实现协议适配 |
| `ServiceConfig.java` | 独立配置类：apiType + anthropicVersion + 通用字段 |
| `ServiceProvider.java` | Provider 接口：getApiType() + createService() |
| `ServiceRegistry.java` | 注册表：register() / createService() / getSupportedTypes() |
| `OpenAIServiceProvider.java` | api-type="openai-completions" → OpenAILLMService |
| `AnthropicServiceProvider.java` | api-type="anthropic-messages" → AnthropicLLMService |
| `AnthropicMessageConverter.java` | 纯转换：内部消息 ↔ Anthropic Messages API 格式 |
| `AnthropicLLMService.java` | Anthropic 协议实现（非流式 + 流式 SSE） |

### AnthropicMessageConverter 核心转换

| 内部格式 → | Anthropic 格式 |
|-----------|---------------|
| `SYSTEM` role | 提取到顶层 `system` 字段 |
| `USER` role | `{"role":"user","content":[{"type":"text","text":...}]}` |
| `TOOL` role | `{"type":"tool_result","tool_use_id":"...","content":"..."}`（合并到 user 消息中） |
| `ASSISTANT` role | `{"role":"assistant","content":[text?, thinking?, tool_use*]}` |
| Tool definition | `{"name":"...","description":"...","input_schema":{...}}`（input_schema 替代 OpenAI 的 parameters） |

### SSE 流式事件状态机

```
message_start          → 初始化 accumulator（model, input_tokens）
content_block_start    → 根据 type 进入 text / tool_use / thinking 子状态
content_block_delta    → text_delta: 追加 content
                         input_json_delta: 追加 tool input StringBuilder
                         thinking_delta: 追加 thinking
content_block_stop     → 收尾 block：tool_use 完成时从 buffer 反序列化为完整 JSON
message_delta          → 更新 stop_reason / output_tokens
message_stop           → 流结束
ping                   → 心跳，无操作
```


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
| 客户端 → 服务端 | `chat`, `plan`, `plan_confirm`, `stop`, `pause`, `resume`, `hook_allow`, `hook_deny`, `model_change`, `workspace`, `command_execute` |
| 服务端 → 客户端 | `start`, `content`, `thinking`, `tool_call`, `tool_result`, `step_start/thinking/action/complete`, `complete`, `hook_ask`, `token_update`, `error`, `command_start`, `command_complete`, `command_error` |

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
| command_execute | — | 统一 slash command 执行协议（详见 COMMAND_SYSTEM.md） |
| Maven | 3.8+ | 构建 |
| Jackson | 2.17.0 | JSON 序列化 |
| SLF4J + Logback | 2.0.13 / 1.5.6 | 日志 |
| okhttp | 4.12.0 | HTTP 客户端 |
| JUnit 5 + Mockito | 5.10.2 / 5.11.0 | 测试 |
