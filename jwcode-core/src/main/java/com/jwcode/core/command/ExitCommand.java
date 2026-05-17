package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/**
 * 退出命令
 *
 * <p>注意：本命令不会直接调用 System.exit()，而是返回一个标记为 EXIT 的 CommandResult，
 * 由上层调用者（如 REPL 循环）负责执行实际的退出操作。这样可以避免在测试中触发 JVM 退出。</p>
 */
public class ExitCommand implements Command {

    @Override
    public String getName() {
        return "exit";
    }

    @Override
    public java.util.List<String> getAliases() {
        return java.util.List.of("quit", "q");
    }

    @Override
    public String getDescription() {
        return "退出程序";
    }

    @Override
    public String getUsage() {
        return "exit";
    }

    @Override
    public CommandResult execute(String[] args, Session session) {
        return CommandResult.exit("再见！");
    }
}
