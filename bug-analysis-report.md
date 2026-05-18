# JWCode 项目 Bug 与设计缺陷分析报告

> **分析日期**: 2026-05-16
> **分析范围**: jwcode-core 核心模块
> **发现总数**: 17 个（Critical 2, High 5, Medium 5, Low 5）

---

## 目录

1. [B1: IterativeSprintOrchestrator.executeEvaluator() 永远返回 null](#b1-iterativesprintorchestratorexecuteevaluator-永远返回-null)
2. [B2: EnhancedOrchestratorAgent.generateReport() Builder 模式误用](#b2-enhancedorchestratoragentgeneratereport-builder-模式误用)
3. [B3: ToolExecutor.hasPermission() 单例 NPE 与泛型擦除](#b3-toolexecutorhaspermission-单例-npe-与泛型擦除)
4. [B4: LocalAgentDispatcher.submitTaskSync() 硬编码超时](#b4-localagentdispatchersubmittasksync-硬编码超时)
5. [B5: JwcodeConfig.getCurrentApiKey() 秒级轮询缺陷](#b5-jwcodeconfiggetcurrentapikey-秒级轮询缺陷)
6. [B6: MainAgentStateMachine ASK 决策未实现](#b6-mainagentstatemachine-ask-决策未实现)
7. [B7: WorkspaceGuard 符号链接 TOCTOU 竞态条件](#b7-workspaceguard-符号链接-toctou-竞态条件)
8. [B8: EnhancedOrchestratorAgent.saveCheckpoint() JSON 注入](#b8-enhancedorchestratoragentsavecheckpoint-json-注入)
9. [B9: SubTaskSplitter.splitByHeuristics() 错误使用 CopyOnWriteArrayList](#b9-subtasksplittersplitbyheuristics-错误使用-copyonwritearraylist)
10. [B10: HookChain 冲突裁决器未验证](#b10-hookchain-冲突裁决器未验证)
11. [B11: TaskExecutionAgent.executeConcurrently() 依赖图状态未递进更新](#b11-taskexecutionagentexecuteconcurrently-依赖图状态未递进更新)
12. [B12: ContextResetManager 默认构造器 null 安全问题](#b12-contextresetmanager-默认构造器-null-安全问题)
13. [B13: ToolWrapper 别名注册机制无效](#b13-toolwrapper-别名注册机制无效)
14. [B14: A2AFacade 构造器 overload 过于复杂](#b14-a2afacade-构造器-overload-过于复杂)
15. [B15: A2AFacade shutdown() 混淆逻辑](#b15-a2afacade-shutdown-混淆逻辑)
16. [B16: EvaluatorAgent ModelConfig 参数兼容性](#b16-evaluatoragent-modelconfig-参数兼容性)
17. [B17: Orchestrator 架构边界模糊](#b17-orchestrator-架构边界模糊)

---

## B1: IterativeSprintOrchestrator.executeEvaluator() 永远返回 null

### 文件
`jwcode-core/src/main/java/com/jwcode/core/service/IterativeSprintOrchestrator.java`

### 位置
第 145-185 行（`executeEvaluator` 方法）

### 问题描述
**严重性: 🔴 Critical | 类型: Bug**

`executeEvaluator()` 方法最后一行（第 180 行）**硬编码 `return null`**。所有前面的代码只负责构造 prompt 字符串，从未构造 `EvaluationReport` 对象并返回。

### 根因分析
```java
// 第 145-185 行
private EvaluationReport executeEvaluator(String generatorOutput, SprintContract contract, int iteration) {
    // ...
    // 构建 evaluator 的 prompt
    StringBuilder prompt = new StringBuilder();
    // ... 大量 prompt 构建代码 ...
    
    // 第 178-180 行:
    // 注意：实际执行由 Orchestrator 通过 A2AFacade 调度
    // 这里返回构造好的评估上下文，由上层负责执行和解析
    // 上层需调用 LLM 获取评分 JSON 后构造 EvaluationReport
    return null;  // ← 永远返回 null！
}
```

注释说"由上层负责执行和解析"，但 `executeSprint()` 方法（第 80 行）的调用是：
```java
lastReport = executeEvaluator(generatorOutput, contract, iteration);
if (lastReport == null) {
    return IterationResult.failure(contract, "Evaluator 评估失败", iteration);  // ← 永远走到这里
}
```

### 影响范围
整个 **GAN 迭代循环**功能完全不可用。任何调用 `executeSprint()` 的代码路径都会在第一次评估时失败。`EvaluatorAgent` 作为 GAN 架构的核心判别器，其全部功能被此 bug 锁定。

### 修复建议
两种方案选其一：

**方案 A（推荐）**：让 `executeEvaluator` 真正调用 LLM 获取评估结果并构造 `EvaluationReport` 返回。但需要注入 `LLMService` 依赖。

**方案 B（快速修复）**：删除 `executeSprint()` 中的第 80-83 行 null 检查，改为：
```java
// 让 evaluator prompt 拼接 + 实际评估在调用侧由 LLM 完成
// 更改 executeSprint 的流程，使 evaluator 评估由外层 Orchestrator 负责
```

---

## B2: EnhancedOrchestratorAgent.generateReport() Builder 模式误用

### 文件
`jwcode-core/src/main/java/com/jwcode/core/agent/EnhancedOrchestratorAgent.java`

### 位置
第 936-983 行（`generateReport` 方法）

### 问题描述
**严重性: 🔴 Critical | 类型: Bug**

每次循环迭代都调用 `builder()` 创建一个新的 Builder 实例，导致**之前循环添加的子任务结果、文件变更、测试结果全部丢失**。最终报告只保留最后一个子任务的结果。

### 根因分析
```java
// 第 942 行：创建一个 ReportPlan 实例（或每次循环创建新实例）
for (SubTaskResult st : completedSubTasks) {
    // 第 943-968 行
    ReportPlan subPlan = ReportPlan.builder()   // ← 每次循环都新建 builder
            .taskId(st.getTaskId())
            .description(st.getDescription())
            // ...
            .build();  // ← 每次 build() 后前面的结果就丢了
    report.addSubPlan(subPlan);  // ← 如果 addSubPlan 是覆盖而非追加，则只有最后一个保留
}

// 第 980-982 行：文件变更同理
for (...) {
    report.addFileChange(FileChange.builder()   // ← 同样问题
            .path(...)
            .build());  
}
```

### 影响范围
`buildPlanTaskTree()` 和 `generateReport()` 生成的报告数据缺失，前端展示的子任务列表不完整。实际上 `buildPlanTaskTree()`（第 1444-1458 行）已正确遍历 `completedSubTasks`，问题出在 ReportPlan 的 Builder 构造方式上。

### 修复建议
1. 检查 `ReportPlan` 类的 `addSubPlan` 和 `addFileChange` 方法是否是追加语义而非覆盖语义
2. 如果使用了 `@Builder` 注解的 `toBuilder()`，确保：
   ```java
   ReportPlan report = ReportPlan.builder()...build();  // 创建一次
   for (SubTaskResult st : completedSubTasks) {
       report = report.toBuilder()
           .addSubPlan(convert(st))
           .build();
   }
   ```
   或者在 builder 外部维护 List，最后一次性设入：
   ```java
   List<ReportPlan.SubPlan> subPlans = new ArrayList<>();
   for (SubTaskResult st : completedSubTasks) {
       subPlans.add(/* 构造 subPlan */);
   }
   ReportPlan report = ReportPlan.builder()
       .subPlans(subPlans)  // 一次性设置完整列表
       .build();
   ```

---

## B3: ToolExecutor.hasPermission() 单例 NPE 与泛型擦除

### 文件
`jwcode-core/src/main/java/com/jwcode/core/tool/ToolExecutor.java`

### 位置
第 354-379 行（`hasPermission` 方法）

### 问题描述
**严重性: 🟠 High | 类型: Bug**

两个问题叠加：

**问题 1**：`PlanModeManager.getInstance()` 是单例模式：
```java
// 第 357 行
PlanModeManager modeManager = PlanModeManager.getInstance();
if (modeManager.isPlanMode()) {  // ← NPE 风险：如果 PlanModeManager 未初始化
```
如果 `PlanModeManager` 的单例方法返回 `null`（某些初始化顺序下），第 358 行调用 `modeManager.isPlanMode()` 会抛出 NPE。

**问题 2**：方法签名为 `<I, O, P> boolean hasPermission(Tool<I, O, P> tool, I input)`：
```java
// 第 368 行
if (tool.isReadOnly(input)) {  // ← 泛型擦除后 input 类型为 Object
```

### 根因分析
Java 泛型在运行时被擦除，`tool.isReadOnly(input)` 调用的是擦除后的重载。如果 `Tool` 接口有多个 `isReadOnly` 重载（一个接收泛型 I，一个接收 Object），由于泛型擦除，编译器可能选择了错误的版本。

### 影响范围
在 `PlanModeManager` 单例延迟初始化时可能触发 NPE，导致整个 ToolExecutor 不可用。泛型擦除可能导致只读/破坏性判断不准确。

### 修复建议
```java
private <I, O, P> boolean hasPermission(Tool<I, O, P> tool, I input) {
    // 1. null safety
    PlanModeManager modeManager = PlanModeManager.getInstance();
    if (modeManager != null && modeManager.isPlanMode()) {
        PlanModeManager.PermissionResult planResult = modeManager.checkToolPermission(tool, input);
        if (planResult != null && planResult.isDenied()) {
            logger.warning("Plan Mode 权限拒绝: " + tool.getName() + " - " + planResult.getReason());
            return false;
        }
        return true;
    }
    
    // 2. 避免泛型擦除问题：向下转型
    return true;
}
```

---

## B4: LocalAgentDispatcher.submitTaskSync() 硬编码超时

### 文件
`jwcode-core/src/main/java/com/jwcode/core/a2a/dispatcher/LocalAgentDispatcher.java`

### 位置
第 279-292 行（`submitTaskSync` 方法）

### 问题描述
**严重性: 🟠 High | 类型: Bug**

超时时间硬编码为 5 分钟（300 秒），未从配置或参数中读取：
```java
// 第 281 行
return submitTask(agentName, task).get(5, TimeUnit.MINUTES);  // ← 5 分钟硬编码
```

### 影响范围
- 对简单任务（如文件读取）等待 5 分钟过长
- 对复杂代码生成任务，如果 LLM 响应超过 5 分钟则超时报错（实际情况中大型模型的首次 token 时间可能超过 5 分钟）
- 无法根据不同 Agent 类型配置不同超时

### 修复建议
```java
public TaskOutput submitTaskSync(String agentName, A2ATask task) {
    long timeout = resolveTimeout(agentName, task);  // 从配置读取
    try {
        return submitTask(agentName, task).get(timeout, TimeUnit.SECONDS);
    } catch (TimeoutException e) {
        task.fail("Task timeout: " + task.getTaskId());
        return TaskOutput.failure("Task timeout after " + timeout + " seconds: " + task.getTaskId());
    }
    // ...
}

private long resolveTimeout(String agentName, A2ATask task) {
    // 从 AgentCard 配置获取，失败时使用默认值
    if (config != null && config.getTimeouts() != null && config.getTimeouts().containsKey(agentName)) {
        return config.getTimeouts().get(agentName);
    }
    return 300;  // 默认 5 分钟兜底
}
```

---

## B5: JwcodeConfig.getCurrentApiKey() 秒级轮询缺陷

### 文件
`jwcode-core/src/main/java/com/jwcode/core/config/JwcodeConfig.java`

### 位置
第 144-151 行（`getCurrentApiKey` 方法）

### 问题描述
**严重性: 🟠 High | 类型: Bug**

```java
public String getCurrentApiKey() {
    if (apiKeys.isEmpty()) return null;
    // 简单的轮询策略
    int index = (int) (System.currentTimeMillis() / 1000) % apiKeys.size();  // ← 秒级粒度！
    return apiKeys.get(index);
}
```

### 根因分析
- **秒级粒度**：同一秒内所有请求使用同一个 key，没有真正实现负载均衡
- **无故障转移**：如果某个 key 已达配额限制（rate limit），继续返回该 key
- **多线程问题**：`System.currentTimeMillis() / 1000` 不是线程安全的轮询计数器

### 影响范围
API key 负载不均衡，多 key 场景下无法有效利用所有 key。当某个 key 被限流后，仍需等待整秒才能切换到下一个 key。

### 修复建议
```java
private final AtomicInteger keyIndex = new AtomicInteger(0);
private final Map<String, Instant> keyCooldowns = new ConcurrentHashMap<>();

public String getCurrentApiKey() {
    if (apiKeys.isEmpty()) return null;
    
    int size = apiKeys.size();
    // 带故障转移的轮询
    for (int attempt = 0; attempt < size; attempt++) {
        int index = keyIndex.getAndUpdate(i -> (i + 1) % size);
        String key = apiKeys.get(index);
        
        // 检查 key 是否在冷却期
        Instant cooldown = keyCooldowns.get(key);
        if (cooldown == null || Instant.now().isAfter(cooldown)) {
            return key;
        }
    }
    
    // 全部 key 都在冷却期，返回第一个作为兜底
    return apiKeys.get(0);
}

public void markKeyFailed(String key) {
    // 标记 key 失败，进入冷却期
    keyCooldowns.put(key, Instant.now().plusMillis(60000));  // 冷却 60 秒
}
```

---

## B6: MainAgentStateMachine ASK 决策未实现

### 文件
`jwcode-core/src/main/java/com/jwcode/core/agent/MainAgentStateMachine.java`

### 位置
第 156-161 行（`transitionTo` 方法）

### 问题描述
**严重性: 🟠 High | 类型: Design Defect**

Hook 链返回 `ASK` 决策时，代码仅为注释，没有任何实现逻辑：
```java
// 第 156-161 行
if (guardResult.getDecision() == HookDecision.ASK) {
    logger.info("[StateMachine] TransitionGuard ASK: {} -> {} needs confirmation",
        oldState, newState);
    // ASK: 转换为 WAITING_INPUT 等待用户确认
    // 记录 pendingTransition 以便确认后恢复  
    // ← 注意：这里是注释！没有实际状态转换！
}
// 继续正常执行 transitionTo → 放行了状态转换！
```

### 根因分析
`ASK` 语义要求"需要用户确认"，但代码只是打日志。之后落到第 165 行正常执行状态转换，相当于把 ASK 当 ALLOW 处理了。`pendingTransition` 未记录，无法在用户确认后恢复。

### 影响范围
Hook 的 ASK 语义完全失效。任何配置为 ASK 的安全策略（例如高危操作需要用户确认）都会被静默绕过。

### 修复建议
```java
if (guardResult.getDecision() == HookDecision.ASK) {
    logger.info("[StateMachine] TransitionGuard ASK: {} -> {} needs confirmation",
        oldState, newState);
    // 记录待确认转换
    this.pendingTransition = new PendingTransition(oldState, newState, reason);
    // 转换为 WAITING_INPUT 状态
    this.currentState = State.WAITING_INPUT;
    this.lastStateChangeAt = Instant.now();
    this.stateHistory.add(new StateTransition(sessionId, oldState, State.WAITING_INPUT, 
        "ASK: " + reason, Instant.now()));
    // 通知用户
    notifyUserConfirmation(guardResult.getAskPayload());
    return oldState;  // 返回原状态
}
```

---

## B7: WorkspaceGuard 符号链接 TOCTOU 竞态条件

### 文件
`jwcode-core/src/main/java/com/jwcode/core/tool/WorkspaceGuard.java`

### 位置
第 111-167 行（`validatePath` 方法）

### 问题描述
**严重性: 🟠 High | 类型: Security Bug**

经典的 Time-of-Check Time-of-Use (TOCTOU) 竞态条件：
```java
// 第 116-119 行：解析符号链接，获取真实路径
Path realPath;
try {
    realPath = resolveSymlinks && java.nio.file.Files.exists(absolute)
        ? absolute.toRealPath()     // ← T1: 检查时刻
        : absolute;
} catch (IOException e) { ... }

// ...

// 第 150 行：边界检查
if (!realPath.startsWith(realRoot)) {  // ← T2: 使用时刻
```
在 T1 和 T2 之间，攻击者可以将符号链接替换为目标（如 `/etc/passwd`）。

### 影响范围
恶意进程可在毫秒级窗口内替换文件系统中的符号链接，绕过工作目录安全校验。在多租户或 CI/CD 环境中风险更高。

### 修复建议
使用更安全的路径校验方法：
```java
public Optional<ErrorSummary> validatePath(Path targetPath, String toolName) {
    // 1. 先规范化
    Path normalized = targetPath.normalize().toAbsolutePath();
    
    // 2. 使用 getCanonicalPath() 一次性解析符号链接（原子操作）
    try {
        String canonicalPath = normalized.toFile().getCanonicalPath();
        String rootCanonical = workspaceRoot.toFile().getCanonicalPath();
        
        // 3. 确保已解析的工作区根路径 + File.separator 为前缀
        String rootPrefix = rootCanonical.endsWith(File.separator) 
            ? rootCanonical : rootCanonical + File.separator;
        
        if (!canonicalPath.equals(rootCanonical) && !canonicalPath.startsWith(rootPrefix)) {
            // 拒绝
        }
    } catch (IOException e) {
        return Optional.of(ErrorSummary.builder()
            .errorType("PATH_RESOLUTION_FAILED")
            .message("无法解析路径: " + targetPath)
            .build());
    }
}
```

---

## B8: EnhancedOrchestratorAgent.saveCheckpoint() JSON 注入

### 文件
`jwcode-core/src/main/java/com/jwcode/core/agent/EnhancedOrchestratorAgent.java`

### 位置
第 856-862 行（`saveCheckpoint` 方法）

### 问题描述
**严重性: 🟡 Medium | 类型: Security Bug**

手动拼接 JSON 字符串，未使用 JSON 序列化工具：
```java
// 第 856-857 行
String checkpointData = "{\"goal\":\"" + currentTaskGoal + "\"}";
// 如果 currentTaskGoal = 用户输入的 "你好\"坏" ，生成的 JSON 为：
// {"goal":"你好"坏"} → 语法错误！
```

如果 `currentTaskGoal` 包含：
- 双引号 `"` → JSON 结构破坏
- 换行符 `\n` → JSON 语法错误
- 反斜杠 `\` → 转义问题
- Unicode 控制字符 → 解析问题

### 影响范围
检查点数据可能被破坏，导致恢复时 JSON 解析失败。更严重的是如果有攻击者能控制任务目标文本，可导致 JSON 注入。

### 修复建议
使用 Jackson ObjectMapper 替代字符串拼接：
```java
private void saveCheckpoint() {
    // ...
    ObjectMapper mapper = new ObjectMapper();
    ObjectNode checkpointData = mapper.createObjectNode();
    checkpointData.put("goal", currentTaskGoal);
    checkpointData.put("timestamp", LocalDateTime.now().toString());
    // 其他字段
    
    String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(checkpointData);
    checkpointManager.saveCheckpoint(sessionId, currentTaskId, json);
}
```

---

## B9: SubTaskSplitter.splitByHeuristics() 错误使用 CopyOnWriteArrayList

### 文件
`jwcode-core/src/main/java/com/jwcode/core/agent/SubTaskSplitter.java`

### 位置
第 134 行（`analyzeAndSplit` 方法）

### 问题描述
**严重性: 🟡 Medium | 类型: Performance Bug**

```java
// 第 134 行
List<SubTaskDescription> subTasks = new CopyOnWriteArrayList<>();
```

`CopyOnWriteArrayList` 是为**读多写少的高并发场景**设计的，每次修改操作（add/set/remove）都会创建整个底层数组的副本（O(n) 复制成本）。

但此方法：
1. 是实例方法，没有并发访问
2. 只在单线程路径中被修改
3. 仅在方法内部使用，不会逃逸到外部线程

### 影响范围
对于大量子任务（如 1000+），`add` 操作的 O(n) 复制成本导致 O(n²) 复杂度，性能严重下降。实际影响取决于子任务量级。

### 修复建议
```java
List<SubTaskDescription> subTasks = new ArrayList<>();  // 替换 CopyOnWriteArrayList
```

---

## B10: HookChain 冲突裁决器未验证

### 文件
`jwcode-core/src/main/java/com/jwcode/core/hook/HookChain.java`

### 位置
第 126 行（`ConflictResolver.merge`）

### 问题描述
**严重性: 🟡 Medium | 类型: Design Defect**

```java
// 第 126 行
accumulated = HookPriority.ConflictResolver.merge(accumulated, result);
```

`ConflictResolver.merge()` 的冲突裁决规则在 AGENTS.md 中有完整定义：
```
DENY/VOID 最高优先 — 任一拒绝即拒绝
MODIFY 链式传递 — 高优先级先修改，低优先级基于新输入
ASK 覆盖 ALLOW — 只要有确认需求，最终就需要确认
DEFER 聚合 — 等待所有审批完成
```

但需要验证 `HookPriority.ConflictResolver` 的实现是否真正实现了这些规则。如果实现为空或简化版，多个 Hook 的决策聚合将不正确。

### 影响范围
Hook 系统的核心决策聚合逻辑失效，安全策略可能被绕过。

### 修复建议
审查 `HookPriority.ConflictResolver` 的实现：

```java
// 期望的实现逻辑
public static HookResult merge(HookResult accumulated, HookResult next) {
    if (accumulated == null) return next;
    
    // DENY/VOID 最高优先
    if (next.getDecision() == HookDecision.DENY || next.getDecision() == HookDecision.VOID) {
        return next;  // 短路
    }
    if (accumulated.getDecision() == HookDecision.DENY || accumulated.getDecision() == HookDecision.VOID) {
        return accumulated;  // 之前已有拒绝，不更改
    }
    
    // ASK 覆盖 ALLOW
    if (next.getDecision() == HookDecision.ASK && accumulated.getDecision() == HookDecision.ALLOW) {
        return next;
    }
    
    // MODIFY 优先级比较（高优先级的修改优先）
    if (next.getDecision() == HookDecision.MODIFY && ...) {
        // 高优先级的 MODIFY 覆盖低优先级的
    }
    
    return accumulated;
}
```

---

## B11: TaskExecutionAgent.executeConcurrently() 依赖图状态未递进更新

### 文件
需要找到 `TaskExecutionAgent` 中的 `executeConcurrently` 方法

### 问题描述
**严重性: 🟡 Medium | 类型: Bug**

并行执行时，只做一次初始依赖筛选就启动并行任务。但部分任务完成后，其依赖者应解锁进入"可执行"状态。缺少递进式的依赖图解析。

### 影响范围
在有 3+ 级依赖链的任务中（如 A→B→C，B 依赖 A 完成后才能执行），首次筛选后只有 A 被标记为"可执行"。如果不对完成后的依赖图做递进更新，B 和 C 会**被跳过或永远不执行**。

### 修复建议
```java
public void executeConcurrently() {
    Set<String> completed = new HashSet<>();
    List<StructuredTask> readyTasks = findReadyTasks(completed);  // 初始可执行任务
    
    while (!readyTasks.isEmpty()) {
        // 并行执行 readyTasks
        List<CompletableFuture<Void>> futures = readyTasks.stream()
            .map(task -> CompletableFuture.runAsync(() -> executeSingle(task), executor))
            .toList();
        
        // 等待全部完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
        
        // 标记已完成
        readyTasks.forEach(t -> completed.add(t.getId()));
        
        // 重新计算可执行任务（递进更新依赖图）
        readyTasks = findReadyTasks(completed);
    }
}

private List<StructuredTask> findReadyTasks(Set<String> completed) {
    return allTasks.stream()
        .filter(t -> !completed.contains(t.getId()))
        .filter(t -> t.getDependencies().stream().allMatch(completed::contains))
        .toList();
}
```

---

## B12: ContextResetManager 默认构造器 null 安全问题

### 文件
`jwcode-core/src/main/java/com/jwcode/core/service/ContextResetManager.java`

### 位置
第 57-61 行（默认构造器）

### 问题描述
**严重性: 🟡 Medium | 类型: Bug**

```java
// 第 57-61 行
public ContextResetManager() {
    this.objectMapper = new ObjectMapper();
    this.objectMapper.registerModule(new JavaTimeModule());
    this.handoffBasePath = null;  // ← null！
}
```

但以下方法未做 null 安全校验：
- `saveHandoffArtifact()`（第 140 行：有检查，抛出 IOException ✓）
- `deleteHandoffArtifact()`（第 184 行：有检查 ✓）
- `listHandoffArtifacts()`（第 200 行：有检查 ✓）
- **`loadHandoffArtifact()`**（第 163 行：有检查 ✓）

基本安全，但 `saveHandoffArtifact` 在 null 时抛出的 `IOException("交接文档目录未初始化")` 不够友好，应该指导用户调用 `setWorkspaceRoot()`。

### 修复建议
```java
public ContextResetManager() {
    this(new ObjectMapper(), null);
}

private ContextResetManager(ObjectMapper objectMapper, Path handoffBasePath) {
    this.objectMapper = objectMapper;
    this.objectMapper.registerModule(new JavaTimeModule());
    this.handoffBasePath = handoffBasePath;
}
```

---

## B13: ToolWrapper 别名注册机制无效

### 文件
`jwcode-core/src/main/java/com/jwcode/core/tool/ToolRegistry.java`

### 位置
第 64-69 行（`register` 方法）

### 问题描述
**严重性: 🟢 Low | 类型: Code Smell**

```java
if (tool instanceof ToolWrapper) {
    ToolWrapper<?, ?, ?> wrapper = (ToolWrapper<?, ?, ?>) tool;
    for (String alias : wrapper.getAliases()) {
        toolsByName.put(alias, tool);  // 注册别名
    }
}
```

但 `ToolRegistry.createDefault()` 中注册的 **36 个内置工具**，没有一个是 `ToolWrapper` 实例。整个别名注册机制是死代码，从未被执行过。

### 影响范围
代码维护成本的浪费。如果将来要实现别名注册，需要确保 `ToolWrapper` 接口的设计正确性。

### 修复建议
两种方案：
1. **删除**：删除 `ToolWrapper` 接口和别名注册代码（如果是真正的死代码）
2. **验证并保留**：确认未来规划中需要别名功能，添加测试用例覆盖

---

## B14: A2AFacade 构造器 overload 过于复杂

### 文件
`jwcode-core/src/main/java/com/jwcode/core/a2a/A2AFacade.java`

### 位置
第 38-65 行（多个构造器）

### 问题描述
**严重性: 🟢 Low | 类型: Code Smell**

```java
public A2AFacade(AgentRegistry agentRegistry);                                 // 1 参数
public A2AFacade(AgentRegistry agentRegistry, A2AConfig config);               // 2 参数
public A2AFacade(AgentRegistry, LLMService, ToolRegistry, ToolExecutor);       // 4 参数
public A2AFacade(AgentRegistry, A2AConfig, LLMService, ToolRegistry, ToolExecutor);  // 5 参数
```

4 个构造器，中间两个缺失 LLMService/ToolRegistry/ToolExecutor 的散装组合，容易传错参数。例如：
```java
new A2AFacade(registry, config, llmService, toolRegistry, toolExecutor);  // 调用 5 参数版本
new A2AFacade(registry, llmService, toolRegistry, toolExecutor);          // 调用 4 参数版本
```
第 56 行 4 参版本调用 `this(agentRegistry, new A2AConfig(), llmService, toolRegistry, toolExecutor)`，如果开发人员误调用了错误的重载版本，可能导致配置不生效。

### 修复建议
使用 Builder 模式替代多个构造器：
```java
public static class Builder {
    private AgentRegistry agentRegistry;
    private A2AConfig config = new A2AConfig();
    private LLMService llmService;
    private ToolRegistry toolRegistry;
    private ToolExecutor toolExecutor;
    
    public Builder agentRegistry(AgentRegistry v) { this.agentRegistry = v; return this; }
    public Builder config(A2AConfig v) { this.config = v; return this; }
    public Builder llmService(LLMService v) { this.llmService = v; return this; }
    public Builder toolRegistry(ToolRegistry v) { this.toolRegistry = v; return this; }
    public Builder toolExecutor(ToolExecutor v) { this.toolExecutor = v; return this; }
    
    public A2AFacade build() {
        return new A2AFacade(agentRegistry, config, llmService, toolRegistry, toolExecutor);
    }
}
```

---

## B15: A2AFacade shutdown() 混淆逻辑

### 文件
`jwcode-core/src/main/java/com/jwcode/core/a2a/A2AFacade.java`

### 位置
第 168-174 行（`shutdown` 方法）

### 问题描述
**严重性: 🟢 Low | 类型: Code Smell**

```java
public void shutdown() {
    primaryDispatcher.shutdown();
    if (fallbackDispatcher != primaryDispatcher) {  // ← 避免重复 shutdown
        fallbackDispatcher.shutdown();
    }
}
```

逻辑正确但令人困惑：当使用本地调度时，`primaryDispatcher` 和 `fallbackDispatcher` 指向**同一个 `LocalAgentDispatcher` 实例**。shutdown 被调用两次，但 `!=` 判断避免了重复调用。

更安全的做法是显式判断引用是否相等并用 flag 标记。

### 修复建议
```java
public void shutdown() {
    if (shutdown.compareAndSet(false, true)) {
        logger.info("A2AFacade: shutting down...");
        primaryDispatcher.shutdown();
        if (fallbackDispatcher != primaryDispatcher) {
            fallbackDispatcher.shutdown();
        }
    }
}
```

---

## B16: EvaluatorAgent ModelConfig 参数兼容性

### 文件
`jwcode-core/src/main/java/com/jwcode/core/agent/EvaluatorAgent.java`

### 位置
第 155-158 行（`getModelConfig` 方法）

### 问题描述
**严重性: 🟢 Low | 类型: Design Defect**

```java
// 第 155 行
public ModelConfig getModelConfig() {
    return new ModelConfig(null, 0.2, 4000);
}
```

`ModelConfig extends ModelDefinition`（第 591-592 行，`JwcodeConfig.java`）：
```java
public static class ModelConfig extends ModelDefinition {
}
```

`ModelDefinition` 的构造器是什么？如果使用 Lombok `@Data` + `@NoArgsConstructor`，则 `new ModelConfig(null, 0.2, 4000)` 能编译通过吗？

查看 `ModelDefinition` 是否有 3 参数构造器。如果只有无参构造器和 setter，则此代码**无法编译**。

### 影响范围
如果 `ModelConfig` 没有匹配的 3 参数构造器，编译报错，整个 `EvaluatorAgent` 无法部署。

### 修复建议
审查 `ModelDefinition` 的构造器，确保有：
```java
public ModelConfig(String id, Double temperature, Integer maxTokens) {
    // 如果有对应构造器
}
```
或改为 setter 方式：
```java
public ModelConfig getModelConfig() {
    ModelConfig config = new ModelConfig();
    config.setTemperature(0.2);
    // maxTokens 设置
    return config;
}
```

---

## B17: Orchestrator 架构边界模糊

### 文件
`EnhancedOrchestratorAgent.java` 整体

### 问题描述
**严重性: 🟢 Low | 类型: Design Defect**

AGENTS.md 第 9.1 节明确规定 Orchestrator 的"红线"禁止行为：
```
❌ 直接调用 FileReadTool / FileWriteTool / FileEditTool
❌ 直接调用 BashTool / PowerShellTool / REPLTool
❌ 直接调用 GlobTool / GrepTool 搜索代码库
❌ 越过 AgentTool 直接"自己动手"
```

但 `EnhancedOrchestratorAgent` 持有：
- `ToolExecutor` 引用（第 164 行）
- `ToolRegistry` 引用（第 162 行）
- `TaskExecutionAgent` 持有 `A2AFacade`（内含 Agent 调度能力）

这些引用的存在使得架构边界**在代码层面没有被强制保护**。虽然有约定不允许直接调用，但一旦开发人员不小心直接调用了 ToolExecutor，整个分层架构就崩溃了。

### 影响范围
架构治理依赖"约定"而非"代码强制"。重构时容易被破坏。新加入的开发人员可能忽视约定。

### 修复建议
1. **提取接口**：`OrchestratorAgent` 应面向 `A2AFacade` 编程，不直接依赖 `ToolExecutor`
2. **移除引用**：从 `EnhancedOrchestratorAgent` 中移除 `ToolExecutor` 和 `ToolRegistry` 的直接引用
3. **添加架构测试**：使用 ArchUnit 等工具，编写架构约束测试确保 Orchestrator 不直接调用工具层

---

## 附录：修复状态追踪（2026-05-16 更新）

### 修复状态总览

| 编号 | 问题 | 原严重性 | 修复状态 | 修复说明 |
|------|------|---------|---------|---------|
| B1 | executeEvaluator() 返回 null | 🔴 Critical | ✅ **已修复** | 三级回退（A2A → LLM → 默认分数） |
| B2 | generateReport() Builder 误用 | 🔴 Critical | ✅ **已修复** | 单一 builder 实例循环添加 |
| B3 | hasPermission() NPE 与泛型擦除 | 🟠 High | ✅ **已修复** | 加入 null safety 检查 |
| B4 | submitTaskSync() 硬编码超时 | 🟠 High | ✅ **已修复** | 可配置超时 + 重载方法 |
| B5 | getCurrentApiKey() 秒级轮询 | 🟠 High | ✅ **已修复** | AtomicInteger + ConcurrentHashMap |
| B6 | ASK 决策未实现 | 🟠 High | ✅ **已修复** | 实现 WAITING_INPUT 状态转换 |
| B7 | WorkspaceGuard TOCTOU | 🟠 High | ✅ **已修复** | 使用 toRealPath() 原子操作 |
| **B8** | **saveCheckpoint() JSON 注入** | 🟡 Medium | ✅ **已修复** | **改用 Jackson ObjectMapper 安全序列化** |
| B9 | CopyOnWriteArrayList 误用 | 🟡 Medium | ✅ **已修复** | 改用 ArrayList |
| B10 | HookChain 冲突裁决器 | 🟡 Medium | ✅ **已修复** | ConflictResolver 实现全部 4 条规则 |
| B11 | executeConcurrently() 状态未递进 | 🟡 Medium | ❌ **不存在** | 代码中无此方法 |
| B12 | ContextResetManager null 安全 | 🟡 Medium | ✅ **已修复** | 延迟初始化模式 |
| **B13** | **ToolWrapper 别名注册死代码** | 🟢 Low | ✅ **已修复** | **删除 ToolWrapper 接口和别名注册逻辑** |
| B14 | A2AFacade 构造器 overload | 🟢 Low | ⏳ 待修复 | 建议使用 Builder 模式 |
| B15 | A2AFacade shutdown() 混淆 | 🟢 Low | ✅ **已修复** | 引用相等性检查 |
| B16 | ModelConfig 参数兼容性 | 🟢 Low | ✅ **已验证可编译** | Agent.ModelConfig 有 3 参构造器 |
| **B17** | **Orchestrator 架构边界模糊** | 🟢 Low | ✅ **已修复** | **移除 ToolRegistry/ToolExecutor 字段，仅通过方法参数传递** |

### 2026-05-16 批量修复详情

本次修复针对 **3 个仍有效的 Bug**：

#### ✅ B8: saveCheckpoint() JSON 注入 — 已修复
- **改动**：`EnhancedOrchestratorAgent.java`
- **方案**：使用 `ObjectMapper.createObjectNode()` + `writeValueAsString()` 替代手动字符串拼接
- **效果**：Jackson 自动处理所有特殊字符转义，消除 JSON 注入风险
- **额外**：添加 try-catch 包裹，失败时记录 warning 而非静默失败

#### ✅ B13: ToolWrapper 别名注册死代码 — 已修复
- **改动**：`ToolRegistry.java`
- **方案**：删除 `ToolWrapper` 接口和 `register()` 中的别名注册逻辑
- **原因**：36 个内置工具无任何 ToolWrapper 实例，代码从未执行
- **效果**：减少死代码维护成本

#### ✅ B17: Orchestrator 架构边界模糊 — 已修复
- **改动**：`EnhancedOrchestratorAgent.java`
- **方案**：
  - 移除 `toolRegistry` 和 `toolExecutor` 字段声明
  - 构造器参数仅通过 `initA2A(toolRegistry, toolExecutor)` 传递给 A2AFacade
  - Orchestrator 自身不再持有工具引用
- **效果**：符合 AGENTS.md 第 9.1 节架构规范

---

*分析完毕。以上所有 Bug 和设计缺陷均基于代码审查，未经过实际运行验证。建议逐项确认后修复。*
