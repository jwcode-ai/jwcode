package com.jwcode.cli;

import com.jwcode.cli.commands.*;
import com.jwcode.cli.log.CliLogger;
import com.jwcode.cli.log.LogConfigurator;
import com.jwcode.cli.log.ProgressIndicator;
import com.jwcode.core.config.JwcodeConfig;
import com.jwcode.core.config.SystemPromptLoader;
import com.jwcode.core.llm.*;
import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;
import com.jwcode.core.skill.SkillLoader;
import com.jwcode.core.skill.SkillRegistry;
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
    private LLMQueryEngine llmQueryEngine;
    private JLine3Terminal terminal;
    
    public JwCodeApplication() {
        this.context = new CommandContext();
        this.configManager = new ConfigManager();
        initializeLLMQueryEngine();
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
    
    private void initializeLLMQueryEngine() {
        LogConfigurator.configureQuietMode();
        CliLogger logger = CliLogger.getInstance();
        
        // 检查配置文件是否存在
        if (!configManager.isConfigured()) {
            printConfigError();
            System.exit(1);
        }
        
        String model = configManager.getModel();
        if (model == null || model.isEmpty()) {
            printModelError();
            System.exit(1);
        }
        
        try {
            // 加载 YAML 配置
            JwcodeConfig config = JwcodeConfig.load(configManager.getConfigPath());
            
            // 创建 LLM 工厂
            LLMFactory llmFactory = LLMFactory.fromConfig(config);
            
            // 创建会话
            Session session = new Session(UUID.randomUUID().toString(), System.getProperty("user.dir"));
            
            // 加载并添加系统提示词
            String systemPrompt = SystemPromptLoader.getSystemPrompt();
            if (systemPrompt != null && !systemPrompt.isEmpty()) {
                session.addMessage(Message.createSystemMessage(systemPrompt));
                logger.debug("System prompt loaded: " + SystemPromptLoader.getPromptInfo());
            }
            
            // 创建 LLM 查询引擎
            this.llmQueryEngine = llmFactory.createQueryEngine(session);
            
            // 获取工具信息
            int toolCount = llmQueryEngine.getToolExecutor().getEnabledTools().size();
            
            logger.debug("LLMQueryEngine initialized with model: " + model);
            System.out.println("✅ 已加载配置: " + (configManager.isUsingYamlConfig() ? "YAML" : "Properties"));
            System.out.println("📍 模型: " + model);
            System.out.println("🔗 端点: " + configManager.getApiEndpoint());
            
            Double temperature = configManager.getTemperature();
            if (temperature != null) {
                System.out.println("🌡️  Temperature: " + temperature);
            }
            
            System.out.println("🛠️  使用 LLM 架构 (OpenAI 兼容)");
            System.out.println("🔧 已加载工具: " + toolCount + " 个");
            
            // 显示前 5 个工具名称
            var tools = llmQueryEngine.getToolExecutor().getEnabledTools();
            if (!tools.isEmpty()) {
                System.out.print("   ");
                int showCount = Math.min(5, tools.size());
                for (int i = 0; i < showCount; i++) {
                    System.out.print(tools.get(i).getName());
                    if (i < showCount - 1) System.out.print(", ");
                }
                if (tools.size() > 5) {
                    System.out.print(" 等");
                }
                System.out.println();
            }
            
            // 加载并显示技能
            SkillRegistry skillRegistry = new SkillRegistry();
            String skillsDir = System.getProperty("user.home") + "\\.jwcode\\skills";
            SkillLoader skillLoader = new SkillLoader(skillRegistry, skillsDir);
            var loadedSkills = skillLoader.loadAll();
            
            int skillCount = skillRegistry.size();
            System.out.println("🎯 已加载技能: " + skillCount + " 个");
            if (skillCount > 0) {
                System.out.print("   ");
                var allSkills = skillRegistry.getAll();
                int showCount = Math.min(5, allSkills.size());
                for (int i = 0; i < showCount; i++) {
                    System.out.print(allSkills.get(i).getName());
                    if (i < showCount - 1) System.out.print(", ");
                }
                if (allSkills.size() > 5) {
                    System.out.print(" 等");
                }
                System.out.println();
            }
            
        } catch (Exception e) {
            logger.error("初始化失败: " + e.getMessage());
            System.err.println("错误: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
    
    private void printConfigError() {
        System.err.println("╔═══════════════════════════════════════════════════════════╗");
        System.err.println("║  ⚠️  未检测到配置文件                                      ║");
        System.err.println("╠═══════════════════════════════════════════════════════════╣");
        System.err.println("║  请在以下位置创建配置文件：                                ║");
        System.err.println("║    " + String.format("%-50s", configManager.getConfigPath()) + " ║");
        System.err.println("║                                                           ║");
        System.err.println("║  示例配置：                                               ║");
        System.err.println("║    default-provider: moonshot                            ║");
        System.err.println("║    providers:                                            ║");
        System.err.println("║      moonshot:                                           ║");
        System.err.println("║        base-url: https://api.moonshot.cn/v1              ║");
        System.err.println("║        api-keys:                                         ║");
        System.err.println("║          - sk-your-api-key                               ║");
        System.err.println("║        models:                                           ║");
        System.err.println("║          - id: kimi-k2.5                                 ║");
        System.err.println("║            temperature: 1                                ║");
        System.err.println("╚═══════════════════════════════════════════════════════════╝");
    }
    
    private void printModelError() {
        System.err.println("╔═══════════════════════════════════════════════════════════╗");
        System.err.println("║  ⚠️  未配置模型名称                                        ║");
        System.err.println("╠═══════════════════════════════════════════════════════════╣");
        System.err.println("║  请在配置文件中设置模型：                                  ║");
        System.err.println("║                                                           ║");
        System.err.println("║  YAML 配置 (~/.jwcode/config.yaml):                      ║");
        System.err.println("║    providers:                                            ║");
        System.err.println("║      moonshot:                                           ║");
        System.err.println("║        models:                                           ║");
        System.err.println("║          - id: kimi-k2.5                                 ║");
        System.err.println("║            name: kimi-k2.5                               ║");
        System.err.println("║                                                           ║");
        System.err.println("╚═══════════════════════════════════════════════════════════╝");
    }
    
    private void registerCommands() {
        // 注册基础命令
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
        registerCommand(new PlanCommand());
        registerCommand(new TodoCommand());
        registerCommand(new ThemeCommand());
        registerCommand(new VersionCommand());
        registerCommand(new CheckpointCommand());
        registerCommand(new UsageCommand());
        registerCommand(new TestModelCommand());
        registerCommand(new WebCommand());
        registerCommand(new WhoamiCommand());
        
        // 其他实现了 Command 接口的命令
        registerCommand(new AdvancedCmd());
        registerCommand(new AgentCmd());
        registerCommand(new BridgeCmd());
        registerCommand(new ParallelCmd());
        registerCommand(new PlanCmd());
        registerCommand(new SkillCmd());
    }
    
    private void registerCommand(Command command) {
        commands.put(command.getName(), command);
        // 同时注册别名
        for (String alias : command.getAliases()) {
            commands.put(alias, command);
        }
    }
    
    /**
     * 应用程序主入口
     */
    public void run(String[] args) {
        // 显示启动信息
        printBanner();
        
        // 如果有命令行参数，执行单次命令
        if (args.length > 0) {
            executeCommand(args);
            return;
        }
        
        // 否则进入交互模式
        runInteractiveMode();
    }
    
    private void printBanner() {
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
     * 使用 JLine3 终端的交互模式
     */
    private void runInteractiveModeWithJLine3() {
        terminal.println("提示: 输入自然语言与 AI 对话，或输入 'help' 查看命令。");
        terminal.println("Tip: 使用上下箭头键可以查看命令历史，Tab 键可以自动补全。");
        terminal.println("Tip: 多行输入请在行末使用 \\ 续行");
        terminal.println();
        
        while (!context.isExitRequested()) {
            String input = terminal.readMultiline("jwcode> ");
            
            if (input == null) {
                terminal.println("再见！");
                break;
            }
            
            input = input.trim();
            if (input.isEmpty()) {
                continue;
            }
            
            terminal.addToHistory(input);
            
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
                handleNaturalLanguageQuery(input);
            }
        }
    }
    
    /**
     * 处理自然语言查询（带思考过程和工具调用显示）
     */
    private void handleNaturalLanguageQuery(String input) {
        System.out.println("\n" + CliLogger.CYAN + "🤔 思考中..." + CliLogger.RESET);
        
        try {
            // 使用 LLMQueryEngine 执行查询
            Session session = llmQueryEngine.getSession();
            session.addMessage(Message.createUserMessage(input));
            
            // 开始对话循环（带思考过程显示）
            runConversationWithThinking(0);
            
        } catch (Exception e) {
            terminal.printError("执行错误: " + e.getMessage());
            e.printStackTrace(System.err);
        }
        
        terminal.println();
    }
    
    /**
     * 带思考过程的对话循环
     */
    private void runConversationWithThinking(int iteration) throws Exception {
        // 检查迭代限制
        LLMQueryEngine.EngineConfig config = llmQueryEngine.getConfig();
        if (config.getMaxIterations() > 0 && iteration >= config.getMaxIterations()) {
            System.out.println(CliLogger.YELLOW + "⚠️ 达到最大迭代次数限制" + CliLogger.RESET);
            return;
        }
        
        Session session = llmQueryEngine.getSession();
        
        // 转换会话消息到 LLM 格式
        List<LLMMessage> llmMessages = convertSessionMessages(session);
        
        // 获取可用工具
        List<LLMTool> tools = convertTools(llmQueryEngine.getToolExecutor().getEnabledTools());
        
        // 发送请求
        CompletableFuture<LLMResponse> future = tools.isEmpty() 
            ? llmQueryEngine.getLLMService().chat(llmMessages)
            : llmQueryEngine.getLLMService().chatWithTools(llmMessages, tools);
        
        LLMResponse response = future.get();
        
        if (response.hasError()) {
            System.out.println(CliLogger.RED + "❌ API 错误: " + response.getErrorMessage() + CliLogger.RESET);
            return;
        }
        
        // 显示思考过程（如果存在）
        String content = response.getContent();
        if (content != null && !content.isEmpty()) {
            // 检查是否有 <thinking> 标签
            if (content.contains("<thinking>") || content.contains("【思考】")) {
                String thinking = extractThinking(content);
                if (!thinking.isEmpty()) {
                    System.out.println(CliLogger.CYAN + "💭 思考过程:" + CliLogger.RESET);
                    System.out.println(CliLogger.DIM + thinking + CliLogger.RESET);
                    System.out.println();
                }
                // 移除思考部分，只保留回复
                content = removeThinking(content);
            }
        }
        
        // 创建助手消息
        Message assistantMessage;
        if (response.hasToolCalls()) {
            // 显示工具调用
            System.out.println(CliLogger.YELLOW + "🔧 工具调用:" + CliLogger.RESET);
            for (LLMMessage.ToolCall tc : response.getToolCalls()) {
                System.out.println("  • " + CliLogger.GREEN + tc.getFunction().getName() + CliLogger.RESET + 
                    ": " + tc.getFunction().getArguments());
            }
            System.out.println();
            
            assistantMessage = Message.createAssistantMessageWithToolCalls(
                content,
                convertToolCalls(response.getToolCalls())
            );
            session.addMessage(assistantMessage);
            
            // 执行工具调用
            executeToolCallsWithDisplay(response.getToolCalls());
            
            // 继续对话循环
            runConversationWithThinking(iteration + 1);
        } else {
            // 没有工具调用，直接显示回复
            assistantMessage = Message.createAssistantMessage(content);
            session.addMessage(assistantMessage);
            
            if (content != null && !content.isEmpty()) {
                System.out.println(CliLogger.GREEN + "💬 回复:" + CliLogger.RESET);
                System.out.println(content);
            }
        }
    }
    
    /**
     * 提取思考内容
     */
    private String extractThinking(String content) {
        // 提取 <thinking> 标签内容
        if (content.contains("<thinking>")) {
            int start = content.indexOf("<thinking>") + "<thinking>".length();
            int end = content.indexOf("</thinking>");
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        // 提取 【思考】 标记内容
        if (content.contains("【思考】")) {
            int start = content.indexOf("【思考】") + "【思考】".length();
            int end = content.indexOf("【/思考】");
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        return "";
    }
    
    /**
     * 移除思考部分
     */
    private String removeThinking(String content) {
        content = content.replaceAll("(?s)<thinking>.*?</thinking>", "");
        content = content.replaceAll("(?s)【思考】.*?【/思考】", "");
        return content.trim();
    }
    
    /**
     * 执行工具调用并显示结果
     */
    private void executeToolCallsWithDisplay(List<LLMMessage.ToolCall> toolCalls) throws Exception {
        Session session = llmQueryEngine.getSession();
        
        for (LLMMessage.ToolCall tc : toolCalls) {
            String toolName = tc.getFunction().getName();
            String args = tc.getFunction().getArguments();
            
            com.jwcode.core.tool.Tool<?, ?, ?> tool = findTool(toolName);
            if (tool == null) {
                System.out.println(CliLogger.RED + "  ❌ 工具未找到: " + toolName + CliLogger.RESET);
                continue;
            }
            
            System.out.println(CliLogger.YELLOW + "  ⏳ 执行 " + toolName + "..." + CliLogger.RESET);
            
            // 模拟工具执行（实际应该调用工具执行器）
            Thread.sleep(500); // 模拟执行时间
            String result = "工具执行完成: " + toolName;
            
            System.out.println(CliLogger.GREEN + "  ✅ 完成" + CliLogger.RESET);
            
            // 添加工具结果到会话
            session.addMessage(Message.createToolResultMessage(tc.getId(), toolName, result));
        }
    }
    
    /**
     * 查找工具
     */
    private com.jwcode.core.tool.Tool<?, ?, ?> findTool(String name) {
        for (com.jwcode.core.tool.Tool<?, ?, ?> tool : llmQueryEngine.getToolExecutor().getEnabledTools()) {
            if (tool.getName().equals(name)) {
                return tool;
            }
        }
        return null;
    }
    
    /**
     * 转换工具
     */
    private List<LLMTool> convertTools(List<com.jwcode.core.tool.Tool<?, ?, ?>> tools) {
        List<LLMTool> result = new ArrayList<>();
        for (com.jwcode.core.tool.Tool<?, ?, ?> tool : tools) {
            LLMTool llmTool = new LLMTool();
            llmTool.setType("function");
            
            LLMTool.Function func = new LLMTool.Function();
            func.setName(tool.getName());
            func.setDescription(tool.getDescription());
            // 转换 JsonNode 到 Map
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> params = mapper.convertValue(tool.getInputSchema(), Map.class);
                func.setParameters(params);
            } catch (Exception e) {
                func.setParameters(null);
            }
            
            llmTool.setFunction(func);
            result.add(llmTool);
        }
        return result;
    }
    
    /**
     * 转换会话消息到 LLM 格式
     * 限制消息数量，防止请求体过大
     */
    private List<LLMMessage> convertSessionMessages(Session session) {
        List<LLMMessage> result = new ArrayList<>();
        List<Message> messages = session.getMessages();
        
        // 限制消息数量，保留最近的消息（保留系统消息+最近19条）
        final int MAX_MESSAGES = 20;
        int startIndex = 0;
        
        // 始终保留第一条系统消息（如果有）
        boolean hasSystemMessage = !messages.isEmpty() && messages.get(0).getRole() == Message.Role.SYSTEM;
        if (hasSystemMessage && messages.size() > MAX_MESSAGES) {
            startIndex = messages.size() - (MAX_MESSAGES - 1); // 为系统消息留一个位置
        } else if (messages.size() > MAX_MESSAGES) {
            startIndex = messages.size() - MAX_MESSAGES;
        }
        
        // 添加系统消息（如果有且被裁剪了）
        if (hasSystemMessage && startIndex > 0) {
            Message sysMsg = messages.get(0);
            LLMMessage.Role role = convertRole(sysMsg.getRole());
            String content = sysMsg.getTextContent();
            if (role != null && content != null) {
                result.add(LLMMessage.builder().role(role).content(content).build());
            }
        }
        
        // 添加剩余消息
        for (int i = startIndex; i < messages.size(); i++) {
            Message msg = messages.get(i);
            LLMMessage.Role role = convertRole(msg.getRole());
            String content = msg.getTextContent();
            
            // 跳过无效消息
            if (role == null || content == null) {
                continue;
            }
            
            result.add(LLMMessage.builder().role(role).content(content).build());
        }
        
        return result;
    }
    
    /**
     * 转换角色
     */
    private LLMMessage.Role convertRole(Message.Role role) {
        return switch (role) {
            case SYSTEM -> LLMMessage.Role.SYSTEM;
            case USER -> LLMMessage.Role.USER;
            case ASSISTANT -> LLMMessage.Role.ASSISTANT;
            case TOOL -> LLMMessage.Role.TOOL;
        };
    }
    
    /**
     * 转换工具调用
     */
    private List<Message.ToolCallInfo> convertToolCalls(List<LLMMessage.ToolCall> toolCalls) {
        List<Message.ToolCallInfo> result = new ArrayList<>();
        for (LLMMessage.ToolCall tc : toolCalls) {
            Message.ToolCallInfo info = new Message.ToolCallInfo(
                tc.getId(),
                tc.getFunction().getName(),
                tc.getFunction().getArguments()
            );
            result.add(info);
        }
        return result;
    }
    
    /**
     * 从消息中提取文本内容
     */
    private String extractMessageContent(Message message) {
        if (message == null || message.getContent() == null) {
            return "";
        }
        
        StringBuilder sb = new StringBuilder();
        for (Message.ContentBlock block : message.getContent()) {
            if (block instanceof Message.TextContent) {
                sb.append(((Message.TextContent) block).getText());
            }
        }
        return sb.toString();
    }
    
    /**
     * 后备交互模式
     */
    private void runInteractiveModeFallback() {
        Scanner scanner = new Scanner(System.in);
        
        System.out.println("提示: 输入自然语言与 AI 对话，或输入 'help' 查看命令。");
        System.out.println("Tip: 多行输入请在行末使用 \\ 续行");
        System.out.println();
        
        while (!context.isExitRequested()) {
            System.out.print("jwcode> ");
            StringBuilder inputBuilder = new StringBuilder();
            String line = scanner.nextLine();
            inputBuilder.append(line);
            
            while (line.endsWith("\\")) {
                inputBuilder.deleteCharAt(inputBuilder.length() - 1);
                System.out.print("      │ ");
                line = scanner.nextLine();
                inputBuilder.append("\n").append(line);
            }
            
            String input = inputBuilder.toString().trim();
            
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
                handleNaturalLanguageQueryFallback(input);
            }
        }
        
        System.out.println("再见！");
    }
    
    /**
     * 处理自然语言查询（后备模式）
     */
    private void handleNaturalLanguageQueryFallback(String input) {
        System.out.print("? 正在思考...");
        
        try {
            CompletableFuture<LLMQueryEngine.QueryResult> future = llmQueryEngine.query(input);
            LLMQueryEngine.QueryResult result = future.get();
            
            if (result.isSuccess()) {
                Message message = result.getMessage();
                if (message != null) {
                    String content = extractMessageContent(message);
                    if (!content.isEmpty()) {
                        System.out.println("\n" + content + "\n");
                    } else {
                        System.out.println("\n(空响应)\n");
                    }
                } else {
                    System.out.println("\n(无消息返回)\n");
                }
            } else {
                System.err.println("\n错误: " + result.getErrorMessage() + "\n");
            }
        } catch (Exception e) {
            System.err.println("\n执行错误: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
    
    private void executeCommand(String[] args) {
        String commandName = args[0];
        String commandArgs = args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "";
        
        Command command = commands.get(commandName);
        if (command != null) {
            CommandResult result = command.execute(commandArgs, context);
            if (result.getOutput() != null) {
                System.out.println(result.getOutput());
            }
            if (result.getError() != null) {
                System.err.println("错误: " + result.getError());
                System.exit(1);
            }
        } else {
            // 将整个参数作为自然语言查询
            String query = String.join(" ", args);
            handleNaturalLanguageQueryFallback(query);
        }
    }
    
    // Getters
    public CommandContext getContext() { return context; }
    public ConfigManager getConfigManager() { return configManager; }
    public LLMQueryEngine getLLMQueryEngine() { return llmQueryEngine; }
    public JLine3Terminal getTerminal() { return terminal; }
    
    /**
     * 主入口
     */
    public static void main(String[] args) {
        try (JwCodeApplication app = new JwCodeApplication()) {
            app.run(args);
        }
    }
}
