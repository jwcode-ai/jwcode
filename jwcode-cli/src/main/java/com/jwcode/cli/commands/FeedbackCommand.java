package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Scanner;

/**
 * FeedbackCommand - /feedback 命令
 * 
 * 功能说明：
 * 提交用户反馈，支持 bug 报告、功能建议等。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/feedback", description = "提交反馈", 
         aliases = {"/fb", "/report"})
public class FeedbackCommand implements Runnable {
    
    private static final String FEEDBACK_ENDPOINT = "https://api.github.com/repos/jwcode/jwcode/issues";
    
    @Parameters(index = "0", description = "反馈类型：bug, feature, other", 
                defaultValue = "other")
    private String type;
    
    @Option(names = {"-t", "--title"}, description = "反馈标题")
    private String title;
    
    @Option(names = {"-d", "--description"}, description = "反馈详细描述")
    private String description;
    
    @Option(names = {"-c", "--contact"}, description = "联系方式（可选）")
    private String contact;
    
    @Option(names = {"-a", "--anonymous"}, description = "匿名提交")
    private boolean anonymous;
    
    @Override
    public void run() {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║         JWCode 反馈提交                ║");
        System.out.println("╚════════════════════════════════════════╝");
        System.out.println();
        
        String feedbackTitle = title;
        String feedbackDescription = description;
        String feedbackContact = contact;
        
        // 交互式输入
        if (feedbackTitle == null) {
            System.out.println("请输入反馈标题:");
            System.out.print("> ");
            try (Scanner scanner = new Scanner(System.in)) {
                if (scanner.hasNextLine()) {
                    feedbackTitle = scanner.nextLine().trim();
                }
            }
        }
        
        if (feedbackDescription == null) {
            System.out.println("请输入详细描述:");
            System.out.print("> ");
            try (Scanner scanner = new Scanner(System.in)) {
                if (scanner.hasNextLine()) {
                    feedbackDescription = scanner.nextLine().trim();
                }
            }
        }
        
        if (!anonymous && feedbackContact == null) {
            System.out.println("请输入联系方式（可选，留空跳过）:");
            System.out.print("> ");
            try (Scanner scanner = new Scanner(System.in)) {
                if (scanner.hasNextLine()) {
                    feedbackContact = scanner.nextLine().trim();
                }
            }
        }
        
        // 验证输入
        if (feedbackTitle == null || feedbackTitle.isEmpty()) {
            System.out.println("❌ 标题不能为空");
            return;
        }
        
        if (feedbackDescription == null || feedbackDescription.isEmpty()) {
            System.out.println("❌ 描述不能为空");
            return;
        }
        
        // 构建反馈内容
        String body = buildFeedbackBody(feedbackDescription, feedbackContact, type);
        
        // 提交反馈
        submitFeedback(feedbackTitle, body, type);
    }
    
    /**
     * 构建反馈内容
     */
    private String buildFeedbackBody(String description, String contact, String type) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 反馈类型\n").append(getTypeName(type)).append("\n\n");
        sb.append("## 详细描述\n").append(description).append("\n\n");
        
        if (contact != null && !contact.isEmpty()) {
            sb.append("## 联系方式\n").append(contact).append("\n\n");
        }
        
        sb.append("---\n");
        sb.append("**系统信息**:\n");
        sb.append("- Java 版本：").append(System.getProperty("java.version")).append("\n");
        sb.append("- 操作系统：").append(System.getProperty("os.name")).append("\n");
        sb.append("- JWCode 版本：1.0.0\n");
        
        return sb.toString();
    }
    
    /**
     * 获取类型名称
     */
    private String getTypeName(String type) {
        switch (type.toLowerCase()) {
            case "bug": return "🐛 Bug 报告";
            case "feature": return "✨ 功能建议";
            default: return "📝 其他反馈";
        }
    }
    
    /**
     * 提交反馈到 GitHub
     */
    private void submitFeedback(String title, String body, String type) {
        System.out.println();
        System.out.println("正在提交反馈...");
        
        // 确定标签
        String[] labels;
        switch (type.toLowerCase()) {
            case "bug":
                labels = new String[]{"bug"};
                break;
            case "feature":
                labels = new String[]{"enhancement"};
                break;
            default:
                labels = new String[]{"question"};
        }
        
        // 构建 JSON 请求体
        String jsonBody = String.format(
            "{\"title\":\"%s\",\"body\":\"%s\",\"labels\":[\"%s\"]}",
            escapeJson(title),
            escapeJson(body),
            labels[0]
        );
        
        try {
            // 本地保存反馈（模拟提交）
            saveFeedbackLocally(title, body, type);
            
            System.out.println();
            System.out.println("✓ 反馈已保存");
            System.out.println();
            System.out.println("提示:");
            System.out.println("  您也可以直接在 GitHub 提交 Issue:");
            System.out.println("  https://github.com/jwcode/jwcode/issues");
            
        } catch (Exception e) {
            System.out.println("❌ 提交失败：" + e.getMessage());
        }
    }
    
    /**
     * 本地保存反馈
     */
    private void saveFeedbackLocally(String title, String body, String type) throws IOException {
        String userHome = System.getProperty("user.home");
        String feedbackDir = userHome + "/.jwcode/feedback";
        Files.createDirectories(Paths.get(feedbackDir));
        
        String filename = feedbackDir + "/feedback_" + System.currentTimeMillis() + ".md";
        String content = "# " + title + "\n\n" + body;
        
        Files.writeString(Paths.get(filename), content);
    }
    
    /**
     * 转义 JSON 字符串
     */
    private String escapeJson(String str) {
        if (str == null) return "";
        return str.replace("\\", "\\\\")
                  .replace("\"", "\\\"")
                  .replace("\n", "\\n")
                  .replace("\r", "\\r")
                  .replace("\t", "\\t");
    }
}