package com.jwcode.cli;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 命令执行结果
 * 
 * 封装命令执行后的结果，包括输出内容、状态等信息。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CommandResult {
    
    /**
     * 执行是否成功
     */
    private boolean success;
    
    /**
     * 输出内容
     */
    private String output;
    
    /**
     * 错误信息（如果有）
     */
    private String error;
    
    /**
     * 退出代码
     */
    private int exitCode;
    
    /**
     * 创建成功结果
     * 
     * @param output 输出内容
     * @return 成功的命令结果
     */
    public static CommandResult success(String output) {
        return CommandResult.builder()
                .success(true)
                .output(output)
                .exitCode(0)
                .build();
    }
    
    /**
     * 创建失败结果
     * 
     * @param error 错误信息
     * @return 失败的命令结果
     */
    public static CommandResult error(String error) {
        return CommandResult.builder()
                .success(false)
                .error(error)
                .exitCode(1)
                .build();
    }
    
    /**
     * 创建带输出的失败结果
     * 
     * @param output 输出内容
     * @param error 错误信息
     * @return 失败的命令结果
     */
    public static CommandResult error(String output, String error) {
        return CommandResult.builder()
                .success(false)
                .output(output)
                .error(error)
                .exitCode(1)
                .build();
    }
}
