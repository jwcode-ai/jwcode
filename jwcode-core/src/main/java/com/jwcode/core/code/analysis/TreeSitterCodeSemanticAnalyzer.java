package com.jwcode.core.code.analysis;

import com.jwcode.core.code.api.QueryMatch;
import com.jwcode.core.code.api.BuiltinQueryTemplates;
import com.jwcode.core.code.engine.DefaultSyntaxEngine;
import com.jwcode.core.code.engine.SyntaxEngine;
import com.jwcode.core.tool.analysis.CodeSemanticAnalyzer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link CodeSemanticAnalyzer} 的 TreeSitter 后端实现。
 *
 * <p>使用 {@link DefaultSyntaxEngine} 解析代码，
 * {@link IncrementalAnalysisEngine} 管理增量分析和符号图谱。</p>
 */
public class TreeSitterCodeSemanticAnalyzer implements CodeSemanticAnalyzer {

    private final SyntaxEngine syntaxEngine;

    public TreeSitterCodeSemanticAnalyzer() {
        this(new DefaultSyntaxEngine());
    }

    public TreeSitterCodeSemanticAnalyzer(SyntaxEngine syntaxEngine) {
        this.syntaxEngine = syntaxEngine;
    }

    @Override
    public CodeAnalysisResult analyze(Path projectRoot) {
        // Create a fresh incremental engine for this project
        var projectAnalyzer = new com.jwcode.core.advanced.analyzer.SmartProjectAnalyzer(projectRoot.toString());
        var incrementalEngine = new IncrementalAnalysisEngine(syntaxEngine, projectAnalyzer);
        try {
            var report = incrementalEngine.analyzeProject(projectRoot);
            var stats = incrementalEngine.getStats();
            return new CodeAnalysisResult(
                report.analyzedFiles(),
                report.totalFiles() - report.analyzedFiles(), // approximated cached
                stats.symbolNodes(),
                stats.symbolEdges(),
                List.of()
            );
        } finally {
            incrementalEngine.shutdown();
        }
    }

    @Override
    public List<Map<String, Object>> query(Path projectRoot, String queryPattern) {
        var projectAnalyzer = new com.jwcode.core.advanced.analyzer.SmartProjectAnalyzer(projectRoot.toString());
        var incrementalEngine = new IncrementalAnalysisEngine(syntaxEngine, projectAnalyzer);
        try {
            incrementalEngine.analyzeProject(projectRoot);
            List<QueryMatch> matches = incrementalEngine.queryProject(queryPattern);
            return convertMatches(matches);
        } finally {
            incrementalEngine.shutdown();
        }
    }

    @Override
    public List<Map<String, Object>> queryByTemplate(Path projectRoot, String language, String templateName) {
        BuiltinQueryTemplates template = BuiltinQueryTemplates.find(language, templateName);
        if (template == null) {
            return List.of();
        }
        return query(projectRoot, template.getPattern());
    }

    private List<Map<String, Object>> convertMatches(List<QueryMatch> matches) {
        List<Map<String, Object>> result = new ArrayList<>();
        int count = 0;
        for (QueryMatch match : matches) {
            if (count++ >= 100) break; // limit results
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("file", match.getFile());
            map.put("range", match.getRange().toString());
            map.put("text", match.toPreview(80));
            Map<String, String> captures = new LinkedHashMap<>();
            match.getCaptures().forEach((k, v) -> captures.put(k, v.getText()));
            map.put("captures", captures);
            result.add(map);
        }
        return result;
    }
}
