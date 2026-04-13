package com.jwcode.core;

import com.jwcode.core.tool.*;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 集成测试 - 验证所有新增功能已正确注册
 */
public class IntegrationTest {
    
    @Test
    @DisplayName("Phase 1-8: 所有新工具已注册到 ToolRegistry")
    void testAllNewToolsRegistered() {
        ToolRegistry registry = ToolRegistry.createDefault();
        assertNotNull(registry);
        
        System.out.println("已注册工具总数: " + registry.size());
        System.out.println("工具列表: " + registry.getAllToolNames());
        
        // Phase 1: 任务管理工具
        String[] taskTools = {"TaskCreate", "TaskGet", "TaskUpdate", "TaskList", "TaskOutput", "TaskStop"};
        for (String toolName : taskTools) {
            assertTrue(registry.contains(toolName), 
                "Phase 1 - 任务管理工具应该被注册: " + toolName);
            System.out.println("✅ Phase 1: " + toolName);
        }
        
        // Phase 2: Agent 工具（已有 AgentTool，增强在内部）
        assertTrue(registry.contains("AgentTool"), "Phase 2 - AgentTool 应该被注册");
        System.out.println("✅ Phase 2: AgentTool (增强并行执行)");
        
        // Phase 3: REPL 和 Notebook
        assertTrue(registry.contains("REPL"), "Phase 3 - REPL 应该被注册");
        assertTrue(registry.contains("NotebookEdit"), "Phase 3 - NotebookEdit 应该被注册");
        System.out.println("✅ Phase 3: REPL, NotebookEdit");
        
        // Phase 4: LSP
        assertTrue(registry.contains("LSP"), "Phase 4 - LSP 应该被注册");
        System.out.println("✅ Phase 4: LSP");
        
        // Phase 5: 团队协作
        String[] teamTools = {"TeamCreate", "TeamDelete", "TeamList", "SendMessage"};
        for (String toolName : teamTools) {
            assertTrue(registry.contains(toolName), 
                "Phase 5 - 团队协作工具应该被注册: " + toolName);
            System.out.println("✅ Phase 5: " + toolName);
        }
        
        // Phase 6: 配置管理（已有 ConfigTool，增强在内部）
        assertTrue(registry.contains("Config"), "Phase 6 - Config 应该被注册");
        System.out.println("✅ Phase 6: Config (增强继承链支持)");
        
        // Phase 7: Worktree
        String[] worktreeTools = {"EnterWorktree", "ExitWorktree", "WorktreeList"};
        for (String toolName : worktreeTools) {
            assertTrue(registry.contains(toolName), 
                "Phase 7 - Worktree 工具应该被注册: " + toolName);
            System.out.println("✅ Phase 7: " + toolName);
        }
        
        // Phase 8: Web 搜索
        assertTrue(registry.contains("WebFetch"), "Phase 8 - WebFetch 应该被注册");
        assertTrue(registry.contains("WebSearch"), "Phase 8 - WebSearch 应该被注册");
        System.out.println("✅ Phase 8: WebFetch, WebSearch");
        
        System.out.println("\n🎉 所有 8 个阶段的新功能工具已正确注册！");
    }
    
    @Test
    @DisplayName("验证核心工具实例可创建")
    void testToolInstances() {
        // 测试可以实例化新工具
        assertDoesNotThrow(() -> new TaskCreateTool());
        assertDoesNotThrow(() -> new TaskGetTool());
        assertDoesNotThrow(() -> new TaskListTool());
        assertDoesNotThrow(() -> new TaskUpdateTool());
        assertDoesNotThrow(() -> new TaskOutputTool());
        assertDoesNotThrow(() -> new TaskStopTool());
        
        assertDoesNotThrow(() -> new TeamCreateTool());
        assertDoesNotThrow(() -> new TeamDeleteTool());
        assertDoesNotThrow(() -> new TeamListTool());
        assertDoesNotThrow(() -> new SendMessageTool());
        
        assertDoesNotThrow(() -> new EnterWorktreeTool());
        assertDoesNotThrow(() -> new ExitWorktreeTool());
        assertDoesNotThrow(() -> new WorktreeListTool());
        
        assertDoesNotThrow(() -> new REPLTool());
        assertDoesNotThrow(() -> new NotebookEditTool());
        assertDoesNotThrow(() -> new LSPTool());
        
        assertDoesNotThrow(() -> new WebFetchTool());
        
        System.out.println("✅ 所有新工具实例可正常创建");
    }
    
    @Test
    @DisplayName("验证工具基本信息")
    void testToolBasicInfo() {
        // 测试任务创建工具
        TaskCreateTool taskCreate = new TaskCreateTool();
        assertEquals("TaskCreate", taskCreate.getName());
        assertNotNull(taskCreate.getDescription());
        assertNotNull(taskCreate.getPrompt());
        
        // 测试 REPL 工具
        REPLTool repl = new REPLTool();
        assertEquals("REPL", repl.getName());
        assertNotNull(repl.getDescription());
        
        // 测试 LSP 工具
        LSPTool lsp = new LSPTool();
        assertEquals("LSP", lsp.getName());
        assertNotNull(lsp.getDescription());
        
        // 测试 WebFetch 工具
        WebFetchTool webFetch = new WebFetchTool();
        assertEquals("WebFetch", webFetch.getName());
        assertNotNull(webFetch.getDescription());
        
        System.out.println("✅ 所有工具基本信息正确");
    }
}