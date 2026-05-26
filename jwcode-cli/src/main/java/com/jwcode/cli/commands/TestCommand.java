package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.core.command.EvalCommand;
import com.jwcode.core.session.SessionManager;

/**
 * test 命令 — 适配器模式，包装 core 模块的 EvalCommand。
 * <p>
 * 用法：
 * <ul>
 *   <li>{@code test} — 运行所有评测任务（模拟模式）</li>
 *   <li>{@code test simple} — 只运行简单任务</li>
 *   <li>{@code test medium} — 只运行中等任务</li>
 *   <li>{@code test complex} — 只运行复杂任务</li>
 *   <li>{@code test full} — 完整模式（真实 LLM 调用）</li>
 *   <li>{@code test list} — 列出所有评测任务</li>
 * </ul>
 */
public class TestCommand implements Command {

    private final EvalCommand delegate;

    public TestCommand() {
        this.delegate = new EvalCommand();
    }

    @Override
    public String getName() {
        return "test";
    }

    @Override
    public String getDescription() {
        return "运行 JWCode 能力评测，验证系统在不同难度下的任务完成能力";
    }

    @Override
    public String getUsage() {
        return "test [simple|medium|complex|full|list|help]";
    }

    @Override
    public String[] getAliases() {
        return new String[]{"eval", "t"};
    }

    @Override
    public CommandResult execute(String args, CommandContext context) {
        // 解析参数
        String[] argArray = args == null || args.isBlank() ? new String[0] : args.trim().split("\\s+");

        // 获取 Core Session — 使用全限定名避免与 CLI 的 CommandContext.Session 冲突
        com.jwcode.core.session.Session session = SessionManager.getInstance().getActiveSession();

        // 委托给 core 的 EvalCommand
        com.jwcode.core.command.CommandResult coreResult = delegate.execute(argArray, session);

        // 转换结果
        return CommandResult.builder()
                .success(coreResult.isSuccess())
                .output(coreResult.getMessage())
                .error(coreResult.isSuccess() ? null : coreResult.getMessage())
                .exitCode(coreResult.isSuccess() ? 0 : 1)
                .build();
    }
}
