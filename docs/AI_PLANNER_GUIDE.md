# AI 驱动任务规划系统使用指南

## 概述

JWCode 现已实现完全 AI 驱动的任务规划系统，追平并超越 Kimi Code 的任务分解能力。

## 核心特性

### ✅ 已实现功能

| 特性 | 说明 | 状态 |
|------|------|------|
| **AI 深度分析** | LLM 分析任务意图、复杂度、风险 | ✅ |
| **动态分解** | 完全 AI 驱动，无预定义模板 | ✅ |
| **递归分解** | 复杂任务自动递归细分 | ✅ |
| **智能依赖分析** | AI 分析子任务依赖关系 | ✅ |
| **动态执行** | 执行中监控和自适应调整 | ✅ |
| **重规划** | 失败时自动重规划 | ✅ |
| **执行追踪** | 完整生命周期记录 | ✅ |
| **学习优化** | 历史数据优化未来规划 | ✅ |

### 🚀 超越 Kimi Code 的特性

1. **递归分解** - 子任务可无限递归直到原子级别
2. **智能合并** - 自动合并过于细粒度的任务
3. **学习记忆** - 记录历史执行数据优化未来规划
4. **多维度评估** - 复杂度、风险、耗时、资源消耗
5. **可视化追踪** - 生成 Mermaid 执行图

---

## 快速开始

### 1. 创建 AI 规划器

```java
import com.jwcode.core.planner.ai.AITaskPlanner;
import com.jwcode.core.service.ApiClient;
import com.jwcode.core.tool.ToolRegistry;

// 创建 AI 规划器
ApiClient apiClient = new ApiClient();
ToolRegistry toolRegistry = ToolRegistry.createDefault();
AITaskPlanner planner = new AITaskPlanner(apiClient, toolRegistry);
```

### 2. 分析和分解任务

```java
import com.jwcode.core.planner.ai.TaskAnalysis;
import com.jwcode.core.planner.ai.AITaskPlanner.PlanningResult;

// 方式 1：仅分析
TaskAnalysis analysis = planner.analyze("重构用户认证模块").join();
System.out.println(analysis.formatReport());

// 方式 2：分析和分解
PlanningResult result = planner.plan("实现订单管理功能").join();
System.out.println(result.formatReport());
```

### 3. 完整流程（分析 -> 分解 -> 执行）

```java
import com.jwcode.core.agent.Agent;
import com.jwcode.core.session.Session;
import com.jwcode.core.planner.ai.AITaskPlanner.Result;

Agent parentAgent = ...;
Session parentSession = ...;

// 完整流程
Result result = planner.planAndExecute(
    "重构所有 API 为异步模式",
    Map.of("priority", "high"),
    parentAgent,
    parentSession
).join();

System.out.println(result.formatReport());
```

---

## 高级用法

### 递归分解

对复杂任务自动递归分解：

```java
// 复杂度超过 7 的任务自动递归分解
PlanningResult result = planner.planRecursively("实现电商系统", 7).join();

// 查看分解后的步骤
result.getPlan().getSteps().forEach(step -> {
    System.out.println(step.getStepNumber() + ". " + step.getAction());
});
```

### 执行控制

```java
// 开始执行
CompletableFuture<ExecutionResult> future = planner.execute(plan, parentAgent, parentSession);

// 获取执行控制接口
ExecutionControl control = planner.getExecutionControl(executionId);

// 暂停执行
control.pause();

// 恢复执行
control.resume();

// 取消执行
control.cancel();

// 获取状态
ExecutionStatus status = control.getStatus();
```

### 执行追踪

```java
// 获取追踪报告
ExecutionTracer.TracerReport report = result.getExecutionResult().getTracerReport();

// 生成 Mermaid 流程图
String mermaid = executionTracer.generateMermaidDiagram(executionId);
System.out.println(mermaid);

// 生成时间线
String timeline = executionTracer.generateTimeline(executionId);
System.out.println(timeline);
```

### 学习记忆

```java
// 获取学习统计
AILearningMemory.MemoryStats stats = planner.getLearningStats();
System.out.println("已学习 " + stats.getTotalPatterns() + " 个任务模式");

// 清除学习记忆
planner.clearLearningMemory();
```

---

## 任务分析结果

### 意图分析

```java
TaskAnalysis.IntentAnalysis intent = analysis.getIntent();
System.out.println("任务类型: " + intent.getType());
System.out.println("置信度: " + intent.getConfidence());
System.out.println("目标文件: " + intent.getTargetFiles());
System.out.println("相关技术: " + intent.getTechnologies());
```

### 复杂度评估

```java
TaskAnalysis.ComplexityAnalysis complexity = analysis.getComplexity();
System.out.println("总体复杂度: " + complexity.getOverallScore() + "/10");
System.out.println("等级: " + complexity.getLevel());
System.out.println("技术复杂度: " + complexity.getTechnicalComplexity());
System.out.println("代码量: " + complexity.getCodeVolume());
System.out.println("依赖复杂度: " + complexity.getDependencyComplexity());
```

### 风险评估

```java
TaskAnalysis.RiskAnalysis risk = analysis.getRisk();
System.out.println("风险等级: " + risk.getOverallLevel());

for (RiskAnalysis.RiskItem r : risk.getRisks()) {
    System.out.println("- " + r.getDescription() + " [" + r.getLevel() + "]");
}
```

### 资源预估

```java
TaskAnalysis.Estimation estimation = analysis.getEstimation();
System.out.println("预估时间: " + estimation.getFormattedTime());
System.out.println("预估子任务: " + estimation.getEstimatedSubTasks());
System.out.println("Token 消耗: ~" + 
    (estimation.getEstimatedInputTokens() + estimation.getEstimatedOutputTokens()));
```

### 执行策略

```java
TaskAnalysis.ExecutionStrategy strategy = analysis.getStrategy();
System.out.println("推荐模式: " + strategy.getRecommendedMode());
System.out.println("并行度: " + strategy.getRecommendedParallelism());

if (strategy.isRequiresHumanConfirmation()) {
    System.out.println("⚠️ 需要人工确认");
    for (String point : strategy.getConfirmationPoints()) {
        System.out.println("  - " + point);
    }
}
```

---

## 依赖分析

```java
SmartDependencyAnalyzer analyzer = new SmartDependencyAnalyzer();
SmartDependencyAnalyzer.DependencyAnalysis analysis = analyzer.analyze(steps);

// 关键路径
System.out.println("关键路径: " + analysis.getCriticalPath());

// 并行分组
for (int i = 0; i < analysis.getParallelGroups().size(); i++) {
    List<PlanStep> group = analysis.getParallelGroups().get(i);
    System.out.println("组 " + (i+1) + ": " + 
        group.stream().map(s -> String.valueOf(s.getStepNumber())).collect(Collectors.joining(", ")));
}

// 优化建议
for (OptimizationSuggestion suggestion : analysis.getSuggestions()) {
    System.out.println("[P" + suggestion.getPriority() + "] " + suggestion.getDescription());
}
```

---

## 与旧版 TaskPlanner 集成

```java
import com.jwcode.core.planner.TaskPlanner;

// 创建支持 AI 的 TaskPlanner
TaskPlanner taskPlanner = new TaskPlanner(agentRegistry, apiClient, toolRegistry);

// 使用 AI 模式（默认）
taskPlanner.setAiMode(true);
ExecutionPlan plan = taskPlanner.plan("重构代码", context);

// 或切换回规则模式
taskPlanner.setAiMode(false);
ExecutionPlan plan = taskPlanner.plan("重构代码", context);

// 递归规划
ExecutionPlan recursivePlan = taskPlanner.planRecursively("复杂任务", 7);

// 执行计划
taskPlanner.planAndExecute("任务描述", context, parentAgent, parentSession)
    .thenAccept(result -> {
        System.out.println(result.formatReport());
    });
```

---

## 配置建议

### API 配置

确保 `~/.jwcode/config.properties` 配置正确：

```properties
api.endpoint=https://api.minimaxi.com/v1/chat/completions
api.key.env=MINIMAX_API_KEY
api.timeout=60000
api.maxRetries=3
```

### 性能调优

```java
// 设置递归分解阈值（复杂度超过此值才递归）
planner.planRecursively("任务", 6);  // 默认 7

// 智能合并阈值
List<PlanStep> merged = planner.smartMerge(steps, 60000);  // 合并小于 1 分钟的任务
```

---

## 示例输出

### 任务分析报告

```
╔══════════════════════════════════════════════════════════╗
║              🤖 AI 任务分析报告                          ║
╚══════════════════════════════════════════════════════════╝

📋 意图分析
   类型: REFACTOR (置信度: 85%)
   描述: 重构用户认证模块，改进代码结构和性能
   目标文件: [UserService.java, AuthController.java]
   相关技术: [java, spring-boot, jwt]

📊 复杂度评估: 7/10 (HIGH)
   技术复杂度: 7/10
   代码量: 6/10
   依赖复杂度: 8/10
   集成复杂度: 7/10
   测试复杂度: 7/10
   关键因素: [涉及多个模块, 需要保证向后兼容]

⚠️ 风险评估: MEDIUM
   • 可能影响现有登录功能 [MEDIUM]
   • 数据库迁移风险 [HIGH]

⏱️ 预估资源
   时间: 15分钟 (置信度: 70%)
   子任务: 6 (4-8)
   Token: ~8000

🚀 执行策略
   推荐模式: ADAPTIVE
   并行度: 3
```

### 执行报告

```
╔══════════════════════════════════════════════════════════╗
║           🤖 AI 任务规划执行报告                          ║
╚══════════════════════════════════════════════════════════╝

✅ 执行成功

📊 复杂度评估: 7/10 (HIGH)
...

🔗 依赖分析结果报告
...

🎯 动态执行结果报告
计划ID: plan_1699123456789
状态: ✅ 成功
进度: 6/6
失败: 0
耗时: 892341ms
```

---

## 故障排除

### AI 分析失败

如果 AI 分析失败，系统会自动回退到规则模式：

```
[TaskPlanner] AI 规划失败，回退到规则模式: Connection timeout
```

### 循环依赖

系统会自动检测并打破循环依赖：

```
[SmartDependencyAnalyzer] 检测到 1 个循环依赖
[SmartDependencyAnalyzer] 打破循环依赖: [task-2, task-3, task-4]
```

### 重规划触发

当失败率超过阈值时会自动重规划：

```
[DynamicExecutionEngine] 触发重规划
[DynamicExecutionEngine] 重规划策略: SUBDIVIDE
[DynamicExecutionEngine] 使用新计划继续执行
```

---

## 与 Orchestrator 的协作（Phase 5 分层架构）

从 v2.0.0 开始，JWCode 强制执行**主从分层架构**：

- **OrchestratorAgent**（主Agent）：唯一入口，只做任务分析、拆解、调度和整合
- **AITaskPlanner**：作为 Orchestrator 的"大脑"，提供 AI 驱动的分析和拆解能力

### 协作流程

```
用户输入
   ↓
OrchestratorAgent
   ├── 调用 planner.analyze()    → 获取意图分析 + 复杂度评估
   ├── 调用 planner.plan()       → 获取 ExecutionPlan（子任务列表）
   ├── 将 PlanStep 映射为 SubAgentTask
   │     Coder/Architect/Test/Reviewer/Doc/Explore/Debug
   ├── 调用 AgentTool.execute()  → 并行/串行执行子Agent
   └── 验收结果 → 整合输出
```

### 关键约束

| 层级 | 能否直接调用工具 | 能否创建子Agent |
|------|------------------|----------------|
| Orchestrator | 仅限 AgentTool / SmartAnalyzeTool / AskUserQuestionTool | ✅ 通过 AgentTool |
| Worker（子Agent） | 按角色白名单（Coder可读写，Reviewer只读等） | ❌ 禁止递归 |

### Agent 选型映射

AITaskPlanner 生成的 `PlanStep.agentType` 自动映射到对应子Agent：

| PlanStep 类型 | 子Agent | 说明 |
|---------------|---------|------|
| `coder` | CoderAgent | 代码实现、重构 |
| `debug` | DebugAgent | 错误排查、修复验证 |
| `reviewer` | ReviewerAgent | 代码审查（只读） |
| `test` | TestAgent | 测试设计、执行 |
| `analyzer` / `explore` | ExploreAgent | 代码库调研（只读） |
| `architect` | ArchitectAgent | 架构设计、接口定义 |
| `doc` | DocAgent | 文档编写 |
| `default` | DefaultAgent | 通用兜底 |

### 使用示例（Orchestrator 视角）

```java
// Orchestrator 内部的工作流
public class OrchestratorAgent implements Agent {

    public String handleUserRequest(String request, Session session) {
        // 1. AI 分析
        TaskAnalysis analysis = planner.analyze(request).join();

        // 2. 生成执行计划
        ExecutionPlan plan = planner.plan(request, context).join();

        // 3. 将 PlanStep 转换为 SubAgentTask
        List<SubAgentTask> subTasks = plan.getSteps().stream()
            .map(step -> new SubAgentTask(
                step.getAction(),
                step.getDescription(),
                mapAgentType(step.getAgentType())  // coder/debug/test/...
            ))
            .toList();

        // 4. 通过 AgentTool 并行执行
        Map<String, Object> executeInput = Map.of(
            "action", "execute",
            "tasks", subTasks,
            "parallel", true
        );
        ToolResult<Map<String, Object>> result = agentTool.call(executeInput, context, progress -> {});

        // 5. 整合输出
        return integrateResults(result);
    }
}
```

---

## 总结

JWCode 的 AI 驱动任务规划系统提供了：

1. **智能化** - 完全 AI 驱动，无预定义模板
2. **自适应性** - 执行中监控和动态调整
3. **学习能力** - 历史数据优化未来规划
4. **可观测性** - 完整追踪和可视化
5. **可靠性** - 失败时自动重规划

开始使用：

```java
AITaskPlanner planner = new AITaskPlanner(apiClient, toolRegistry);
Result result = planner.planAndExecute("你的任务", context, agent, session).join();
System.out.println(result.formatReport());
```
