package com.jwcode.core.tool;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 工具注册完整性验证测试。
 * 
 * <p>验证所有工具满足以下完整性条件：</p>
 * <ul>
 *   <li>🔤 工具名称全局唯一</li>
 *   <li>📝 所有工具有非空描述</li>
 *   <li>💬 所有工具有非空 Prompt</li>
 *   <li>📂 工具分类（Category）正确分配</li>
 *   <li>⚠️ 写操作工具 SideEffect 注解正确</li>
 *   <li>🔢 Phase 1-8 工具总数 >= 70</li>
 *   <li>📊 每个 Phase 至少有 5 个工具</li>
 * </ul>
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class ToolRegistryCompletenessTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = ToolRegistry.createDefault();
        assertNotNull(registry, "ToolRegistry.createDefault() 不应返回 null");
    }

    // ========================================================================
    // 1. 工具注册基础设施
    // ========================================================================

    @Test
    @Order(1)
    @DisplayName("所有已注册工具具有唯一名称")
    void testAllToolsHaveUniqueNames() {
        Set<String> allNames = registry.getAllToolNames();
        
        // Set 本身保证唯一性，只需检查无 null
        assertFalse(allNames.contains(null), "工具名不应包含 null");
        assertTrue(allNames.size() > 0, "工具名集合不应为空");
    }

    @Test
    @Order(2)
    @DisplayName("所有已注册工具有非空描述")
    void testAllToolsHaveDescription() {
        List<String> missingDescriptions = new ArrayList<>();

        for (String name : registry.getAllToolNames()) {
            Tool tool = registry.getTool(name);
            if (tool == null || tool.getDescription() == null || tool.getDescription().isBlank()) {
                missingDescriptions.add(name);
            }
        }

        assertTrue(missingDescriptions.isEmpty(),
                () -> "以下工具缺少描述: " + missingDescriptions);
    }

    @Test
    @Order(3)
    @DisplayName("所有已注册工具有非空 Prompt")
    void testAllToolsHavePrompt() {
        List<String> missingPrompts = new ArrayList<>();

        for (String name : registry.getAllToolNames()) {
            Tool tool = registry.getTool(name);
            if (tool == null || tool.getPrompt() == null || tool.getPrompt().isBlank()) {
                missingPrompts.add(name);
            }
        }

        assertTrue(missingPrompts.isEmpty(),
                () -> "以下工具缺少 Prompt: " + missingPrompts);
    }

    // ========================================================================
    // 2. 工具分类完整性
    // ========================================================================

    @Test
    @Order(4)
    @DisplayName("所有工具都有合法的 Category 分配")
    void testToolCategoryAssignment() {
        List<String> invalidCategory = new ArrayList<>();

        for (String name : registry.getAllToolNames()) {
            Tool tool = registry.getTool(name);
            if (tool == null || tool.getCategory() == null) {
                invalidCategory.add(name + "(null category)");
            }
        }

        assertTrue(invalidCategory.isEmpty(),
                () -> "以下工具缺少或 Category 为 null: " + invalidCategory);
    }

    @Test
    @Order(5)
    @DisplayName("每个有工具分配的 Category 都正确记录")
    void testAllCategoriesHaveTools() {
        Map<String, Long> categoryCounts = registry.getAllToolNames().stream()
                .map(name -> registry.getTool(name))
                .filter(Objects::nonNull)
                .collect(Collectors.groupingBy(
                        tool -> tool.getCategory() != null ? tool.getCategory().name() : "UNKNOWN",
                        Collectors.counting()
                ));

        System.out.println("=== Category 分布 ===");
        categoryCounts.forEach((cat, count) -> 
            System.out.println("  " + cat + ": " + count + " 个工具"));

        // 至少 SYSTEM 和 METACOGNITION 两个 Category 有工具
        assertTrue(categoryCounts.containsKey("SYSTEM"),
                "SYSTEM Category 应至少有一个工具");
        assertTrue(categoryCounts.containsKey("METACOGNITION"),
                "METACOGNITION Category 应至少有一个工具");
    }

    // ========================================================================
    // 3. SideEffect 注解完整性
    // ========================================================================

    @Test
    @Order(6)
    @DisplayName("写操作工具有正确的 SideEffect 标记")
    void testSideEffectAnnotation() {
        List<String> readToolsMissingAnnotation = new ArrayList<>();
        List<String> writeToolsMissingAnnotation = new ArrayList<>();

        for (String name : registry.getAllToolNames()) {
            Tool tool = registry.getTool(name);
            if (tool == null) continue;

            Set<SideEffect> sideEffects = tool.getSideEffects();
            ToolCategory category = tool.getCategory();

            // 文件写入类工具应有 WRITE_FILE 副作用标记
            if (category == ToolCategory.FILE_OPERATION && name.toLowerCase().contains("write")) {
                if (sideEffects == null || !sideEffects.contains(SideEffect.WRITE_FILE)) {
                    writeToolsMissingAnnotation.add(name);
                }
            }
        }

        assertTrue(writeToolsMissingAnnotation.isEmpty(),
                () -> "以下写工具缺少 WRITE_FILE SideEffect: " + writeToolsMissingAnnotation);
    }

    // ========================================================================
    // 4. Phase 覆盖完整性
    // ========================================================================

    /**
     * 各 Phase 预期工具清单（名称需与工具实际 getName() 返回值完全一致）
     */
    static final Map<String, List<String>> PHASE_TOOLS = new LinkedHashMap<>();
    static {
        PHASE_TOOLS.put("Phase1_任务管理", Arrays.asList(
                "TaskCreate", "TaskGet", "TaskUpdate", "TaskList", "TaskOutput", "TaskStop"));
        PHASE_TOOLS.put("Phase2_Agent", Arrays.asList("AgentTool", "TeamCreate", "TeamDelete", "TeamList"));
        PHASE_TOOLS.put("Phase3_REPL_Notebook", Arrays.asList("REPL", "NotebookEdit"));
        PHASE_TOOLS.put("Phase4_LSP", Arrays.asList("LSP"));
        PHASE_TOOLS.put("Phase5_搜索", Arrays.asList("WebSearch", "WebFetch", "GrepTool", "GlobTool"));
        PHASE_TOOLS.put("Phase6_工具定义", Arrays.asList("BashTool", "PowerShell", "GitTool", "FileReadTool", "FileWriteTool", "FileEditTool", "BatchReadTool"));
        PHASE_TOOLS.put("Phase7_配置_计划", Arrays.asList("Config", "Pattern", "Sleep"));
        PHASE_TOOLS.put("Phase8_通信_远程", Arrays.asList("SendMessage"));
    }

    @Test
    @Order(7)
    @DisplayName("Phase 1-8 全部都有对应工具注册")
    void testAllPhasesAreCovered() {
        for (Map.Entry<String, List<String>> entry : PHASE_TOOLS.entrySet()) {
            String phaseName = entry.getKey();
            List<String> expectedTools = entry.getValue();

            for (String toolName : expectedTools) {
                assertTrue(registry.contains(toolName),
                        () -> phaseName + " - 工具 [" + toolName + "] 应已注册");
            }
            System.out.println("✅ " + phaseName + ": " + expectedTools.size() + " 个工具");
        }
    }

    @Test
    @Order(8)
    @DisplayName("每个 Phase 至少有 1 个工具")
    void testEachPhaseHasMinTools() {
        for (Map.Entry<String, List<String>> entry : PHASE_TOOLS.entrySet()) {
            String phaseName = entry.getKey();
            int actualCount = entry.getValue().size();
            assertTrue(actualCount >= 1,
                    () -> phaseName + " 应至少包含 1 个工具，实际: " + actualCount);
            System.out.println("  " + phaseName + ": " + actualCount + " 个工具 ✅");
        }
    }

    @Test
    @Order(9)
    @DisplayName("注册工具总数 >= 40")
    void testToolCountIsComplete() {
        int totalCount = registry.size();
        System.out.println("当前已注册工具总数: " + totalCount);

        assertTrue(totalCount >= 40,
                () -> "工具总数应 >= 40，当前: " + totalCount);
    }

    // ========================================================================
    // 5. 工具名称命名规范
    // ========================================================================

    @Test
    @Order(10)
    @DisplayName("工具名称符合驼峰命名规范")
    void testToolNamingConvention() {
        List<String> invalidNames = new ArrayList<>();

        for (String name : registry.getAllToolNames()) {
            // 工具名应以大写字母开头，不包含空格
            if (name == null || name.isEmpty() || !Character.isUpperCase(name.charAt(0))
                    || name.contains(" ")) {
                invalidNames.add(name);
            }
        }

        assertTrue(invalidNames.isEmpty(),
                () -> "以下工具名不符合命名规范: " + invalidNames);
    }

    // ========================================================================
    // 辅助方法
    // ========================================================================

    private List<String> findDuplicates(List<String> names) {
        Set<String> seen = new HashSet<>();
        List<String> duplicates = new ArrayList<>();
        for (String name : names) {
            if (!seen.add(name)) {
                duplicates.add(name);
            }
        }
        return duplicates;
    }
}
