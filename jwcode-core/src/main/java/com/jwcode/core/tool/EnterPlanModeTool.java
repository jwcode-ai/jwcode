package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.plan.PlanModeManager;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/**
 * EnterPlanModeTool — 进入计划模式工具（增强版）。
 * 
 * <p>进入 Plan Mode 后：</p>
 * <ul>
 *   <li>禁用写操作：FileWrite、FileEdit、Bash、PowerShell 等全部不可用</li>
 *   <li>只剩探索性工具：Read、Glob、Grep、WebFetch 等</li>
 *   <li>可以自由探索代码、设计方案，但不能改任何东西</li>
 * </ul>
 * 
 * <p>何时使用：</p>
 * <ul>
 *   <li>实现新功能</li>
 *   <li>重构现有代码</li>
 *   <li>有多种合理方案的任务</li>
 *   <li>涉及 2-3 文件以上的修改</li>
 *   <li>架构层面的决策</li>
 *   <li>用户需求模糊需要先探索</li>
 * </ul>
 * 
 * <p>何时不使用：</p>
 * <ul>
 *   <li>修一个 typo</li>
 *   <li>加一行 console.log</li>
 *   <li>加一个明确需求的小功能</li>
 * </ul>
 */
public class EnterPlanModeTool implements Tool<EnterPlanModeTool.Input, EnterPlanModeTool.Output, EnterPlanModeTool.Progress> {
    
    private static final Logger logger = Logger.getLogger(EnterPlanModeTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    @Override
    public String getName() {
        return "EnterPlanMode";
    }
    
    @Override
    public String getDescription() {
        return "进入计划模式（Plan Mode）。Plan Mode 下只允许只读操作，禁止写文件和执行命令。";
    }
    
    @Override
    public String getPrompt() {
        return """
            **EnterPlanMode** — 进入计划模式
            
            进入 Plan Mode 后，你将只能使用只读工具，所有写操作和命令执行将被禁用。
            
            **✅ Plan Mode 下推荐的工具（按优先级）**：
            1. **SmartAnalyzeTool** — 智能项目分析（自动排噪、定位关键文件），**首选**
            2. **GlobTool** — 文件搜索（替代 Bash 的 ls/find/dir）
            3. **FileReadTool** — 读取文件内容
            4. **AskUserQuestion** — 向用户澄清需求
            5. **WebFetchTool** — 获取网页内容
            
            **❌ Plan Mode 下被禁用的工具**：
            - Bash / PowerShell — 禁止执行任何 shell 命令
            - FileWrite / FileEdit — 禁止修改文件
            - Git — 禁止 Git 操作
            
            **💡 工具选择决策树**：
            - 想了解项目结构？→ 用 **SmartAnalyzeTool**（不要用 Bash ls/find）
            - 想搜索特定文件？→ 用 **GlobTool**（不要用 Bash find/dir）
            - 想读取文件内容？→ 用 **FileReadTool**（不要用 Bash cat/type）
            - 需要更多上下文？→ 用 **AskUserQuestion**
            
            **何时使用**：
            - 实现新功能 / 重构代码 / 架构决策
            - 有多种合理方案的任务
            - 涉及 2-3 文件以上的修改
            - 用户需求模糊需要先探索
            
            **何时不使用**：
            - 修 typo / 加 console.log / 简单小功能
            
            **标准流程**：
            1. 进入 Plan Mode
            2. 用 SmartAnalyzeTool 或 GlobTool 探索代码
            3. 用 FileReadTool 读取关键文件
            4. 需要时用 AskUserQuestion 澄清需求
            5. 生成结构化任务清单（每个任务必须有 id/action/description/agentType/dependencies/expectedOutput）
            6. 调用 ExitPlanModeV2 提交审批
            
            **⚠️ Plan Mode 铁律**：
            - 你只能探索和规划，不得声称自己完成了任何写操作
            - 任何声称"已完成"的声明必须附带对应工具调用的证据
            - 如果无法完成某项操作，如实说明 — 撒谎比失败更严重
            - 返回的任务清单必须是结构化 JSON 格式（见 ExitPlanModeV2）
            - **禁止调用 Bash/PowerShell** — 所有探索需求都可以用 SmartAnalyzeTool / GlobTool / FileReadTool 满足
            """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            String schema = """
               {
                 "type": "object",
                 "properties": {
                   "task": {
                     "type": "string",
                     "description": "任务描述 — 说明为什么需要进入 Plan Mode"
                   }
                 },
                 "required": ["task"]
               }
               """;
            return MAPPER.readTree(schema);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input args,
            ToolExecutionContext context,
            java.util.function.Consumer<ToolProgress<Progress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String taskDescription = args != null && args.task != null 
                    ? args.task 
                    : "未指定任务";
                
                // 通过 PlanModeManager 进入 Plan Mode
                PlanModeManager modeManager = PlanModeManager.getInstance();
                boolean success = modeManager.enterPlanMode(taskDescription);
                
                if (success) {
                    logger.info("Entered Plan Mode: " + taskDescription);
                    return ToolResult.success(new Output(true, "plan", 
                        "已进入计划模式（Plan Mode）。\n" +
                        "你现在可以自由探索代码、设计方案，但不能修改任何文件或执行命令。\n" +
                        "完成规划后，请调用 ExitPlanMode 退出计划模式。"));
                } else {
                    return ToolResult.error("进入计划模式失败");
                }
                
            } catch (Exception e) {
                logger.warning("EnterPlanMode failed: " + e.getMessage());
                return ToolResult.error("进入计划模式失败: " + e.getMessage());
            }
        });
    }
    
    @Override
    public ToolValidationResult validate(Input input) {
        if (input == null || input.task == null || input.task.trim().isEmpty()) {
            return ToolValidationResult.invalid("必须提供任务描述（task 参数）");
        }
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        return false; // 模式切换本身不是只读操作
    }
    
    @Override
    public boolean isDestructive(Input input) {
        return false;
    }
    
    @Override
    public boolean requiresApproval(Input input) {
        return false;
    }
    
    @Override
    public Set<SideEffect> getSideEffects() {
        return Set.of(SideEffect.SESSION_MUTATION);
    }
    
    @Override
    public ToolCategory getCategory() {
        return ToolCategory.METACOGNITION;
    }
    
    // ==================== 输入/输出类型 ====================
    
    public static class Input {
        public String task;
    }
    
    public static class Output {
        public boolean success;
        public String mode;
        public String message;
        
        public Output() {
            this.success = true;
            this.mode = "plan";
            this.message = "";
        }
        
        public Output(boolean success, String mode, String message) {
            this.success = success;
            this.mode = mode;
            this.message = message;
        }
    }
    
    public static class Progress {}
}
