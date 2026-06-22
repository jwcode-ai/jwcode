package com.jwcode.core.repl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.logging.Logger;

public class JavaScriptREPLExecutor extends REPLExecutor {

    private static final Logger logger = Logger.getLogger(JavaScriptREPLExecutor.class.getName());

    private final String nodeCommand;
    private final Object execLock = new Object();

    public JavaScriptREPLExecutor() {
        this(30000, 256);
    }

    public JavaScriptREPLExecutor(long timeoutMillis, long maxMemoryMB) {
        super("javascript", timeoutMillis, maxMemoryMB);
        this.nodeCommand = detectNodeCommand();
        if (this.nodeCommand != null) {
            logger.info("JavaScript REPL using Node.js command: " + this.nodeCommand);
        } else {
            logger.warning("Node.js not found. JavaScript REPL will report as unavailable.");
        }
    }

    private static String detectNodeCommand() {
        String[] candidates = {"node", "nodejs"};
        for (String cmd : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (finished && process.exitValue() == 0) {
                    String version = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
                    if (!version.isEmpty()) {
                        logger.info("Found Node.js: " + cmd + " -> " + version);
                        return cmd;
                    }
                }
            } catch (Exception e) {
                logger.fine("Node.js command not found: " + cmd);
            }
        }
        return null;
    }

    @Override
    public ExecutionResult execute(String code) {
        if (!isAvailable()) {
            return ExecutionResult.error(
                "Node.js is not available. Please install Node.js from https://nodejs.org/",
                "NODE_UNAVAILABLE"
            );
        }
        if (code == null || code.trim().isEmpty()) {
            return ExecutionResult.success("", 0);
        }
        return executeWithTimeout(() -> {
            synchronized (execLock) {
                long startTime = System.currentTimeMillis();
                Path tempFile = Files.createTempFile("jwcode_js_", ".js");
                try {
                    String wrappedCode = buildWrappedCode(code);
                    Files.writeString(tempFile, wrappedCode, StandardCharsets.UTF_8);
                    List<String> cmdArgs = new ArrayList<>();
                    cmdArgs.add(nodeCommand);
                    cmdArgs.add(tempFile.toAbsolutePath().toString());
                    if (isMemoryExceeded()) {
                        return ExecutionResult.memoryExceeded(maxMemoryMB);
                    }
                    ProcessBuilder pb = new ProcessBuilder(cmdArgs);
                    pb.redirectErrorStream(false);
                    Process process = pb.start();
                    StringBuilder stdout = new StringBuilder();
                    StringBuilder stderr = new StringBuilder();
                    Thread stdoutReader = new Thread(() -> {
                        try (BufferedReader r = new BufferedReader(
                                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = r.readLine()) != null) stdout.append(line).append("\n");
                        } catch (IOException e) { }
                    });
                    stdoutReader.setDaemon(true);
                    Thread stderrReader = new Thread(() -> {
                        try (BufferedReader r = new BufferedReader(
                                new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8))) {
                            String line;
                            while ((line = r.readLine()) != null) stderr.append(line).append("\n");
                        } catch (IOException e) { }
                    });
                    stderrReader.setDaemon(true);
                    stdoutReader.start();
                    stderrReader.start();
                    boolean finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
                    long execTime = System.currentTimeMillis() - startTime;
                    stdoutReader.join(2000);
                    stderrReader.join(2000);
                    if (!finished) {
                        process.destroyForcibly();
                        return ExecutionResult.timeout(timeoutMillis);
                    }
                    int exitCode = process.exitValue();
                    String outStr = stdout.toString().trim();
                    String errStr = stderr.toString().trim();
                    if (exitCode != 0 && !errStr.isEmpty()) {
                        return new ExecutionResult(false, outStr, errStr, execTime, String.valueOf(exitCode));
                    }
                    return ExecutionResult.success(outStr, execTime);
                } finally {
                    try { Files.deleteIfExists(tempFile); } catch (IOException e) {
                        logger.fine("Failed to delete temp JS file: " + tempFile);
                    }
                }
            }
        });
    }

    private String buildWrappedCode(String code) {
        StringBuilder sb = new StringBuilder();
        sb.append("const __jwcode_output = [];\n");
        sb.append("const __jwcode_console = {\n");
        sb.append("  log: (...args) => __jwcode_output.push(args.map(String).join(' ')),\n");
        sb.append("  info: (...args) => __jwcode_output.push(args.map(String).join(' ')),\n");
        sb.append("  warn: (...args) => __jwcode_output.push('[WARN] ' + args.map(String).join(' ')),\n");
        sb.append("  error: (...args) => __jwcode_output.push('[ERROR] ' + args.map(String).join(' ')),\n");
        sb.append("};\n");
        sb.append("console = __jwcode_console;\n\n");
        sb.append(code).append("\n\n");
        sb.append("process.stdout.write(__jwcode_output.join('\\n'));\n");
        return sb.toString();
    }

    /**
     * 检查内存使用是否超过限制。
     *
     * JavaScript REPL 以子进程（node）运行，不消耗 JVM 堆内存；
     * JVM 的 totalMemory/freeMemory 不反映子进程实际使用量。
     * 此处跳过 JVM 堆检查以避免误报。
     *
     * @return false 始终返回 false，子进程内存由操作系统限制
     */
    @Override
    protected boolean isMemoryExceeded() {
        return false;
    }

    @Override
    public void reset() {
        logger.fine("JavaScript REPL reset: no-op (stateless executor)");
    }

    @Override
    public boolean isAvailable() {
        return nodeCommand != null;
    }

    public String getNodeCommand() { return nodeCommand; }

    @Override
    public void shutdown() { super.shutdown(); }
}
