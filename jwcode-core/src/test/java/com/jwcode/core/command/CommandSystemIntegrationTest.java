package com.jwcode.core.command;

import com.jwcode.core.session.Session;
import com.jwcode.core.session.SessionManager;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 命令系统集成测试
 *
 * <p>测试命令系统的核心组件：Command 接口实现、CommandRegistry 注册、
 * CommandExecutor 执行、各类具体命令等。</p>
 */
@DisplayName("命令系统集成测试")
public class CommandSystemIntegrationTest {

    private CommandRegistry registry;
    private CommandExecutor executor;
    private SessionManager sessionManager;
    private Session session;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        registry = new CommandRegistry();
        executor = new CommandExecutor(registry);
        sessionManager = new SessionManager(tempDir);
        session = sessionManager.createSession(tempDir.toString());
    }

    @Test
    @DisplayName("注册自定义命令")
    void testRegisterCommand() {
        Command testCmd = new TestCommand("test-cmd", "A test command");
        registry.register(testCmd);

        Command retrieved = registry.getCommand("test-cmd");
        assertNotNull(retrieved, "已注册的命令应能通过名称获取");
        assertEquals("test-cmd", retrieved.getName());
    }

    @Test
    @DisplayName("注册多个命令")
    void testRegisterAllCommands() {
        List<Command> commands = List.of(
            new TestCommand("cmd1", "Command 1"),
            new TestCommand("cmd2", "Command 2")
        );
        registry.registerAll(commands);

        assertEquals(2, registry.getCommandCount());
        assertNotNull(registry.getCommand("cmd1"));
        assertNotNull(registry.getCommand("cmd2"));
    }

    @Test
    @DisplayName("获取所有已注册命令")
    void testGetAllCommands() {
        registry.register(new TestCommand("cmd1", "Command 1"));
        registry.register(new TestCommand("cmd2", "Command 2"));

        var all = registry.getAllCommands();
        assertEquals(2, all.size());
    }

    @Test
    @DisplayName("检查命令是否存在")
    void testHasCommand() {
        registry.register(new TestCommand("exists", "test"));
        assertTrue(registry.hasCommand("exists"));
        assertFalse(registry.hasCommand("nonexistent"));
    }

    @Test
    @DisplayName("匹配前缀的命令")
    void testGetMatchingCommands() {
        registry.register(new TestCommand("help", "Show help"));
        registry.register(new TestCommand("history", "Show history"));
        registry.register(new TestCommand("config", "Manage config"));

        // history 以 "hi" 开头，所以 "he" 应匹配 help 和 "hello"（如果有的话）
        List<Command> matches = registry.getMatchingCommands("he");
        assertEquals(1, matches.size(), "仅 help 以 he 开头");
        assertEquals("help", matches.get(0).getName());

        // "h" 应匹配 help 和 history
        matches = registry.getMatchingCommands("h");
        assertEquals(2, matches.size(), "h 应匹配 help 和 history");
    }

    @Test
    @DisplayName("CommandExecutor 判断命令输入")
    void testExecutorIsCommand() {
        executor.getRegistry().register(new TestCommand("help", "Help command"));

        assertTrue(executor.isCommand("/help"));
        assertFalse(executor.isCommand("not a command"));
    }

    @Test
    @DisplayName("CommandExecutor 执行命令")
    void testExecutorExecute() {
        Command testCmd = new TestCommand("greet", "Greets the user") {
            @Override
            public CommandResult execute(String[] args, Session s) {
                return CommandResult.success("Hello, " + (args.length > 0 ? args[0] : "world") + "!");
            }
        };
        executor.getRegistry().register(testCmd);

        CommandResult result = executor.execute("/greet", session);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("执行未知命令返回错误")
    void testExecuteUnknownCommand() {
        CommandResult result = executor.execute("/nonexistent", session);
        assertFalse(result.isSuccess(), "未知命令应返回错误");
    }

    @Test
    @DisplayName("非命令输入应被正确识别")
    void testNonCommandInput() {
        CommandResult result = executor.execute("just a normal prompt", session);
        assertNotNull(result);
    }

    @Test
    @DisplayName("创建默认命令注册表")
    void testCreateDefaultRegistry() {
        CommandRegistry defaultRegistry = CommandRegistry.createDefault();
        assertTrue(defaultRegistry.getCommandCount() > 0);
    }

    @Test
    @DisplayName("创建默认命令执行器")
    void testCreateDefaultExecutor() {
        CommandExecutor defaultExecutor = CommandExecutor.createDefault();
        assertNotNull(defaultExecutor);
        assertTrue(defaultExecutor.getRegistry().getCommandCount() > 0);
    }

    @Test
    @DisplayName("Executor 获取 Registry")
    void testExecutorGetRegistry() {
        assertSame(registry, executor.getRegistry());
    }

    @Test
    @DisplayName("命令别名匹配")
    void testCommandAlias() {
        Command cmd = new TestCommand("quit", "Quit the application") {
            @Override
            public List<String> getAliases() {
                return List.of("q", "exit");
            }
        };
        registry.register(cmd);

        assertNotNull(registry.getCommand("quit"));
        // 别名可通过 getCommand 查找
    }

    @Test
    @DisplayName("HelpCommand 默认注册")
    void testHelpCommand() {
        HelpCommand helpCmd = new HelpCommand(registry);
        registry.register(helpCmd);

        CommandResult result = helpCmd.execute(new String[]{}, session);
        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("StatusCommand 执行")
    void testStatusCommand() {
        StatusCommand statusCmd = new StatusCommand();
        registry.register(statusCmd);

        CommandResult result = statusCmd.execute(new String[]{}, session);
        assertNotNull(result);
    }

    @Test
    @DisplayName("ExitCommand 执行")
    void testExitCommand() {
        ExitCommand exitCmd = new ExitCommand();
        registry.register(exitCmd);

        CommandResult result = exitCmd.execute(new String[]{}, session);
        assertNotNull(result);
        assertTrue(result.isExit(), "ExitCommand 应返回 EXIT 类型结果");
        assertEquals("再见！", result.getMessage());
    }

    // ========== 辅助测试命令 ==========

    /**
     * 测试用简单命令实现
     */
    private static class TestCommand implements Command {
        private final String name;
        private final String description;

        TestCommand(String name, String description) {
            this.name = name;
            this.description = description;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public String getUsage() {
            return "/" + name;
        }

        @Override
        public CommandResult execute(String[] args, Session session) {
            return CommandResult.success(name + " executed");
        }
    }
}
