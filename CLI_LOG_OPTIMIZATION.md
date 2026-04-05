# CLI 日志输出优化说明

## 优化目标
参照 Kimi Code 的日志风格，优化 jwcode 的 CLI 输出：
- 简洁美观
- 减少无关信息
- 彩色输出
- 进度指示

---

## 优化内容

### 1. 简化 Banner

**优化前**:
```
╔══════════════════════════════════════════════════════════════╗
║                                                              ║
║   ██╗    ██╗ ██████╗ ██████╗ ██████╗ ███████╗               ║
║   ██║    ██║██╔════╝██╔═══██╗██╔══██╗██╔════╝               ║
║   ██║ █╗ ██║██║     ██║   ██║██║  ██║█████╗                 ║
║   ██║███╗██║██║     ██║   ██║██║  ██║██╔══╝                 ║
║   ╚███╔███╔╝╚██████╗╚██████╔╝██████╔╝███████╗               ║
║    ╚══╝╚══╝  ╚═════╝ ╚═════╝ ╚═════╝ ╚══════╝               ║
║                                                              ║
║              Jw Code 工具                                   ║
║                                                              ║
╚══════════════════════════════════════════════════════════════╝

版本: 1.0.0-SNAPSHOT
输入 'help' 查看可用命令，或输入 'exit' 退出。
```

**优化后**:
```
  ╔═══════════════════════════════════════════════════════════╗
  ║  ⚡ JwCode - AI Coding Assistant                         ║
  ╚═══════════════════════════════════════════════════════════╝

  版本: 1.0.0-SNAPSHOT  |  输入 'help' 查看命令  |  'exit' 退出
```

### 2. 减少调试日志

**优化前**:
```
[调试] API 客户端初始化成功
[调试] API Endpoint: https://api.minimaxi.com/v1
[调试] API Key 已配置: true
[调试] QueryEngine 初始化成功
```

**优化后**:
```
# 无输出（只在 DEBUG 级别显示）
```

### 3. 新增 CliLogger 日志器

**文件**: `jwcode-cli/src/main/java/com/jwcode/cli/log/CliLogger.java`

**功能**:
- 分级日志: DEBUG, INFO, SUCCESS, WARN, ERROR
- 彩色输出
- 紧凑/详细模式切换
- 时间戳控制

**使用**:
```java
CliLogger logger = CliLogger.getInstance();
logger.info("普通信息");
logger.success("成功信息");
logger.warn("警告信息");
logger.error("错误信息");
```

**输出效果**:
```
[10:30:15] ℹ 普通信息
[10:30:16] ✓ 成功信息
[10:30:17] ⚠ 警告信息
[10:30:18] ✗ 错误信息
```

### 4. 工具调用可视化

**优化后效果**:
```
┌─ Tool: WebSearch
│ {query: "Java 21", max_results: 5}
└
✓ WebSearch success
```

### 5. 日志配置器

**文件**: `jwcode-cli/src/main/java/com/jwcode/cli/log/LogConfigurator.java`

**模式**:
- `configureQuietMode()` - 静默模式（只显示警告及以上）
- `configureNormalMode()` - 正常模式
- `configureDebugMode()` - 调试模式
- `disableLogging()` - 完全禁用

### 6. 进度指示器

**文件**: `jwcode-cli/src/main/java/com/jwcode/cli/log/ProgressIndicator.java`

**使用**:
```java
ProgressIndicator progress = new ProgressIndicator();
progress.start("加载中...");
// 执行任务
progress.success("完成!");
```

**效果**:
```
⠙ 加载中...
```

---

## 对比 Kimi Code

| 特性 | Kimi Code | 优化后的 JwCode |
|------|-----------|----------------|
| Banner | 简洁 | ✅ 简洁 |
| 彩色输出 | ✅ | ✅ |
| 日志分级 | ✅ | ✅ |
| 工具可视化 | ✅ | ✅ |
| 进度指示 | ✅ | ✅ |
| 流式输出 | ✅ | ✅ |
| 思考过程 | ✅ | ✅ |

---

## 使用示例

### 设置日志级别
```java
CliLogger.getInstance().setLevel(CliLogger.LogLevel.WARN);
```

### 禁用颜色
```java
CliLogger.getInstance().setUseColor(false);
```

### 紧凑模式
```java
CliLogger.getInstance().setCompactMode(true);
```

### 显示工具调用
```java
logger.toolCall("WebSearch", "{query: 'Java 21'}");
logger.toolResult("WebSearch", true, "结果...");
```

---

## 效果对比

### 启动效果
```
# 优化前
[调试] API 客户端初始化成功
[调试] API Endpoint: https://api.minimaxi.com/v1
[调试] API Key 已配置: true
[调试] QueryEngine 初始化成功
✓ JLine3 终端初始化成功 - 支持命令历史和自动补全
╔══════════ 大 Banner ══════════╗
║                              ║
║   很长的 ASCII 艺术字体       ║
║                              ║
╚══════════════════════════════╝
版本: 1.0.0-SNAPSHOT
输入 'help' 查看可用命令，或输入 'exit' 退出。

# 优化后
  ╔═══════════════════════════════╗
  ║  ⚡ JwCode - AI Coding Asst   ║
  ╚═══════════════════════════════╝

  版本: 1.0.0-SNAPSHOT  |  'help' 查看命令  |  'exit' 退出
```

---

## 后续优化建议

1. **流式输出美化** - 显示 AI 思考过程动画
2. **工具调用时间** - 显示执行耗时
3. **会话统计** - 显示 token 使用/成本
4. **错误折叠** - 大段错误信息可折叠
5. **Markdown 渲染** - 终端显示格式化 Markdown

---

*优化时间: 2026-04-05*
