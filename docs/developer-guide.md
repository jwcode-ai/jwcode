# JWCode 开发者文档

## 目录

1. [项目架构](#项目架构)
2. [模块设计](#模块设计)
3. [代码规范](#代码规范)
4. [开发环境设置](#开发环境设置)
5. [调试指南](#调试指南)

---

## 项目架构

### 整体架构图

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              JWCode Application                              │
├─────────────────────────────────────────────────────────────────────────────┤
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐        │
│  │   CLI       │  │   REPL      │  │   SDK       │  │   MCP       │        │
│  │   Interface │  │   Engine    │  │   Bridge    │  │   Server    │        │
│  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘  └──────┬──────┘        │
│         │                │                │                │                │
│  ┌──────┴────────────────┴────────────────┴────────────────┴──────┐        │
│  │                    Core Engine Layer                            │        │
│  │  ┌───────────┐  ┌───────────┐  ┌───────────┐  ┌───────────┐    │        │
│  │  │  Query    │  │   Tool    │  │  Session  │  │   Task    │    │        │
│  │  │  Engine   │  │  Executor │  │  Manager  │  │  Manager  │    │        │
│  │  └───────────┘  └───────────┘  └───────────┘  └───────────┘    │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                    Services Layer                               │        │
│  │  ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐ ┌────────┐        │        │
│  │  │  MCP   │ │ Plugin │ │ Skill  │ │  LSP   │ │Analytics│       │        │
│  │  │Service │ │Service │ │Service │ │Service │ │ Service │       │        │
│  │  └────────┘ └────────┘ └────────┘ └────────┘ └────────┘        │        │
│  └─────────────────────────────────────────────────────────────────┘        │
│  ┌─────────────────────────────────────────────────────────────────┐        │
│  │                    Infrastructure Layer                         │        │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌──────────┐           │        │
│  │  │   File   │ │  Process │ │  Network │ │  Config  │           │        │
│  │  │  System  │ │  Manager │ │  Client  │ │  Manager │           │        │
│  │  └──────────┘ └──────────┘ └──────────┘ └──────────┘           │        │
│  └─────────────────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────────────────┘
```

### 数据流图

```
用户输入
    │
    ▼
┌─────────────┐
│  CLI/REPL   │ 接收用户输入
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ QueryEngine │ 构建 API 请求
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  ApiClient  │ 发送请求到 AI 服务
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ StreamHandler│ 流式处理响应
└──────┬──────┘
       │
       ▼
┌─────────────┐
│ ToolExecutor │ 执行工具调用
└──────┬──────┘
       │
       ▼
┌─────────────┐
│   Output    │ 渲染结果到终端
└─────────────┘
```

---

## 模块设计

### jwcode-core 模块

#### 包结构

```
com.jwcode.core
├── query/           # 查询引擎
├── tool/            # 工具系统
├── session/         # 会话管理
├── task/            # 任务管理
├── agent/           # Agent 系统（多Agent分层架构）
│   ├── OrchestratorAgent       # 主控指挥家
│   ├── EnhancedOrchestratorAgent # 增强版（PDCA循环）
│   ├── TaskAgent               # AI回复→结构化任务解析器 ← v1.1
│   ├── TaskExecutionAgent      # 结构化任务逐步执行器 ← v1.1
│   ├── CoderAgent/DebugAgent/  # 专业Worker Agent
│   │   ReviewerAgent/TestAgent/
│   │   DocAgent/ExploreAgent/
│   │   ArchitectAgent
│   ├── CompactorAgent          # 上下文压缩专家
│   └── fork/                   # 子Agent Fork机制
├── aicl/            # AICL 协议引擎（上下文生命周期管理）← v1.1
│   ├── BlockPriority           # 6级优先级枚举
│   ├── BlockLifecycle           # 6状态生命周期枚举
│   ├── ContextBlock            # 上下文块模型
│   ├── ContextControl          # 控制层（预算+策略）
│   ├── ContextAssembler        # Priority-LRU 淘汰引擎
│   ├── AICLSerializer          # XML 序列化器
│   ├── AICLDeserializer        # XML 反序列化器
│   ├── AICLContext             # 完整上下文容器
│   └── AICLPromptBuilder       # AI解析规则生成器
├── hook/            # Hook 拦截体系（生命周期拦截与运行时治理）← v2.1
│   ├── HookDecision            # 6种决策语义枚举
│   ├── HookEventType           # 12种事件类型枚举
│   ├── HookImplementationType  # 4种实现形态枚举
│   ├── HookPriority            # 5级优先级 + ConflictResolver
│   ├── HookContext             # 上下文数据模型
│   ├── HookResult              # 决策结果模型
│   ├── HookConfig              # 配置模型
│   ├── HookChain               # ★ 核心拦截编排引擎
│   ├── HookRegistry            # 配置加载 + 热重载
│   ├── HookAuditLogger         # 审计日志
│   ├── StateTransitionEvent    # TransitionGuard 事件
│   ├── RollbackAction          # 回退策略
│   └── executor/
│       ├── ShellHookExecutor   # Shell 脚本执行器
│       ├── HttpHookExecutor    # HTTP REST 执行器
│       ├── PromptHookExecutor  # LLM Prompt 执行器
│       └── AgentHookExecutor   # 子 Agent 调查执行器
├── a2a/             # A2A 协议（Agent间通信）
│   ├── model/       # A2ATask, ErrorSummary, TaskLifecycle...
│   └── retry/       # RetryOrchestrator, RetryStrategy
├── model/           # 数据模型
│   ├── PlanTask     # 计划任务模型
│   └── StructuredTask # 结构化任务模型 ← v1.1
└── exception/       # 异常定义
```

#### 核心类说明

| 类名 | 职责 | 依赖关系 |
|------|------|----------|
| QueryEngine | 处理 AI 查询请求 | ApiClient, ToolExecutor, Session |
| ToolExecutor | 执行工具调用 | ToolRegistry, PermissionChecker |
| SessionManager | 管理会话生命周期 | SessionStore, SessionId |
| ToolRegistry | 工具注册和查找 | Tool |
| EnhancedOrchestratorAgent | 主Agent：意图识别、PDCA循环、调度子Agent | IntentAnalyzer, TaskAgent, A2AFacade, PlanTaskBroadcaster |
| TaskAgent | AI回复→结构化任务解析（阶段/模式/依赖） | StructuredTask |
| TaskExecutionAgent | 结构化任务逐步执行（并发/串行调度） | A2AFacade, PlanTaskBroadcaster |
| StructuredTask | 结构化任务模型（执行模式+阶段+并发组） | PlanTask |
| ContextAssembler | AICL Priority-LRU 淘汰引擎 | ContextBlock, BlockPriority |
| ContextBlock | AICL 上下文块（id/type/priority/state/ttl/generation） | BlockPriority, BlockLifecycle |
| AICLSerializer | AICL XML 序列化器（ContextBlock → AICL XML v1.0） | ContextBlock, AICLContext |
| HookChain | ★ Hook 拦截编排引擎（优先级排序→串行执行→短路）← v2.1 | HookRegistry, HookExecutor, HookAuditLogger |
| HookRegistry | Hook 配置加载 + 热重载 + 事件索引 ← v2.1 | HookConfig, HookExecutor, .jwcode/hooks.json |
| HookContext | Hook 上下文（公共字段 + 事件专用字段 + 序列化）← v2.1 | HookEventType, ToolExecutionContext |
| HookResult | Hook 决策结果（decision/reason/modifiedInput/rollbackAction）← v2.1 | HookDecision, RollbackAction |
| TransitionGuard | 状态转换前置审批（STATE_TRANSITION Hook）← v2.1 | MainAgentStateMachine, HookChain |

### jwcode-core 命令系统

统一 slash command 系统以 Java 为单一源头，详见 [COMMAND_SYSTEM.md](COMMAND_SYSTEM.md)。

#### 包结构

```
com.jwcode.core.command
├── Command.java           # 命令契约（含 category/source/requiresArgs 默认方法）
├── CommandSource.java     # CORE/SESSION/WORKSPACE/TOOLS/CONFIG 枚举
├── CommandResult.java     # SUCCESS/ERROR/EXIT 结果
├── CommandRegistry.java   # 单例注册表 + createFull 工厂
├── CommandExecutor.java   # 解析与执行
└── *Command.java          # 26 个命令实现
```

### jwcode-mcp 模块

#### 包结构

```
com.jwcode.mcp
├── client/          # MCP 客户端
├── server/          # MCP 服务器
├── protocol/        # 协议定义
└── transport/       # 传输层
```

---

## 代码规范

### 命名规范

#### 类命名
- 使用 PascalCase（大驼峰）
- 名词命名，表达清晰职责
- 接口使用形容词或名词

```java
// 好的示例
public class QueryEngine { }
public class ToolExecutor { }
public interface Runnable { }

// 不好的示例
public class queryEngine { }  // 小写开头
public class Manager { }      // 太模糊
```

#### 方法命名
- 使用 camelCase（小驼峰）
- 动词开头，表达操作
- getter/setter 遵循 JavaBean 规范

```java
// 好的示例
public CompletableFuture<ToolResult> executeTool();
public String getSessionId();
public void setModel(String model);

// 不好的示例
public CompletableFuture<ToolResult> ToolExecute();  // 动词位置不对
public String sessionId();  // 缺少 get 前缀
```

#### 常量命名
- 全大写，下划线分隔
- 表达清晰含义

```java
public static final int MAX_RETRY_COUNT = 3;
public static final String DEFAULT_MODEL = "sonnet";
```

### 注释规范

#### 类注释模板

```java
/**
 * [类名] - [简短描述]
 * 
 * 功能说明：
 * [详细描述类的主要功能和职责]
 * 
 * 上下文关系：
 * - [与哪些类有关联]
 * - [被哪些类使用]
 * - [依赖哪些服务]
 * 
 * 线程安全：
 * [说明是否线程安全，如适用]
 * 
 * 使用示例：
 * <pre>{@code
 * // 代码示例
 * }</pre>
 * 
 * @author JWCode Team
 * @since 1.0.0
 * @see [相关类]
 */
```

#### 方法注释模板

```java
/**
 * [方法名] - [方法功能]
 * 
 * 功能说明：
 * [详细描述方法的功能，包括前置条件和后置条件]
 * 
 * @param [参数名] [参数描述，包括约束条件]
 * @return [返回值描述，包括可能的 null 值]
 * @throws [异常类型] [异常说明，包括触发条件]
 */
```

### 异常处理规范

```java
/**
 * 自定义异常应继承 RuntimeException（非受检异常）
 * 或 Exception（受检异常），并提供清晰的错误信息
 */
public class ToolExecutionException extends RuntimeException {
    
    private final String toolName;
    private final String errorCode;
    
    public ToolExecutionException(String toolName, String message, Throwable cause) {
        super(String.format("Tool '%s' execution failed: %s", toolName, message), cause);
        this.toolName = toolName;
        this.errorCode = "TOOL_EXECUTION_FAILED";
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public String getToolName() {
        return toolName;
    }
}
```

---

## 开发环境设置

### 1. 安装 JDK 17+

```bash
# macOS (使用 Homebrew)
brew install openjdk@17

# Ubuntu/Debian
sudo apt-get install openjdk-17-jdk

# Windows
# 从 https://adoptium.net/ 下载安装
```

### 2. 安装 Maven

```bash
# macOS
brew install maven

# Ubuntu/Debian
sudo apt-get install maven

# Windows
# 从 https://maven.apache.org/download.cgi 下载
```

### 3. 克隆项目

```bash
git clone https://github.com/your-org/jwcode.git
cd jwcode
```

### 4. 构建项目

```bash
# 编译并运行测试
mvn clean install

# 跳过测试编译
mvn clean install -DskipTests

# 只编译特定模块
mvn clean install -pl jwcode-core -am
```

### 5. IDE 设置

#### IntelliJ IDEA
1. 打开项目目录
2. 选择"Open as Maven Project"
3. 等待依赖下载完成
4. 安装 Lombok 插件（如使用）

#### Eclipse
1. File -> Import -> Existing Maven Projects
2. 选择项目根目录
3. 完成导入

---

## 调试指南

### 本地调试

#### 使用 IDE 调试

1. 在代码中设置断点
2. 以 Debug 模式运行主类
3. 触发相应功能

#### 远程调试

```bash
# 启动时添加调试参数
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=5005 \
     -jar jwcode-cli/target/jwcode-cli-1.0.0.jar
```

然后在 IDE 中配置 Remote Debug，连接到 localhost:5005。

### 日志调试

```java
// 使用 SLF4J 进行日志记录
private static final Logger log = LoggerFactory.getLogger(MyClass.class);

public void doSomething() {
    log.debug("调试信息：{}", data);
    log.info("一般信息：{}", info);
    log.warn("警告信息：{}", warning);
    log.error("错误信息：{}", error, exception);
}
```

### 常见问题排查

#### 问题 1：依赖冲突

```bash
# 查看依赖树
mvn dependency:tree

# 查找特定依赖
mvn dependency:tree -Dincludes=com.fasterxml.jackson.core
```

#### 问题 2：测试失败

```bash
# 运行单个测试
mvn test -Dtest=MyTestClass

# 运行特定模块的测试
mvn test -pl jwcode-core
```

#### 问题 3：构建缓慢

```bash
# 使用离线模式（依赖已下载时）
mvn clean install -o

# 并行构建
mvn clean install -T 4
```

---

## 测试指南

### 单元测试

```java
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import static org.junit.jupiter.api.Assertions.*;

class QueryEngineTest {
    
    private QueryEngine queryEngine;
    
    @BeforeEach
    void setUp() {
        queryEngine = new QueryEngine.Builder()
            .withModel("test-model")
            .build();
    }
    
    @Test
    void testQuery_Success() {
        // Given
        QueryRequest request = new QueryRequest("test prompt");
        
        // When
        QueryResult result = queryEngine.query(request).join();
        
        // Then
        assertNotNull(result);
        assertFalse(result.getMessages().isEmpty());
    }
    
    @Test
    void testQuery_EmptyPrompt() {
        // Given
        QueryRequest request = new QueryRequest("");
        
        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            queryEngine.query(request).join();
        });
    }
}
```

### 集成测试

```java
@SpringBootTest
@AutoConfigureMockMvc
class ToolIntegrationTest {
    
    @Autowired
    private ToolExecutor toolExecutor;
    
    @Autowired
    private ToolContext context;
    
    @Test
    void testBashTool_ExecuteCommand() {
        // Given
        BashTool tool = new BashTool();
        BashInput input = new BashInput("echo hello");
        
        // When
        ToolResult<BashOutput> result = tool.call(input, context, null, null, null).join();
        
        // Then
        assertEquals("hello", result.getData().getOutput().trim());
    }
}
```

---

## Plan/Act 模式开发指南

> 新增于 v1.1

### 结构化任务流程

Plan/Act 模式下的任务执行链路：

```
用户输入 → EnhancedOrchestratorAgent.processConfirmedPlan()
  ├── TaskAgent.parsePlan()       → List<StructuredTask>
  ├── broadcastStructuredTasks()  → WebSocket → 前端
  └── TaskExecutionAgent.execute()
        ├── SEQUENTIAL: 拓扑排序 → 逐个 A2AFacade.submitTaskSync()
        └── CONCURRENT: 线程池 CompletableFuture.allOf()
```

### 添加新的任务解析规则

在 `TaskAgent.java` 中扩展解析能力：

```java
// 1. 添加新的阶段关键词
PHASE_KEYWORDS.put("deploy|部署|发布", TaskPhase.GENERAL);

// 2. 添加新的并发检测模式
// 修改 CONCURRENT_PATTERN

// 3. 添加新的依赖检测模式
// 修改 DEPENDS_ON_PATTERN
```

### 添加新的前端视图

1. 在 `StructuredTaskView.tsx` 中添加新的渲染组件
2. 在 `PlanPanel.tsx` 中添加到 `ViewMode` 枚举
3. 在 `planStore.ts` 中添加对应的状态管理方法

### Web 前端组件结构

```
jwcode-web/src/
├── components/Plan/
│   ├── PlanPanel.tsx              # 主面板（模式切换+视图选择）
│   ├── StructuredTaskView.tsx     # 结构化任务视图 ← v1.1
│   ├── KanbanBoard.tsx            # 看板视图
│   ├── TaskTree.tsx               # 树形视图
│   ├── PlanTimeline.tsx           # 时间线视图
│   └── ...
├── stores/
│   ├── planStore.ts               # Plan 状态管理（含结构化任务）← v1.1
│   └── ...
├── types/
│   └── index.ts                   # TypeScript 类型定义 ← v1.1
└── hooks/
    └── useWebSocket.ts            # WebSocket 消息处理 ← v1.1
```

---

## 发布流程

### 1. 版本号更新

```bash
# 更新 pom.xml 中的版本号
mvn versions:set -DnewVersion=1.0.0
```

### 2. 构建发布包

```bash
mvn clean package -P release
```

### 3. 创建 Git 标签

```bash
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin v1.0.0
```

---

## 参考资源

- [Java 官方文档](https://docs.oracle.com/en/java/)
- [Maven 官方文档](https://maven.apache.org/guides/)
- [JUnit 5 用户指南](https://junit.org/junit5/docs/current/user-guide/)
- [SLF4J 用户手册](http://www.slf4j.org/manual.html)