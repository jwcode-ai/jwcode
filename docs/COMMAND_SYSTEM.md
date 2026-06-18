# 统一 Slash Command 系统

> 最后更新：2026-06-18 | 模块: jwcode-core, jwcode-web, ts-cli

## 设计目标

Java 后端是 slash command 能力的**单一源头**。TS CLI 与 Web 前端在运行期动态拉取命令清单，并通过统一的 `command_execute` WebSocket 协议执行；TS CLI 只保留纯 UI 命令与控制流命令。

此前系统存在三方漂移：Java `CommandRegistry` 仅 10 个命令且与 WebSocket 解耦；`StreamingWebSocketHandler` 另有一套 per-command WS 分发，其中 `export/checkpoint/test/lint/search` 是 stub；TS CLI `localActions.ts` 又重新实现了 12 个业务命令；Web 前端再硬编码一套。迁移后新增命令只需在 Java 一处实现并注册。

## 命令清单（26 个）

| 命令 | 类别 | Source | 说明 |
|------|------|--------|------|
| help | core | CORE | 显示所有命令 |
| exit / quit / q | core | CORE | 退出程序 |
| clear / cls | core | CORE | 清屏或清会话历史 |
| eval / t | core | CORE | 运行能力评测 |
| status | core | CORE | 显示当前会话状态 |
| doctor | core | CORE | 系统诊断 |
| cost | core | CORE | token 用量与费用统计 |
| model | config | CORE | 查看/切换 AI 模型 |
| config | config | CONFIG | 管理应用配置（含 provider 状态） |
| skills | config | CORE | 列出可用技能 |
| mcp | config | CONFIG | 显示 MCP 服务器状态 |
| agents | config | CONFIG | 列出可用 Agents |
| plugin | config | CONFIG | 插件管理 |
| effort | config | CONFIG | 设置推理 effort 级别 |
| tokens | session | SESSION | 报告会话 token 用量 |
| rewind | session | SESSION | 回退会话消息 |
| compact | session | SESSION | 压缩上下文 |
| branch | session | SESSION | 创建分支会话（需后端编排） |
| export | session | SESSION | 导出会话为 Markdown |
| checkpoint | session | SESSION | 创建/列出/恢复检查点 |
| memory | workspace | WORKSPACE | 浏览项目记忆文件 |
| search | workspace | WORKSPACE | 工作区文本搜索 |
| init | workspace | WORKSPACE | 分析项目生成 JWCODE.md（AI） |
| project / update_docs | workspace | WORKSPACE | 生成/更新项目文档（AI） |
| test | tools | TOOLS | 运行项目测试脚本 |
| lint | tools | TOOLS | 运行项目 lint 脚本 |

## 架构

```
                ┌─────────────────────────────────────┐
                │  CommandRegistry (单例, createFull) │
                │  26 个 Command 实现                 │
                └──────────┬───────────────┬──────────┘
                           │               │
            GET /api/commands        command_execute (WS)
            (CommandsHandler)        (StreamingWebSocketHandler)
                           │               │
        ┌──────────────────┴───────┐       │
        │  TS CLI (动态拉取)        │       │
        │  Web 前端 (动态拉取)      │       │
        └──────────────────────────┘       │
                                            │
                   ┌────────────────────────┴────────────────────────┐
                   │  handleCommandExecute                            │
                   │  ├─ 编排类命令 → 委托既有 WS handler            │
                   │  ├─ AI 命令 (init/project) → executeQuery        │
                   │  └─ 纯命令 → Command.execute()                  │
                   └─────────────────────────────────────────────────┘
```

### Command 契约

`com.jwcode.core.command.Command` 接口：

```java
String getName();
List<String> getAliases();          // default: List.of()
String getDescription();
String getUsage();
CommandResult execute(String[] args, Session session);
default boolean requiresInteractive();  // false
default boolean requiresConfirmation(); // false
default String getCategory();           // "core"
default boolean requiresArgs();         // false
default CommandSource getSource();      // CORE
```

`CommandSource` 枚举：`CORE / SESSION / WORKSPACE / TOOLS / CONFIG`。

`CommandResult` 三种类型：`SUCCESS / ERROR / EXIT`。AI 提示类命令（init/project）返回 `success(prompt, "AI_PROMPT")`，由 WS 层检测标记后转发给查询执行器。

## 通信协议

### HTTP — GET /api/commands

返回完整命令清单，供客户端初始化时拉取：

```json
{
  "success": true,
  "data": [
    {
      "name": "search",
      "description": "Search text in the workspace",
      "category": "workspace",
      "usage": "search <keyword>",
      "requiresArgs": true,
      "source": "WORKSPACE",
      "aliases": []
    }
  ]
}
```

### WebSocket — command_execute

客户端 → 服务端：

```json
{"type":"command_execute","sessionId":"...","data":"{\"command\":\"/search\",\"args\":\"keyword\"}"}
```

服务端 → 客户端事件流：

```json
{"type":"command_start","sessionId":"...","data":"{\"command\":\"/search\",\"args\":\"keyword\"}"}
{"type":"command_complete","sessionId":"...","data":"{\"command\":\"/search\",\"exitCode\":0,\"result\":\"...\"}"}
{"type":"command_error","sessionId":"...","data":"{\"command\":\"/search\",\"error\":\"...\"}"}
```

v1 不实现 `command_output` 流式事件，所有命令同步执行，输出在 `command_complete.result` 中返回。成功结果同时以 `notification` 消息发送，保证既有客户端能渲染。

### 控制流命令不走 command_execute

`stop / pause / resume / plan_confirm / plan_refine / hook_allow / hook_deny` 是反应式控制信号（取消 future、暂停 executor、响应 hook 审批），保留独立 WS 类型，不走统一协议。

## 客户端实现

### TS CLI

- `LOCAL_COMMANDS`（7 个）：`/help /plan /auto /context /clear /exit /quit` —— 纯 UI，本地处理。
- `CONTROL_COMMANDS`（5 个）：`/confirm /cancel /stop /pause /resume` —— 保留独立 WS 类型。
- `DYNAMIC_COMMANDS`：连接成功后从 `/api/commands` 拉取，通过 `client.executeCommand()` 走 `command_execute`。
- 拉取失败时回退到本地命令，不影响启动。

### Web 前端

- `local: true` 的命令（tab 切换、主题、清屏等）完全不动。
- `local: false` 的命令（doctor/compact/cost/model/...）通过 `command_execute` 发送。
- `auth_success` 后调用 `fetchCommands()` 拉取清单；`commands_list` WS 处理器保留作为向后兼容 fallback。
- 新增 `command_start/command_complete/command_error` 事件处理 → toast 通知。

## 向后兼容

所有既有 per-command WS 类型分支（`doctor/rewind/compact/model_change/...`）保留不动。老客户端、python-cli 仍能直接发送这些类型。`command_execute` 是叠加路径，不替换旧路径。

## 扩展指南

新增一个命令只需三步：

1. 在 `jwcode-core/.../command/` 新建 `XxxCommand implements Command`，实现 7 个方法。
2. 在 `CommandRegistry.createFull()` 的 `registerAll` 列表中加入 `new XxxCommand()`。
3. 若命令需要后端编排（如访问 sessions map / queryExecutor），在 `StreamingWebSocketHandler.handleCommandExecute` 的 switch 中加一个委托分支；否则自动走 `Command.execute()`。

客户端无需任何改动 —— 下次连接拉取清单时自动出现新命令。
