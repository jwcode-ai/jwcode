package com.jwcode.core.checker;

import com.jwcode.core.lsp.LspServerManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URL;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 环境自检器
 * 
 * 功能：
 * - 检测 LSP 服务器可用性
 * - 检测网络连接
 * - 检测 REPL 环境
 * - 检测 MCP 服务
 * - 依赖缺失时提供修复建议
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class EnvironmentChecker {

    private static final Logger logger = LoggerFactory.getLogger(EnvironmentChecker.class);
    
    private final LspServerManager lspServerManager;
    private final int networkTimeoutMs;
    private final int connectionTimeoutMs;

    public EnvironmentChecker() {
        this(null, 5000, 3000);
    }

    public EnvironmentChecker(LspServerManager lspServerManager, int networkTimeoutMs, int connectionTimeoutMs) {
        this.lspServerManager = lspServerManager;
        this.networkTimeoutMs = networkTimeoutMs;
        this.connectionTimeoutMs = connectionTimeoutMs;
    }

    /**
     * 执行完整的环境检测
     */
    public CheckerResult checkAll() {
        CheckerResult result = CheckerResult.create();
        
        logger.info("开始环境自检...");
        
        // 1. 检测 Java 环境
        checkJavaEnvironment(result);
        
        // 2. 检测 Maven/Gradle
        checkBuildTools(result);
        
        // 3. 检测 LSP 服务器
        checkLspServers(result);
        
        // 4. 检测网络连接
        checkNetworkConnectivity(result);
        
        // 5. 检测 REPL 环境
        checkReplEnvironments(result);
        
        // 6. 检测 MCP 服务
        checkMcpServices(result);
        
        logger.info(result.generateSummary());
        
        return result.build();
    }

    /**
     * 异步执行环境检测
     */
    public CompletableFuture<CheckerResult> checkAllAsync() {
        return CompletableFuture.supplyAsync(this::checkAll);
    }

    /**
     * 检测 Java 环境
     */
    private void checkJavaEnvironment(CheckerResult result) {
        DependencyInfo info = DependencyInfo.of("Java", "runtime");
        
        try {
            String version = System.getProperty("java.version");
            String vendor = System.getProperty("java.vendor");
            
            if (version != null && !version.isEmpty()) {
                info.available()
                    .withVersion(version)
                    .withFix("当前版本: " + version + " (" + vendor + ")");
                
                // 检查版本是否满足最低要求
                if (version.startsWith("1.8") || version.startsWith("11") || version.startsWith("17") || version.startsWith("21")) {
                    logger.debug("Java 版本检测通过: {}", version);
                } else {
                    info.unavailable("Java 版本过低，建议升级到 11 或更高版本");
                    info.withFix("请安装 JDK 11 或更高版本: https://adoptium.net/");
                }
            } else {
                info.unavailable("无法获取 Java 版本");
                info.withFix("请确保已安装 JDK");
            }
        } catch (Exception e) {
            info.unavailable("Java 环境检测失败: " + e.getMessage());
            info.withFix("请安装 JDK 8 或更高版本");
        }
        
        result.addDependency(info);
    }

    /**
     * 检测构建工具 (Maven/Gradle)
     */
    private void checkBuildTools(CheckerResult result) {
        // 检测 Maven
        checkCommandAvailability(result, "Maven", "mvn", "-version", 
            "https://maven.apache.org/install.html");
        
        // 检测 Gradle
        checkCommandAvailability(result, "Gradle", "gradle", "-version",
            "https://gradle.org/install/");
    }

    /**
     * 检测命令可用性
     */
    private void checkCommandAvailability(CheckerResult result, String name, String command, 
                                         String versionArg, String installUrl) {
        DependencyInfo info = DependencyInfo.of(name, "build-tool");
        
        try {
            ProcessBuilder pb = new ProcessBuilder(command, versionArg);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            
            boolean finished = process.waitFor(5, TimeUnit.SECONDS);
            
            if (finished && process.exitValue() == 0) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
                String firstLine = reader.readLine();
                
                info.available()
                    .withVersion(firstLine != null ? firstLine : "unknown");
            } else {
                info.unavailable("命令不可用或执行超时")
                    .withFix("请安装 " + name + ": " + installUrl);
            }
        } catch (IOException e) {
            info.unavailable(name + " 未安装")
                .withFix("请安装 " + name + ": " + installUrl);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            info.unavailable(name + " 检测被中断")
                .withFix("请手动安装 " + name);
        }
        
        result.addDependency(info);
    }

    /**
     * 检测 LSP 服务器
     */
    private void checkLspServers(CheckerResult result) {
        DependencyInfo info = DependencyInfo.of("LSP Server", "language-server");
        
        if (lspServerManager == null) {
            info.skipped("LSP 管理器未初始化，跳过检测");
            result.addDependency(info);
            return;
        }
        
        try {
            // 使用反射或通用方法检测 LSP 服务器状态
            int activeCount = 0;
            try {
                java.lang.reflect.Method method = lspServerManager.getClass().getMethod("getActiveServers");
                Object result2 = method.invoke(lspServerManager);
                if (result2 instanceof java.util.Collection) {
                    activeCount = ((java.util.Collection<?>) result2).size();
                }
            } catch (NoSuchMethodException e) {
                // 如果没有该方法，尝试获取服务器列表
                try {
                    java.lang.reflect.Method method = lspServerManager.getClass().getMethod("getServers");
                    Object result2 = method.invoke(lspServerManager);
                    if (result2 instanceof java.util.Collection) {
                        activeCount = ((java.util.Collection<?>) result2).size();
                    }
                } catch (Exception ex) {
                    activeCount = 0;
                }
            }
            
            if (activeCount > 0) {
                info.available()
                    .withVersion(activeCount + " 个活跃服务器")
                    .withFix("当前活跃服务器: " + activeCount);
            } else {
                info.skipped("没有活跃的 LSP 服务器");
            }
        } catch (Exception e) {
            info.unavailable("LSP 服务器检测失败: " + e.getMessage())
                .withFix("请检查 LSP 配置");
        }
        
        result.addDependency(info);
    }

    /**
     * 检测网络连接
     */
    private void checkNetworkConnectivity(CheckerResult result) {
        // 检测常见网络端点
        String[] endpoints = {
            "https://api.github.com:443",
            "https://google.com:443"
        };
        
        DependencyInfo info = DependencyInfo.of("Network", "connectivity");
        boolean allFailed = true;
        
        for (String endpoint : endpoints) {
            try {
                URL url = new URL(endpoint);
                String host = url.getHost();
                int port = url.getPort() != -1 ? url.getPort() : 443;
                
                if (checkTcpConnection(host, port)) {
                    info.available()
                        .withVersion("已连接到 " + host);
                    allFailed = false;
                    break;
                }
            } catch (Exception e) {
                // 继续尝试下一个端点
            }
        }
        
        if (allFailed) {
            info.unavailable("无法连接到外部网络")
                .withFix("请检查网络连接或代理设置");
        }
        
        result.addDependency(info);
    }

    /**
     * 检测 TCP 连接
     */
    private boolean checkTcpConnection(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), connectionTimeoutMs);
            return true;
        } catch (IOException e) {
            logger.debug("无法连接到 {}:{}", host, port);
            return false;
        }
    }

    /**
     * 检测 REPL 环境
     */
    private void checkReplEnvironments(CheckerResult result) {
        // 检测 Python
        checkCommandAvailability(result, "Python", "python", "--version",
            "https://www.python.org/downloads/");
        
        // 检测 Node.js
        checkCommandAvailability(result, "Node.js", "node", "--version",
            "https://nodejs.org/");
    }

    /**
     * 检测 MCP 服务
     */
    private void checkMcpServices(CheckerResult result) {
        DependencyInfo info = DependencyInfo.of("MCP Services", "mcp");
        
        // 这是一个占位实现，实际的 MCP 检测需要连接到 MCP 客户端
        info.skipped("MCP 服务检测需要在运行时进行")
            .withFix("MCP 服务将在首次使用时自动检测");
        
        result.addDependency(info);
    }

    /**
     * 快速检测 - 仅检测关键依赖
     */
    public CheckerResult quickCheck() {
        CheckerResult result = CheckerResult.create();
        
        checkJavaEnvironment(result);
        checkBuildTools(result);
        
        return result.build();
    }

    /**
     * 检测指定类型的依赖
     */
    public DependencyInfo checkDependency(String name, String type) {
        DependencyInfo info = DependencyInfo.of(name, type);
        
        switch (type.toLowerCase()) {
            case "java":
            case "runtime":
                checkJavaEnvironment(CheckerResult.create().addDependency(info));
                break;
            case "build-tool":
            case "maven":
            case "gradle":
                checkBuildTools(CheckerResult.create().addDependency(info));
                break;
            case "lsp":
            case "language-server":
                checkLspServers(CheckerResult.create().addDependency(info));
                break;
            case "network":
            case "connectivity":
                checkNetworkConnectivity(CheckerResult.create().addDependency(info));
                break;
            case "repl":
            case "python":
            case "node":
                checkReplEnvironments(CheckerResult.create().addDependency(info));
                break;
            default:
                info.skipped("未知依赖类型: " + type);
        }
        
        return info;
    }
}
