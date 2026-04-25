package com.jwcode.parser;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.parser.model.CodeSymbol;
import com.jwcode.parser.model.ParseResult;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * Tree-sitter 解析服务客户端
 * 负责与 Python 解析服务通信
 */
@Slf4j
public class TreeSitterClient implements AutoCloseable {
    
    private static final String DEFAULT_HOST = "127.0.0.1";
    private static final int DEFAULT_PORT = 8765;
    private static final int DEFAULT_TIMEOUT_MS = 30000;
    
    private final String baseUrl;
    private final ObjectMapper objectMapper;
    private final ExecutorService executor;
    private Process pythonProcess;
    private boolean managedProcess = false;
    
    public TreeSitterClient() {
        this(DEFAULT_HOST, DEFAULT_PORT);
    }
    
    public TreeSitterClient(String host, int port) {
        this.baseUrl = String.format("http://%s:%d", host, port);
        this.objectMapper = new ObjectMapper();
        this.executor = Executors.newCachedThreadPool();
    }
    
    /**
     * 启动本地 Python 服务（嵌入式模式）
     */
    public static TreeSitterClient startEmbedded() throws IOException {
        String pythonPath = findPython();
        if (pythonPath == null) {
            throw new IOException("Python not found in PATH");
        }
        
        TreeSitterClient client = new TreeSitterClient();
        client.managedProcess = true;
        
        // 获取解析服务脚本路径
        Path scriptPath = findParserScript();
        
        ProcessBuilder pb = new ProcessBuilder(
            pythonPath,
            scriptPath.toString(),
            "--host", DEFAULT_HOST,
            "--port", String.valueOf(DEFAULT_PORT)
        );
        
        pb.redirectErrorStream(true);
        pb.inheritIO();
        
        log.info("Starting Tree-sitter parser service...");
        client.pythonProcess = pb.start();
        
        // 等待服务启动
        if (!client.waitForService(30000)) {
            client.close();
            throw new IOException("Parser service failed to start");
        }
        
        log.info("Tree-sitter parser service started successfully");
        return client;
    }
    
    /**
     * 等待服务启动
     */
    private boolean waitForService(int timeoutMs) {
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs) {
            try {
                if (healthCheck()) {
                    return true;
                }
            } catch (Exception e) {
                // 服务尚未就绪
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
    
    /**
     * 健康检查
     */
    public boolean healthCheck() throws IOException {
        try {
            String response = httpGet("/health");
            Map<String, Object> result = objectMapper.readValue(response, Map.class);
            return "healthy".equals(result.get("status"));
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * 解析文件
     */
    public ParseResult parseFile(Path filePath) throws IOException {
        return parseFile(filePath, null);
    }
    
    /**
     * 解析文件内容
     */
    public ParseResult parseFile(Path filePath, String content) throws IOException {
        String filePathStr = filePath.toAbsolutePath().toString().replace("\\", "/");
        
        Map<String, Object> request = Map.of(
            "file_path", filePathStr,
            "content", content
        );
        
        String response = httpPost("/parse", request);
        return parseResponse(response);
    }
    
    /**
     * 批量解析
     */
    public Map<String, ParseResult> parseBatch(List<Path> files) throws IOException {
        List<Map<String, Object>> fileRequests = files.stream()
            .map(p -> Map.of(
                "file_path", p.toAbsolutePath().toString().replace("\\", "/"),
                "content", (String) null
            ))
            .toList();
        
        Map<String, Object> request = Map.of("files", fileRequests);
        String response = httpPost("/parse/batch", request);
        
        Map<String, Object> result = objectMapper.readValue(response, Map.class);
        Map<String, Map<String, Object>> results = (Map<String, Map<String, Object>>) result.get("results");
        
        return results.entrySet().stream()
            .collect(java.util.stream.Collectors.toMap(
                Map.Entry::getKey,
                e -> parseSingleResponse(e.getValue())
            ));
    }
    
    /**
     * 获取指定位置的符号
     */
    public Optional<CodeSymbol> getSymbolAtPosition(Path filePath, int line, int col) throws IOException {
        Map<String, Object> request = Map.of(
            "file_path", filePath.toAbsolutePath().toString().replace("\\", "/"),
            "line", line,
            "col", col
        );
        
        String response = httpPost("/symbol-at-position", request);
        Map<String, Object> result = objectMapper.readValue(response, Map.class);
        
        if (Boolean.TRUE.equals(result.get("success"))) {
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            if (data != null) {
                return Optional.of(convertToCodeSymbol(data));
            }
        }
        return Optional.empty();
    }
    
    /**
     * 获取包含位置的作用域
     */
    public Optional<CodeSymbol> getEnclosingScope(Path filePath, int line) throws IOException {
        Map<String, Object> request = Map.of(
            "file_path", filePath.toAbsolutePath().toString().replace("\\", "/"),
            "line", line,
            "col", 0
        );
        
        String response = httpPost("/enclosing-scope", request);
        Map<String, Object> result = objectMapper.readValue(response, Map.class);
        
        if (Boolean.TRUE.equals(result.get("success"))) {
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            if (data != null) {
                return Optional.of(convertToCodeSymbol(data));
            }
        }
        return Optional.empty();
    }
    
    /**
     * 检测文件语言类型
     */
    public String detectLanguage(Path filePath) throws IOException {
        String url = baseUrl + "/detect-language?file_path=" + 
            filePath.toAbsolutePath().toString().replace("\\", "/");
        
        String response = httpGet(url.replace(" ", "%20"));
        Map<String, Object> result = objectMapper.readValue(response, Map.class);
        return (String) result.get("language");
    }
    
    // ============ 私有方法 ============
    
    private String httpGet(String path) throws IOException {
        URL url = new URL(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        conn.setReadTimeout(DEFAULT_TIMEOUT_MS);
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(java.util.stream.Collectors.joining("\n"));
        }
    }
    
    private String httpPost(String path, Object body) throws IOException {
        URL url = new URL(baseUrl + path);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);
        conn.setConnectTimeout(DEFAULT_TIMEOUT_MS);
        conn.setReadTimeout(DEFAULT_TIMEOUT_MS);
        
        String json = objectMapper.writeValueAsString(body);
        conn.getOutputStream().write(json.getBytes(StandardCharsets.UTF_8));
        
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(java.util.stream.Collectors.joining("\n"));
        }
    }
    
    private ParseResult parseResponse(String response) throws IOException {
        Map<String, Object> result = objectMapper.readValue(response, Map.class);
        return parseSingleResponse(result);
    }
    
    private ParseResult parseSingleResponse(Map<String, Object> result) {
        ParseResult parseResult = new ParseResult();
        
        if (Boolean.TRUE.equals(result.get("success"))) {
            Map<String, Object> data = (Map<String, Object>) result.get("data");
            if (data != null) {
                parseResult.setLanguage((String) data.get("language"));
                parseResult.setPackage((String) data.get("package"));
                parseResult.setImports((List<String>) data.get("imports"));
                parseResult.setErrors((List<String>) data.get("errors"));
                
                List<Map<String, Object>> symbols = (List<Map<String, Object>>) data.get("symbols");
                if (symbols != null) {
                    parseResult.setSymbols(symbols.stream()
                        .map(this::convertToCodeSymbol)
                        .toList());
                }
            }
        } else {
            String error = (String) result.get("error");
            parseResult.setErrors(List.of(error != null ? error : "Unknown error"));
        }
        
        return parseResult;
    }
    
    private CodeSymbol convertToCodeSymbol(Map<String, Object> data) {
        CodeSymbol symbol = new CodeSymbol();
        symbol.setName((String) data.get("name"));
        symbol.setKind(CodeSymbol.SymbolKind.valueOf(((String) data.get("kind")).toUpperCase()));
        symbol.setStartLine((Integer) data.get("start_line"));
        symbol.setStartCol((Integer) data.get("start_col"));
        symbol.setEndLine((Integer) data.get("end_line"));
        symbol.setEndCol((Integer) data.get("end_col"));
        symbol.setSignature((String) data.get("signature"));
        symbol.setDocstring((String) data.get("docstring"));
        symbol.setParent((String) data.get("parent"));
        symbol.setChildren((List<String>) data.get("children"));
        symbol.setModifiers((List<String>) data.get("modifiers"));
        return symbol;
    }
    
    private static String findPython() {
        String[] commands = {"python3", "python", "py"};
        for (String cmd : commands) {
            try {
                Process process = new ProcessBuilder(cmd, "--version").start();
                if (process.waitFor() == 0) {
                    return cmd;
                }
            } catch (Exception e) {
                // 尝试下一个
            }
        }
        return null;
    }
    
    private static Path findParserScript() {
        // 尝试多种路径查找方式
        Path[] candidates = {
            Path.of("jwcode-parser/src/main/python/api_server.py"),
            Path.of("src/main/python/api_server.py"),
            Path.of("../jwcode-parser/src/main/python/api_server.py"),
        };
        
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate.toAbsolutePath();
            }
        }
        
        throw new RuntimeException("Parser script not found");
    }
    
    @Override
    public void close() {
        executor.shutdown();
        
        if (managedProcess && pythonProcess != null && pythonProcess.isAlive()) {
            log.info("Stopping Tree-sitter parser service...");
            pythonProcess.destroy();
            try {
                if (!pythonProcess.waitFor(5, TimeUnit.SECONDS)) {
                    pythonProcess.destroyForcibly();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                pythonProcess.destroyForcibly();
            }
        }
    }
}
