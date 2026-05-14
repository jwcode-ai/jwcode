package com.jwcode.core.task;

import com.jwcode.core.session.Session;
import com.jwcode.core.llm.LLMService;
import com.jwcode.core.observability.ObservationPipeline;
import org.junit.jupiter.api.*;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 任务生命周期集成测试
 *
 * <p>测试 TaskLifecycleManager 核心功能：意图检测、任务启动、
 * 步骤推进、错误处理、任务列表操作。</p>
 */
@DisplayName("任务生命周期集成测试")
public class TaskLifecycleIntegrationTest {

    private TaskLifecycleManager manager;
    private LLMService mockLLM;
    private ObservationPipeline mockPipeline;
    private Session session;

    @BeforeEach
    void setUp() {
        mockLLM = Mockito.mock(LLMService.class);
        mockPipeline = Mockito.mock(ObservationPipeline.class);
        manager = new TaskLifecycleManager(mockLLM, mockPipeline);
        session = new Session("test-session", "/test/workdir");
    }

    @Test
    @DisplayName("检测用户意图")
    void testDetectIntent() {
        TaskIntent intent = manager.detectIntent(session, "test prompt");
        assertNotNull(intent);
    }

    @Test
    @DisplayName("启动新任务")
    void testStartNewTask() {
        manager.startNewTask(session, "test prompt");
        // 验证任务已启动（不抛异常）
    }

    @Test
    @DisplayName("用户输入处理")
    void testOnUserInput() {
        manager.onUserInput(session, "user prompt");
        // 验证输入被处理（不抛异常）
    }

    @Test
    @DisplayName("步骤推进")
    void testAdvanceStep() {
        manager.advanceStep(session, "step result");
        // 验证步骤推进（不抛异常）
    }

    @Test
    @DisplayName("启动第一步")
    void testStartFirstStep() {
        manager.startFirstStep(session);
        // 验证第一步启动（不抛异常）
    }

    @Test
    @DisplayName("步骤失败处理")
    void testFailStep() {
        manager.failStep(session, "error occurred");
        // 验证错误处理（不抛异常）
    }

    @Test
    @DisplayName("等待用户输入")
    void testWaitForUserInput() {
        manager.waitForUserInput(session, "What do you want to do?");
        // 验证等待用户输入（不抛异常）
    }

    @Test
    @DisplayName("检查任务完成状态")
    void testCheckTaskCompletion() {
        manager.checkTaskCompletion(session);
        // 验证完成检查（不抛异常）
    }

    @Test
    @DisplayName("添加动态步骤")
    void testAddDynamicStep() {
        manager.addDynamicStep(session, "new dynamic step");
        // 验证动态步骤添加（不抛异常）
    }

    @Test
    @DisplayName("操作任务列表")
    void testOperateTaskList() {
        String result = manager.operateTaskList(session, "list", null);
        assertNotNull(result);
    }

    @Test
    @DisplayName("完整任务生命周期")
    void testFullLifecycle() {
        manager.startNewTask(session, "initial prompt");
        manager.startFirstStep(session);
        manager.advanceStep(session, "step 1 done");
        manager.onUserInput(session, "continue");
        manager.advanceStep(session, "step 2 done");
        manager.checkTaskCompletion(session);

        String taskList = manager.operateTaskList(session, "list", null);
        assertNotNull(taskList);
    }
}
