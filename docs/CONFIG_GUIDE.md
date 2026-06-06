# JwCode 配置指南

## 配置文件位置

JwCode 会按以下顺序查找配置文件：
1. `~/.jwcode/config.properties`（用户目录）- **优先使用**
2. `./jwcode.properties`（当前目录）

## 必需配置项

```properties
# API 模型配置（必需）
api.model=sonnet

# API 基础 URL（可选）
api.baseUrl=https://api.example.com

# API 密钥（必需）
api.key=your-api-key-here
```

## 创建配置文件

### Windows
```cmd
mkdir %USERPROFILE%\.jwcode
notepad %USERPROFILE%\.jwcode\config.properties
```

### 示例配置文件内容
```properties
# JwCode 配置文件

# API 配置
api.model=sonnet
api.baseUrl=https://api.example.com/v1
api.key=sk-your-api-key-here

# 可选配置
# api.maxTokens=4096
# api.temperature=0.7
```

## Web UI 使用说明

启动 Web UI 后，错误 `model is required - 请在配置文件中设置 api.model` 已修复：

- **如果有配置文件**：系统会自动从配置文件读取 `api.model`
- **如果没有配置文件**：系统会使用默认值 `sonnet`（会在日志中显示警告）

## 查看日志

在 Web UI 中点击 "📋 日志" 按钮可以查看：
- 模型配置信息
- 查询执行状态
- 错误信息

---

## AICL 上下文协议配置（v1.1 新增）

AICL (Agent Interaction Context Language) 是上下文块的结构化生命周期管理协议。
在 `~/.jwcode/config.properties` 或 `./jwcode.properties` 中添加以下配置：

```properties
# ===== AICL 上下文协议配置 =====

# 是否启用 AICL 协议（默认 true）
aicl.enabled=true

# Token 总预算（默认 8000）
aicl.totalBudget=8000

# 触发淘汰的使用率阈值（默认 0.8，即使用率达 80% 时触发）
aicl.evictionThreshold=0.8

# 停止淘汰的使用率水位（默认 0.75）
aicl.stopThreshold=0.75

# 上下文块默认存活轮数（默认 3，-1 表示永久）
aicl.defaultTtl=3

# 最大压缩代际（默认 2，防止无限摘要）
aicl.maxGeneration=2
```

### 配置说明

| 配置项 | 默认值 | 说明 |
|--------|--------|------|
| `aicl.enabled` | true | 禁用后降级为传统 SMART 策略 |
| `aicl.totalBudget` | 8000 | 根据模型上下文窗口调整 |
| `aicl.evictionThreshold` | 0.8 | 80% 水位线触发 Priority-LRU 淘汰 |
| `aicl.stopThreshold` | 0.75 | 淘汰停止水位，确保不浪费 CPU |
| `aicl.defaultTtl` | 3 | 新块的默认存活轮数 |
| `aicl.maxGeneration` | 2 | 防止信息失真，超过后直接归档 |

### 淘汰策略

AICL 使用 **Priority-LRU** 算法：
- `optional` → 直接删除
- `low` → 归档（仅保留元数据）
- `medium` → 摘要替换
- `high` → 同义压缩（去冗余格式）
- `critical` → 仅删注释
- `pinned` → 永久保留

详细协议规范见 `docs/AICL_SPEC.md`。


---

## YAML 配置格式（v2 新增）

系统也支持 YAML 格式的配置文件 `~/.jwcode/config.yaml`，支持多 Provider、多模型、API key 轮询等高级功能。

### 完整配置示例

```yaml
providers:
  # — OpenAI 兼容格式（默认）
  deepseek:
    base-url: https://api.deepseek.com
    api-type: openai-completions
    api-keys:
      - sk-deepseek-key-1
      - sk-deepseek-key-2
    key-rotation:
      strategy: round_robin      # round_robin | random | priority
      failover-enabled: true
      cooldown-ms: 60000
    models:
      - id: deepseek-chat
        max-tokens: 8192
        temperature: 0.7
        context-window: 65536

  # — Anthropic Messages 格式
  deepseek-anthropic:
    base-url: https://api.deepseek.com/anthropic
    api-type: anthropic-messages
    api-keys: [ sk-deepseek-key-1 ]
    anthropic-version: 2023-06-01    # 可选，默认即可
    models:
      - id: deepseek-chat
        max-tokens: 8192

  # — 原生 Anthropic Claude
  claude:
    base-url: https://api.anthropic.com
    api-type: anthropic-messages
    api-keys: [ sk-ant-xxx ]
    models:
      - id: claude-sonnet-4-20250514
        max-tokens: 8192

default-provider: deepseek           # 默认使用的 provider
fallback-provider: deepseek-anthropic # 限流时的备选 provider

settings:
  timeout-seconds: 300
  max-retries: 3
  debug: false
  engine:
    max-iterations: 0
    timeout-minutes: 5
    token-budget: 1000000
```

### api-type 说明

| api-type | 对应服务类 | 协议格式 |
|----------|-----------|---------|
| `openai-completions`（默认） | `OpenAILLMService` | OpenAI Chat Completions API |
| `anthropic-messages` | `AnthropicLLMService` | Anthropic Messages API |

选择 `anthropic-messages` 可以：
- 使用原生 Anthropic API 调用 Claude 模型
- 通过兼容层调用支持 Anthropic 格式的第三方 API（如 DeepSeek 的 `/anthropic` 端点）
- 利用 Anthropic 的 thinking 块和 tool_use 增量传输功能

### ProviderConfig 字段

| 字段 | 类型 | 默认值 | 说明 |
|------|------|--------|------|
| `base-url` | string | — | API 基础 URL |
| `api-type` | string | `openai-completions` | API 协议类型 |
| `api-keys` | string[] | — | API key 列表，支持多 key 轮询 |
| `anthropic-version` | string | `2023-06-01` | Anthropic API 版本（仅 `anthropic-messages` 类型） |
| `key-rotation` | object | — | API key 轮询配置 |
| `models` | ModelDefinition[] | — | 模型定义列表 |

