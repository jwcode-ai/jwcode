package com.jwcode.cli.commands;

import com.jwcode.core.service.GitService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;

import java.util.concurrent.TimeUnit;

/**
 * CommitCommand - /commit 命令
 * 
 * 功能说明：
 * 提交代码到 Git。支持快速提交和带描述的提交。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/commit", description = "提交代码到 Git")
public class CommitCommand implements Runnable {
    
    @Parameters(index = "0", description = "提交消息", arity = "0..1")
    private String message;
    
    @Option(names = {"-a", "--all"}, description = "自动添加所有修改的文件", defaultValue = "true")
    private boolean all;
    
    @Option(names = {"-m", "--message"}, description = "提交消息（替代位置参数）")
    private String messageOpt;
    
    @Option(names = {"-s", "--status"}, description = "提交前显示状态")
    private boolean showStatus;
    
    private final GitService gitService;
    
    public CommitCommand() {
        this.gitService = new GitService();
    }
    
    @Override
    public void run() {
        String commitMessage = messageOpt != null ? messageOpt : message;
        
        if (commitMessage == null) {
            System.out.println("错误：需要提供提交消息");
            System.out.println("用法：/commit <message> 或 /commit -m <message>");
            return;
        }
        
        if (showStatus) {
            showGitStatus();
        }
        
        try {
            System.out.println("正在提交代码...");
            
            // 添加文件
            if (all) {
                gitService.addAll().get(30, TimeUnit.SECONDS);
            }
            
            // 提交
            var result = gitService.commit(commitMessage, all).get(30, TimeUnit.SECONDS);
            
            if (result.success) {
                System.out.println("提交成功：" + commitMessage);
            } else {
                System.out.println("提交失败：" + result.error);
            }
            
        } catch (Exception e) {
            System.out.println("提交出错：" + e.getMessage());
        }
    }
    
    private void showGitStatus() {
        try {
            var status = gitService.getStatus().get(10, TimeUnit.SECONDS);
            System.out.println("Git 状态：");
            System.out.println(status != null ? status : "无法获取状态");
        } catch (Exception e) {
            System.out.println("无法获取 Git 状态");
        }
    }
}