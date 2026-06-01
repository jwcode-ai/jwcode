package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/**
 * 诊断命令 — 系统健康检查和诊断信息。
 */
public class DoctorCommand implements Command {

    @Override
    public String getName() { return "doctor"; }

    @Override
    public String getDescription() { return "运行系统诊断，检查配置和连接状态"; }

    @Override
    public String getUsage() { return "doctor [check]"; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║                     JWCode 系统诊断                          ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");

        // Java 环境
        sb.append("【Java 环境】\n");
        sb.append("  Java 版本:   ").append(System.getProperty("java.version")).append("\n");
        sb.append("  Java 厂商:   ").append(System.getProperty("java.vendor")).append("\n");
        sb.append("  Java 目录:   ").append(System.getProperty("java.home")).append("\n");
        sb.append("  VM 名称:     ").append(System.getProperty("java.vm.name")).append("\n");
        sb.append("  可用 CPU:    ").append(Runtime.getRuntime().availableProcessors()).append("\n");
        sb.append("  最大内存:    ").append(Runtime.getRuntime().maxMemory() / 1024 / 1024).append(" MB\n");
        sb.append("  已用内存:    ").append((Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / 1024 / 1024).append(" MB\n\n");

        // 操作系统
        sb.append("【操作系统】\n");
        sb.append("  名称:        ").append(System.getProperty("os.name")).append("\n");
        sb.append("  版本:        ").append(System.getProperty("os.version")).append("\n");
        sb.append("  架构:        ").append(System.getProperty("os.arch")).append("\n");
        sb.append("  用户目录:    ").append(System.getProperty("user.home")).append("\n");
        sb.append("  工作目录:    ").append(System.getProperty("user.dir")).append("\n\n");

        // 会话信息
        sb.append("【会话信息】\n");
        if (session != null) {
            sb.append("  会话 ID:     ").append(session.getId()).append("\n");
            sb.append("  消息数:      ").append(session.getMessageCount()).append("\n");
            sb.append("  模型:        ").append(session.getModel() != null ? session.getModel() : "未设置").append("\n");
            sb.append("  工作目录:    ").append(session.getWorkingDirectory()).append("\n");
            sb.append("  压缩次数:    ").append(session.getCompactCount()).append("\n");
            sb.append("  创建时间:    ").append(session.getCreatedAt()).append("\n");
        } else {
            sb.append("  (无活动会话)\n");
        }

        // Docker 检查
        sb.append("\n【容器环境】\n");
        sb.append("  Docker:      ").append(checkDocker()).append("\n");

        // 网络检查
        sb.append("\n【网络连接】\n");
        sb.append("  后端状态:    ").append(checkBackend()).append("\n");

        return CommandResult.success(sb.toString());
    }

    private String checkDocker() {
        try {
            Process p = new ProcessBuilder("docker", "info").start();
            int rc = p.waitFor();
            return rc == 0 ? "可用" : "不可用 (exit=" + rc + ")";
        } catch (Exception e) {
            return "不可用 (" + e.getMessage() + ")";
        }
    }

    private String checkBackend() {
        try {
            java.net.Socket s = new java.net.Socket("127.0.0.1", 8080);
            s.close();
            return "运行中 (port 8080)";
        } catch (Exception e) {
            return "未连接";
        }
    }
}
