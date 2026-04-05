package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;

/**
 * WebCommand - 启动 Web UI
 * 
 * 用法: web [port]
 */
public class WebCommand implements Command {
    
    @Override
    public String getName() {
        return "web";
    }
    
    @Override
    public String getDescription() {
        return "启动 Web UI 界面";
    }
    
    @Override
    public String getUsage() {
        return "web [port]";
    }
    
    @Override
    public CommandResult execute(String args, CommandContext context) {
        try {
            int port = 8080;
            if (args != null && !args.trim().isEmpty()) {
                port = Integer.parseInt(args.trim());
            }
            
            // 检查 WebServer 类是否存在
            try {
                Class<?> webServerClass = Class.forName("com.jwcode.web.WebServer");
                Object server = webServerClass.getDeclaredConstructor(int.class).newInstance(port);
                
                System.out.println("\n═══════════════════════════════════════════════════");
                System.out.println("  🌐 正在启动 JwCode Web...");
                System.out.println("═══════════════════════════════════════════════════");
                
                webServerClass.getMethod("start").invoke(server);
                
                System.out.println("  访问地址: http://localhost:" + port);
                System.out.println("  按 Ctrl+C 停止服务器");
                System.out.println("═══════════════════════════════════════════════════\n");
                
                // 保持运行
                Thread.currentThread().join();
                
            } catch (ClassNotFoundException e) {
                return CommandResult.error("Web 模块未找到，请确保 jwcode-web 模块已编译");
            }
            
            return CommandResult.success("Web 服务器已停止");
            
        } catch (NumberFormatException e) {
            return CommandResult.error("无效的端口号: " + args);
        } catch (Exception e) {
            return CommandResult.error("启动失败: " + e.getMessage());
        }
    }
}
