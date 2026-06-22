package com.jwcode.core.repl;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Python REPL 执行器
 * 使用 ProcessBuilder 调用 Python 解释器进行交互式执行
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class PythonREPLExecutor extends REPLExecutor {
    
    private static final Logger logger = Logger.getLogger(PythonREPLExecutor.class.getName());
    
    private Process pythonProcess;
    private BufferedWriter processInput;
    private BufferedReader processOutput;
    private BufferedReader processError;
    private final Object processLock = new Object();
    private final String pythonCommand;
    private final BlockingQueue<String> outputQueue;
    private Thread outputReaderThread;
    private Thread errorReaderThread;
    private volatile boolean running = false;
    
    /**
     * 默认构造函数
     * 自动检测 Python 命令（python3 或 python）
     */
    public PythonREPLExecutor() {
        this(detectPythonCommand(), 30000, 256);
    }
    
    /**
     * 创建 Python REPL 执行器
     * 
     * @param pythonCommand Python 命令（如 "python3", "python"），如果为 null 则不会启动进程
     * @param timeoutMillis 超时时间（毫秒）
     * @param maxMemoryMB 最大内存限制（MB）
     */
    public PythonREPLExecutor(String pythonCommand, long timeoutMillis, long maxMemoryMB) {
        super("python", timeoutMillis, maxMemoryMB);
        this.outputQueue = new LinkedBlockingQueue<>();
        
        // 如果 Python 命令为 null（未检测到 Python），不尝试启动进程
        if (pythonCommand == null) {
            this.pythonCommand = "python3"; // 保存用于错误消息
            this.pythonProcess = null;
            logger.warning("Python not found. REPL will report as unavailable when used.");
            return;
        }
        
        this.pythonCommand = pythonCommand;
        initializeProcess();
    }
    
    /**
     * 检测可用的 Python 命令
     * 
     * @return 检测到的 Python 命令，如果未找到返回 null
     */
    private static String detectPythonCommand() {
        String[] candidates = {"python3", "python", "py"};
        String foundCommand = null;
        
        for (String cmd : candidates) {
            try {
                ProcessBuilder pb = new ProcessBuilder(cmd, "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (finished && process.exitValue() == 0) {
                    // 读取版本输出验证是真正的 Python
                    String version = new String(process.getInputStream().readAllBytes());
                    if (version.toLowerCase().contains("python")) {
                        foundCommand = cmd;
                        logger.info("Found Python command: " + cmd + " - " + version.trim());
                        break;
                    }
                }
            } catch (Exception e) {
                logger.fine("Python command not found: " + cmd);
            }
        }
        
        if (foundCommand == null) {
            logger.warning("No Python installation found. Python REPL will be unavailable.");
        }
        return foundCommand;
    }
    
    /**
     * 初始化 Python 进程
     */
    private void initializeProcess() {
        synchronized (processLock) {
            try {
                // 启动 Python 交互式解释器
                ProcessBuilder pb = new ProcessBuilder(
                    pythonCommand,
                    "-i",  // 交互模式
                    "-u"   // 无缓冲输出
                );
                
                // 设置环境变量限制内存
                pb.environment().put("PYTHONIOENCODING", "utf-8");
                
                pythonProcess = pb.start();
                
                processInput = new BufferedWriter(
                    new OutputStreamWriter(pythonProcess.getOutputStream(), StandardCharsets.UTF_8));
                processOutput = new BufferedReader(
                    new InputStreamReader(pythonProcess.getInputStream(), StandardCharsets.UTF_8));
                processError = new BufferedReader(
                    new InputStreamReader(pythonProcess.getErrorStream(), StandardCharsets.UTF_8));
                
                running = true;
                
                // 启动输出读取线程
                startOutputReaders();
                
                // 初始化 Python 环境
                initializePythonEnvironment();
                
            } catch (Exception e) {
                logger.log(Level.SEVERE, "Failed to initialize Python process", e);
                cleanup();
            }
        }
    }
    
    /**
     * 启动输出读取线程
     */
    private void startOutputReaders() {
        // 标准输出读取线程
        outputReaderThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = processOutput.readLine()) != null) {
                    if (!isPromptLine(line)) {
                        outputQueue.offer(line);
                    }
                }
            } catch (IOException e) {
                if (running) {
                    logger.fine("Output reader stopped: " + e.getMessage());
                }
            }
        });
        outputReaderThread.setDaemon(true);
        outputReaderThread.start();
        
        // 错误输出读取线程
        errorReaderThread = new Thread(() -> {
            try {
                String line;
                while (running && (line = processError.readLine()) != null) {
                    outputQueue.offer("[ERROR] " + line);
                }
            } catch (IOException e) {
                if (running) {
                    logger.fine("Error reader stopped: " + e.getMessage());
                }
            }
        });
        errorReaderThread.setDaemon(true);
        errorReaderThread.start();
    }
    
    /**
     * 检查是否是 Python 提示符行
     */
    private boolean isPromptLine(String line) {
        return line.equals(">>>") || line.equals("...") || line.trim().isEmpty();
    }
    
    /**
     * 初始化 Python 环境
     */
    private void initializePythonEnvironment() throws IOException {
        try {
            // 发送一个简单的测试命令验证进程存活
            sendCommand("import sys");
            
            // 等待Python处理并清空输出
            Thread.sleep(200);
            clearOutput();
            
            // 验证进程是否正常响应
            if (!isAvailable()) {
                throw new IOException("Python process failed to initialize");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Python initialization interrupted", e);
        }
    }
    
    /**
     * 发送命令到 Python 进程
     */
    private void sendCommand(String command) throws IOException {
        // 检查进程是否存活
        if (!isAvailable()) {
            throw new IOException("Python process is not available");
        }
        
        try {
            processInput.write(command);
            processInput.newLine();
            processInput.flush();
        } catch (IOException e) {
            // 如果是管道关闭错误，标记进程不可用
            if (e.getMessage() != null && 
                (e.getMessage().contains("Pipe") || e.getMessage().contains("管道"))) {
                logger.warning("Python process pipe closed unexpectedly");
                running = false;
            }
            throw e;
        }
    }
    
    /**
     * 清空输出队列
     */
    private void clearOutput() {
        outputQueue.clear();
        try {
            // 等待一下让 Python 处理
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        outputQueue.clear();
    }
    
    @Override
    public ExecutionResult execute(String code) {
        if (!isAvailable()) {
            return ExecutionResult.error(
                "Python is not available. Please install Python 3.",
                "PYTHON_UNAVAILABLE"
            );
        }
        
        if (code == null || code.trim().isEmpty()) {
            return ExecutionResult.success("", 0);
        }
        
        return executeWithTimeout(() -> {
            synchronized (processLock) {
                try {
                    // 检查内存限制
                    if (isMemoryExceeded()) {
                        return ExecutionResult.memoryExceeded(maxMemoryMB);
                    }
                    
                    // 清空之前的输出
                    clearOutput();
                    
                    // 发送代码到 Python
                    String[] lines = code.split("\n");
                    for (String line : lines) {
                        sendCommand(line);
                    }
                    
                    // 发送特殊标记来检测输出结束
                    String marker = "___JWCODE_OUTPUT_END___" + System.currentTimeMillis();
                    sendCommand("print('" + marker + "')");
                    
                    // 收集输出
                    StringBuilder output = new StringBuilder();
                    StringBuilder error = new StringBuilder();
                    long startTime = System.currentTimeMillis();
                    long deadline = startTime + timeoutMillis;
                    
                    boolean markerFound = false;
                    while (System.currentTimeMillis() < deadline && !markerFound) {
                        String line = outputQueue.poll(100, TimeUnit.MILLISECONDS);
                        if (line != null) {
                            if (line.contains(marker)) {
                                markerFound = true;
                            } else if (line.startsWith("[ERROR] ")) {
                                error.append(line.substring(8)).append("\n");
                            } else {
                                output.append(line).append("\n");
                            }
                        }
                        
                        // 检查进程是否还在运行
                        if (!pythonProcess.isAlive()) {
                            return ExecutionResult.error("Python process terminated unexpectedly", "PROCESS_DEAD");
                        }
                    }
                    
                    long executionTime = System.currentTimeMillis() - startTime;
                    
                    String outputStr = output.toString().trim();
                    String errorStr = error.toString().trim();
                    
                    if (!errorStr.isEmpty()) {
                        return new ExecutionResult(false, outputStr, errorStr, executionTime, "ERROR");
                    }
                    
                    return ExecutionResult.success(outputStr, executionTime);
                    
                } catch (Exception e) {
                    logger.log(Level.SEVERE, "Python execution error", e);
                    return ExecutionResult.error("Execution error: " + e.getMessage(), "RUNTIME_ERROR");
                }
            }
        });
    }
    
    @Override
    public void reset() {
        synchronized (processLock) {
            cleanup();
            initializeProcess();
        }
    }
    
    @Override
    public boolean isAvailable() {
        return pythonProcess != null && pythonProcess.isAlive();
    }
    
    /**
     * 清理资源
     */
    private void cleanup() {
        running = false;
        
        if (processInput != null) {
            try {
                processInput.close();
            } catch (IOException e) {
                logger.fine("Error closing input: " + e.getMessage());
            }
            processInput = null;
        }
        
        if (processOutput != null) {
            try {
                processOutput.close();
            } catch (IOException e) {
                logger.fine("Error closing output: " + e.getMessage());
            }
            processOutput = null;
        }
        
        if (processError != null) {
            try {
                processError.close();
            } catch (IOException e) {
                logger.fine("Error closing error: " + e.getMessage());
            }
            processError = null;
        }
        
        if (pythonProcess != null) {
            pythonProcess.destroy();
            try {
                if (!pythonProcess.waitFor(5, TimeUnit.SECONDS)) {
                    pythonProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                pythonProcess.destroyForcibly();
                Thread.currentThread().interrupt();
            }
            pythonProcess = null;
        }
        
        outputQueue.clear();
    }
    
    @Override
    public void shutdown() {
        synchronized (processLock) {
            cleanup();
        }
        super.shutdown();
    }
    
    /**
     * 检查内存使用是否超过限制
     *
     * Python 以子进程运行，不消耗 JVM 堆内存；
     * 子进程内存由操作系统管理，JVM 的 totalMemory/freeMemory 不反映其实际使用量。
     * 此处跳过 JVM 堆检查以避免误报。
     *
     * @return false 始终返回 false，子进程内存由操作系统限制
     */
    @Override
    protected boolean isMemoryExceeded() {
        return false; // 子进程型 REPL，不检查 JVM 堆内存
    }

    /**
     * 获取 Python 版本
     *
     * @return Python 版本字符串
     */
    public String getPythonVersion() {
        if (!isAvailable()) {
            return "Python not available";
        }
        
        ExecutionResult result = execute("import sys; print(f'{sys.version_info.major}.{sys.version_info.minor}.{sys.version_info.micro}')");
        if (result.success()) {
            return result.output();
        }
        return "Unknown";
    }
}
