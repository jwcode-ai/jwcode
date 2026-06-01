package com.jwcode.core.command;

import com.jwcode.core.session.Session;

/**
 * 代码审查命令 — 触发代码审查流程。
 */
public class ReviewCommand implements Command {

    @Override
    public String getName() { return "review"; }

    @Override
    public java.util.List<String> getAliases() {
        return java.util.List.of("pr");
    }

    @Override
    public String getDescription() { return "代码审查 — 检查当前变更的正确性、安全性和代码质量"; }

    @Override
    public String getUsage() { return "review [file|diff|all] [--level low|medium|high]"; }

    @Override
    public CommandResult execute(String[] args, Session session) {
        String target = args.length > 0 ? args[0] : "diff";
        String level = "medium";

        // 解析 --level 参数
        for (int i = 0; i < args.length - 1; i++) {
            if ("--level".equals(args[i])) {
                level = args[i + 1];
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("╔══════════════════════════════════════════════════════════════╗\n");
        sb.append("║                      代码审查请求                            ║\n");
        sb.append("╚══════════════════════════════════════════════════════════════╝\n\n");

        sb.append("审查配置:\n");
        sb.append("  范围: ").append(target).append("\n");
        sb.append("  强度: ").append(level).append("\n");
        sb.append("\n检查项:\n");
        sb.append("  ✓ 逻辑正确性     — 空指针、边界条件、异常处理\n");
        sb.append("  ✓ 安全性         — 注入漏洞、权限逃逸、敏感信息泄露\n");
        sb.append("  ✓ 性能           — 内存泄漏、不必要的对象创建、算法复杂度\n");
        sb.append("  ✓ 代码风格       — 命名规范、代码重复、设计模式滥用\n");
        sb.append("  ✓ 全面性         — 边界情况、错误处理、可测试性\n");

        if (session != null) {
            sb.append("\n提示: 接下来 AI 将基于以上维度审查代码变更。\n");
            sb.append("      使用 /review ").append(target).append(" --level ").append(level).append(" 触发审查模式\n");
        }

        return CommandResult.success(sb.toString());
    }
}
