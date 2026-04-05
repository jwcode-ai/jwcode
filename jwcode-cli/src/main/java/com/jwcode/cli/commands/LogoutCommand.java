package com.jwcode.cli.commands;

import com.jwcode.core.service.AuthService;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

/**
 * LogoutCommand - /logout 命令
 * 
 * 功能说明：
 * 用户登出，清除认证信息并重置状态。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
@Command(name = "/logout", description = "用户登出", 
         aliases = {"/logout", "/signout", "/exit-auth"})
public class LogoutCommand implements Runnable {
    
    private final AuthService authService;
    
    @Option(names = {"-f", "--force"}, description = "强制登出，不显示确认提示")
    private boolean force;
    
    @Option(names = {"-q", "--quiet"}, description = "静默模式，不显示输出")
    private boolean quiet;
    
    public LogoutCommand() {
        this.authService = new AuthService();
    }
    
    @Override
    public void run() {
        if (!authService.isAuthenticated()) {
            if (!quiet) {
                System.out.println("当前未登录，无需登出");
            }
            return;
        }
        
        if (!force) {
            System.out.println("确认要登出吗？当前会话将被清除。");
            System.out.print("确认 (y/N): ");
            
            try {
                int response = System.in.read();
                if (response != 'y' && response != 'Y') {
                    System.out.println("已取消登出");
                    return;
                }
            } catch (Exception e) {
                // 忽略读取错误
            }
        }
        
        // 执行登出
        authService.logout();
        
        if (!quiet) {
            System.out.println();
            System.out.println("✓ 已成功登出");
            System.out.println();
            System.out.println("提示：");
            System.out.println("  - 使用 /login 重新登录");
        }
    }
}