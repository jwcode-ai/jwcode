# Anthropic SDK 兼容方式接入 MiniMax API

本文档说明如何在 JWCode 中使用 Anthropic SDK 兼容方式调用 MiniMax 模型。

## 概述

MiniMax API 现已支持 Anthropic API 格式，通过简单的配置即可将 MiniMax 的能力接入到 Anthropic API 生态中。

## 支持的模型

| 模型名称 | 上下文窗口 | 模型介绍 |
|---------|-----------|---------|
| MiniMax-M2.7 | 204,800 | 开启模型的自我迭代（输出速度约 60 TPS） |
| MiniMax-M2.7-highspeed | 204,800 | M2.7 极速版：效果不变，更快，更敏捷（输出速度约 100 TPS） |
| MiniMax-M2.5 | 204,800 | 顶尖性能与极致性价比，轻松驾驭复杂任务（输出速度约 60 TPS） |
| MiniMax-M2.5-highspeed | 204,800 | M2.5 极速版：效果不变，更快，更敏捷（输出速度约 100 TPS） |
| MiniMax-M2.1 | 204,800 | 强大多语言编程能力，全面升级编程体验（输出速度约 60 TPS） |
| MiniMax-M2.1-highspeed | 204,800 | M2.1 极速版：效果不变，更快，更敏捷（输出速度约 100 TPS） |
| MiniMax-M2 | 204,800 | 专为高效编码与 Agent 工作流而生 |

## 快速配置

### 1. 首次配置

```bash
# 设置 API 端点（Anthropic 兼容端点）
jwcode config set-endpoint https://api.minimaxi.com/anthropic

# 设置 API 密钥
jwcode config set-key <YOUR_MINIMAX_API_KEY>

# 设置模型（可选，默认使用 MiniMax-M2.7）
jwcode config set-model MiniMax-M2.7

# 查看配置
jwcode config show
```

### 2. 环境变量方式

也可以直接设置环境变量：

```bash
# Windows PowerShell
$env:ANTHROPIC_API_KEY="your-api-key"

# Windows CMD
set ANTHROPIC_API_KEY=your-api-key

# Linux/macOS
export ANTHROPIC_API_KEY=your-api-key
```

### 3. 配置文件位置

配置文件位于：`~/.jwcode/config.properties`

示例配置：

```properties
# JWCode Configuration
api.endpoint=https://api.minimaxi.com/anthropic
api.key=your-api-key
api.model=MiniMax-M2.7
```

## 使用示例

配置完成后，直接启动 JWCode：

```bash
jwcode
```

然后输入自然语言与 AI 对话：

```
jwcode> 写一个 Java 版本的快速排序算法
```

## 切换模型

在 JWCode 交互式界面中：

```
jwcode> model

可用模型:
  * MiniMax-M2.7 (当前)
    MiniMax-M2.7-highspeed
    MiniMax-M2.5
    MiniMax-M2.5-highspeed
    MiniMax-M2.1
    MiniMax-M2.1-highspeed
    MiniMax-M2
    MiniMax-Text-01
    claude-3-5-sonnet
    gpt-4

jwcode> model MiniMax-M2.7-highspeed
已切换到模型: MiniMax-M2.7-highspeed
```

## 注意事项

1. **temperature 参数**：取值范围为 (0.0, 1.0]，推荐使用 1.0，超出范围会返回错误
2. **多轮 Function Call**：在多轮 Function Call 对话中，必须将完整的模型返回（即 assistant 消息）添加到对话历史，以保持思维链的连续性
3. **支持的参数**：
   - 完全支持：model、max_tokens、stream、system、temperature、tool_choice、tools、top_p、thinking、metadata
   - 忽略参数：top_k、stop_sequences、service_tier、mcp_servers、context_management、container
4. **不支持的内容类型**：暂不支持图像和文档类型的输入

## 兼容性说明

### 与 OpenAI 兼容格式的区别

| 特性 | Anthropic 格式 | OpenAI 格式 |
|-----|---------------|------------|
| 端点路径 | /v1/messages | /v1/chat/completions |
| System 消息 | 作为顶层参数 | 作为 messages 数组中的角色 |
| 工具调用 | tool_use / tool_result | tool_calls / tool |
| 响应格式 | content 数组 | choices[0].message |

### 自动端点识别

JWCode 会自动识别 API 端点类型并选择正确的请求格式：

- 如果 URL 包含 `anthropic` 或 `minimaxi`，默认使用 Anthropic 格式
- 如果 URL 包含 `openai`、`azure` 或 `ollama`，使用 OpenAI 格式

## 故障排除

### API 调用失败

1. 检查 API 密钥是否正确设置
2. 确认网络连接正常
3. 查看 `~/.jwcode/logs/` 目录下的日志文件

### 模型响应为空

1. 检查模型名称是否正确
2. 尝试切换其他模型
3. 确认 API 密钥有权限访问该模型

### 工具调用失败

1. 在多轮对话中，确保完整回传 assistant 消息的 content
2. 检查 tool_use_id 是否正确传递

## 参考文档

- [MiniMax Anthropic API 文档](https://www.minimaxi.com/anthropic-api-docs)
- [Anthropic SDK 文档](https://docs.anthropic.com/)
