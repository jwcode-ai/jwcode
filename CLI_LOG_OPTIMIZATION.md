# JWCode CLI 日志调试指南

## 问题描述

jwcode-cli 执行到半路没有继续执行，停在 `"? 思考中...? 工具调用:"` 输出之后。

## 已添加的日志增强

### 1. LogConfigurator 类
位置：`jwcode-cli/src/main/java/com/jwcode/cli/log/LogConfigurator.java`

功能：
- 配置日志输出到文件：`~/.jwcode/logs/jwcode.log`
- 配置调试日志：`~/.jwcode/logs/jwcode-debug.log`
- 支持安静模式、详细模式、调试模式

### 2. JwCodeApplication 中的日志

#### handleNaturalLanguageQuery 方法
```java
logger.debug("=== handleNaturalLanguageQuery 开始 ===");
logger.debug("用户输入: " + input);
logger.debug("消息已添加到会话，当前消息数: " + session.getMessages().size());
logger.debug("开始 runConversationWithThinking...");
logger.debug("=== handleNaturalLanguageQuery 结束 ===");
```

#### executeToolCallsWithDisplay 方法
```java
logger.info("开始执行 " + toolCalls.size() + " 个工具调用");
logger.debug("工具调用 - 名称: " + toolName);
logger.debug("工具调用 - 参数: " + args);
logger.info("开始执行工具: " + toolName);
logger.debug("提交工具执行到线程池...");
logger.debug("等待工具执行结果...");
logger.debug("工具执行完成，检查结果...");
logger.debug("工具执行成功，结果长度: " + result.length());
logger.info("工具 " + toolName + " 执行成功");
logger.error("工具 " + toolName + " 抛出异常", e);
logger.info("所有工具调用执行完成，当前会话消息数: " + session.getMessages().size());
```

## 日志文件位置

日志文件位于：
- **主日志**：`~/.jwcode/logs/jwcode.log`
- **调试日志**：`~/.jwcode/logs/jwcode-debug.log`

## 如何查看日志

### Windows
```bash
# 查看主日志
type %USERPROFILE%\.jwcode\logs\jwcode.log

# 查看调试日志
type %USERPROFILE%\.jwcode\logs\jwcode-debug.log

# 实时跟踪日志（推荐）
powershell -command "Get-Content %USERPROFILE%\.jwcode\logs\jwcode.log -Wait -Tail 50"
```

### macOS/Linux
```bash
# 查看主日志
cat ~/.jwcode/logs/jwcode.log

# 查看调试日志
cat ~/.jwcode/logs/jwcode-debug.log

# 实时跟踪日志
tail -f ~/.jwcode/logs/jwcode.log
```

## 可能的中断原因

根据代码分析，以下是 jwcode-cli 可能中断的原因：

### 1. 工具执行超时
- 某些工具（如 MCP 服务器连接、文件搜索等）执行时间过长
- `future.get()` 会无限等待CompletableFuture完成

### 2. API 请求阻塞
- LLM API 请求超时（默认 300 秒）
- 网络连接问题导致请求挂起

### 3. 线程死锁
- CompletableFuture 链式调用中的潜在死锁
- 工具执行器的线程池问题

### 4. 异常被吞没
- 某些异常没有正确传播
- 异步执行中的异常未被捕获

## 进一步调试建议

### 启用更详细的日志
修改 `LogConfigurator.configureQuietMode()` 为 `LogConfigurator.configureVerboseMode()`

### 添加断点调试
在以下位置添加断点：
- `JwCodeApplication.java` 第 321 行：`handleNaturalLanguageQuery()`
- `JwCodeApplication.java` 第 470 行：`executeToolCallsWithDisplay()`
- `OpenAILLMService.java` 第 95 行：`httpClient.send()`

### 使用 JMX 监控
添加 JMX 参数启动应用：
```bash
java -Dcom.sun.management.jmxremote -jar jwcode-cli.jar
```

## 预期日志输出

正常执行时，你应该看到类似以下的日志：

```
[INFO] 开始执行 2 个工具调用
[DEBUG] 工具调用 - 名称: BashTool
[DEBUG] 工具调用 - 参数: {"command":"Get-ChildItem ..."}
[INFO] 开始执行工具: BashTool
[DEBUG] 提交工具执行到线程池...
[DEBUG] 等待工具执行结果...
[DEBUG] 工具执行完成，检查结果...
[DEBUG] 工具执行成功，结果长度: 1024
[INFO] 工具 BashTool 执行成功
[INFO] 所有工具调用执行完成，当前会话消息数: 8
```

如果执行中断，日志会停在某个步骤，帮助你定位问题。

## 如何报告问题

如果找到问题，请提供：
1. 完整的日志文件（`jwcode-debug.log`）
2. 执行的命令/输入
3. 中断前的最后输出
4. 你的配置（去掉敏感信息）

---
最后更新：2026-04-15
