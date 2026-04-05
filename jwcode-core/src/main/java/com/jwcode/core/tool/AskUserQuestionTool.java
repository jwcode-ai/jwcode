package com.jwcode.core.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * AskUserQuestionTool - 向用户提问工具
 * 
 * 功能说明：
 * 向用户提出问题以获取额外信息或澄清。
 * 用于在任务执行过程中需要用户输入时使用。
 * 
 * 上下文关系：
 * - 被 QueryEngine 调用
 * - 在 REPL 模式下显示交互式问题
 * - 在非交互模式下返回错误
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class AskUserQuestionTool implements Tool<AskUserQuestionTool.Input, AskUserQuestionTool.Output, AskUserQuestionTool.Progress> {
    
    public static final String NAME = "AskUserQuestion";
    
    @Override
    public String getName() {
        return NAME;
    }
    
    @Override
    public String getDescription() {
        return "向用户提出问题以获取额外信息或澄清。当需要用户输入或确认时使用此工具。";
    }
    
    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<Input>() {};
    }
    
    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<Output>() {};
    }
    
    @Override
    public JsonNode getInputSchema() {
        return com.jwcode.core.tool.ToolSchemaGenerator.generateSchema(Input.class);
    }
    
    @Override
    public JsonNode getOutputSchema() {
        return com.jwcode.core.tool.ToolSchemaGenerator.generateSchema(Output.class);
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input args,
            ToolExecutionContext context,
            java.util.function.Consumer<ToolProgress<Progress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // 检查是否为交互式会话
                boolean isInteractive = context.isInteractive();
                
                if (!isInteractive) {
                    // 非交互模式下，返回需要用户输入的标志
                    Output output = new Output();
                    output.question = args.question;
                    output.questionType = args.questionType != null ? args.questionType : "open_ended";
                    output.answer = null;
                    output.status = "awaiting_user_input";
                    output.message = "需要用户输入：" + args.question;
                    return ToolResult.<Output>builder().data(output).build();
                }
                
                // 交互模式下，显示问题并等待回答
                if (onProgress != null) {
                    onProgress.accept(new ToolProgress<>(new Progress("asking", "正在向用户提问...")));
                }
                
                // 在实际实现中，这里会通过 UI 显示问题并等待用户输入
                // 目前返回需要用户输入的标志
                Output output = new Output();
                output.question = args.question;
                output.questionType = args.questionType != null ? args.questionType : "open_ended";
                output.options = args.options;
                output.status = "awaiting_user_input";
                output.message = "问题：" + args.question;
                
                return ToolResult.<Output>builder().data(output).build();
                
            } catch (Exception e) {
                return ToolResult.<Output>builder()
                        .data(createErrorOutput(args, e.getMessage()))
                        .build();
            }
        });
    }
    
    private Output createErrorOutput(Input args, String error) {
        Output output = new Output();
        output.question = args.question;
        output.error = error;
        return output;
    }
    
    @Override
    public boolean isConcurrencySafe(Input input) {
        return true;
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        return true;
    }
    
    /**
     * 输入类
     */
    public static class Input {
        public String question;
        public String questionType; // open_ended, yes_no, multiple_choice, confirm
        public String[] options;
        public String defaultAnswer;
        public Boolean allowEmpty;
    }
    
    /**
     * 输出类
     */
    public static class Output {
        public String question;
        public String questionType;
        public String[] options;
        public String answer;
        public String status; // awaiting_user_input, answered, skipped
        public String message;
        public String error;
    }
    
    /**
     * 进度类
     */
    public static class Progress {
        public final String status;
        public final String message;
        
        public Progress(String status, String message) {
            this.status = status;
            this.message = message;
        }
    }
}
