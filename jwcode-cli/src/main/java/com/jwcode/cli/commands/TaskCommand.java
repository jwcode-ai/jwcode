package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.core.task.TaskStore;

/**
 * TaskCommand - /task 命令
 * 
 * 启动 Lanterna 三列交互式任务浏览器 TUI。
 * 使用反射加载 jwcode-ui 模块的 TaskTui，避免编译期循环依赖。
 * 
 * 快捷键：
 *   ↑/↓  - 选择任务
 *   S    - 停止选中任务
 *   R    - 刷新列表
 *   A    - 显示全部
 *   O    - 仅运行中
 *   C    - 仅已完成
 *   F    - 仅失败
 *   Q/Esc- 退出
 */
public class TaskCommand implements Command {
    
    @Override
    public String getName() {
        return "task";
    }
    
    @Override
    public String getDescription() {
        return "启动任务 TUI 浏览器 (Lanterna)";
    }
    
    @Override
    public String getUsage() {
        return "task";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        try {
            // 反射加载 TaskTui，避免 jwcode-cli 编译期依赖 jwcode-ui
            Class<?> tuiClass = Class.forName("com.jwcode.ui.TaskTui");
            Object tui = tuiClass
                .getDeclaredConstructor(TaskStore.class)
                .newInstance(TaskStore.getInstance());
            tuiClass.getMethod("start").invoke(tui);
            return CommandResult.success("Task TUI 已退出");
        } catch (ClassNotFoundException e) {
            return CommandResult.error("TaskTui 未找到。请确保 jwcode-ui 模块已在 classpath 中。");
        } catch (Exception e) {
            return CommandResult.error("无法启动 Task TUI: " + e.getMessage());
        }
    }
}
