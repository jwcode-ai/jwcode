# Agent 系统与桥接模式指南

## Agent 系统

JwCode 支持多 Agent 架构，允许针对不同类型的任务使用专门的 AI Agent。

### 内置 Agents

| Agent | 描述 | 特点 |
|-------|------|------|
| `default` | 通用助手 | 适合大多数任务 |
| `coder` | 编码专家 | 擅长编写、重构和优化代码 |
| `debug` | 调试专家 | 专注于问题诊断和修复 |

### 命令

```bash
# 列出所有可用 Agents
jwcode> agent list

# 查看 Agent 详情
jwcode> agent show coder

# 切换到指定 Agent
jwcode> agent switch coder

# 查看当前 Agent
jwcode> agent current
```

### Agent 配置

每个 Agent 包含：
- **系统提示词**: 定义 Agent 的行为和专长
- **可用工具**: 限制 Agent 能使用的工具集
- **模型配置**: 可指定特定的模型和参数

## 桥接模式 (Bridge Mode)

桥接模式允许远程客户端通过网络连接到 JwCode 实例，实现远程执行。

### 启动服务器

```bash
# 启动桥接服务器（默认端口 8080）
jwcode> bridge start

# 指定端口
jwcode> bridge start 9090
```

### API 端点

| 端点 | 方法 | 描述 |
|------|------|------|
| `/bridge/connect` | POST | 创建新会话 |
| `/bridge/message` | POST | 发送消息 |
| `/bridge/stream` | GET | SSE 流式响应 |
| `/bridge/status` | GET | 服务器状态 |

### 连接到远程服务器

```bash
# 连接到远程 JwCode 实例
jwcode> bridge connect http://localhost:8080
```

### 停止服务器

```bash
jwcode> bridge stop
```

## 架构设计

```
┌─────────────────┐     ┌─────────────────┐     ┌─────────────────┐
│   CLI Client    │────▶│  Bridge Server  │────▶│  QueryEngine    │
│  (jwcode-cli)   │     │  (jwcode-core)  │     │  (jwcode-core)  │
└─────────────────┘     └─────────────────┘     └─────────────────┘
                               │                          │
                               ▼                          ▼
                        ┌─────────────────┐     ┌─────────────────┐
                        │ Bridge Session  │     │  AgentRegistry  │
                        │   (sessionId)   │     │  (agents map)   │
                        └─────────────────┘     └─────────────────┘
```

## 与 JavaScript Claude Code 的对比

| 功能 | JavaScript Claude Code | JwCode (Java) |
|------|------------------------|---------------|
| 多 Agent | ✅ | ✅ |
| Agent 切换 | `/agent <name>` | `agent switch <name>` |
| 桥接模式 | `--bridge` | `bridge start` |
| 远程连接 | WebSocket/SSE | HTTP + SSE |
| 会话管理 | 自动 | sessionId 机制 |
