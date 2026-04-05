package com.jwcode.core.lsp;

import java.util.List;
import java.util.Map;

/**
 * LspModels - LSP 数据模型类
 * 
 * 功能说明：
 * 定义 LSP 协议中使用的数据模型。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public class LspModels {
    
    /**
     * LSP 悬停信息
     */
    public static class LspHover {
        public String contents;
        public LspRange range;
        
        public LspHover() {}
        
        public LspHover(String contents, LspRange range) {
            this.contents = contents;
            this.range = range;
        }
    }
    
    /**
     * LSP 位置信息
     */
    public static class LspLocation {
        public String uri;
        public LspRange range;
        
        public LspLocation() {}
        
        public LspLocation(String uri, LspRange range) {
            this.uri = uri;
            this.range = range;
        }
    }
    
    /**
     * LSP 范围
     */
    public static class LspRange {
        public LspPosition start;
        public LspPosition end;
        
        public LspRange() {}
        
        public LspRange(LspPosition start, LspPosition end) {
            this.start = start;
            this.end = end;
        }
    }
    
    /**
     * LSP 位置
     */
    public static class LspPosition {
        public int line;
        public int column;
        
        public LspPosition() {}
        
        public LspPosition(int line, int column) {
            this.line = line;
            this.column = column;
        }
    }
    
    /**
     * LSP 工作区编辑
     */
    public static class LspWorkspaceEdit {
        public List<LspTextDocumentEdit> documentChanges;
        public Map<String, List<LspTextEdit>> changes;
        
        public LspWorkspaceEdit() {}
        
        public LspWorkspaceEdit(List<LspTextDocumentEdit> documentChanges, 
                                Map<String, List<LspTextEdit>> changes) {
            this.documentChanges = documentChanges;
            this.changes = changes;
        }
    }
    
    /**
     * LSP 文档编辑
     */
    public static class LspTextDocumentEdit {
        public LspVersionedTextDocumentIdentifier textDocument;
        public List<LspTextEdit> edits;
        
        public LspTextDocumentEdit() {}
        
        public LspTextDocumentEdit(LspVersionedTextDocumentIdentifier textDocument, 
                                   List<LspTextEdit> edits) {
            this.textDocument = textDocument;
            this.edits = edits;
        }
    }
    
    /**
     * LSP 版本化文档标识符
     */
    public static class LspVersionedTextDocumentIdentifier {
        public String uri;
        public int version;
        
        public LspVersionedTextDocumentIdentifier() {}
        
        public LspVersionedTextDocumentIdentifier(String uri, int version) {
            this.uri = uri;
            this.version = version;
        }
    }
    
    /**
     * LSP 文本编辑
     */
    public static class LspTextEdit {
        public LspRange range;
        public String newText;
        
        public LspTextEdit() {}
        
        public LspTextEdit(LspRange range, String newText) {
            this.range = range;
            this.newText = newText;
        }
    }
    
    /**
     * LSP 代码操作
     */
    public static class LspCodeAction {
        public String title;
        public String kind;
        public LspWorkspaceEdit edit;
        public String command;
        
        public LspCodeAction() {}
        
        public LspCodeAction(String title, String kind, LspWorkspaceEdit edit, String command) {
            this.title = title;
            this.kind = kind;
            this.edit = edit;
            this.command = command;
        }
    }
    
    /**
     * LSP 诊断信息
     */
    public static class LspDiagnostic {
        public LspRange range;
        public String message;
        public Integer severity;
        public String code;
        public String source;
        
        public LspDiagnostic() {}
        
        public LspDiagnostic(LspRange range, String message, Integer severity, 
                            String code, String source) {
            this.range = range;
            this.message = message;
            this.severity = severity;
            this.code = code;
            this.source = source;
        }
    }
    
    /**
     * LSP 完成项
     */
    public static class LspCompletionItem {
        public String label;
        public String kind;
        public String detail;
        public String documentation;
        public String insertText;
        
        public LspCompletionItem() {}
        
        public LspCompletionItem(String label, String kind, String detail, 
                                String documentation, String insertText) {
            this.label = label;
            this.kind = kind;
            this.detail = detail;
            this.documentation = documentation;
            this.insertText = insertText;
        }
    }
    
    /**
     * LSP 符号信息
     */
    public static class LspSymbol {
        public String name;
        public String kind;
        public LspRange location;
        public List<LspSymbol> children;
        
        public LspSymbol() {}
        
        public LspSymbol(String name, String kind, LspRange location, List<LspSymbol> children) {
            this.name = name;
            this.kind = kind;
            this.location = location;
            this.children = children;
        }
    }
}