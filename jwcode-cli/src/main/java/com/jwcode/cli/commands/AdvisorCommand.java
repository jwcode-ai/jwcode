package com.jwcode.cli.commands;

import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * AdvisorCommand - /advisor 命令
 * 
 * 功能说明：
 * 顾问模式，获取专家建议。可以针对特定领域或主题获取专业建议。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/advisor", description = "顾问模式，获取专家建议")
public class AdvisorCommand implements Runnable {
    
    @Option(names = {"-t", "--topic"}, description = "咨询主题")
    private String topic;
    
    @Option(names = {"-e", "--expert"}, description = "专家类型 (architect, security, performance, etc.)")
    private String expertType;
    
    @Option(names = {"-q", "--question"}, description = "具体问题")
    private String question;
    
    @Override
    public void run() {
        if (topic == null && question == null) {
            showAdvisorHelp();
            return;
        }
        
        System.out.println("=== 顾问模式 ===");
        System.out.println();
        
        if (expertType != null) {
            System.out.println("专家类型：" + expertType);
        }
        
        if (topic != null) {
            System.out.println("主题：" + topic);
        }
        
        if (question != null) {
            System.out.println("问题：" + question);
        }
        
        System.out.println();
        System.out.println("正在获取专家建议...");
        System.out.println("(顾问模式需要 AI 后端支持)");
    }
    
    private void showAdvisorHelp() {
        System.out.println("顾问模式 - 获取专家建议");
        System.out.println();
        System.out.println("用法:");
        System.out.println("  /advisor -t <topic> -q <question> [-e <expert>]");
        System.out.println();
        System.out.println("选项:");
        System.out.println("  -t, --topic <topic>     咨询主题");
        System.out.println("  -e, --expert <type>     专家类型 (architect, security, performance, devops)");
        System.out.println("  -q, --question <ques>   具体问题");
        System.out.println();
        System.out.println("示例:");
        System.out.println("  /advisor -t 微服务 -q 如何设计服务间的通信");
        System.out.println("  /advisor -e security -q 如何防止 SQL 注入攻击");
    }
}