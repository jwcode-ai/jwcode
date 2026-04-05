package com.jwcode.core.tool;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * ToolRegistry 测试类
 */
public class ToolRegistryTest {
    
    @Test
    public void testCreateDefault() {
        ToolRegistry registry = ToolRegistry.createDefault();
        
        // 验证工具数量
        int toolCount = registry.size();
        System.out.println("注册的工具数量: " + toolCount);
        
        // 打印所有工具名称
        System.out.println("\n已注册的工具列表:");
        for (String name : registry.getAllToolNames()) {
            System.out.println("  - " + name);
        }
        
        // 验证至少有核心工具
        assertTrue(registry.contains("BashTool"), "BashTool 应该已注册");
        assertTrue(registry.contains("FileReadTool"), "FileReadTool 应该已注册");
        assertTrue(registry.contains("FileEditTool"), "FileEditTool 应该已注册");
        assertTrue(registry.contains("FileWriteTool"), "FileWriteTool 应该已注册");
        assertTrue(registry.contains("GrepTool"), "GrepTool 应该已注册");
        assertTrue(registry.contains("GlobTool"), "GlobTool 应该已注册");
        
        // 验证工具数量大于 0
        assertTrue(toolCount > 0, "应该有工具被注册");
        
        System.out.println("\n✅ 工具注册测试通过！");
    }
}
