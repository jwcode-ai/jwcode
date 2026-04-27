package com.jwcode.cli.commands;

import com.jwcode.cli.log.CliLogger;
import com.jwcode.core.model.Message;
import com.jwcode.core.session.ContextCompressor;
import com.jwcode.core.session.Session;
import com.jwcode.core.session.SessionManager;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.List;

/**
 * CompactCommand - /compact 命令
 *
 * 功能说明：
 * 压缩会话历史，减少上下文占用。
 */
@Command(name = "/compact", description = "压缩会话历史")
public class CompactCommand implements Runnable {

    @Option(names = {"-f", "--force"}, description = "强制压缩，不确认")
    private boolean force;

    @Option(names = {"-l", "--level"}, description = "压缩级别 (1-3, 默认：2)", defaultValue = "2")
    private int level;

    @Override
    public void run() {
        System.out.println(CliLogger.CYAN + "=== 压缩会话历史 ===" + CliLogger.RESET);

        SessionManager sm = SessionManager.getInstance();
        Session session = sm.getActiveSession();
        if (session == null) {
            System.out.println(CliLogger.RED + "错误：当前没有活动会话" + CliLogger.RESET);
            return;
        }

        List<Message> original = session.getMessages();
        int originalCount = original.size();
        int originalTokens = ContextCompressor.estimateTokens(original);

        if (originalCount <= 4) {
            System.out.println("会话消息较少（" + originalCount + " 条），无需压缩。");
            return;
        }

        if (!force) {
            System.out.println("此操作将压缩当前会话历史，部分历史消息将被摘要替代。");
            System.out.println("当前消息数：" + originalCount + "，估计 Token：" + originalTokens);
            System.out.println();
        }

        System.out.println("正在压缩会话...");

        // 执行压缩
        List<Message> compressed = ContextCompressor.compress(original, session);
        int compressedCount = compressed.size();
        int compressedTokens = ContextCompressor.estimateTokens(compressed);

        // 写回会话
        session.clearMessages();
        compressed.forEach(session::addMessage);
        session.markCompacted();

        System.out.println(CliLogger.GREEN + "压缩完成！" + CliLogger.RESET);
        System.out.println("  TokenBudget 将在下次查询时自动重置。");
        System.out.println("  消息数: " + originalCount + " -> " + compressedCount
            + " (节省 " + (originalCount - compressedCount) + " 条)");
        System.out.println("  Token:  " + originalTokens + " -> " + compressedTokens
            + " (节省 " + (originalTokens - compressedTokens) + ")");
    }
}
