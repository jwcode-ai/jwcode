package com.jwcode.core.command;

import com.jwcode.core.model.Message;
import com.jwcode.core.session.Session;
import com.jwcode.core.tool.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 纯命令单元测试 — 原子级，不依赖外部进程或网络。
 *
 * <p>覆盖 Export/Search/Checkpoint/Memory/Rewind/Compact/Effort/Tokens 等
 * 通过 {@code Command.execute()} 直接执行的命令，使用 @TempDir 隔离文件系统副作用。
 */
public class CommandUnitTest {

    @TempDir
    Path tempDir;

    private Session session;

    @BeforeEach
    void setUp() {
        session = new Session("test-session", tempDir.toString());
        session.setModel("test-model");
        session.addMessage(Message.createUserMessage("hello world"));
        session.addMessage(Message.createAssistantMessage("hi there"));
    }

    // ═══ ExportCommand ═══

    @Test
    void exportWritesMarkdownFile() throws Exception {
        ExportCommand cmd = new ExportCommand();
        Path target = tempDir.resolve("sub").resolve("out.md");
        CommandResult result = cmd.execute(new String[]{"sub/out.md"}, session);

        assertTrue(result.isSuccess(), "export should succeed: " + result.getMessage());
        assertTrue(Files.exists(target), "exported file should exist");
        String content = Files.readString(target);
        assertTrue(content.contains("# JWCode conversation export"), "should have title");
        assertTrue(content.contains("hello world"), "should contain user message");
        assertTrue(content.contains("hi there"), "should contain assistant message");
    }

    @Test
    void exportRequiresPathArgument() {
        ExportCommand cmd = new ExportCommand();
        CommandResult result = cmd.execute(new String[]{}, session);
        assertTrue(result.getType() == CommandResult.ResultType.ERROR);
        assertTrue(result.getMessage().contains("path"));
    }

    @Test
    void exportFailsWithoutSession() {
        ExportCommand cmd = new ExportCommand();
        CommandResult result = cmd.execute(new String[]{"out.md"}, null);
        assertTrue(result.getType() == CommandResult.ResultType.ERROR);
    }

    @Test
    void exportContractMetadata() {
        ExportCommand cmd = new ExportCommand();
        assertEquals("export", cmd.getName());
        assertEquals("session", cmd.getCategory());
        assertEquals(CommandSource.SESSION, cmd.getSource());
        assertTrue(cmd.requiresArgs());
    }

    // ═══ SearchCommand ═══

    @Test
    void searchFindsKeywordInWorkspaceFile() throws Exception {
        Files.writeString(tempDir.resolve("note.txt"), "first line\nsecond line with FOOBAR\nthird");
        SearchCommand cmd = new SearchCommand();
        CommandResult result = cmd.execute(new String[]{"FOOBAR"}, session);

        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("FOOBAR"), "result should contain keyword");
        assertTrue(result.getMessage().contains("note.txt"), "result should contain file name");
        assertTrue(result.getMessage().contains(":2:"), "result should contain line number");
    }

    @Test
    void searchIsCaseInsensitive() throws Exception {
        Files.writeString(tempDir.resolve("a.ts"), "const Hello = 1;");
        SearchCommand cmd = new SearchCommand();
        CommandResult result = cmd.execute(new String[]{"hello"}, session);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().toLowerCase().contains("hello"));
    }

    @Test
    void searchReturnsNoResultsWhenNothingMatches() throws Exception {
        Files.writeString(tempDir.resolve("x.md"), "nothing relevant here");
        SearchCommand cmd = new SearchCommand();
        CommandResult result = cmd.execute(new String[]{"zzznotpresent"}, session);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("No results"));
    }

    @Test
    void searchRequiresKeyword() {
        SearchCommand cmd = new SearchCommand();
        CommandResult result = cmd.execute(new String[]{}, session);
        assertTrue(result.getType() == CommandResult.ResultType.ERROR);
        assertTrue(cmd.requiresArgs());
        assertEquals("workspace", cmd.getCategory());
    }

    // ═══ CheckpointCommand ═══

    @Test
    void checkpointSaveAndList() {
        CheckpointCommand cmd = new CheckpointCommand();
        CommandResult save = cmd.execute(new String[]{"mytag"}, session);
        assertTrue(save.isSuccess(), save.getMessage());

        CommandResult list = cmd.execute(new String[]{"list"}, session);
        assertTrue(list.isSuccess());
        assertTrue(list.getMessage().contains("mytag"), "list should show saved checkpoint");
    }

    @Test
    void checkpointRestoreReadsContent() {
        CheckpointCommand cmd = new CheckpointCommand();
        cmd.execute(new String[]{"tag1"}, session);
        CommandResult list = cmd.execute(new String[]{"list"}, session);
        // extract filename from list output
        String fileName = list.getMessage().lines()
            .filter(l -> l.contains("tag1"))
            .map(l -> l.replace("- ", "").trim())
            .findFirst()
            .orElseThrow();
        CommandResult restore = cmd.execute(new String[]{"restore", fileName}, session);
        assertTrue(restore.isSuccess());
        assertTrue(restore.getMessage().contains("hello world"));
    }

    @Test
    void checkpointRestoreMissingFileFails() {
        CheckpointCommand cmd = new CheckpointCommand();
        CommandResult result = cmd.execute(new String[]{"restore", "nonexistent.md"}, session);
        assertTrue(result.getType() == CommandResult.ResultType.ERROR);
    }

    // ═══ MemoryCommand ═══

    @Test
    void memoryListsJwcodeMdAndDotDir() throws Exception {
        Files.writeString(tempDir.resolve("JWCODE.md"), "# project memory\n");
        Files.createDirectories(tempDir.resolve(".jwcode"));
        Files.writeString(tempDir.resolve(".jwcode").resolve("rules.md"), "rules");

        MemoryCommand cmd = new MemoryCommand();
        CommandResult result = cmd.execute(new String[]{}, session);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("JWCODE.md"));
        assertTrue(result.getMessage().contains("rules.md"));
    }

    @Test
    void memoryReadsSpecificFile() throws Exception {
        Files.writeString(tempDir.resolve("JWCODE.md"), "important context here");
        MemoryCommand cmd = new MemoryCommand();
        CommandResult result = cmd.execute(new String[]{"JWCODE.md"}, session);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("important context here"));
    }

    @Test
    void memoryReportsNoneWhenEmpty() {
        MemoryCommand cmd = new MemoryCommand();
        CommandResult result = cmd.execute(new String[]{}, session);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("No project memory files found"));
    }

    @Test
    void memoryRejectsPathEscape() {
        MemoryCommand cmd = new MemoryCommand();
        CommandResult result = cmd.execute(new String[]{"../../../etc/passwd"}, session);
        assertTrue(result.getType() == CommandResult.ResultType.ERROR);
    }

    // ═══ RewindCommand ═══

    @Test
    void rewindRemovesRecentMessages() {
        RewindCommand cmd = new RewindCommand();
        int before = session.getMessageCount();
        CommandResult result = cmd.execute(new String[]{"1"}, session);
        assertTrue(result.isSuccess());
        assertTrue(session.getMessageCount() < before, "message count should decrease");
        assertTrue(result.getMessage().contains("Rewound"));
    }

    @Test
    void rewindOnEmptySessionErrors() {
        Session empty = new Session("empty", tempDir.toString());
        RewindCommand cmd = new RewindCommand();
        CommandResult result = cmd.execute(new String[]{}, empty);
        assertTrue(result.getType() == CommandResult.ResultType.ERROR);
    }

    @Test
    void rewindDefaultStepsIsOne() {
        RewindCommand cmd = new RewindCommand();
        int before = session.getMessageCount();
        cmd.execute(new String[]{}, session);
        assertEquals(before - 2, session.getMessageCount(), "default 1 step removes 1 pair (2 msgs)");
    }

    // ═══ EffortCommand ═══

    @Test
    void effortSetsValidLevel() {
        EffortCommand cmd = new EffortCommand();
        CommandResult result = cmd.execute(new String[]{"high"}, session);
        assertTrue(result.isSuccess());
        assertEquals("high", session.getMetadata("effort"));
    }

    @Test
    void effortRejectsInvalidLevel() {
        EffortCommand cmd = new EffortCommand();
        CommandResult result = cmd.execute(new String[]{"extreme"}, session);
        assertTrue(result.getType() == CommandResult.ResultType.ERROR);
    }

    @Test
    void effortRequiresArg() {
        EffortCommand cmd = new EffortCommand();
        CommandResult result = cmd.execute(new String[]{}, session);
        assertTrue(result.getType() == CommandResult.ResultType.ERROR);
        assertTrue(cmd.requiresArgs());
        assertEquals("config", cmd.getCategory());
    }

    // ═══ TokensCommand ═══

    @Test
    void tokensReportsSessionInfo() {
        TokensCommand cmd = new TokensCommand();
        CommandResult result = cmd.execute(new String[]{}, session);
        assertTrue(result.isSuccess());
        assertTrue(result.getMessage().contains("Token Usage"));
        assertTrue(result.getMessage().contains("test-model"));
        assertTrue(result.getMessage().contains("Messages in session: 2"));
    }

    // ═══ CompactCommand ═══

    @Test
    void compactOnEmptySessionErrors() {
        Session empty = new Session("empty", tempDir.toString());
        CompactCommand cmd = new CompactCommand();
        CommandResult result = cmd.execute(new String[]{}, empty);
        assertTrue(result.getType() == CommandResult.ResultType.ERROR);
    }

    // ═══ ExitCommand ═══

    @Test
    void exitReturnsExitResult() {
        ExitCommand cmd = new ExitCommand();
        CommandResult result = cmd.execute(new String[]{}, session);
        assertTrue(result.isExit());
        assertNotNull(result.getMessage());
    }

    @Test
    void exitHasQuitAlias() {
        ExitCommand cmd = new ExitCommand();
        assertTrue(cmd.getAliases().contains("quit"));
        assertTrue(cmd.getAliases().contains("q"));
    }

    // ═══ InitCommand / ProjectCommand (AI prompt marker) ═══

    @Test
    void initReturnsAiPromptMarker() {
        InitCommand cmd = new InitCommand();
        CommandResult result = cmd.execute(new String[]{}, session);
        assertTrue(result.isSuccess());
        assertEquals("AI_PROMPT", result.getData());
        assertTrue(result.getMessage().contains("JWCODE.md"));
    }

    @Test
    void projectReturnsAiPromptMarker() {
        ProjectCommand cmd = new ProjectCommand();
        CommandResult result = cmd.execute(new String[]{}, session);
        assertTrue(result.isSuccess());
        assertEquals("AI_PROMPT", result.getData());
    }

    @Test
    void projectHasUpdateDocsAlias() {
        ProjectCommand cmd = new ProjectCommand();
        assertTrue(cmd.getAliases().contains("update_docs"));
    }

    // ═══ BranchCommand ═══

    @Test
    void branchRequiresBackendOrchestration() {
        BranchCommand cmd = new BranchCommand();
        CommandResult result = cmd.execute(new String[]{"feature"}, session);
        assertTrue(result.getType() == CommandResult.ResultType.ERROR);
        assertTrue(result.getMessage().contains("backend"), "should explain delegation");
    }

    // ═══ Registry lookup ═══

    @Test
    void registryResolvesByAlias() {
        CommandRegistry registry = CommandRegistry.createFull(ToolRegistry.createDefault());
        assertNotNull(registry.getCommand("quit"), "quit alias should resolve");
        assertNotNull(registry.getCommand("q"), "q alias should resolve");
        assertNotNull(registry.getCommand("update_docs"), "update_docs alias should resolve");
    }

    @Test
    void registryReturnsNullForUnknown() {
        CommandRegistry registry = CommandRegistry.createFull(ToolRegistry.createDefault());
        assertNull(registry.getCommand("nonexistent_command"));
        assertFalse(registry.hasCommand("nonexistent_command"));
    }

    @Test
    void registryMatchingCommandsForPrefix() {
        CommandRegistry registry = CommandRegistry.createFull(ToolRegistry.createDefault());
        List<Command> matches = registry.getMatchingCommands("co");
        assertTrue(matches.stream().anyMatch(c -> c.getName().equals("cost")));
        assertTrue(matches.stream().anyMatch(c -> c.getName().equals("config")));
        assertTrue(matches.stream().anyMatch(c -> c.getName().equals("compact")));
    }
}
