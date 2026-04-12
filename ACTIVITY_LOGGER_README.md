# AI 活动日志系统 - 类似 KimiCode 的实时活动显示

## 功能概述

这个系统让你在执行 AI 任务时，能够实时看到 AI 正在做什么操作，类似于 KimiCode 的体验：

```
[14:32:10] ▶️ 📄 读取文件 读取 src/main/java 256 行 (45ms)
[14:32:11] ▶️ 🔍 搜索代码 搜索: class ActivityLogger 在 src 5 个匹配 (23ms)
[14:32:12] ▶️ ⚡ 执行命令 执行: mvn clean compile BUILD SUCCESS (1.2s)
```

## 核心特性

- ✅ **实时活动显示** - 看到 AI 正在读取文件、搜索代码、执行命令等
- ✅ **进度追踪** - 显示操作进度百分比
- ✅ **执行时间** - 显示每个操作的耗时
- ✅ **成功/失败状态** - 清晰的成功 ✓ 和失败 ✗ 标识
- ✅ **多彩输出** - 使用颜色和图标增强可读性
- ✅ **两种显示模式** - 紧凑模式（单行）和详细模式（多行）
- ✅ **工具执行追踪** - 自动追踪所有工具调用
- ✅ **活动历史** - 记录所有活动供后续查看

## 创建的文件

```
jwcode-cli/src/main/java/com/jwcode/cli/log/
├── ActivityType.java              # 活动类型枚举（文件操作、代码操作、AI 操作等）
├── ActivityEntry.java             # 活动条目类（记录单个活动的完整信息）
├── ActivityLogger.java            # 核心活动日志记录器
├── ActivityTrackingToolExecutor.java  # 带追踪的工具执行器
└── ActivityLoggerDemo.java        # 演示程序

docs/
└── ACTIVITY_LOGGER_GUIDE.md       # 详细使用指南
```

## 快速使用

### 1. 记录文件操作

```java
// 开始读取文件
String activityId = CliLogger.readingFile("src/main/java/Main.java");

// ... 执行文件读取 ...

// 标记完成
CliLogger.done(activityId, "256 行");
```

### 2. 记录代码搜索

```java
String activityId = CliLogger.searchingCode("class ActivityLogger", "src");
// ... 执行搜索 ...
CliLogger.done(activityId, "5 个匹配");
```

### 3. 记录命令执行

```java
String activityId = CliLogger.executingCommand("mvn clean compile");
// ... 执行命令 ...
CliLogger.done(activityId, "BUILD SUCCESS");
```

### 4. 带进度的操作

```java
String activityId = ActivityLogger.start(ActivityType.FILE_READ, "读取大文件");

for (int i = 0; i <= 100; i += 10) {
    ActivityLogger.progress(activityId, i);
    Thread.sleep(100);
}

ActivityLogger.complete(activityId, "完成 2.5 MB");
```

## 运行演示

```bash
# Windows
run-activity-demo.bat

# 或手动运行
mvn compile exec:java -pl jwcode-cli -Dexec.mainClass="com.jwcode.cli.log.ActivityLoggerDemo"
```

## 集成到现有代码

### 方式 1: 使用 CliLogger 便捷方法（推荐）

```java
import com.jwcode.cli.log.CliLogger;

// 文件操作
String id = CliLogger.readingFile("path/to/file.java");
// ... do work ...
CliLogger.done(id, "结果描述");

// 搜索代码
String id = CliLogger.searchingCode("pattern", "path");
// ... do work ...
CliLogger.done(id, "找到 X 个匹配");

// 执行命令
String id = CliLogger.executingCommand("command");
// ... do work ...
CliLogger.done(id, "输出结果");
```

### 方式 2: 使用 ActivityLogger 直接

```java
import com.jwcode.cli.log.ActivityLogger;
import com.jwcode.cli.log.ActivityType;

ActivityLogger logger = ActivityLogger.getInstance();

String id = logger.startActivity(ActivityType.FILE_READ, "描述");
// ... do work ...
logger.completeActivity(id, "结果");
```

### 方式 3: 追踪工具执行

```java
import com.jwcode.cli.log.ActivityTrackingToolExecutor;

ActivityTrackingToolExecutor executor = new ActivityTrackingToolExecutor();

// 执行工具时会自动记录活动
executor.execute("FileReadTool", inputJson, context);
```

## 输出示例

### 紧凑模式（默认）

```
[14:32:10] ▶️ 🤔 思考: 分析需求 (120ms)
[14:32:10] ▶️ 📄 读取文件 读取 UserService.java 256 行 (45ms)
[14:32:11] ▶️ 🔍 搜索代码 搜索: findById 在 src 3 个匹配 (23ms)
[14:32:11] ▶️ ✏️ 编辑文件 编辑 UserService.java 修改 2 处 (67ms)
[14:32:12] ▶️ ⚡ 执行命令 执行: mvn test BUILD SUCCESS (12.3s)
```

### 详细模式

```
┌─ 📄 读取文件 @ 14:32:10 ─────
│ 读取 UserService.java
│ 开始: 14:32:10.123 | 耗时: 45ms
│ 详情:
│   lines: 256
└─ ✓ 成功
```

## 配置选项

```java
ActivityLogger logger = ActivityLogger.getInstance();

// 启用/禁用
logger.setEnabled(true);

// 使用颜色
logger.setUseColor(true);

// 紧凑模式 (true) 或详细模式 (false)
logger.setCompactMode(true);

// 显示时间戳
logger.setShowTimestamp(true);
```

## 进阶功能

### 活动监听器

```java
logger.addActivityListener(entry -> {
    System.out.println("活动更新: " + entry.getType());
});
```

### 统计信息

```java
ActivityLogger.ActivityStats stats = logger.getStats();
System.out.println("总活动: " + stats.getTotalActivities());
System.out.println("成功率: " + stats.getSuccessRate() + "%");
```

### 工具调用框（KimiCode 风格）

```java
CliLogger.toolCallBox("FileReadTool", 
    "file_path: src/main.java\nstart_line: 1");
```

输出：
```
┌─ Tool: FileReadTool
│ file_path: src/main.java
│ start_line: 1
└
```

## 活动类型列表

| 类型 | 图标 | 用途 |
|------|------|------|
| FILE_READ | 📄 | 读取文件 |
| FILE_WRITE | 📝 | 写入文件 |
| FILE_EDIT | ✏️ | 编辑文件 |
| CODE_SEARCH | 🔎 | 搜索代码 |
| SHELL_EXEC | ⚡ | 执行命令 |
| WEB_SEARCH | 🌐 | 网络搜索 |
| AI_THINKING | 🤔 | AI 思考 |
| AI_PLANNING | 📋 | 规划任务 |
| TASK_CREATE | 📌 | 创建任务 |
| GIT_COMMIT | 📦 | Git 提交 |
| ... | ... | ... |

## 完整文档

详细的使用指南请查看: [docs/ACTIVITY_LOGGER_GUIDE.md](docs/ACTIVITY_LOGGER_GUIDE.md)

## 集成到 Agent 工作流

```java
public class MyAgent {
    public void processUserRequest(String request) {
        // 1. AI 思考
        String thinkingId = CliLogger.aiThinking("分析用户需求");
        analyzeRequest(request);
        CliLogger.done(thinkingId, "确定执行方案");
        
        // 2. 读取相关文件
        String readId = CliLogger.readingFile("config.json");
        Config config = readConfig();
        CliLogger.done(readId, "读取 " + config.size() + " 项配置");
        
        // 3. 搜索代码
        String searchId = CliLogger.searchingCode("TODO", "src");
        List<Result> results = searchCode("TODO");
        CliLogger.done(searchId, "找到 " + results.size() + " 处");
        
        // 4. 执行修改
        for (Result result : results) {
            String editId = CliLogger.editingFile(result.getFile());
            editFile(result);
            CliLogger.done(editId, "已修复");
        }
        
        // 5. 验证
        String testId = CliLogger.executingCommand("mvn test");
        TestResult testResult = runTests();
        CliLogger.done(testId, testResult.isSuccess() ? "通过" : "失败");
    }
}
```

这样用户就能清楚地看到 AI 正在做什么，每一步都有可视化的反馈！
