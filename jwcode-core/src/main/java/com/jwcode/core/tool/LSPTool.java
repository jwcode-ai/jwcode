package com.jwcode.core.tool;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.lsp.*;
import com.jwcode.core.tool.context.ToolExecutionContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * LSPTool - LSP 工具
 * 
 * 功能说明：
 * 提供与 Language Server Protocol 集成的工具接口。
 * 支持代码智能提示、定义跳转、引用查找、重命名、格式化等操作。
 * 
 * 核心特性：
 * - hover: 悬停提示
 * - definition: 跳转到定义
 * - references: 查找引用
 * - rename: 重命名符号
 * - format: 代码格式化
 * - codeAction: 快速修复
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LSPTool implements Tool<LSPTool.Input, LSPTool.Output, LSPTool.Progress> {
    
    private static final Logger LOGGER = Logger.getLogger(LSPTool.class.getName());
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    private final LspServerManager serverManager;
    
    /**
     * 工具输入参数
     */
    public record Input(
        /** 操作类型: hover, definition, references, rename, format, codeAction */
        @JsonProperty("action") String action,
        
        /** 文件路径 */
        @JsonProperty("filePath") String filePath,
        
        /** 行号（从 0 开始） */
        @JsonProperty("line") Integer line,
        
        /** 列号（从 0 开始） */
        @JsonProperty("column") Integer column,
        
        /** 新名称（用于 rename 操作） */
        @JsonProperty("newName") String newName,
        
        /** 语言 ID（可选，自动检测） */
        @JsonProperty("languageId") String languageId,
        
        /** 文档内容（用于同步） */
        @JsonProperty("content") String content,
        
        /** 文档版本 */
        @JsonProperty("version") Integer version
    ) {
        public Input {
            if (version == null) {
                version = 1;
            }
        }
    }
    
    /**
     * 工具输出结果
     */
    public record Output(
        @JsonProperty("success") boolean success,
        @JsonProperty("message") String message,
        @JsonProperty("action") String action,
        @JsonProperty("hoverInfo") HoverInfo hoverInfo,
        @JsonProperty("locations") List<LocationInfo> locations,
        @JsonProperty("edits") List<TextEditInfo> edits,
        @JsonProperty("codeActions") List<CodeActionInfo> codeActions,
        @JsonProperty("diagnostics") List<DiagnosticInfo> diagnostics
    ) {
        public Output() {
            this(true, "", null, null, null, null, null, null);
        }
        
        public Output(boolean success, String message) {
            this(success, message, null, null, null, null, null, null);
        }
    }
    
    /**
     * 悬停信息
     */
    public record HoverInfo(
        @JsonProperty("contents") String contents,
        @JsonProperty("range") RangeInfo range
    ) {}
    
    /**
     * 位置信息
     */
    public record LocationInfo(
        @JsonProperty("uri") String uri,
        @JsonProperty("path") String path,
        @JsonProperty("range") RangeInfo range,
        @JsonProperty("preview") String preview
    ) {}
    
    /**
     * 范围信息
     */
    public record RangeInfo(
        @JsonProperty("startLine") int startLine,
        @JsonProperty("startCharacter") int startCharacter,
        @JsonProperty("endLine") int endLine,
        @JsonProperty("endCharacter") int endCharacter
    ) {
        public RangeInfo(LspRange range) {
            this(
                range.getStart().getLine(),
                range.getStart().getCharacter(),
                range.getEnd().getLine(),
                range.getEnd().getCharacter()
            );
        }
    }
    
    /**
     * 文本编辑信息
     */
    public record TextEditInfo(
        @JsonProperty("range") RangeInfo range,
        @JsonProperty("newText") String newText
    ) {}
    
    /**
     * 代码操作信息
     */
    public record CodeActionInfo(
        @JsonProperty("title") String title,
        @JsonProperty("kind") String kind,
        @JsonProperty("edits") List<TextEditInfo> edits,
        @JsonProperty("isPreferred") boolean isPreferred
    ) {}
    
    /**
     * 诊断信息
     */
    public record DiagnosticInfo(
        @JsonProperty("message") String message,
        @JsonProperty("severity") String severity,
        @JsonProperty("range") RangeInfo range,
        @JsonProperty("source") String source,
        @JsonProperty("code") String code
    ) {}
    
    /**
     * 进度信息
     */
    public record Progress(
        @JsonProperty("stage") String stage,
        @JsonProperty("percentage") int percentage
    ) {}
    
    public LSPTool() {
        this.serverManager = new LspServerManager();
    }
    
    public LSPTool(LspServerManager serverManager) {
        this.serverManager = serverManager != null ? serverManager : new LspServerManager();
    }
    
    @Override
    public String getName() {
        return "LSP";
    }
    
    @Override
    public String getDescription() {
        return "Language Server Protocol - 提供代码智能提示、定义跳转、引用查找等功能";
    }
    
    @Override
    public String getPrompt() {
        return """
            LSP (Language Server Protocol) 工具提供以下功能：
            
            1. hover: 获取光标位置的类型信息、文档注释等悬停提示
            2. definition: 跳转到符号的定义位置
            3. references: 查找符号的所有引用位置
            4. rename: 重命名符号并获取需要修改的位置
            5. format: 格式化代码
            6. codeAction: 获取快速修复建议（如自动导入、修复错误等）
            
            使用示例：
            - hover: 提供 filePath, line, column
            - definition: 提供 filePath, line, column
            - references: 提供 filePath, line, column
            - rename: 提供 filePath, line, column, newName
            - format: 提供 filePath
            - codeAction: 提供 filePath, line, column
            """;
    }
    
    @Override
    public JsonNode getInputSchema() {
        try {
            return MAPPER.readTree("""
                {
                    "type": "object",
                    "properties": {
                        "action": {
                            "type": "string",
                            "enum": ["hover", "definition", "references", "rename", "format", "codeAction"],
                            "description": "LSP 操作类型"
                        },
                        "filePath": {
                            "type": "string",
                            "description": "文件路径"
                        },
                        "line": {
                            "type": "integer",
                            "description": "行号（从 0 开始）"
                        },
                        "column": {
                            "type": "integer",
                            "description": "列号（从 0 开始）"
                        },
                        "newName": {
                            "type": "string",
                            "description": "新名称（用于 rename 操作）"
                        },
                        "languageId": {
                            "type": "string",
                            "description": "语言 ID（可选，自动检测）"
                        },
                        "content": {
                            "type": "string",
                            "description": "文档内容（用于同步）"
                        },
                        "version": {
                            "type": "integer",
                            "description": "文档版本"
                        }
                    },
                    "required": ["action", "filePath"]
                }
                """);
        } catch (Exception e) {
            return null;
        }
    }
    
    @Override
    public CompletableFuture<ToolResult<Output>> call(
            Input input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Progress>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                String action = input.action();
                if (action == null || action.isEmpty()) {
                    return ToolResult.error("操作类型不能为空");
                }
                
                String filePath = input.filePath();
                if (filePath == null || filePath.isEmpty()) {
                    return ToolResult.error("文件路径不能为空");
                }
                
                // 确保文件存在
                Path path = Paths.get(filePath);
                if (!Files.exists(path)) {
                    return ToolResult.error("文件不存在: " + filePath);
                }
                
                // 获取或检测语言 ID
                String languageId = input.languageId();
                if (languageId == null || languageId.isEmpty()) {
                    languageId = detectLanguageId(filePath);
                }
                
                if (languageId == null) {
                    return ToolResult.error("无法检测文件类型: " + filePath);
                }
                
                // 报告进度
                reportProgress(onProgress, "准备", 10);
                
                // 获取或启动 LSP 客户端
                LspService client = getOrStartClient(languageId, context);
                if (client == null) {
                    return ToolResult.error("无法启动语言服务器: " + languageId);
                }
                
                // 同步文档
                reportProgress(onProgress, "同步文档", 20);
                syncDocument(client, filePath, input.content(), languageId, input.version());
                
                // 执行操作
                reportProgress(onProgress, "执行操作", 50);
                Output output = executeAction(action, client, input, filePath, languageId);
                
                reportProgress(onProgress, "完成", 100);
                
                return ToolResult.success(output);
                
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "LSP tool execution failed", e);
                return ToolResult.error("LSP 操作失败: " + e.getMessage());
            }
        });
    }
    
    /**
     * 执行具体的 LSP 操作
     */
    private Output executeAction(String action, LspService client, Input input, 
                                  String filePath, String languageId) throws Exception {
        return switch (action.toLowerCase()) {
            case "hover" -> executeHover(client, input, filePath);
            case "definition" -> executeDefinition(client, input, filePath);
            case "references" -> executeReferences(client, input, filePath);
            case "rename" -> executeRename(client, input, filePath);
            case "format" -> executeFormat(client, filePath);
            case "codeaction", "code_action" -> executeCodeAction(client, input, filePath);
            default -> new Output(false, "不支持的操作类型: " + action);
        };
    }
    
    /**
     * 执行悬停提示
     */
    private Output executeHover(LspService client, Input input, String filePath) throws Exception {
        int line = input.line() != null ? input.line() : 0;
        int column = input.column() != null ? input.column() : 0;
        
        LspHover hover = client.hover(filePath, line, column).get();
        
        HoverInfo hoverInfo = null;
        if (hover != null && hover.getContents() != null) {
            RangeInfo range = hover.getRange() != null ? new RangeInfo(hover.getRange()) : null;
            hoverInfo = new HoverInfo(hover.getContents(), range);
        }
        
        String message = hoverInfo != null ? "获取悬停信息成功" : "未找到悬停信息";
        return new Output(true, message, "hover", hoverInfo, null, null, null, null);
    }
    
    /**
     * 执行定义跳转
     */
    private Output executeDefinition(LspService client, Input input, String filePath) throws Exception {
        int line = input.line() != null ? input.line() : 0;
        int column = input.column() != null ? input.column() : 0;
        
        List<LspLocation> locations = client.definition(filePath, line, column).get();
        List<LocationInfo> locationInfos = convertLocations(locations);
        
        String message = locationInfos.isEmpty() ? "未找到定义" : 
                        "找到 " + locationInfos.size() + " 个定义位置";
        return new Output(true, message, "definition", null, locationInfos, null, null, null);
    }
    
    /**
     * 执行引用查找
     */
    private Output executeReferences(LspService client, Input input, String filePath) throws Exception {
        int line = input.line() != null ? input.line() : 0;
        int column = input.column() != null ? input.column() : 0;
        
        List<LspLocation> locations = client.references(filePath, line, column).get();
        List<LocationInfo> locationInfos = convertLocations(locations);
        
        String message = locationInfos.isEmpty() ? "未找到引用" : 
                        "找到 " + locationInfos.size() + " 个引用位置";
        return new Output(true, message, "references", null, locationInfos, null, null, null);
    }
    
    /**
     * 执行重命名
     */
    private Output executeRename(LspService client, Input input, String filePath) throws Exception {
        int line = input.line() != null ? input.line() : 0;
        int column = input.column() != null ? input.column() : 0;
        String newName = input.newName();
        
        if (newName == null || newName.isEmpty()) {
            return new Output(false, "重命名操作需要提供 newName 参数");
        }
        
        LspWorkspaceEdit edit = client.rename(filePath, line, column, newName).get();
        List<TextEditInfo> edits = convertWorkspaceEdit(edit);
        
        String message = edits.isEmpty() ? "无需重命名" : 
                        "需要修改 " + edits.size() + " 处位置";
        return new Output(true, message, "rename", null, null, edits, null, null);
    }
    
    /**
     * 执行格式化
     */
    private Output executeFormat(LspService client, String filePath) throws Exception {
        List<LspTextEdit> edits = client.format(filePath).get();
        List<TextEditInfo> editInfos = edits.stream()
            .map(e -> new TextEditInfo(
                e.getRange() != null ? new RangeInfo(e.getRange()) : null,
                e.getNewText()
            ))
            .collect(Collectors.toList());
        
        String message = editInfos.isEmpty() ? "无需格式化" : 
                        "格式化需要修改 " + editInfos.size() + " 处";
        return new Output(true, message, "format", null, null, editInfos, null, null);
    }
    
    /**
     * 执行代码操作
     */
    private Output executeCodeAction(LspService client, Input input, String filePath) throws Exception {
        int line = input.line() != null ? input.line() : 0;
        int column = input.column() != null ? input.column() : 0;
        
        List<LspCodeAction> actions = client.codeAction(filePath, line, column).get();
        List<CodeActionInfo> actionInfos = actions.stream()
            .map(a -> {
                List<TextEditInfo> edits = new ArrayList<>();
                if (a.getEdit() != null && a.getEdit().getChanges() != null) {
                    for (List<LspTextEdit> textEdits : a.getEdit().getChanges().values()) {
                        for (LspTextEdit edit : textEdits) {
                            edits.add(new TextEditInfo(
                                edit.getRange() != null ? new RangeInfo(edit.getRange()) : null,
                                edit.getNewText()
                            ));
                        }
                    }
                }
                return new CodeActionInfo(
                    a.getTitle(),
                    a.getKind(),
                    edits,
                    false
                );
            })
            .collect(Collectors.toList());
        
        // 获取诊断信息
        String uri = filePath;
        List<LspDiagnosticRegistry.LspDiagnostic> diagnostics = 
            serverManager.getDiagnosticRegistry().getDiagnostics(uri);
        List<DiagnosticInfo> diagnosticInfos = diagnostics.stream()
            .map(d -> {
                LspDiagnosticRegistry.LspRange dRange = d.getRange();
                LspRange lspRange = dRange != null ? new LspRange(
                    new LspPosition(dRange.getStart().getLine(), dRange.getStart().getCharacter()),
                    new LspPosition(dRange.getEnd().getLine(), dRange.getEnd().getCharacter())
                ) : null;
                return new DiagnosticInfo(
                    d.getMessage(),
                    d.getSeverity().name(),
                    lspRange != null ? new RangeInfo(lspRange) : null,
                    d.getSource(),
                    d.getCode()
                );
            })
            .collect(Collectors.toList());
        
        String message = actionInfos.isEmpty() ? "没有可用的代码操作" : 
                        "找到 " + actionInfos.size() + " 个代码操作";
        return new Output(true, message, "codeAction", null, null, null, actionInfos, diagnosticInfos);
    }
    
    /**
     * 获取或启动 LSP 客户端
     */
    private LspService getOrStartClient(String languageId, ToolExecutionContext context) throws Exception {
        // 检查是否已有运行中的服务器
        LspService client = serverManager.getServerClient(languageId);
        if (client != null) {
            return client;
        }
        
        // 更新工作区根目录
        String workingDir = context.getWorkingDirectory() != null ? context.getWorkingDirectory().toString() : null;
        if (workingDir != null) {
            serverManager.setWorkspaceRoot(workingDir);
        }
        
        // 启动服务器
        LspServerManager.ServerWrapper wrapper = 
            serverManager.startServer(languageId).get();
        
        return wrapper != null ? wrapper.getClient() : null;
    }
    
    /**
     * 同步文档
     */
    private void syncDocument(LspService client, String filePath, String content, 
                              String languageId, int version) {
        try {
            if (content != null) {
                // 使用提供的内容
                client.openDocument(filePath, content, languageId, version);
            } else {
                // 从文件读取内容
                Path path = Paths.get(filePath);
                if (Files.exists(path)) {
                    String fileContent = Files.readString(path);
                    client.openDocument(filePath, fileContent, languageId, version);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Failed to sync document", e);
        }
    }
    
    /**
     * 检测语言 ID
     */
    private String detectLanguageId(String filePath) {
        Path path = Paths.get(filePath);
        String fileName = path.getFileName().toString().toLowerCase();
        int dotIndex = fileName.lastIndexOf('.');
        
        if (dotIndex > 0) {
            String extension = fileName.substring(dotIndex);
            return switch (extension) {
                case ".java" -> "java";
                case ".js", ".jsx" -> "javascript";
                case ".ts", ".tsx" -> "typescript";
                case ".py" -> "python";
                case ".rs" -> "rust";
                case ".go" -> "go";
                case ".c", ".cpp", ".cc", ".h", ".hpp" -> "c";
                case ".cs" -> "csharp";
                case ".rb" -> "ruby";
                case ".php" -> "php";
                case ".swift" -> "swift";
                case ".kt" -> "kotlin";
                case ".scala" -> "scala";
                default -> null;
            };
        }
        return null;
    }
    
    /**
     * 转换位置列表
     */
    private List<LocationInfo> convertLocations(List<LspLocation> locations) {
        if (locations == null) {
            return new ArrayList<>();
        }
        
        return locations.stream()
            .map(loc -> {
                String uri = loc.getUri();
                String path = uriToPath(uri);
                RangeInfo range = loc.getRange() != null ? new RangeInfo(loc.getRange()) : null;
                String preview = readPreview(path, range);
                return new LocationInfo(uri, path, range, preview);
            })
            .collect(Collectors.toList());
    }
    
    /**
     * 转换工作区编辑
     */
    private List<TextEditInfo> convertWorkspaceEdit(LspWorkspaceEdit edit) {
        List<TextEditInfo> result = new ArrayList<>();
        if (edit == null) {
            return result;
        }
        
        if (edit.getChanges() != null) {
            for (List<LspTextEdit> edits : edit.getChanges().values()) {
                for (LspTextEdit e : edits) {
                    result.add(new TextEditInfo(
                        e.getRange() != null ? new RangeInfo(e.getRange()) : null,
                        e.getNewText()
                    ));
                }
            }
        }
        
        return result;
    }
    
    /**
     * URI 转路径
     */
    private String uriToPath(String uri) {
        if (uri.startsWith("file:///")) {
            return uri.substring(8);
        } else if (uri.startsWith("file://")) {
            return uri.substring(7);
        }
        return uri;
    }
    
    /**
     * 路径转 URI
     */
    private String pathToUri(String path) {
        return Paths.get(path).toUri().toString();
    }
    
    /**
     * 读取预览文本
     */
    private String readPreview(String path, RangeInfo range) {
        try {
            if (path == null || range == null) {
                return null;
            }
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return null;
            }
            
            List<String> lines = Files.readAllLines(filePath);
            if (range.startLine() < lines.size()) {
                return lines.get(range.startLine()).trim();
            }
        } catch (Exception e) {
            // 忽略预览读取错误
        }
        return null;
    }
    
    /**
     * 报告进度
     */
    private void reportProgress(Consumer<ToolProgress<Progress>> onProgress, 
                                String stage, int percentage) {
        if (onProgress != null) {
            try {
                onProgress.accept(new ToolProgress<>(new Progress(stage, percentage), null));
            } catch (Exception e) {
                LOGGER.log(Level.FINE, "Progress report error", e);
            }
        }
    }
    
    @Override
    public TypeReference<Input> getInputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public TypeReference<Output> getOutputType() {
        return new TypeReference<>() {};
    }
    
    @Override
    public ToolValidationResult validate(Input input) {
        if (input == null) {
            return ToolValidationResult.invalid("输入不能为空");
        }
        
        if (input.action() == null || input.action().isEmpty()) {
            return ToolValidationResult.invalid("操作类型不能为空");
        }
        
        List<String> validActions = Arrays.asList(
            "hover", "definition", "references", "rename", "format", "codeAction", "code_action"
        );
        if (!validActions.contains(input.action().toLowerCase())) {
            return ToolValidationResult.invalid("不支持的操作类型: " + input.action());
        }
        
        if (input.filePath() == null || input.filePath().isEmpty()) {
            return ToolValidationResult.invalid("文件路径不能为空");
        }
        
        return ToolValidationResult.valid();
    }
    
    @Override
    public boolean isReadOnly(Input input) {
        // 所有 LSP 操作都是只读的，不修改文件
        return true;
    }
    
    @Override
    public boolean isDestructive(Input input) {
        return false;
    }
}
