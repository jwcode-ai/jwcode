# JWCode 新功能使用指南

## 概述

本文档介绍 JWCode 新增的核心功能：
1. 子 Agent Fork 上下文继承机制
2. 配置化最大循环次数限制
3. 流式响应与 Web UI 深度集成
4. Agent 配置化（多 Agent 支持）
5. **Plan/Act 模式结构化任务系统** ← 新增 v1.1

---

## 1. 子 Agent Fork 上下文继承机制

### 功能说明

从父 Agent Fork 出子 Agent，实现：
- **上下文继承**：子 Agent 可以看到父 Agent 的历史对话
- **执行隔离**：子 Agent 的执行状态完全独立
- **并行执行**：可以同时 Fork 多个子 Agent 并行工作

### 使用示例

```java
import com.jwcode.core.agent.Agent;
import com.jwcode.core.agent.fork.SubAgentFork;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolRegistry;

public class SubAgentExample {
    
    public void example(Agent parentAgent, Session parentSession, ToolRegistry registry) {
        
        // 方式 1：简单 Fork
        SubAgentFork.SubAgentResult result = SubAgentFork
            .from(parentAgent, parentSession, registry)
            .withTask("分析项目依赖")
            .withAgentType("analyzer")
            .execute();
        
        // 在子 Agent 中执行任务
        result.run("请分析 pom.xml 中的依赖关系").thenAccept(response -> {
            System.out.println("子 Agent 结果: " + response);
        });
        
        // 方式 2：完整配置
        SubAgentFork.SubAgentResult result2 = SubAgentFork
            .from(parentAgent, parentSession, registry)
            .withTask("重构代码")
            .withAgentType("coder")
            .withSystemPrompt("你是一个专门做代码重构的专家...")
            .withContext("targetFile", "UserService.java")
            .withContext("refactorType", "extract-method")
            .withParentTools()  // 继承父 Agent 的所有工具
            .withTimeout(120000)  // 2分钟超时
            .execute();
        
        // 方式 3：批量并行 Fork
        List<CompletableFuture<String>> futures = new ArrayList<>();
        
        for (String file : filesToAnalyze) {
            SubAgentFork.SubAgentResult subAgent = SubAgentFork
                .from(parentAgent, parentSession, registry)
                .withTask("分析文件: " + file)
                .execute();
            
            futures.add(subAgent.run("分析这个文件的代码质量"));
        }
        
        // 等待所有子 Agent 完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                System.out.println("所有分析完成!");
            });
    }
}
```

### Session Fork

```java
import com.jwcode.core.session.Session;

public class SessionForkExample {
    
    public void example(Session parentSession) {
        // 方式 1：简单 Fork
        Session forked = parentSession.fork();
        
        // 方式 2：带原因的 Fork
        Session forkedWithReason = parentSession.fork("代码重构任务");
        
        // 方式 3：使用 Fork 构建器
        Session customFork = SessionFork.from(parentSession, "复杂任务")
            .withContext("taskId", "12345")
            .withContext("priority", "high")
            .execute();
    }
}
```

---

## 2. 配置化最大循环次数限制

### 功能说明

QueryEngine 现在支持灵活的配置，可以：
- **禁用限制**：不限制最大循环次数
- **智能检测**：自动检测任务完成和循环
- **时间预算**：限制最大运行时间
- **Token 预算**：限制总 token 消耗

### 配置方式

```java
import com.jwcode.core.query.EngineConfig;
import com.jwcode.core.query.QueryEngine;

public class EngineConfigExample {
    
    public void examples(Session session) {
        
        // 方式 1：使用默认配置（最大 100 次迭代）
        QueryEngine engine1 = QueryEngine.builder()
            .session(session)
            .build();
        
        // 方式 2：无限制模式（推荐用于复杂任务）
        QueryEngine engine2 = QueryEngine.builder()
            .session(session)
            .unlimitedIterations()  // 快捷方法
            .build();
        
        // 或显式配置
        QueryEngine engine3 = QueryEngine.builder()
            .session(session)
            .engineConfig(EngineConfig.unlimited())
            .build();
        
        // 方式 3：自定义配置
        EngineConfig customConfig = new EngineConfig.Builder()
            .maxIterations(200)                    // 最大 200 次迭代
            .unlimitedIterations()                 // 或完全禁用限制
            .tokenBudget(200000)                   // Token 预算
            .maxDuration(Duration.ofMinutes(30))   // 时间预算
            .enableSmartCompletion(true)           // 启用智能完成检测
            .enableLoopDetection(true)             // 启用循环检测
            .loopDetectionThreshold(3)             // 循环检测阈值
            .debug(true)                           // 调试模式
            .build();
        
        QueryEngine engine4 = QueryEngine.builder()
            .session(session)
            .engineConfig(customConfig)
            .build();
        
        // 方式 4：严格模式（安全限制）
        QueryEngine engine5 = QueryEngine.builder()
            .session(session)
            .engineConfig(EngineConfig.strict())
            .build();
        
        // 方式 5：开发模式（调试友好）
        QueryEngine engine6 = QueryEngine.builder()
            .session(session)
            .engineConfig(EngineConfig.development())
            .build();
    }
}
```

### 预定义配置

| 配置 | 迭代限制 | 时间限制 | Token 限制 | 智能检测 | 用途 |
|------|---------|---------|-----------|---------|------|
| `defaultConfig()` | 100 | 30分钟 | 100K | 开启 | 日常使用 |
| `unlimited()` | 无限制 | 30分钟 | 100K | 开启 | 复杂任务 |
| `strict()` | 50 | 10分钟 | 50K | 开启 | 安全环境 |
| `development()` | 200 | 30分钟 | 100K | 开启 | 开发调试 |

---

## 3. 流式响应与 Web UI 深度集成

### 功能说明

Web UI 现在支持：
- **流式响应**：实时显示 AI 生成的内容
- **思考过程**：实时显示 AI 的思考过程
- **工具调用**：实时显示工具调用信息
- **WebSocket**：低延迟的双向通信

### 启动 Web UI

```java
import com.jwcode.web.WebServer;
import com.jwcode.core.tool.ToolRegistry;

public class WebUIExample {
    
    public static void main(String[] args) throws Exception {
        // 创建工具注册表
        ToolRegistry registry = ToolRegistry.createDefault();
        
        // 方式 1：使用默认端口（HTTP 8080, WebSocket 8081）
        WebServer server = new WebServer();
        server.start();
        
        // 方式 2：自定义端口
        WebServer server2 = new WebServer(3000, 3001, registry);
        server2.start();
        
        // 方式 3：仅自定义 HTTP 端口
        WebServer server3 = new WebServer(8080, registry);
        server3.start();
        
        System.out.println("按 Enter 键停止服务器...");
        System.in.read();
        
        server.stop();
    }
}
```

### WebSocket API

**连接地址**: `ws://localhost:8081`

**消息协议**:

```javascript
// 客户端 -> 服务器：发送消息
{
    "type": "chat",
    "sessionId": "session-123",
    "message": "你好，请分析这个代码"
}

// 客户端 -> 服务器：创建新会话
{
    "type": "create_session",
    "model": "sonnet"
}

// 客户端 -> 服务器：心跳
{
    "type": "ping"
}

// 服务器 -> 客户端：连接成功
{
    "type": "connected",
    "data": "Connected to JwCode Streaming Server"
}

// 服务器 -> 客户端：会话创建成功
{
    "type": "session_created",
    "data": "{\"sessionId\": \"session-xxx\", \"model\": \"sonnet\"}"
}

// 服务器 -> 客户端：开始生成
{
    "type": "start"
}

// 服务器 -> 客户端：内容块
{
    "type": "content",
    "data": "这是一段生成的内容..."
}

// 服务器 -> 客户端：思考过程
{
    "type": "thinking",
    "data": "正在分析问题..."
}

// 服务器 -> 客户端：工具调用
{
    "type": "tool_call",
    "data": "{\"tool\": \"WebSearch\", \"query\": \"...\"}"
}

// 服务器 -> 客户端：完成
{
    "type": "complete"
}

// 服务器 -> 客户端：错误
{
    "type": "error",
    "data": "错误信息"
}

// 服务器 -> 客户端：心跳响应
{
    "type": "pong"
}
```

---

## 4. Agent 配置化（多 Agent 支持）

### 功能说明

Agent 现在支持从 JSON/YAML 配置文件加载，支持：
- **配置继承**：子配置可以继承并覆盖父配置
- **热重载**：配置文件修改后自动重新加载
- **权限控制**：细粒度的工具权限管理
- **多 Agent 切换**：动态切换不同的 Agent 配置

### 配置文件格式

#### YAML 格式 (coder.yaml)

```yaml
name: "Coding Agent"
description: "专业的代码编写 Agent"
type: "coder"
version: "1.0.0"

systemPrompt: |
  你是一个专业的编码助手，擅长：
  - 编写清晰、高效的代码
  - 代码重构和优化
  - 代码审查和建议
  - 解决技术问题
  
  原则：
  - 遵循最佳实践
  - 编写可维护的代码
  - 提供清晰的注释

model:
  name: "sonnet"
  temperature: 0.7
  maxTokens: 4096
  topP: 1.0

tools:
  - FileRead
  - FileWrite
  - FileEdit
  - Glob
  - Grep
  - Bash
  - WebSearch
  - WebFetch

permissions:
  allowFileRead: true
  allowFileWrite: true
  allowFileEdit: true
  allowShell: true
  allowWebSearch: true
  allowWebFetch: true
  maxFileSize: 1048576  # 1MB
  maxShellTimeout: 300   # 5分钟

metadata:
  author: "JWCode Team"
  category: "coding"
  language: "java"
```

#### JSON 格式 (reviewer.json)

```json
{
  "name": "Code Reviewer",
  "description": "代码审查专家",
  "type": "reviewer",
  "version": "1.0.0",
  "systemPrompt": "你是一个代码审查专家，擅长发现代码中的问题和改进建议...",
  "extendsAgent": "coder",
  "overrides": ["systemPrompt", "permissions"],
  "model": {
    "name": "sonnet",
    "temperature": 0.3,
    "maxTokens": 4096
  },
  "tools": [
    "FileRead",
    "Grep",
    "Glob"
  ],
  "permissions": {
    "allowFileRead": true,
    "allowFileWrite": false,
    "allowFileEdit": false,
    "allowShell": false,
    "allowWebSearch": true,
    "allowWebFetch": true
  }
}
```

### 配置继承

```yaml
# base.yaml - 基础配置
name: "Base Agent"
type: "general"
systemPrompt: "基础提示词..."
tools:
  - FileRead
  - WebSearch

---
# coder.yaml - 继承基础配置
name: "Coding Agent"
extendsAgent: "Base Agent"  # 继承自 Base Agent
tools:
  - FileWrite    # 新增工具
  - FileEdit     # 新增工具
  # FileRead 和 WebSearch 自动继承
```

### 使用 Agent Factory

```java
import com.jwcode.core.agent.config.AgentFactory;
import com.jwcode.core.agent.config.AgentConfig;
import com.jwcode.core.agent.config.ConfigurableAgent;

public class AgentFactoryExample {
    
    public void examples() throws Exception {
        ToolRegistry registry = ToolRegistry.createDefault();
        
        // 创建 Agent 工厂（自动加载配置目录下的所有配置）
        AgentFactory factory = new AgentFactory(registry);
        
        // 获取所有可用的 Agent
        List<String> agents = factory.getAvailableAgents();
        System.out.println("可用 Agent: " + agents);
        
        // 创建 Agent 实例
        ConfigurableAgent coder = factory.createAgent("Coding Agent");
        ConfigurableAgent reviewer = factory.createAgent("Code Reviewer");
        
        // 获取 Agent 配置
        AgentConfig config = factory.getConfig("Coding Agent");
        System.out.println("模型: " + config.getModel().getName());
        System.out.println("工具: " + config.getTools());
        
        // 手动重新加载配置
        factory.reload();
        
        // 创建新的 Agent 配置
        AgentConfig newConfig = factory.createNewAgent("MyAgent", "coder");
        
        // 修改配置并保存
        newConfig.setSystemPrompt("自定义系统提示...");
        newConfig.getTools().add("CustomTool");
        factory.saveConfig(newConfig, "my-agent.yaml");
        
        // 验证配置
        List<String> errors = factory.validateConfig(newConfig);
        if (errors.isEmpty()) {
            System.out.println("配置验证通过!");
        } else {
            System.out.println("配置错误: " + errors);
        }
        
        // 停止文件监听
        factory.stopWatching();
    }
}
```

### 配置目录结构

```
~/.jwcode/
├── config.json          # 全局配置
└── agents/              # Agent 配置目录
    ├── coder.yaml       # 编码 Agent
    ├── reviewer.yaml    # 审查 Agent
    ├── debugger.yaml    # 调试 Agent
    ├── architect.json   # 架构师 Agent
    └── custom/          # 自定义 Agent
        └── my-agent.yaml
```

---

## 5. 综合示例

### 场景：并行代码审查

```java
public class ParallelCodeReview {
    
    public static void main(String[] args) throws Exception {
        ToolRegistry registry = ToolRegistry.createDefault();
        AgentFactory factory = new AgentFactory(registry);
        
        // 获取父 Agent
        ConfigurableAgent mainAgent = factory.createAgent("Coding Agent");
        Session mainSession = new Session("main", System.getProperty("user.dir"));
        
        // 要审查的文件列表
        List<String> files = Arrays.asList(
            "UserService.java",
            "OrderRepository.java",
            "PaymentGateway.java"
        );
        
        // 为每个文件 Fork 一个审查 Agent
        List<CompletableFuture<String>> reviews = files.stream()
            .map(file -> {
                // Fork 子 Agent
                SubAgentFork.SubAgentResult subAgent = SubAgentFork
                    .from(mainAgent, mainSession, registry)
                    .withTask("审查文件: " + file)
                    .withAgentType("reviewer")
                    .withContext("targetFile", file)
                    .withContext("reviewFocus", "security,performance")
                    .withEngineConfig(EngineConfig.unlimited())  // 不限制迭代次数
                    .execute();
                
                // 执行审查
                return subAgent.run(
                    "请审查 " + file + " 文件，关注安全性和性能问题"
                );
            })
            .collect(Collectors.toList());
        
        // 等待所有审查完成并汇总
        CompletableFuture.allOf(reviews.toArray(new CompletableFuture[0]))
            .thenRun(() -> {
                System.out.println("=== 代码审查报告 ===");
                for (int i = 0; i < files.size(); i++) {
                    System.out.println("\n【" + files.get(i) + "】");
                    try {
                        System.out.println(reviews.get(i).get());
                    } catch (Exception e) {
                        System.out.println("审查失败: " + e.getMessage());
                    }
                }
            })
            .join();
    }
}
```

---

## 6. 最佳实践

### 子 Agent Fork

1. **合理设置超时**：复杂任务设置较长的超时时间
2. **传递必要上下文**：只传递必要的上下文数据，避免过度复制
3. **及时清理**：子 Agent 使用完毕后及时清理资源

### 引擎配置

1. **生产环境**：使用 `strict()` 或自定义限制
2. **开发环境**：使用 `development()` 便于调试
3. **复杂任务**：使用 `unlimited()` 或较大的限制值

### Web UI

1. **心跳机制**：定期发送 ping 保持连接
2. **断线重连**：实现自动重连机制
3. **消息去重**：处理可能的重复消息

### Agent 配置

1. **版本控制**：将 Agent 配置文件纳入版本控制
2. **继承设计**：使用继承减少重复配置
3. **权限最小化**：遵循最小权限原则

---

## 8. Plan/Act 模式结构化任务系统

> 新增于 2026-05-12

### 功能说明

在 Plan 模式或 Act 模式下，AI 确认执行计划后，JWCode 自动将 AI 的回复解析为**结构化任务列表**，包括：
- **执行模式**：自动识别串行（SEQUENTIAL）或并发（CONCURRENT）任务
- **阶段分组**：自动分类为调研探索、架构设计、代码实现、测试验证、代码审查、文档编写
- **依赖关系**：自动分析任务间的前置依赖
- **并发组**：同一并发组的任务通过线程池并行执行
- **前端可视化**：Web 前端实时展示结构化任务树、进度、状态

### 架构流程

```
用户输入 "给 parser 模块加 JSON 导出功能"
  ↓
Orchestrator 意图识别 (Plan Mode)
  ↓
AI 生成执行计划:
  Phase 1: 调研现有 parser 架构
  Phase 2: 设计 JSON 导出接口
  Phase 3: 实现核心逻辑 (并发: Coder+Test)
  Phase 4: 代码审查
  ↓
用户确认 → processConfirmedPlan()
  ↓
TaskAgent 解析 AI 回复
  ├── 识别 4 个 Phase
  ├── Phase 3 标记为 CONCURRENT (关键词: 并发)
  ├── Coder/Test 分配 parallelGroup "group-1"
  └── 生成 6 个 StructuredTask
  ↓
WebSocket 广播 → 前端"结构化"视图展示
  ↓
TaskExecutionAgent 执行
  ├── Phase 1: Explorer (串行)
  ├── Phase 2: Architect (串行)
  ├── Phase 3: Coder + Tester (并发 ← 线程池)
  └── Phase 4: Reviewer (串行)
```

### 前端使用

1. 在 Chat 面板输入任务描述
2. 切换到 Plan 模式（📋 Plan 按钮）
3. AI 生成计划后，点击 Plan 面板
4. 选择"**结构化**"视图标签
5. 查看：
   - **统计面板**：总任务数、并发组数、串行任务数、阶段数
   - **阶段分组**：每个 Phase 显示对应图标和标签
   - **执行模式徽章**：⚡并发 / ➡️串行
   - **并发组连线**：同一并发组的任务用琥珀色横线连接
   - **实时进度**：执行中的任务显示渐变色进度条和脉冲动画

### Java API 使用

```java
// 获取 EnhancedOrchestratorAgent 实例
EnhancedOrchestratorAgent orchestrator = new EnhancedOrchestratorAgent(
    llmService, toolRegistry, toolExecutor
);
orchestrator.setSessionId(sessionId);
orchestrator.setPlanTaskBroadcaster(PlanTaskBroadcaster.getInstance());

// 方式 1: AI 确认计划后调用
String aiPlanResponse = """
    Phase 1: 调研现有代码
    - 分析 parser 模块结构
    - 了解现有接口

    Phase 2: 实现新功能 (并发)
    - Coder: 实现 JSON 导出
    - Tester: 编写单元测试
    """;

String result = orchestrator.processConfirmedPlan(aiPlanResponse, "添加 JSON 导出");

// 方式 2: 获取当前结构化任务（供外部查询）
List<StructuredTask> tasks = orchestrator.getCurrentStructuredTasks();

// 方式 3: 直接使用 TaskAgent 解析
TaskAgent taskAgent = new TaskAgent();
List<StructuredTask> parsed = taskAgent.parsePlan(aiPlanResponse, "task-001");
```

### TaskAgent 解析规则

| 识别内容 | 关键词/规则 | 示例 |
|----------|------------|------|
| **并发模式** | `parallel` `concurrent` `simultaneously` `同时` `并行` `并发` | "以下任务并发执行" |
| **串行模式** | `sequential` `step by step` `依次` `串行` `顺序` | "按顺序执行以下步骤" |
| **依赖关系** | `depends on` `requires` `after step N` `需要先完成` | "Step 3 依赖 Step 1 的结果" |
| **阶段识别** | `Phase N` `阶段N` `### Exploration` 等 | "### Phase 2: Implementation" |
| **Agent 分配** | `assigned to: Coder` `使用 Tester` | "由 Coder 实现" |

### TaskExecutionAgent 执行策略

```
1. 按 Phase 顺序依次执行
2. 每个 Phase 内:
   ├── SEQUENTIAL: 拓扑排序 → 逐个调用 A2AFacade
   └── CONCURRENT:
         ├── 无依赖任务 → 线程池并行提交
         └── 有依赖任务 → 等待依赖完成后串行执行
3. 每个任务执行:
   ├── plan_task_start 广播
   ├── A2AFacade.submitTaskSync() → 对应子Agent
   └── plan_task_result 广播 (completed/failed)
4. 上游任务结果通过 SharedContext 传递给下游
```

### 前端组件

| 组件 | 文件 | 说明 |
|------|------|------|
| `StructuredTaskView` | `components/Plan/StructuredTaskView.tsx` | 递归树形渲染，支持阶段标签、模式徽章、并发组连线 |
| `StructuredTaskSummary` | `components/Plan/StructuredTaskView.tsx` | 四格统计面板 |
| `ModeBadge` | `components/Plan/StructuredTaskView.tsx` | ⚡并发 / ➡️串行 徽章 |
| `PhaseTag` | `components/Plan/StructuredTaskView.tsx` | 阶段图标+标签 |
| `ProgressBar` | `components/Plan/StructuredTaskView.tsx` | 渐变色进度条 |
| `ParallelGroupIndicator` | `components/Plan/StructuredTaskView.tsx` | 并发组琥珀色连接线 |

### Store API

```typescript
// 设置结构化任务（WebSocket 接收后调用）
planStore.setStructuredTasks(sessionId, structuredTasks);

// 更新单个结构化任务
planStore.updateStructuredTask(sessionId, taskId, {
  status: 'running',
  progress: 50,
});

// 获取结构化任务
const tasks = planStore.getStructuredTasks(sessionId);
```

---

## 7. 故障排除

### 常见问题

**Q: 子 Agent 无法访问父 Agent 的历史消息？**
A: 确保使用 `SessionFork` 正确 Fork 会话。

**Q: QueryEngine 提前终止？**
A: 检查 `EngineConfig` 的迭代限制和时间限制。

**Q: WebSocket 连接失败？**
A: 检查端口是否被占用，防火墙设置是否正确。

**Q: Agent 配置未生效？**
A: 检查配置文件路径和格式，查看日志输出。

---

*文档版本: 1.1.0*
*最后更新: 2026-05-12*
