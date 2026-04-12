# JWCode YAML 配置指南

## 概述

JWCode 现在支持 YAML 配置文件格式，提供更灵活、更强大的配置能力。

## 配置文件位置

1. **用户级配置**: `~/.jwcode/config.yaml`
2. **项目级配置**: `./.jwcode/config.yaml` (当前工作目录)

项目级配置会覆盖用户级配置。

## 完整配置示例

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
      - sk-your-second-api-key  # 支持多密钥轮询
    key-rotation:
      strategy: round_robin      # 轮询策略: round_robin, random
      failover-enabled: true     # 启用故障转移
      max-retries: 3             # 最大重试次数
      cooldown-ms: 60000         # 冷却时间(毫秒)
    models:
      - id: kimi-k2.5
        name: kimi-k2.5
        enabled: true
        priority: 10
        reasoning: false
        context-window: 2048000   # 上下文窗口大小
        max-tokens: 32768         # 最大生成token数
        temperature: 1            # 温度参数 (kimi-k2.5 必须设置为 1)
        cost:
          input: 0.0              # 输入成本
          output: 0.0             # 输出成本
          cache-read: 0.0
          cache-write: 0.0
        input:
          - text
          - image
        supports-vision: true
        supports-image-generation: false
        supported-modalities:
          - text
          - image
        max-image-size: 20971520  # 最大图片大小(字节)

  # 可以配置多个提供商
  openai:
    base-url: https://api.openai.com/v1
    api-type: openai-completions
    api-keys:
      - sk-openai-api-key
    models:
      - id: gpt-4
        name: gpt-4
        temperature: 0.7
        max-tokens: 4096

# 全局设置
settings:
  timeout-seconds: 60
  max-retries: 3
  debug: false
  log-level: INFO
```

## 关键配置说明

### temperature 参数

**重要**: 不同模型对 `temperature` 的要求不同：

| 模型 | 支持的 temperature | 建议值 |
|------|-------------------|--------|
| kimi-k2.5 | 只能为 1 | 1 |
| kimi-k1.5 | 0-2 | 0.7-1 |
| gpt-4 | 0-2 | 0.7 |
| gpt-3.5-turbo | 0-2 | 0.7 |

如果不设置 `temperature`，系统不会发送该参数，使用模型默认值。

### 多 API 密钥轮询

支持配置多个 API 密钥，系统会自动轮询使用：

```yaml
api-keys:
  - sk-key-1
  - sk-key-2
  - sk-key-3
```

轮询策略：
- `round_robin`: 轮流使用（默认）
- `random`: 随机选择
- `priority`: 优先使用第一个，故障时切换

### 多模型配置

可以在同一提供商下配置多个模型：

```yaml
models:
  - id: kimi-k2.5
    name: kimi-k2.5
    temperature: 1
    
  - id: kimi-k1.5
    name: kimi-k1.5
    temperature: 0.7
    
  - id: kimi-128k
    name: kimi-128k
    temperature: 0.7
```

## 代码中使用

### 使用默认配置

```java
ApiClientV2 client = new ApiClientV2();
```

### 使用指定提供商

```java
JwcodeConfig config = YamlConfigLoader.getInstance().getConfig();
ApiClientV2 client = new ApiClientV2(config, "moonshot", "kimi-k2.5");
```

### 切换模型

```java
ApiClientV2 client = new ApiClientV2();
client.switchModel("kimi-k1.5");  // 切换到其他模型
```

### 获取当前配置

```java
ApiClientV2 client = new ApiClientV2();

// 获取当前模型配置
JwcodeConfig.ModelDefinition model = client.getCurrentModel();
System.out.println("Model: " + model.getName());
System.out.println("Temperature: " + model.getTemperature());
System.out.println("Max Tokens: " + model.getMaxTokens());
```

## 故障排除

### "invalid temperature" 错误

如果看到类似错误：
```
HTTP 400: {"error":{"message":"invalid temperature: only 1 is allowed for this model"}}
```

**解决方案**: 在配置文件中为该模型设置正确的 temperature：

```yaml
models:
  - id: kimi-k2.5
    name: kimi-k2.5
    temperature: 1  # 必须设置为 1
```

### 配置文件未生效

1. 检查文件位置是否正确：`~/.jwcode/config.yaml`
2. 检查 YAML 格式是否正确（注意缩进）
3. 检查是否有语法错误：使用在线 YAML 验证器

### 如何重新加载配置

```java
YamlConfigLoader.getInstance().reload();
```

## 从旧版配置迁移

旧版 `config.properties` 格式：

```properties
api.endpoint=https://api.moonshot.cn/v1
api.key=sk-your-key
api.model=kimi-k2.5
```

新版 `config.yaml` 格式：

```yaml
default-provider: moonshot
providers:
  moonshot:
    base-url: https://api.moonshot.cn/v1
    api-keys:
      - sk-your-key
    models:
      - id: kimi-k2.5
        name: kimi-k2.5
        temperature: 1
```

**向后兼容**: ApiClientV2 仍然支持旧版配置文件，但建议使用新版 YAML 配置。
