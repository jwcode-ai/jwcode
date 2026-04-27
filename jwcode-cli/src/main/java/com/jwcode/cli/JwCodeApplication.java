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
import com.jwcode.core.session.SessionManager;
import com.jwcode.core.skill.SkillLoader;
import com.jwcode.core.skill.SkillRegistry;
import com.jwcode.core.terminal.JLine3Terminal;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JWCode 应用程序入口
 */
public class JwCodeApplication implements AutoCloseable {
    
    private final Map<String, Command> commands = new HashMap<>();
    private final CommandContext context;
    private final ConfigManager configManager;
    private LLMQueryEngine llmQueryEngine;
    private JLine3Terminal terminal;
    
    public JwCodeApplication(String[] args) {
        this.context = new CommandContext();
        this.configManager = new ConfigManager();
        initializeSession(args);
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
    
    /**
     * 初始化会话 — 从 main() 下沉的会话选择/创建逻辑
     */
    private void initializeSession(String[] args) {
        // 解析启动参数
        boolean forceNew = false;
        for (String arg : args) {
            if ("--new".equals(arg) || "-n".equals(arg)) {
                forceNew = true;
                break;
            }
        }
        
        SessionManager sm = SessionManager.getInstance();
        
        // 检查是否有活动会话
        if (!forceNew) {
            Session activeSession = sm.getActiveSession();
            if (activeSession != null && activeSession.getMessageCount() > 0) {
                System.out.println();
                System.out.println(CliLogger.CYAN + "=== 选择会话 ===" + CliLogger.RESET);
                System.out.println();
                System.out.println("  1. 继续上次会话");
                System.out.println("     " + (activeSession.getTitle() != null ? activeSession.getTitle() : activeSession.getId().substring(0, 8)) 
                    + " (" + activeSession.getMessageCount() + " 条消息)");
                System.out.println("  2. 新建会话");
                System.out.println();
                System.out.print("请选择 [1]: ");
                
                java.util.Scanner scanner = new java.util.Scanner(System.in);
                String choice = scanner.nextLine().trim();
                if ("2".equals(choice)) {
                    forceNew = true;
                }
            }
        }
        
        // 强制新建或无活动会话时，创建新会话
        if (forceNew || sm.getActiveSession() == null) {
            Session newSession = sm.createSession(System.getProperty("user.dir"));
            newSession.setTitle("会话 " + newSession.getId().substring(0, 8));
            sm.setActiveSession(newSession.getId());
            System.out.println(CliLogger.GREEN + "✓ 已创建新会话: " + newSession.getTitle() + CliLogger.RESET);
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
            
            // 通过 SessionManager 获取或创建会话，确保持久化一致性
            SessionManager sessionManager = SessionManager.getInstance();
            Session session = sessionManager.getActiveSession();
            if (session == null) {
                session = sessionManager.createSession(System.getProperty("user.dir"));
                sessionManager.setActiveSession(session.getId());
                logger.info("Created and activated new session: " + session.getId());
            }
            
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
        registerCommand(new AnalyzeCommand());
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
        registerCommand(new NewCommand());

        // 其他实现了 Command 接口的命令
        registerCommand(new AdvancedCmd());
        registerCommand(new AgentCmd());
        registerCommand(new BridgeCmd());
        registerCommand(new ParallelCmd());
        registerCommand(new PlanCmd());
        registerCommand(new SkillCmd());

        // 批量注册历史遗留命令（死代码复活）
        registerLegacyCommands();
    }

    /**
     * 批量注册历史遗留命令 — 用反射实例化，失败则跳过。
     */
    private void registerLegacyCommands() {
        String[] legacyClasses = {
            "com.jwcode.cli.commands.AdvisorCommand",
            "com.jwcode.cli.commands.AgentCommand",
            "com.jwcode.cli.commands.AgentsCommand",
            "com.jwcode.cli.commands.BriefCommand",
            "com.jwcode.cli.commands.CommitCommand",
            "com.jwcode.cli.commands.CompactCommand",
            "com.jwcode.cli.commands.ContextCommand",
            "com.jwcode.cli.commands.EffortCommand",
            "com.jwcode.cli.commands.FastCommand",
            "com.jwcode.cli.commands.FeedbackCommand",
            "com.jwcode.cli.commands.HooksCommand",
            "com.jwcode.cli.commands.IdeCommand",
            "com.jwcode.cli.commands.InitCommand",
            "com.jwcode.cli.commands.LoginCommand",
            "com.jwcode.cli.commands.LogoutCommand",
            "com.jwcode.cli.commands.MemoryCommand",
            "com.jwcode.cli.commands.PermissionsCommand",
            "com.jwcode.cli.commands.RemoteCommand",
            "com.jwcode.cli.commands.ResumeCommand",
            "com.jwcode.cli.commands.RewindCommand",
            "com.jwcode.cli.commands.SessionCommand",
            "com.jwcode.cli.commands.ShareCommand",
            "com.jwcode.cli.commands.SkillsCommand",
            "com.jwcode.cli.commands.StatsCommand",
            "com.jwcode.cli.commands.TasksCommand",
            "com.jwcode.cli.commands.TeleportCommand",
            "com.jwcode.cli.commands.UpgradeCommand",
            "com.jwcode.cli.commands.VimCommand",
            "com.jwcode.cli.commands.VoiceCommand"
        };

        int registered = 0;
        for (String className : legacyClasses) {
            try {
                Class<?> clazz = Class.forName(className);
                if (Command.class.isAssignableFrom(clazz)) {
                    Command cmd = (Command) clazz.getDeclaredConstructor().newInstance();
                    registerCommand(cmd);
                    registered++;
                }
            } catch (Exception e) {
                CliLogger.getInstance().debug("跳过遗留命令 " + className + ": " + e.getMessage());
            }
        }
        CliLogger.getInstance().info("已复活 " + registered + "/" + legacyClasses.length + " 个遗留命令");
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
        // 获取当前时间和工作目录
        var now = java.time.LocalDateTime.now();
        var formatter = java.time.format.DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
        String currentTime = now.format(formatter);
        String workingDir = System.getProperty("user.dir");
        
        // 获取配置信息
        String configPath = configManager.getConfigPath();
        String configType = configManager.isUsingYamlConfig() ? "YAML" : "Properties";
        
        System.out.println();
        System.out.println(CliLogger.CYAN + "  ╔═══════════════════════════════════════════════════════════╗");
        System.out.println("  ║  ⚡ JwCode" + CliLogger.RESET + " - AI Coding Assistant                    " + CliLogger.CYAN + "║");
        System.out.println("  ╚═══════════════════════════════════════════════════════════╝" + CliLogger.RESET);
        System.out.println();
        System.out.println("  版本: 1.0.0-SNAPSHOT  |  输入 'help' 查看命令  |  'exit' 退出");
        System.out.println("  工作目录: " + CliLogger.GREEN + workingDir + CliLogger.RESET);
        System.out.println("  当前时间: " + CliLogger.GREEN + currentTime + CliLogger.RESET);
        System.out.println("  配置文件: " + CliLogger.YELLOW + configType + CliLogger.RESET + " (" + configPath + ")");
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
        System.out.print("\n" + CliLogger.CYAN + "🤔 思考中..." + CliLogger.RESET);
        System.out.flush();

        CliLogger logger = CliLogger.getInstance();
        logger.debug("=== handleNaturalLanguageQuery 开始 ===");
        logger.debug("用户输入: " + input);

        try {
            TerminalStepRenderer renderer = new TerminalStepRenderer();
            llmQueryEngine.setStepCallback(renderer);

            // 调用流式查询（内部已包含多轮循环）
            LLMQueryEngine.QueryResult result = llmQueryEngine.queryStream(
                input,
                null,  // contentConsumer — 由 StepCallback.onContentChunk 处理
                null,  // thinkingConsumer — 由 StepCallback.onThinkingChunk 处理
                null   // toolCallConsumer — 由 StepCallback.onToolCallChunk 处理
            ).join();

            llmQueryEngine.setStepCallback(null);

            if (result.isSuccess()) {
                String content = renderer.getContent();
                if (content != null && !content.trim().isEmpty()) {
                    System.out.println("\n" + CliLogger.GREEN + "💬 回复:" + CliLogger.RESET);
                    System.out.println(content);
                }
            } else {
                System.out.println(CliLogger.RED + "❌ " + result.getErrorMessage() + CliLogger.RESET);
            }

            logger.debug("=== handleNaturalLanguageQuery 结束 ===");
        } catch (Exception e) {
            logger.error("执行错误: " + e.getMessage());
            e.printStackTrace(System.err);
        }

        terminal.println();
    }
    
    // 结束标记
    private static final String FINISH_MARKER = "[FINISH]";
    // 空回复限制次数
    private static final int MAX_EMPTY_RESPONSES = 3;
    
    /**
     * 带思考过程的对话循环（已废弃，由 queryStream 替代）
     */
    @Deprecated
    private void runConversationWithThinking(int iteration, int emptyResponseCount) throws Exception {
        CliLogger logger = CliLogger.getInstance();
        
        // 检查迭代限制
        LLMQueryEngine.EngineConfig config = llmQueryEngine.getConfig();
        if (config.getMaxIterations() > 0 && iteration >= config.getMaxIterations()) {
            System.out.println(CliLogger.YELLOW + "⚠️ 达到最大迭代次数限制" + CliLogger.RESET);
            return;
        }
        
        Session session = llmQueryEngine.getSession();
        
        // 接近迭代限制时发出警告（达到 80% 时）
        int warningThreshold = (int)(config.getMaxIterations() * 0.8);
        if (iteration > 0 && iteration >= warningThreshold) {
            String warning = String.format(
                CliLogger.YELLOW + "⚠️ 【警告】迭代次数已达到 %d/%d (%.0f%%)，接近限制！" + CliLogger.RESET + "\n" +
                "请评估当前进度，如果任务无法完成，请添加 [FINISH] 标记结束对话。",
                iteration, config.getMaxIterations(), (iteration * 100.0 / config.getMaxIterations())
            );
            System.out.println(warning);
            session.addMessage(Message.createSystemMessage(warning));
        }
        
        // 转换会话消息到 LLM 格式
        List<LLMMessage> llmMessages = convertSessionMessages(session);
        
        // 获取可用工具
        List<LLMTool> tools = convertTools(llmQueryEngine.getToolExecutor().getEnabledTools());
        
        // 发送请求
        logger.info(">>> runConversationWithThinking 第 " + iteration + " 次迭代");
        logger.debug("发送 LLM 请求，消息数: " + llmMessages.size() + ", 工具数: " + tools.size());
        
        CompletableFuture<LLMResponse> future = tools.isEmpty() 
            ? llmQueryEngine.getLLMService().chat(llmMessages)
            : llmQueryEngine.getLLMService().chatWithTools(llmMessages, tools);
        
        logger.debug("等待 LLM 响应...");
        LLMResponse response = future.get();
        logger.debug("LLM 响应已收到");
        
        if (response.hasError()) {
            System.out.println(CliLogger.RED + "❌ API 错误: " + response.getErrorMessage() + CliLogger.RESET);
            logger.error("LLM API 错误: " + response.getErrorMessage());
            return;
        }
        
        // 获取内容并提取最终回复（<final> 标签内的内容）
        String content = response.getContent();
        if (content != null && !content.isEmpty()) {
            // 提取 <final> 标签内的内容作为正式回复
            content = extractFinalContent(content);
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
                convertToolCalls(response.getToolCalls()),
                response.getReasoningContent()
            );
            session.addMessage(assistantMessage);
            
            logger.info("准备执行 " + response.getToolCalls().size() + " 个工具调用");
            
            // 执行工具调用
            try {
                executeToolCallsWithDisplay(response.getToolCalls());
                logger.info("工具调用执行完成，准备继续对话循环...");
                
                // 继续对话循环（重置空回复计数，因为工具执行可能有有效输出）
                runConversationWithThinking(iteration + 1, 0);
            } catch (Exception e) {
                logger.error("执行工具调用时发生异常", e);
                System.out.println(CliLogger.RED + "❌ 执行工具时出错: " + e.getMessage() + CliLogger.RESET);
                e.printStackTrace();
                
                // 添加错误提示消息到会话，让 AI 知道工具执行失败
                session.addMessage(Message.createSystemMessage(
                    "[系统通知] 工具执行过程中发生异常: " + e.getMessage() + "，请尝试其他方法完成任务。"));
                
                // 继续对话循环，让 AI 处理错误并尝试恢复
                runConversationWithThinking(iteration + 1, 0);
            }
        } else {
            // 没有工具调用，检查是否有 finishReason
            if (response.getFinishReason() != null) {
                logger.info("检测到 finishReason: " + response.getFinishReason() + "，结束对话");
                assistantMessage = Message.createAssistantMessage(content, response.getReasoningContent());
                session.addMessage(assistantMessage);
                
                if (content != null && !content.isEmpty()) {
                    System.out.println("\r" + CliLogger.GREEN + "💬 回复:" + CliLogger.RESET);
                    System.out.println(content);
                }
                return;
            }
            
            // 检查回复内容是否包含结束标记
            if (content != null && content.contains(FINISH_MARKER)) {
                logger.info("检测到结束标记 " + FINISH_MARKER + "，结束对话");
                assistantMessage = Message.createAssistantMessage(content, response.getReasoningContent());
                session.addMessage(assistantMessage);
                
                // 显示回复内容（不包含结束标记）},{
                String displayContent = content.replace(FINISH_MARKER, "").trim();
                if (!displayContent.isEmpty()) {
                    System.out.println("\r" + CliLogger.GREEN + "💬 回复:" + CliLogger.RESET);
                    System.out.println(displayContent);
                }
                return;
            }
            
            // 检查是否为空回复
            if (content == null || content.trim().isEmpty()) {
                emptyResponseCount++;
                System.out.println(CliLogger.YELLOW + "⚠️ 空回复 (第 " + emptyResponseCount + "/" + MAX_EMPTY_RESPONSES + " 次)" + CliLogger.RESET);
                
                if (emptyResponseCount >= MAX_EMPTY_RESPONSES) {
                    System.out.println(CliLogger.YELLOW + "⚠️ 空回复次数已达上限，强制结束对话" + CliLogger.RESET);
                    return;
                }
                
                // 添加提示消息
                session.addMessage(Message.createSystemMessage(
                    "提示：如果任务已完成，请在回复末尾添加 [FINISH] 标记以结束对话。例如：\"任务已完成。\\n\\n[FINISH]\""));
                
                // 继续对话循环
                runConversationWithThinking(iteration + 1, emptyResponseCount);
            } else {
                // 有有效内容，显示回复
                assistantMessage = Message.createAssistantMessage(content, response.getReasoningContent());
                session.addMessage(assistantMessage);
                
                System.out.println("\r" + CliLogger.GREEN + "💬 回复:" + CliLogger.RESET);
                System.out.println(content);
                
                // 添加提示消息，提醒 AI 使用结束标记
                session.addMessage(Message.createSystemMessage(
                    "提示：如果任务已完成，请在回复末尾添加 [FINISH] 标记以结束对话。例如：\"任务已完成。\\n\\n[FINISH]\""));
                
                // 继续对话循环
                runConversationWithThinking(iteration + 1, 0);
            }
        }
    }
    
    /**
     * 提取最终回复内容（<final> 标签内的内容）
     * 
     * 如果 AI 遵循格式要求，返回 <final> 标签内的内容
     * 如果没有 <final> 标签，返回原始内容（兼容旧格式）
     */
    private String extractFinalContent(String content) {
        if (content == null || content.isEmpty()) {
            return content;
        }
        
        // 提取 <final> 标签内容
        if (content.contains("<final>")) {
            int start = content.indexOf("<final>") + "<final>".length();
            int end = content.indexOf("</final>");
            if (end > start) {
                return content.substring(start, end).trim();
            }
        }
        
        // 兼容旧格式：如果没有 <final> 标签，移除 <thinking> 后返回
        return removeThinking(content);
    }
    
    /**
     * 移除思考部分
     */
    private String removeThinking(String content) {
        if (content == null) {
            return "";
        }
        content = content.replaceAll("(?s)<thinking>.*?</thinking>", "");
        content = content.replaceAll("(?s)【思考】.*?【/思考】", "");
        return content.trim();
    }
    
    /**
     * 执行工具调用并显示结果（已废弃，由 queryStream 内部执行）
     */
    @Deprecated
    private void executeToolCallsWithDisplay(List<LLMMessage.ToolCall> toolCalls) throws Exception {
        CliLogger logger = CliLogger.getInstance();
        Session session = llmQueryEngine.getSession();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
        
        logger.info("开始执行 " + toolCalls.size() + " 个工具调用");
        
        for (LLMMessage.ToolCall tc : toolCalls) {
            String toolName = tc.getFunction().getName();
            String args = tc.getFunction().getArguments();
            
            logger.debug("工具调用 - 名称: " + toolName);
            logger.debug("工具调用 - 参数: " + args);
            
            com.jwcode.core.tool.Tool<?, ?, ?> tool = findTool(toolName);
            if (tool == null) {
                System.out.println(CliLogger.RED + "  ❌ 工具未找到: " + toolName + CliLogger.RESET);
                logger.error("工具未找到: " + toolName);
                // 必须添加错误结果，否则 assistant 的 tool_calls 会缺少对应的 tool 消息
                session.addMessage(Message.createToolResultMessage(
                    tc.getId(), toolName, "Error: Tool not found: " + toolName));
                continue;
            }
            
            System.out.println(CliLogger.YELLOW + "  ⏳ 执行 " + toolName + "..." + CliLogger.RESET);
            logger.info("开始执行工具: " + toolName);
            
            String result;
            
            // 特殊处理：AskUserQuestionTool 需要交互式输入
            if ("AskUserQuestion".equals(toolName)) {
                result = handleAskUserQuestion(tc, args, mapper, session);
                // 添加工具结果到会话
                session.addMessage(Message.createToolResultMessage(tc.getId(), toolName, result));
                continue;
            }
            
            try {
                com.fasterxml.jackson.databind.JsonNode argsNode = mapper.readTree(args);
                com.jwcode.core.tool.context.ToolExecutionContext toolContext =
                    new com.jwcode.core.tool.context.ToolExecutionContext(
                        session,
                        java.nio.file.Path.of(System.getProperty("user.dir")),
                        null
                    );
                
                logger.debug("提交工具执行到线程池...");
                java.util.concurrent.CompletableFuture<com.jwcode.core.tool.ToolExecutor.ToolExecutionResult> future =
                    llmQueryEngine.getToolExecutor().execute(toolName, argsNode, toolContext);
                
                logger.debug("等待工具执行结果...");
                com.jwcode.core.tool.ToolExecutor.ToolExecutionResult execResult = future.get();
                
                logger.debug("工具执行完成，检查结果...");
                
                if (execResult.isSuccess()) {
                    com.jwcode.core.tool.ToolResult<?> toolResult = execResult.getResult();
                    if (toolResult != null && toolResult.isSuccess()) {
                        Object data = toolResult.getData();
                        if (data != null) {
                            result = mapper.valueToTree(data).toString();
                            logger.debug("工具执行成功，结果长度: " + result.length());
                        } else {
                            result = "Success";
                            logger.debug("工具执行成功，无返回数据");
                        }
                    } else {
                        String error = toolResult != null ? toolResult.getContent() : "Tool execution failed";
                        result = "Error: " + error;
                        logger.warn("工具执行失败: " + error);
                    }
                } else {
                    result = "Error: " + execResult.getErrorMessage();
                    logger.error("工具执行错误: " + execResult.getErrorMessage());
                }
                
                if (execResult.isSuccess()) {
                    System.out.println(CliLogger.GREEN + "  ✅ 完成" + CliLogger.RESET);
                    logger.info("工具 " + toolName + " 执行成功");
                } else {
                    System.out.println(CliLogger.RED + "  ❌ 失败: " + execResult.getErrorMessage() + CliLogger.RESET);
                    logger.error("工具 " + toolName + " 执行失败");
                }
            } catch (Exception e) {
                result = "Error: " + e.getMessage();
                System.out.println(CliLogger.RED + "  ❌ 异常: " + e.getMessage() + CliLogger.RESET);
                logger.error("工具 " + toolName + " 抛出异常", e);
            }
            
            // 添加工具结果到会话（无论成功还是失败都要添加，确保 tool_calls 与 results 数量一致）
            logger.debug("添加工具结果到会话，toolCallId: " + tc.getId() + ", result: " + (result != null ? result.substring(0, Math.min(100, result.length())) : "null"));
            session.addMessage(Message.createToolResultMessage(tc.getId(), toolName, result));
        }
        
        logger.info("所有工具调用执行完成，当前会话消息数: " + session.getMessages().size());
    }
    
    /**
     * 处理 AskUserQuestionTool 的交互式输入
     */
    private String handleAskUserQuestion(LLMMessage.ToolCall tc, String args, 
            com.fasterxml.jackson.databind.ObjectMapper mapper, Session session) throws Exception {
        
        // 解析输入参数
        com.fasterxml.jackson.databind.JsonNode argsNode = mapper.readTree(args);
        String question = argsNode.has("question") ? argsNode.get("question").asText() : "请输入您的选择：";
        String questionType = argsNode.has("questionType") ? argsNode.get("questionType").asText() : "open_ended";
        
        // 解析选项
        List<String> options = new ArrayList<>();
        if (argsNode.has("options") && argsNode.get("options").isArray()) {
            for (com.fasterxml.jackson.databind.JsonNode opt : argsNode.get("options")) {
                options.add(opt.asText());
            }
        }
        
        String userAnswer;
        
        // 根据问题类型处理输入
        if ("selection".equals(questionType) && !options.isEmpty()) {
            // 多选/单选类型，显示选项
            System.out.println();
            System.out.println(CliLogger.CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + CliLogger.RESET);
            System.out.println(CliLogger.BOLD + question + CliLogger.RESET);
            System.out.println();
            for (int i = 0; i < options.size(); i++) {
                System.out.println(CliLogger.GREEN + "  " + (i + 1) + ". " + options.get(i) + CliLogger.RESET);
            }
            System.out.println(CliLogger.CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + CliLogger.RESET);
            System.out.println();
            
            // 读取用户输入
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("请输入选项编号 (1-" + options.size() + "): ");
                String input = scanner.nextLine().trim();
                
                try {
                    int choice = Integer.parseInt(input);
                    if (choice >= 1 && choice <= options.size()) {
                        userAnswer = options.get(choice - 1);
                        break;
                    } else {
                        System.out.println(CliLogger.YELLOW + "⚠️  请输入有效的编号 (1-" + options.size() + ")" + CliLogger.RESET);
                    }
                } catch (NumberFormatException e) {
                    System.out.println(CliLogger.YELLOW + "⚠️  请输入数字编号" + CliLogger.RESET);
                }
            }
        } else if ("yes_no".equals(questionType)) {
            // 是/否类型
            System.out.println();
            System.out.println(CliLogger.CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + CliLogger.RESET);
            System.out.println(CliLogger.BOLD + question + CliLogger.RESET);
            System.out.println(CliLogger.GREEN + "  1. 是" + CliLogger.RESET);
            System.out.println(CliLogger.RED + "  2. 否" + CliLogger.RESET);
            System.out.println(CliLogger.CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + CliLogger.RESET);
            System.out.println();
            
            Scanner scanner = new Scanner(System.in);
            while (true) {
                System.out.print("请选择 (1/2): ");
                String input = scanner.nextLine().trim();
                if ("1".equals(input) || "是".equals(input)) {
                    userAnswer = "是";
                    break;
                } else if ("2".equals(input) || "否".equals(input)) {
                    userAnswer = "否";
                    break;
                } else {
                    System.out.println(CliLogger.YELLOW + "⚠️  请输入 1 或 2" + CliLogger.RESET);
                }
            }
        } else {
            // 开放类型，直接读取文本输入
            System.out.println();
            System.out.println(CliLogger.CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + CliLogger.RESET);
            System.out.println(CliLogger.BOLD + question + CliLogger.RESET);
            System.out.println(CliLogger.CYAN + "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" + CliLogger.RESET);
            System.out.println();
            
            Scanner scanner = new Scanner(System.in);
            System.out.print("请输入: ");
            userAnswer = scanner.nextLine().trim();
        }
        
        System.out.println(CliLogger.GREEN + "  ✅ 收到您的回答: " + userAnswer + CliLogger.RESET);
        
        // 构建返回结果
        Map<String, Object> output = new HashMap<>();
        output.put("question", question);
        output.put("questionType", questionType);
        output.put("answer", userAnswer);
        output.put("status", "answered");
        output.put("message", "用户已回答: " + userAnswer);
        
        return mapper.writeValueAsString(output);
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
        
        for (Message msg : session.getMessages()) {
            LLMMessage.Role role = convertRole(msg.getRole());
            
            if (role == null) continue;
            
            // 处理 TOOL 消息 - 需要提取 toolUseId
            if (role == LLMMessage.Role.TOOL) {
                String toolCallId = extractToolCallId(msg);
                String content = extractToolResultContent(msg);
                
                if (toolCallId == null || toolCallId.isEmpty()) {
                    System.err.println("[WARN] TOOL message missing toolCallId, skipping");
                    continue;
                }
                
                result.add(LLMMessage.tool(toolCallId, content));
            } 
            // 处理带有 tool_calls 的 assistant 消息
            else if (role == LLMMessage.Role.ASSISTANT && msg.hasToolCalls()) {
                List<LLMMessage.ToolCall> toolCalls = convertToolCallInfoToLLM(msg.getToolCalls());
                result.add(LLMMessage.assistantWithTools(msg.getTextContent(), toolCalls, msg.getReasoningContent()));
            } 
            // 处理其他消息类型
            else {
                String content = msg.getTextContent();
                if (content == null) content = "";
                result.add(LLMMessage.builder()
                    .role(role)
                    .content(content)
                    .build());
            }
        }
        
        return result;
    }
    
    /**
     * 从 TOOL 消息中提取 toolCallId
     */
    private String extractToolCallId(Message msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return null;
        }
        
        for (Message.ContentBlock block : msg.getContent()) {
            if (block instanceof Message.ToolResultContent) {
                return ((Message.ToolResultContent) block).getToolUseId();
            }
        }
        return null;
    }
    
    /**
     * 从 TOOL 消息中提取结果内容
     */
    private String extractToolResultContent(Message msg) {
        if (msg.getContent() == null || msg.getContent().isEmpty()) {
            return msg.getTextContent() != null ? msg.getTextContent() : "";
        }
        
        for (Message.ContentBlock block : msg.getContent()) {
            if (block instanceof Message.ToolResultContent) {
                return ((Message.ToolResultContent) block).getFormattedContent();
            }
        }
        return msg.getTextContent() != null ? msg.getTextContent() : "";
    }
    
    /**
     * 转换 ToolCallInfo 到 LLM ToolCall
     */
    private List<LLMMessage.ToolCall> convertToolCallInfoToLLM(List<Message.ToolCallInfo> toolCalls) {
        List<LLMMessage.ToolCall> result = new ArrayList<>();
        for (Message.ToolCallInfo info : toolCalls) {
            result.add(LLMMessage.ToolCall.builder()
                .id(info.getId())
                .function(info.getName(), info.getArguments())
                .build());
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
            TerminalStepRenderer renderer = new TerminalStepRenderer();
            llmQueryEngine.setStepCallback(renderer);

            LLMQueryEngine.QueryResult result = llmQueryEngine.queryStream(
                input,
                null,
                null,
                null
            ).join();

            llmQueryEngine.setStepCallback(null);

            if (result.isSuccess()) {
                String content = renderer.getContent();
                if (content != null && !content.trim().isEmpty()) {
                    System.out.println("\n" + content + "\n");
                } else {
                    System.out.println("\n(空响应)\n");
                }
            } else {
                System.err.println("\n错误: " + result.getErrorMessage() + "\n");
            }
        } catch (Exception e) {
            System.err.println("\n执行错误: " + e.getMessage());
            e.printStackTrace(System.err);
        }
    }
    
    /**
     * 终端步骤渲染器 — 通过 StepCallback 实时打印 ANSI 颜色树形结构
     */
    private static class TerminalStepRenderer implements LLMQueryEngine.StepCallback {
        private final StringBuilder contentBuilder = new StringBuilder();
        private final Map<String, StringBuilder> toolCallAccumulators = new HashMap<>();
        private final Map<String, String> toolCallNames = new HashMap<>();
        private final Set<String> printedToolCalls = new HashSet<>();

        @Override
        public void onStepStart(String stepName, String description) {
            System.out.println(CliLogger.CYAN + "▶ " + stepName + CliLogger.RESET);
            if (description != null && !description.isEmpty()) {
                System.out.println(CliLogger.GRAY + "  " + description + CliLogger.RESET);
            }
        }

        @Override
        public void onStepThinking(String stepName, String thought) {
            System.out.println(CliLogger.BLUE + "  💭 " + thought + CliLogger.RESET);
        }

        @Override
        public void onStepAction(String stepName, String action) {
            System.out.println(CliLogger.YELLOW + "  ⚡ " + action + CliLogger.RESET);
        }

        @Override
        public void onToolCallChunk(LLMService.StreamToolCallEvent event) {
            String key = event.getId() != null ? event.getId() : String.valueOf(event.getIndex());
            toolCallAccumulators.computeIfAbsent(key, k -> new StringBuilder()).append(event.getArguments());
            toolCallNames.put(key, event.getName());

            if (event.isComplete() && !printedToolCalls.contains(key)) {
                printedToolCalls.add(key);
                String args = toolCallAccumulators.get(key).toString();
                String name = toolCallNames.getOrDefault(key, "Unknown");
                System.out.println(CliLogger.MAGENTA + "    🔧 " + name + CliLogger.RESET);
                System.out.println(CliLogger.GRAY + "      请求: " + args + CliLogger.RESET);
            }
        }

        @Override
        public void onToolResult(String toolName, String result) {
            String preview = result != null && result.length() > 300
                ? result.substring(0, 300) + "..." : result;
            System.out.println(CliLogger.GREEN + "      ✅ 结果: " + preview + CliLogger.RESET);
        }

        @Override
        public void onStepComplete(String stepName, String result) {
            System.out.println(CliLogger.GREEN + "  ✓ " + stepName + CliLogger.RESET);
            if ("LLM查询".equals(stepName)) {
                System.out.println();
            }
        }

        @Override
        public void onContentChunk(String chunk) {
            contentBuilder.append(chunk);
        }

        @Override
        public void onThinkingChunk(String chunk) {
            // 思考内容由 onStepThinking 展示，这里不累积
        }

        public String getContent() {
            return contentBuilder.toString();
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
        try (JwCodeApplication app = new JwCodeApplication(args)) {
            app.run(args);
        }
    }
}
