# 空 Assistant 消息修复说明

## 问题原因

API 返回错误：`"Invalid request: the message at position 2 with role 'assistant' must not be empty"`

这是因为：
1. 当 AI 响应包含工具调用时，`createAssistantMessageWithToolCalls` 方法创建的助手消息 content 为空
2. Moonshot/Kimi API 要求 assistant 消息必须有 content 字段，即使是空字符串也不行

## 修复内容

### 1. Message.java
```java
public static Message createAssistantMessageWithToolCalls(String content, List<ToolCallInfo> toolCalls) {
    List<ContentBlock> blocks = new ArrayList<>();
    // 必须添加 content，即使是空字符串
    blocks.add(new TextContent(content != null ? content : ""));
    return new Message(null, Role.ASSISTANT, blocks, toolCalls) {};
}
```

**修复**: 即使 content 为空，也强制添加一个 TextContent，避免创建空的消息。

### 2. ModelLoader.java
- 在 `convertMessages` 方法中添加了空内容检查
- 如果检测到空的 assistant 消息，自动设置为 "(empty)" 占位符
- 添加了详细的日志输出，帮助调试

### 3. QueryEngine.java
- 添加了请求构建时的消息列表日志
- 添加了响应处理时的日志
- 可以清楚地看到每条消息的角色和内容长度

## 日志输出示例

修复后，程序会输出详细的日志信息：

```
[QueryEngine] 构建请求，迭代=0, 消息数量=3
[QueryEngine] 消息[0] role=SYSTEM, content长度=1234, 内容前50字=You are a helpful assistant...
[QueryEngine] 消息[1] role=USER, content长度=10, 内容前50字=hi
[QueryEngine] 消息[2] role=ASSISTANT, content长度=0, 内容前50字=(empty)
[ModelLoader] 发送请求到: https://api.moonshot.cn/v1/chat/completions
[ModelLoader] 模型: kimi-k2.5
[ModelLoader] 响应状态: 200
```

## 如何启用详细日志

如果需要更详细的调试信息，可以修改 `logging.properties`：

```properties
com.jwcode.core.model.pool.level = FINE
com.jwcode.core.query.level = FINE
```

或者在启动时添加 JVM 参数：
```bash
java -Djava.util.logging.SimpleFormatter.format='%1$tY-%1$tm-%1$td %1$tH:%1$tM:%1$tS %4$s %2$s %5$s%n' -jar jwcode.jar
```

## 测试步骤

1. 编译项目：
   ```bash
   mvn clean compile
   ```

2. 运行测试：
   ```bash
   mvn test
   ```

3. 启动应用并查看日志：
   ```bash
   java -jar jwcode-cli/target/jwcode-cli.jar
   ```

4. 输入测试消息，观察日志输出是否显示完整的消息列表

## 预期结果

- 不再出现 `"message at position X with role 'assistant' must not be empty"` 错误
- 日志中可以看到每条消息的详细信息
- 即使 AI 返回空内容，也会自动填充 "(empty)" 占位符
