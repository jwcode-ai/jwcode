package com.jwcode.cli.commands;

import com.jwcode.cli.Command;
import com.jwcode.cli.CommandContext;
import com.jwcode.cli.CommandResult;
import com.jwcode.cli.log.CliLogger;

import java.io.StringWriter;
import java.io.PrintWriter;

/**
 * WebCommand - 启动 Web UI
 * 
 * 用法: web [port]
 */
public class WebCommand implements Command {
    
    private static final CliLogger logger = CliLogger.getInstance();
    
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
            Class<?> webServerClass;
            try {
                webServerClass = Class.forName("com.jwcode.web.WebServer");
            } catch (ClassNotFoundException e) {
                System.err.println("错误: Web 模块未找到，请确保 jwcode-web 模块已编译");
                logger.error("Web 模块未找到", e);
                return CommandResult.error("Web 模块未找到，请确保 jwcode-web 模块已编译: " + e.getMessage());
            }
            
            System.out.println("\n═══════════════════════════════════════════════════");
            System.out.println("  🌐 正在启动 JwCode Web...");
            System.out.println("═══════════════════════════════════════════════════");
            
            // 创建并启动服务器
            try {
                // 创建服务器实例
                Object server = webServerClass.getDeclaredConstructor(int.class).newInstance(port);
                
                // 调用 start 方法
                java.lang.reflect.Method startMethod = webServerClass.getMethod("start");
                startMethod.invoke(server);
                
                System.out.println("  访问地址: http://localhost:" + port);
                System.out.println("  按 Ctrl+C 停止服务器");
                System.out.println("═══════════════════════════════════════════════════\n");
                
                // 保持运行
                Thread.currentThread().join();
                
            } catch (NoSuchMethodException e) {
                System.err.println("错误: WebServer 构造函数不匹配");
                logger.error("WebServer 构造函数不匹配", e);
                return CommandResult.error("WebServer 构造函数不匹配: " + e.getMessage());
            }
            
            return CommandResult.success("Web 服务器已停止");
            
        } catch (NumberFormatException e) {
            return CommandResult.error("无效的端口号: " + args);
        } catch (Exception e) {
            // 获取详细的错误信息
            String errorMsg = e.getMessage();
            String detailedError;
            
            if (errorMsg == null || errorMsg.isEmpty()) {
                // 如果没有消息，显示完整的堆栈跟踪
                StringWriter sw = new StringWriter();
                e.printStackTrace(new PrintWriter(sw));
                detailedError = "启动失败 (无详细信息):\n" + sw.toString();
            } else {
                // 显示异常类型和消息
                detailedError = e.getClass().getSimpleName() + ": " + errorMsg;
                
                // 如果有根本原因，也显示
                Throwable cause = e.getCause();
                if (cause != null && cause.getMessage() != null) {
                    detailedError += "\n原因: " + cause.getClass().getSimpleName() + ": " + cause.getMessage();
                }
            }
            
            System.err.println("错误: " + detailedError);
            logger.error("Web 启动失败", e);
            
            // 打印堆栈跟踪以便调试
            e.printStackTrace();
            
            return CommandResult.error("启动失败: " + errorMsg);
        }
    }
}
