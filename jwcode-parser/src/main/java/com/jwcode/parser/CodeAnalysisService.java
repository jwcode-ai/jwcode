package com.jwcode.parser;

import com.jwcode.parser.model.ParseResult;
import com.jwcode.parser.model.SemanticInfo;
import com.jwcode.parser.model.TypeDescriptor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 代码分析服务 — parser 模块的统一入口。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>协调 JavaAstParser（本地解析）和 TreeSitterClient（远程解析）</li>
 *   <li>提供缓存、批量解析、增量更新等高级功能</li>
 *   <li>生成跨文件的引用分析报告</li>
 * </ul>
 *
 * <p>使用方式：</p>
 * <pre>{@code
 * CodeAnalysisService service = new CodeAnalysisService();
 * ParseResult result = service.analyze(Paths.get("src/main/java/com/example/Main.java"));
 * }</pre>
 */
public class CodeAnalysisService implements AutoCloseable {

    private final JavaAstParser localParser;
    private final TreeSitterClient remoteClient;
    private final boolean remoteAvailable;

    /** 解析结果缓存 */
    private final Map<Path, ParseResult> cache = new ConcurrentHashMap<>();

    /** 语义分析结果缓存 */
    private final Map<Path, SemanticInfo> semanticCache = new ConcurrentHashMap<>();

    public CodeAnalysisService() {
        this.localParser = new JavaAstParser();
        this.remoteClient = new TreeSitterClient();
        this.remoteAvailable = false; // 默认不启用远程服务
    }

    public CodeAnalysisService(TreeSitterClient remoteClient) {
        this.localParser = new JavaAstParser();
        this.remoteClient = remoteClient;
        this.remoteAvailable = remoteClient != null;
    }

    /**
     * 分析单个文件
     *
     * @param filePath Java 源文件路径
     * @return 解析结果
     * @throws IOException 如果文件读取失败
     */
    public ParseResult analyze(Path filePath) throws IOException {
        // 检查缓存
        ParseResult cached = cache.get(filePath);
        if (cached != null) {
            return cached;
        }

        // 使用本地解析器
        ParseResult result = localParser.parseFile(filePath);

        // 如果远程服务可用，尝试增强结果
        if (remoteAvailable) {
            try {
                ParseResult remoteResult = remoteClient.parseFile(filePath);
                enhanceWithRemote(result, remoteResult);
            } catch (Exception e) {
                // 远程失败不影响本地结果
            }
        }

        cache.put(filePath, result);
        return result;
    }

    /**
     * 分析源码字符串
     *
     * @param source   Java 源码
     * @param filePath 文件路径（用于缓存）
     * @return 解析结果
     */
    public ParseResult analyzeSource(String source, Path filePath) {
        ParseResult result = localParser.parseSource(source, filePath);
        cache.put(filePath, result);
        return result;
    }

    /**
     * 批量分析多个文件
     *
     * @param files Java 源文件路径列表
     * @return 文件路径到解析结果的映射
     */
    public Map<Path, ParseResult> analyzeBatch(List<Path> files) {
        Map<Path, ParseResult> results = new LinkedHashMap<>();
        for (Path file : files) {
            try {
                results.put(file, analyze(file));
            } catch (IOException e) {
                ParseResult errorResult = new ParseResult();
                errorResult.setLanguage("java");
                errorResult.setErrors(List.of("IO error: " + e.getMessage()));
                results.put(file, errorResult);
            }
        }
        return results;
    }

    /**
     * 执行语义分析
     *
     * @param filePath Java 源文件路径
     * @return 语义分析结果
     * @throws IOException 如果文件读取失败
     */
    public SemanticInfo analyzeSemantics(Path filePath) throws IOException {
        SemanticInfo cached = semanticCache.get(filePath);
        if (cached != null) {
            return cached;
        }

        ParseResult parseResult = analyze(filePath);
        String source = java.nio.file.Files.readString(filePath);
        SemanticInfo info = localParser.analyzeSemantics(source, parseResult);
        semanticCache.put(filePath, info);
        return info;
    }

    /**
     * 跨文件引用分析 — 找出所有文件中引用了指定类型的文件
     *
     * @param files     要分析的文件列表
     * @param typeName  目标类型名称
     * @return 引用了该类型的文件路径列表
     */
    public List<Path> findTypeReferences(List<Path> files, String typeName) {
        List<Path> results = new ArrayList<>();
        for (Path file : files) {
            try {
                SemanticInfo info = analyzeSemantics(file);
                if (info.getTypeReferences() != null &&
                    info.getTypeReferences().contains(typeName)) {
                    results.add(file);
                }
            } catch (IOException e) {
                // 跳过无法读取的文件
            }
        }
        return results;
    }

    /**
     * 获取项目中所有类型的依赖图
     *
     * @param files 项目中的所有 Java 文件
     * @return 类型名到依赖类型列表的映射
     */
    public Map<String, List<String>> buildDependencyGraph(List<Path> files) {
        Map<String, List<String>> graph = new LinkedHashMap<>();
        for (Path file : files) {
            try {
                ParseResult result = analyze(file);
                SemanticInfo info = analyzeSemantics(file);

                // 以包名作为节点
                String pkg = result.getPackageName() != null ? result.getPackageName() : "(default)";
                List<String> deps = info.getTypeReferences() != null
                        ? new ArrayList<>(info.getTypeReferences())
                        : new ArrayList<>();

                // 过滤掉自引用
                String selfName = file.getFileName().toString().replace(".java", "");
                deps.remove(selfName);

                graph.merge(pkg, deps, (old, neu) -> {
                    Set<String> merged = new LinkedHashSet<>(old);
                    merged.addAll(neu);
                    return new ArrayList<>(merged);
                });
            } catch (IOException e) {
                // 跳过无法读取的文件
            }
        }
        return graph;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        cache.clear();
        semanticCache.clear();
    }

    /**
     * 从缓存中移除指定文件
     */
    public void invalidate(Path filePath) {
        cache.remove(filePath);
        semanticCache.remove(filePath);
    }

    /**
     * 用远程解析结果增强本地结果
     */
    private void enhanceWithRemote(ParseResult local, ParseResult remote) {
        // 如果远程结果有更精确的行号信息，使用远程的
        if (remote.getSymbols() != null && !remote.getSymbols().isEmpty()) {
            // 合并符号：远程结果优先
            local.getSymbols().forEach(localSym -> {
                remote.getSymbols().stream()
                        .filter(r -> r.getName().equals(localSym.getName()))
                        .findFirst()
                        .ifPresent(remoteSym -> {
                            localSym.setStartLine(remoteSym.getStartLine());
                            localSym.setStartCol(remoteSym.getStartCol());
                            localSym.setEndLine(remoteSym.getEndLine());
                            localSym.setEndCol(remoteSym.getEndCol());
                        });
            });
        }

        // 合并错误信息
        if (remote.getErrors() != null && !remote.getErrors().isEmpty()) {
            List<String> merged = new ArrayList<>();
            if (local.getErrors() != null) merged.addAll(local.getErrors());
            merged.addAll(remote.getErrors());
            local.setErrors(merged);
        }
    }

    @Override
    public void close() {
        cache.clear();
        semanticCache.clear();
        if (remoteClient != null) {
            try {
                remoteClient.close();
            } catch (Exception e) {
                // 忽略关闭错误
            }
        }
    }
}
