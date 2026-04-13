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
     * @param pythonCommand Python 命令（如 "python3", "python"）
     * @param timeoutMillis 超时时间（毫秒）
     * @param maxMemoryMB 最大内存限制（MB）
     */
    public PythonREPLExecutor(String pythonCommand, long timeoutMillis, long maxMemoryMB) {
        super("python", timeoutMillis, maxMemoryMB);
        this.pythonCommand = pythonCommand;
        this.outputQueue = new LinkedBlockingQueue<>();
        initializeProcess();
    }
    
    /**
     * 检测可用的 Python 命令
     */
    private static String detectPythonCommand() {
        String[] candidates = {"python3", "python", "py"};
        for (String cmd : candidates) {
            try {
                Process process = new ProcessBuilder(cmd, "--version")
                    .redirectErrorStream(true)
                    .start();
                boolean finished = process.waitFor(5, TimeUnit.SECONDS);
                if (finished && process.exitValue() == 0) {
                    return cmd;
                }
            } catch (Exception e) {
                logger.fine("Python command not found: " + cmd);
            }
        }
        logger.warning("No Python installation found. Python REPL will be unavailable.");
        return "python3"; // 默认返回，后续会检测不可用
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
        // 设置输出编码
        sendCommand("import sys");
        sendCommand("sys.stdout = open(sys.stdout.fileno(), mode='w', encoding='utf-8', buffering=1)");
        sendCommand("sys.stderr = open(sys.stderr.fileno(), mode='w', encoding='utf-8', buffering=1)");
        
        // 清空初始输出
        clearOutput();
    }
    
    /**
     * 发送命令到 Python 进程
     */
    private void sendCommand(String command) throws IOException {
        processInput.write(command);
        processInput.newLine();
        processInput.flush();
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
