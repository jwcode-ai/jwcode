package com.jwcode.core.agent.parallel;

import com.jwcode.core.agent.AgentRegistry;
import com.jwcode.core.session.Session;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 并行 Agent 执行器测试
 */
public class ParallelAgentExecutorTest {
    
    private ParallelAgentExecutor executor;
    private AgentRegistry registry;
    private Session session;
    
    @BeforeEach
    void setUp() {
        registry = AgentRegistry.createDefault();
        executor = new ParallelAgentExecutor(registry, 4);
        session = new Session(UUID.randomUUID().toString(), System.getProperty("user.dir"));
    }
    
    @AfterEach
    void tearDown() {
        executor.shutdown();
    }
    
    @Test
    void testSingleTaskExecution() throws Exception {
        SubAgentTask task = SubAgentTask.builder()
            .taskId("task-1")
            .instruction("分析代码结构")
            .agentType("default")
            .build();
        
        CompletableFuture<SubAgentResult> future = executor.submit(task, session);
        SubAgentResult result = future.get(10, TimeUnit.SECONDS);
        
        assertTrue(result.isSuccess());
        assertEquals("task-1", result.getTaskId());
        assertNotNull(result.getOutput());
        System.out.println("单任务结果: " + result.getOutput());
    }
    
    @Test
    void testParallelExecution() throws Exception {
        // 创建 5 个独立任务（无依赖）
        List<SubAgentTask> tasks = List.of(
            SubAgentTask.builder().taskId("task-1").instruction("分析代码").agentType("default").build(),
            SubAgentTask.builder().taskId("task-2").instruction("查找 Bug").agentType("debug").build(),
            SubAgentTask.builder().taskId("task-3").instruction("优化性能").agentType("default").build(),
            SubAgentTask.builder().taskId("task-4").instruction("生成文档").agentType("default").build(),
            SubAgentTask.builder().taskId("task-5").instruction("编写测试").agentType("default").build()
        );
        
        long startTime = System.currentTimeMillis();
        
        ParallelExecutionContext context = executor.submitBatch(tasks, session);
        ParallelExecutionContext.BatchResult result = context.awaitCompletion(30, TimeUnit.SECONDS)
            .orElseThrow(() -> new AssertionError("执行超时"));
        
        long duration = System.currentTimeMillis() - startTime;
        
        // 验证结果
        assertEquals(5, result.getTotalTasks());
        assertEquals(5, result.getSuccessfulCount());
        assertEquals(0, result.getFailedCount());
        assertTrue(result.getSuccessRate() == 100.0);
        
        // 并行执行应该比串行快（5个任务每个100-300ms，串行需要500-1500ms）
        System.out.println("并行执行 5 个任务耗时: " + duration + "ms");
        System.out.println("成功率: " + result.getSuccessRate() + "%");
        System.out.println(result.formatReport());
        
        // 验证确实是并行执行（耗时应该小于 800ms）
        assertTrue(duration < 800, "并行执行应该很快，实际耗时: " + duration + "ms");
    }
    
    @Test
    void testDependencyExecution() throws Exception {
        // 创建有依赖的任务链：A -> B -> C，D 独立
        SubAgentTask taskA = SubAgentTask.builder()
            .taskId("step-a")
            .instruction("步骤A: 需求分析")
            .agentType("default")
            .build();
        
        SubAgentTask taskB = SubAgentTask.builder()
            .taskId("step-b")
            .instruction("步骤B: 设计方案")
            .agentType("default")
            .dependencies(List.of("step-a"))
            .build();
        
        SubAgentTask taskC = SubAgentTask.builder()
            .taskId("step-c")
            .instruction("步骤C: 编码实现")
            .agentType("coder")
            .dependencies(List.of("step-b"))
            .build();
        
        SubAgentTask taskD = SubAgentTask.builder()
            .taskId("step-d")
            .instruction("步骤D: 准备环境")
            .agentType("default")
            .build();
        
        List<SubAgentTask> tasks = List.of(taskA, taskB, taskC, taskD);
        
        ParallelExecutionContext context = executor.submitBatch(tasks, session);
        ParallelExecutionContext.BatchResult result = context.awaitCompletion(30, TimeUnit.SECONDS)
            .orElseThrow(() -> new AssertionError("执行超时"));
        
        assertEquals(4, result.getTotalTasks());
        assertEquals(4, result.getSuccessfulCount());
        
        System.out.println("依赖链执行完成:");
        System.out.println(result.formatReport());
    }
    
    @Test
    void testPriorityExecution() throws Exception {
        // 创建不同优先级的任务
        List<SubAgentTask> tasks = List.of(
            SubAgentTask.builder().taskId("low-1").instruction("低优先级任务1").priority(1).build(),
            SubAgentTask.builder().taskId("high-1").instruction("高优先级任务1").priority(10).build(),
            SubAgentTask.builder().taskId("medium-1").instruction("中优先级任务1").priority(5).build(),
            SubAgentTask.builder().taskId("high-2").instruction("高优先级任务2").priority(10).build(),
            SubAgentTask.builder().taskId("low-2").instruction("低优先级任务2").priority(2).build()
        );
        
        ParallelExecutionContext context = executor.submitBatch(tasks, session);
        ParallelExecutionContext.BatchResult result = context.awaitCompletion(30, TimeUnit.SECONDS)
            .orElseThrow();
        
        assertEquals(5, result.getSuccessfulCount());
        System.out.println("优先级测试完成，所有任务执行成功");
    }
    
    @Test
    void testResultMerging() {
        // 测试结果合并功能
        List<SubAgentResult> results = List.of(
            SubAgentResult.success("task-1", "结果 A"),
            SubAgentResult.success("task-2", "结果 B"),
            SubAgentResult.success("task-3", "结果 C")
        );
        
        SubAgentResult merged = SubAgentResult.merge(results);
        
        assertTrue(merged.isSuccess());
        assertTrue(merged.getOutput().contains("结果 A"));
        assertTrue(merged.getOutput().contains("结果 B"));
        assertTrue(merged.getOutput().contains("结果 C"));
        
        System.out.println("合并结果:\n" + merged.getOutput());
    }
}
