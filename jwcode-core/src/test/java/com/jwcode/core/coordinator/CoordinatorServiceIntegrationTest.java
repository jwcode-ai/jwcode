package com.jwcode.core.coordinator;

import com.jwcode.core.coordinator.CoordinatorService.CoordinatedTask;
import com.jwcode.core.coordinator.CoordinatorService.CoordinationResult;
import com.jwcode.core.coordinator.CoordinatorService.CoordinationStrategy;
import com.jwcode.core.coordinator.CoordinatorService.TaskProgress;
import com.jwcode.core.coordinator.CoordinatorService.TaskStatus;
import com.jwcode.core.coordinator.CoordinatorService.WorkerStatus;
import org.junit.jupiter.api.*;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * CoordinatorService 集成测试
 *
 * <p>测试任务协调器的核心功能：任务创建、分解、执行策略（顺序/并行/平衡）、
 * 进度跟踪、状态管理和结果汇总。</p>
 */
@DisplayName("CoordinatorService 集成测试")
public class CoordinatorServiceIntegrationTest {

    private CoordinatorService coordinator;

    @BeforeEach
    void setUp() {
        coordinator = new CoordinatorService();
        coordinator.setMaxWorkers(4);
        coordinator.start();
    }

    @AfterEach
    void tearDown() {
        coordinator.stop();
    }

    // ==================== 任务创建 ====================

    @Test
    @DisplayName("创建协调任务 - 成功创建并返回有效任务")
    void testCreateTask() throws Exception {
        CompletableFuture<CoordinatedTask> future = coordinator.createTask("测试任务");
        CoordinatedTask task = future.get(5, TimeUnit.SECONDS);

        assertAll("创建的任务应包含有效信息",
            () -> assertNotNull(task, "任务不应为 null"),
            () -> assertNotNull(task.id, "任务 ID 不应为 null"),
            () -> assertTrue(task.id.startsWith("coord_"), "任务 ID 应以 coord_ 开头"),
            () -> assertEquals("测试任务", task.description, "任务描述应匹配"),
            () -> assertEquals(TaskStatus.PENDING, task.status, "新创建任务状态应为 PENDING"),
            () -> assertTrue(task.subTasks.isEmpty(), "新创建任务应无子任务")
        );
    }

    @Test
    @DisplayName("创建多个任务 - 每个任务应有唯一 ID")
    void testCreateMultipleTasks() throws Exception {
        // 添加小延迟确保 System.currentTimeMillis() 生成不同 ID
        CoordinatedTask task1 = coordinator.createTask("任务1").get(5, TimeUnit.SECONDS);
        Thread.sleep(5);
        CoordinatedTask task2 = coordinator.createTask("任务2").get(5, TimeUnit.SECONDS);
        Thread.sleep(5);
        CoordinatedTask task3 = coordinator.createTask("任务3").get(5, TimeUnit.SECONDS);

        assertAll("多个任务应有唯一 ID",
            () -> assertNotEquals(task1.id, task2.id, "任务1和任务2的 ID 应不同"),
            () -> assertNotEquals(task2.id, task3.id, "任务2和任务3的 ID 应不同"),
            () -> assertNotEquals(task1.id, task3.id, "任务1和任务3的 ID 应不同")
        );
    }

    // ==================== 任务分解 ====================

    @Test
    @DisplayName("分解任务 - 成功将主任务分解为多个子任务")
    void testDecomposeTask() throws Exception {
        CoordinatedTask task = coordinator.createTask("主任务").get(5, TimeUnit.SECONDS);
        List<String> subDescriptions = Arrays.asList("子任务1", "子任务2", "子任务3");

        CoordinatedTask decomposed = coordinator.decomposeTask(task.id, subDescriptions)
            .get(5, TimeUnit.SECONDS);

        assertAll("分解后的任务应包含子任务",
            () -> assertEquals(task.id, decomposed.id, "任务 ID 应保持不变"),
            () -> assertEquals(3, decomposed.subTasks.size(), "应包含3个子任务"),
            () -> assertEquals("子任务1", decomposed.subTasks.get(0).description, "第一个子任务描述匹配"),
            () -> assertEquals("子任务2", decomposed.subTasks.get(1).description, "第二个子任务描述匹配"),
            () -> assertEquals("子任务3", decomposed.subTasks.get(2).description, "第三个子任务描述匹配"),
            () -> assertEquals(TaskStatus.PENDING, decomposed.status, "分解后任务状态仍为 PENDING")
        );
    }

    @Test
    @DisplayName("分解空子任务列表 - 应返回空子任务列表")
    void testDecomposeWithEmptySubTasks() throws Exception {
        CoordinatedTask task = coordinator.createTask("空子任务测试").get(5, TimeUnit.SECONDS);

        CoordinatedTask decomposed = coordinator.decomposeTask(task.id, List.of())
            .get(5, TimeUnit.SECONDS);

        assertTrue(decomposed.subTasks.isEmpty(), "空子任务列表应返回空");
    }

    @Test
    @DisplayName("分解不存在的任务 - 应抛出异常")
    void testDecomposeNonExistentTask() {
        List<String> subDescriptions = Arrays.asList("子任务1");

        assertThrows(Exception.class, () -> {
            coordinator.decomposeTask("non_existent_id", subDescriptions)
                .get(5, TimeUnit.SECONDS);
        }, "分解不存在的任务应抛出异常");
    }

    // ==================== 顺序执行策略 ====================

    @Test
    @DisplayName("顺序执行 - 使用 SEQUENTIAL 策略")
    void testSequentialExecution() throws Exception {
        // 准备
        coordinator.setStrategy(CoordinationStrategy.SEQUENTIAL);
        CoordinatedTask task = coordinator.createTask("顺序执行测试").get(5, TimeUnit.SECONDS);
        coordinator.decomposeTask(task.id, Arrays.asList("步骤1", "步骤2", "步骤3")).get(5, TimeUnit.SECONDS);

        // 执行
        CoordinationResult result = coordinator.executeTask(task.id)
            .get(10, TimeUnit.SECONDS);

        // 验证
        assertAll("顺序执行结果验证",
            () -> assertTrue(result.success, "所有子任务应成功执行"),
            () -> assertNotNull(result.data, "结果数据不应为 null"),
            () -> {
                // 验证任务状态为 COMPLETED
                TaskProgress progress = coordinator.getTaskProgress(task.id);
                assertEquals(3, progress.completed, "应有3个子任务完成");
                assertEquals(3, progress.total, "总共3个子任务");
            }
        );
    }

    // ==================== 并行执行策略 ====================

    @Test
    @DisplayName("并行执行 - 使用 PARALLEL 策略")
    void testParallelExecution() throws Exception {
        coordinator.setStrategy(CoordinationStrategy.PARALLEL);
        CoordinatedTask task = coordinator.createTask("并行执行测试").get(5, TimeUnit.SECONDS);
        coordinator.decomposeTask(task.id, Arrays.asList("并行任务1", "并行任务2", "并行任务3", "并行任务4"))
            .get(5, TimeUnit.SECONDS);

        long startTime = System.currentTimeMillis();
        CoordinationResult result = coordinator.executeTask(task.id)
            .get(15, TimeUnit.SECONDS);
        long duration = System.currentTimeMillis() - startTime;

        assertAll("并行执行结果验证",
            () -> assertTrue(result.success, "所有并行子任务应成功"),
            () -> assertTrue(duration < 10000, "并行执行应在合理时间内完成")
        );
    }

    @Test
    @DisplayName("并行执行 - 空子任务列表")
    void testParallelExecutionEmptyTasks() throws Exception {
        coordinator.setStrategy(CoordinationStrategy.PARALLEL);
        CoordinatedTask task = coordinator.createTask("空任务并行").get(5, TimeUnit.SECONDS);
        coordinator.decomposeTask(task.id, List.of()).get(5, TimeUnit.SECONDS);

        CoordinationResult result = coordinator.executeTask(task.id)
            .get(5, TimeUnit.SECONDS);

        assertTrue(result.success, "空子任务列表的并行执行应返回成功");
    }

    // ==================== 进度查询 ====================

    @Test
    @DisplayName("获取任务进度 - 各阶段进度信息正确")
    void testGetTaskProgress() throws Exception {
        CoordinatedTask task = coordinator.createTask("进度查询测试").get(5, TimeUnit.SECONDS);
        coordinator.decomposeTask(task.id, Arrays.asList("子任务1", "子任务2")).get(5, TimeUnit.SECONDS);

        // 执行前
        TaskProgress beforeProgress = coordinator.getTaskProgress(task.id);
        assertEquals(2, beforeProgress.total, "总任务数应为2");
        assertEquals(0, beforeProgress.completed, "执行前完成数应为0");

        // 执行后
        coordinator.setStrategy(CoordinationStrategy.SEQUENTIAL);
        coordinator.executeTask(task.id).get(10, TimeUnit.SECONDS);
        TaskProgress afterProgress = coordinator.getTaskProgress(task.id);
        assertEquals(2, afterProgress.completed, "执行后应全部完成");
    }

    // ==================== 工作线程状态 ====================

    @Test
    @DisplayName("获取工作线程状态 - 返回正确的线程信息")
    void testGetWorkerStatus() {
        WorkerStatus status = coordinator.getWorkerStatus();

        assertAll("工作线程状态验证",
            () -> assertEquals(4, status.maxWorkers, "最大工作线程数应为4"),
            () -> assertTrue(status.activeWorkers >= 0, "活跃线程数应 >= 0"),
            () -> assertTrue(status.pendingTasks >= 0, "待处理任务数应 >= 0")
        );
    }

    // ==================== 获取活跃任务 ====================

    @Test
    @DisplayName("获取活跃任务 - 正确筛选运行中任务")
    void testGetActiveTasks() throws Exception {
        CoordinatedTask task = coordinator.createTask("活跃任务测试").get(5, TimeUnit.SECONDS);
        coordinator.decomposeTask(task.id, Arrays.asList("子任务1", "子任务2")).get(5, TimeUnit.SECONDS);

        // 创建后任务为 PENDING，应在活跃列表中
        List<CoordinatedTask> beforeActive = coordinator.getActiveTasks();
        assertFalse(beforeActive.isEmpty(), "创建任务后应有活跃任务（PENDING）");

        // 执行后 - 可能无活跃任务（已完成）
        coordinator.setStrategy(CoordinationStrategy.SEQUENTIAL);
        coordinator.executeTask(task.id).get(10, TimeUnit.SECONDS);
        List<CoordinatedTask> afterActive = coordinator.getActiveTasks();
        // 执行完成后应无 RUNNING 状态任务
        assertTrue(afterActive.stream().noneMatch(t -> t.status == TaskStatus.RUNNING),
            "执行完成后应无 RUNNING 状态任务");
    }

    // ==================== 取消任务 ====================

    @Test
    @DisplayName("取消任务 - 正确取消指定任务")
    void testCancelTask() throws Exception {
        CoordinatedTask task = coordinator.createTask("可取消任务").get(5, TimeUnit.SECONDS);
        coordinator.decomposeTask(task.id, Arrays.asList("长时间任务")).get(5, TimeUnit.SECONDS);

        boolean cancelled = coordinator.cancelTask(task.id);
        assertTrue(cancelled, "取消任务应返回 true");

        // 取消后任务从活跃列表中移除
        TaskProgress progress = coordinator.getTaskProgress(task.id);
        assertNull(progress, "取消后任务不再可查询（已从 map 中移除）");
    }

    // ==================== 关闭服务 ====================

    @Test
    @DisplayName("停止服务 - 优雅停止，资源释放")
    void testStop() {
        assertDoesNotThrow(() -> coordinator.stop(),
            "停止服务不应抛出异常");
    }

    // ==================== 设置最大工作线程 ====================

    @Test
    @DisplayName("设置最大工作线程数 - 最小值为1")
    void testSetMaxWorkersMinimum() {
        coordinator.setMaxWorkers(-5);
        WorkerStatus status = coordinator.getWorkerStatus();
        assertTrue(status.maxWorkers >= 1, "最大工作线程数应至少为1");
    }

    // ==================== 获取任务状态 ====================

    @Test
    @DisplayName("获取任务状态 - 返回正确的任务信息")
    void testGetTaskStatus() throws Exception {
        CoordinatedTask task = coordinator.createTask("状态查询测试").get(5, TimeUnit.SECONDS);
        CoordinatedTask status = coordinator.getTaskStatus(task.id);
        assertNotNull(status);
        assertEquals(task.id, status.id);
    }

    // ==================== 综合场景 ====================

    @Test
    @DisplayName("完整工作流 - 创建、分解、执行、查询全流程")
    void testCompleteWorkflow() throws Exception {
        // 1. 创建任务
        CoordinatedTask task = coordinator.createTask("综合测试任务").get(5, TimeUnit.SECONDS);
        assertNotNull(task);

        // 2. 分解为子任务
        coordinator.decomposeTask(task.id, Arrays.asList("分析", "设计", "实现", "测试", "部署"))
            .get(5, TimeUnit.SECONDS);

        // 3. 获取进度
        TaskProgress beforeProgress = coordinator.getTaskProgress(task.id);
        assertEquals(5, beforeProgress.total);

        // 4. 执行（使用并行策略）
        coordinator.setStrategy(CoordinationStrategy.PARALLEL);
        CoordinationResult result = coordinator.executeTask(task.id)
            .get(15, TimeUnit.SECONDS);
        assertTrue(result.success, "完整工作流执行应成功");

        // 5. 验证最终进度
        TaskProgress afterProgress = coordinator.getTaskProgress(task.id);
        assertEquals(5, afterProgress.completed, "所有子任务应完成");

        // 6. 验证工作线程状态
        WorkerStatus status = coordinator.getWorkerStatus();
        assertNotNull(status);
    }
}
