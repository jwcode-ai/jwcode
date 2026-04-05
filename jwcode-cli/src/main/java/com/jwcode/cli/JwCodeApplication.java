package com.jwcode.cli;

import com.jwcode.cli.commands.*;
import com.jwcode.cli.log.CliLogger;
import com.jwcode.cli.log.LogConfigurator;
import com.jwcode.cli.log.ProgressIndicator;
import com.jwcode.core.query.QueryEngine;
import com.jwcode.core.service.ApiClient;
import com.jwcode.core.session.Session;
import com.jwcode.core.terminal.JLine3Terminal;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * JWCode 应用程序入口
 */
public class JwCodeApplication implements AutoCloseable {
    
    private final Map<String, Command> commands = new HashMap<>();
    private final CommandContext context;
    private final ConfigManager configManager;
    private QueryEngine queryEngine;
    private JLine3Terminal terminal;
    
    public JwCodeApplication() {
        this.context = new CommandContext();
        this.configManager = new ConfigManager();
        initializeQueryEngine();
        registerCommands();
        initializeTerminal();
    }
    
    private void initializeTerminal() {
        CliLogger logger = CliLogger.getInstance();
        try {
            this.terminal = new JLine3Terminal();
            logger.debug("Terminal initialized");
        } catch (Exception e) {
            logger.warn("JLine3 not available, using simple mode");
            this.terminal = null;
        }
    }
    
    @Override
    public void close() {
        if (terminal != null) {
            try {
                terminal.close();
            } catch (Exception e) {
                // 忽略关闭错误
            }
        }
    }
    
    private void initializeQueryEngine() {
        // 配置日志
        LogConfigurator.configureQuietMode();
        CliLogger logger = CliLogger.getInstance();
        
        // 创建 JWCode 核心会话
        Session session = new Session(UUID.randomUUID().toString(), System.getProperty("user.dir"));
        
        try {
            // 创建 API 客户端并配置
            ApiClient apiClient = new ApiClient();
            
            if (configManager.isConfigured()) {
                apiClient.setBaseUrl(configManager.getApiEndpoint());
                apiClient.setApiKey(configManager.getApiKey());
            }
            
            // 构建 QueryEngine
            this.queryEngine = QueryEngine.builder()
                    .session(session)
                    .model(configManager.getModel())
                    .debug(false)
                    .apiClient(apiClient)
                    .build();
            
            logger.debug("QueryEngine initialized");
            
        } catch (Exception e) {
            logger.error("初始化失败: " + e.getMessage());
            // 创建一个使用默认配置的 ApiClient 用于模拟响应
            ApiClient mockClient = new ApiClient("https://api.minimaxi.com/v1/chat/completions", "mock-key");
            this.queryEngine = QueryEngine.builder()
                    .session(session)
                    .model(configManager.getModel())
                    .debug(false)
                    .apiClient(mockClient)
                    .build();
        }
    }
    
    private void registerCommands() {
        // 注册所有已实现 Command 接口的命令
        registerCommand(new HelpCommand(commands));
        registerCommand(new ExitCommand());
        registerCommand(new ClearCommand());
        registerCommand(new ConfigCommand());
        registerCommand(new CopyCommand());
        registerCommand(new CostCommand());
        registerCommand(new DiffCommand());
        registerCommand(new DoctorCommand());
        registerCommand(new ExportCommand());
        registerCommand(new FilesCommand());
        registerCommand(new SummaryCommand());
        
        // 新增命令
        registerCommand(new PlanCommand());
        registerCommand(new TodoCommand());
        registerCommand(new ThemeCommand());
        registerCommand(new UsageCommand());
        registerCommand(new VersionCommand());
        registerCommand(new WhoamiCommand());
        registerCommand(new WebCommand());
        registerCommand(new CheckpointCommand());
        
        // Agent 和 Bridge 命令
        registerCommand(new AgentCmd());
        registerCommand(new BridgeCmd());
        
        // 技能和规划命令
        registerCommand(new SkillCmd());
        registerCommand(new PlanCmd());
        
        // 并行执行演示命令
        registerCommand(new ParallelCmd());
        
        // Kimi Code 高级功能
        registerCommand(new AdvancedCmd());
    }
    private void registerCommand(Command command) {
        commands.put(command.getName(), command);
    }
    
    public void run(String[] args) {
        printBanner();
        
        if (args.length == 0) {
            // 交互模式
            runInteractiveMode();
        } else {
            // 命令模式
            executeCommand(args);
        }
    }
    
    private void printBanner() {
        CliLogger logger = CliLogger.getInstance();
        
        System.out.println();
        System.out.println(CliLogger.CYAN + "  ╔═══════════════════════════════════════════════════════════╗");
        System.out.println("  ║  ⚡ JwCode" + CliLogger.RESET + " - AI Coding Assistant                    " + CliLogger.CYAN + "║");
        System.out.println("  ╚═══════════════════════════════════════════════════════════╝" + CliLogger.RESET);
        System.out.println();
        System.out.println("  版本: 1.0.0-SNAPSHOT  |  输入 'help' 查看命令  |  'exit' 退出");
        System.out.println();
        System.out.println();
    }
    
    private void runInteractiveMode() {
        // 优先使用 JLine3 终端
        if (terminal != null) {
            runInteractiveModeWithJLine3();
        } else {
            runInteractiveModeFallback();
        }
    }
    
    /**
     * 使用 JLine3 终端的交互模式（支持命令历史和自动补全）
     */
    private void runInteractiveModeWithJLine3() {
        terminal.println("提示: 输入自然语言与 AI 对话，或输入 'help' 查看命令。");
        terminal.println("Tip: 使用上下箭头键可以查看命令历史，Tab 键可以自动补全。");
        terminal.println();
        
        while (!context.isExitRequested()) {
            String input = terminal.readLine("jwcode> ");
            
            if (input == null) {
                // Ctrl+C 或 EOF
                terminal.println("再见！");
                break;
            }
            
            input = input.trim();
            if (input.isEmpty()) {
                continue;
            }
            
            // 添加到历史记录
            terminal.addToHistory(input);
            
            // 解析命令
            String[] parts = input.split("\\s+", 2);
            String commandName = parts[0];
            String args = parts.length > 1 ? parts[1] : "";
            
            Command command = commands.get(commandName);
            if (command != null) {
                try {
                    CommandResult result = command.execute(args, context);
                    if (result.getOutput() != null) {
                        terminal.println(result.getOutput());
                    }
                    if (result.getError() != null) {
                        terminal.printError("错误: " + result.getError());
                    }
                } catch (Exception e) {
                    terminal.printError("执行命令时出错: " + e.getMessage());
                }
            } else {
                // 不是命令，当作自然语言查询处理
                handleNaturalLanguageQueryWithTerminal(input);
            }
        }
    }
    
    /**
     * 使用 JLine3 终端处理自然语言查询（支持多轮工具调用循环）
     * 对标 JavaScript 项目的 query 循环：执行任务 -> 显示结果 -> 继续执行更多任务
     */
    private void handleNaturalLanguageQueryWithTerminal(String input) {
        terminal.print("? 正在思考...");
        
        try {
            // 像 JavaScript 项目一样，使用循环来处理多轮工具调用
            // JavaScript 项目中 query() 是一个 AsyncGenerator，会 yield 多个事件
            // 我们在这里模拟类似的循环行为
            int maxToolCycles = 100; // 最大工具调用循环次数，防止无限循环
            int toolCycles = 0;
            boolean hasMoreWork = true;
            
            while (hasMoreWork && toolCycles < maxToolCycles) {
                toolCycles++;
                
                CompletableFuture<com.jwcode.core.query.QueryResult> future = queryEngine.query(input);
                com.jwcode.core.query.QueryResult result = future.get();
                
                if (result.isSuccess()) {
                    com.jwcode.core.model.Message message = result.getMessage();
                    if (message != null) {
                        // 提取并显示内容
                        StringBuilder content = new StringBuilder();
                        for (com.jwcode.core.model.Message.ContentBlock block : message.getContent()) {
                            if (block instanceof com.jwcode.core.model.Message.TextContent) {
                                content.append(((com.jwcode.core.model.Message.TextContent) block).getText());
                            }
                        }
                        String contentStr = content.toString();
                        
                        if (!contentStr.isEmpty()) {
                            terminal.println("\n" + contentStr);
                            
                            // 检查是否需要更多输入
                            // 如果 AI 问问题或等待确认，我们需要获取用户输入
                            if (contentStr.contains("?") || 
                                contentStr.contains("是否") ||
                                contentStr.contains("请确认") ||
                                contentStr.contains("请输入")) {
                                hasMoreWork = false;
                            } else {
                                // 默认认为任务完成，等待用户下一轮输入
                                hasMoreWork = false;
                            }
                        } else {
                            // 内容为空，说明上一轮有工具调用，AI 正在处理结果
                            // 继续循环让 AI 完成
                            hasMoreWork = true;
                            input = ""; // 空输入表示继续当前对话
                        }
                    } else {
                        // 消息为空，检查是否有工具执行结果
                        java.util.List<com.jwcode.core.tool.ToolExecutionResult> toolResults = result.getToolResults();
                        if (toolResults != null && !toolResults.isEmpty()) {
                            terminal.println("\n[工具执行完成]");
                            for (com.jwcode.core.tool.ToolExecutionResult tr : toolResults) {
                                terminal.println("  - " + tr.getToolName() + ": " + 
                                    (tr.getResult() != null ? tr.getResult().toString() : "完成"));
                            }
                        }
                        // 继续等待用户下一轮输入
                        hasMoreWork = false;
                    }
                } else if (result.getStatus() == com.jwcode.core.query.QueryResult.Status.TOOL_EXECUTION) {
                    // 工具执行中，显示工具执行结果
                    java.util.List<com.jwcode.core.tool.ToolExecutionResult> toolResults = result.getToolResults();
                    if (toolResults != null && !toolResults.isEmpty()) {
                        terminal.println("\n[工具执行结果]");
                        for (com.jwcode.core.tool.ToolExecutionResult tr : toolResults) {
                            String resultStr = tr.getResult() != null ? tr.getResult().toString() : "完成";
                            terminal.println("  " + tr.getToolName() + ": " + resultStr);
                        }
                    }
                    // 继续循环，让 AI 处理工具结果
                    input = "";
                    hasMoreWork = true;
                } else {
                    String errorMsg = result.getErrorMessage();
                    terminal.printError("\n错误: " + (errorMsg != null ? errorMsg : "未知错误"));
                    hasMoreWork = false;
                }
            }
            
            if (toolCycles >= maxToolCycles) {
                terminal.printError("\n[警告] 达到最大工具调用次数限制 (" + maxToolCycles + ")，任务可能未完成。");
            }
            
        } catch (Exception e) {
            terminal.printError("\n执行错误: " + e.getMessage());
            e.printStackTrace(System.err);
        }
        
        terminal.println();
    }
    
    /**
     * 后备的交互模式（使用 Scanner）
     */
    private void runInteractiveModeFallback() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("提示: 输入自然语言与 AI 对话，或输入 'help' 查看命令。\n");
        
        while (!context.isExitRequested()) {
            System.out.print("jwcode> ");
            String input = scanner.nextLine().trim();
            
            if (input.isEmpty()) {
                continue;
            }
            
            String[] parts = input.split("\\s+", 2);
            String commandName = parts[0];
            String args = parts.length > 1 ? parts[1] : "";
            
            Command command = commands.get(commandName);
            if (command != null) {
                try {
                    CommandResult result = command.execute(args, context);
                    if (result.getOutput() != null) {
                        System.out.println(result.getOutput());
                    }
                    if (result.getError() != null) {
                        System.err.println("错误: " + result.getError());
                    }
                } catch (Exception e) {
                    System.err.println("执行命令时出错: " + e.getMessage());
                }
            } else {
                handleNaturalLanguageQuery(input);
            }
        }
        
        System.out.println("再见！");
    }
    
    /**
     * 处理自然语言查询（后备模式，支持多轮工具调用循环）
     * 对标 JavaScript 项目的 query 循环
     */
    private void handleNaturalLanguageQuery(String input) {
        System.out.print("? 正在思考...");
        
        try {
            // 像 JavaScript 项目一样，使用循环来处理多轮工具调用
            int maxToolCycles = 100; // 最大工具调用循环次数
            int toolCycles = 0;
            boolean hasMoreWork = true;
            
            while (hasMoreWork && toolCycles < maxToolCycles) {
                toolCycles++;
                
                CompletableFuture<com.jwcode.core.query.QueryResult> future = queryEngine.query(input);
                com.jwcode.core.query.QueryResult result = future.get();
                
                if (result.isSuccess()) {
                    com.jwcode.core.model.Message message = result.getMessage();
                    if (message != null) {
                        // 提取并显示内容
                        StringBuilder content = new StringBuilder();
                        for (com.jwcode.core.model.Message.ContentBlock block : message.getContent()) {
                            if (block instanceof com.jwcode.core.model.Message.TextContent) {
                                content.append(((com.jwcode.core.model.Message.TextContent) block).getText());
                            }
                        }
                        String contentStr = content.toString();
                        
                        if (!contentStr.isEmpty()) {
                            System.out.println("\n" + contentStr + "\n");
                            hasMoreWork = false;
                        } else {
                            // 内容为空，说明上一轮有工具调用，继续循环
                            hasMoreWork = true;
                            input = "";
                        }
                    } else {
                        // 消息为空
                        java.util.List<com.jwcode.core.tool.ToolExecutionResult> toolResults = result.getToolResults();
                        if (toolResults != null && !toolResults.isEmpty()) {
                            System.out.println("\n[工具执行完成]");
                            for (com.jwcode.core.tool.ToolExecutionResult tr : toolResults) {
                                System.out.println("  - " + tr.getToolName() + ": " + 
                                    (tr.getResult() != null ? tr.getResult().toString() : "完成"));
                            }
                        }
                        hasMoreWork = false;
                    }
                } else if (result.getStatus() == com.jwcode.core.query.QueryResult.Status.TOOL_EXECUTION) {
                    // 工具执行中
                    java.util.List<com.jwcode.core.tool.ToolExecutionResult> toolResults = result.getToolResults();
                    if (toolResults != null && !toolResults.isEmpty()) {
                        System.out.println("\n[工具执行结果]");
                        for (com.jwcode.core.tool.ToolExecutionResult tr : toolResults) {
                            String resultStr = tr.getResult() != null ? tr.getResult().toString() : "完成";
                            System.out.println("  " + tr.getToolName() + ": " + resultStr);
                        }
                    }
                    // 继续循环
                    input = "";
                    hasMoreWork = true;
                } else {
                    String errorMsg = result.getErrorMessage();
                    System.err.println("\n错误: " + (errorMsg != null ? errorMsg : "未知错误") + "\n");
                    hasMoreWork = false;
                }
            }
            
            if (toolCycles >= maxToolCycles) {
                System.err.println("\n[警告] 达到最大工具调用次数限制 (" + maxToolCycles + ")，任务可能未完成。\n");
            }
            
        } catch (java.util.concurrent.ExecutionException e) {
            Throwable cause = e.getCause();
            String errorMsg = cause != null ? cause.getMessage() : null;
            System.err.println("\n执行错误: " + (errorMsg != null ? errorMsg : "未知错误"));
            if (cause != null) {
                System.err.println("异常类型: " + cause.getClass().getName());
                cause.printStackTrace(System.err);
            }
            System.err.println("请检查 API 配置和网络连接。\n");
        } catch (InterruptedException e) {
            System.err.println("\n请求被中断: " + e.getMessage());
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            String errorMsg = e.getMessage();
            System.err.println("\nAPI 调用失败: " + (errorMsg != null ? errorMsg : "未知错误"));
            System.err.println("异常类型: " + e.getClass().getName());
            e.printStackTrace(System.err);
            System.err.println("请检查 API 配置和网络连接。\n");
        }
    }
    
    private void executeCommand(String[] args) {
        String commandName = args[0];
        String commandArgs = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
        
        Command command = commands.get(commandName);
        if (command != null) {
            try {
                CommandResult result = command.execute(commandArgs, context);
                if (result.getOutput() != null) {
                    System.out.println(result.getOutput());
                }
                if (result.getError() != null) {
                    System.err.println("错误: " + result.getError());
                    System.exit(1);
                }
            } catch (Exception e) {
                System.err.println("执行命令时出错: " + e.getMessage());
                System.exit(1);
            }
        } else {
            System.err.println("未知命令: " + commandName);
            System.exit(1);
        }
    }
    
    public static void main(String[] args) {
        new JwCodeApplication().run(args);
    }
}
