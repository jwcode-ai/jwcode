# SmartAnalyzeTool 代码智能分析架构设计

## 设计目标

让 SmartAnalyzeTool 从"项目结构分析"进化为"代码语义理解"，同时保持现有优势：

1. **渐进增强** - 现有功能零破坏，新能力可插拔
2. **多引擎支持** - 支持 Tree-sitter、ANTLR、LSP 等多种解析后端
3. **统一抽象** - 无论底层是什么解析器，上层接口一致
4. **增量智能** - 文件变更时只更新变化部分，高效支持大项目

## 架构分层

```
┌─────────────────────────────────────────────────────────────────┐
│  Layer 4: 应用接口层 (Application API)                          │
│  ├─ SmartAnalyzeTool (增强版)                                   │
│  ├─ CodeQueryTool (代码查询)                                    │
│  └─ SemanticSearchTool (语义搜索)                               │
├─────────────────────────────────────────────────────────────────┤
│  Layer 3: 语义分析层 (Semantic Analysis)                        │
│  ├─ SymbolGraph (符号图谱)                                      │
│  ├─ CallGraph (调用关系)                                        │
│  ├─ DependencyGraph (依赖关系)                                  │
│  └─ SemanticIndex (语义索引)                                    │
├─────────────────────────────────────────────────────────────────┤
│  Layer 2: 语法抽象层 (Syntax Abstraction)                       │
│  ├─ SyntaxTree (统一语法树)                                     │
│  ├─ SyntaxNode (语法节点)                                       │
│  ├─ SyntaxQuery (语法查询)                                      │
│  └─ IncrementalParser (增量解析器)                              │
├─────────────────────────────────────────────────────────────────┤
│  Layer 1: 引擎适配层 (Engine Adapter)                           │
│  ├─ TreeSitterAdapter (Tree-sitter 适配)                        │
│  ├─ LspAdapter (LSP 适配)                                       │
│  ├─ AntlrAdapter (ANTLR 适配)                                   │
│  └─ NativeAdapter (原生 Java 解析)                              │
├─────────────────────────────────────────────────────────────────┤
│  Layer 0: 基础能力层 (Foundation)                               │
│  ├─ SmartProjectAnalyzer (现有项目分析)                         │
│  ├─ FileWatcher (文件变更监听)                                  │
│  ├─ AnalysisCache (分析缓存)                                    │
│  └─ LanguageRegistry (语言注册表)                               │
└─────────────────────────────────────────────────────────────────┘
```

## 核心设计原则

### 1. 开闭原则 (Open/Closed)
```java
// 不修改现有 SmartAnalyzeTool，通过扩展增加能力
public class SmartAnalyzeToolV2 extends SmartAnalyzeTool {
    private final CodeIntelligenceEngine codeEngine;
    
    @Override
    public ProjectAnalysisReport analyze() {
        // 复用父类项目分析 + 新增代码语义分析
        ProjectAnalysisReport baseReport = super.analyze();
        CodeIntelligenceReport codeReport = codeEngine.analyze();
        return baseReport.merge(codeReport);
    }
}
```

### 2. 依赖倒置 (Dependency Inversion)
```java
// 上层依赖抽象接口，不依赖具体实现
public interface SyntaxEngine {
    SyntaxTree parse(String source, Language language);
    SyntaxQuery createQuery(String pattern);
}

// 具体实现可替换
public class TreeSitterEngine implements SyntaxEngine { }
public class LspSyntaxEngine implements SyntaxEngine { }
```

### 3. 单一职责 (Single Responsibility)
每层只负责一个抽象层次：
- Layer 0: 文件系统和项目结构
- Layer 1: 语法解析引擎适配
- Layer 2: 统一语法树表示
- Layer 3: 语义关系构建
- Layer 4: 用户接口工具

## 关键组件设计

### 1. 统一语法树 (Universal Syntax Tree)

```java
/**
 * 与具体解析器无关的语法树抽象
 * 类似 Tree-sitter 的 Tree，但更适合 Java 生态
 */
public interface SyntaxTree {
    String getLanguage();
    SyntaxNode getRootNode();
    String getSource();
    
    // 增量更新接口
    SyntaxTree edit(List<TextEdit> edits);
    
    // 序列化
    String toSexp();  // S-expression 格式
    String toXml();   // XML 格式
    JsonNode toJson(); // JSON 格式
}

public interface SyntaxNode {
    String getType();           // 节点类型，如 "function_declaration"
    String getText();           // 原始文本
    Range getRange();           // 代码位置
    
    // 树遍历
    SyntaxNode getParent();
    List<SyntaxNode> getChildren();
    List<SyntaxNode> getChildren(String type); // 按类型过滤
    
    // 语义信息
    boolean isNamed();          // 是否具名节点
    boolean isMissing();        // 是否错误恢复产生的缺失节点
    boolean hasError();         // 是否包含错误
    
    // 查询支持
    List<SyntaxNode> find(String query); // 类似 CSS 选择器
}
```

### 2. 语法查询语言 (Syntax Query Language)

类似 Tree-sitter 的 Query，但更简洁：

```java
/**
 * 声明式语法查询
 * 示例：查找所有 public 方法
 * 
 * (method_declaration
 *   (modifiers (modifier "public"))
 *   name: (identifier) @methodName
 *   parameters: (formal_parameters) @params
 * )
 */
public interface SyntaxQuery {
    // 查询单个文件
    List<QueryMatch> execute(SyntaxTree tree);
    
    // 批量查询项目
    List<QueryMatch> execute(Project project);
    
    // 带过滤的查询
    List<QueryMatch> execute(Project project, Predicate<QueryMatch> filter);
}

public class QueryMatch {
    private final String file;
    private final Range range;
    private final Map<String, SyntaxNode> captures; // @captureName -> node
}
```

### 3. 增量分析引擎 (Incremental Analysis Engine)

```java
/**
 * 监听文件变化，只重新分析变更部分
 */
public class IncrementalAnalysisEngine {
    private final FileWatcher watcher;
    private final AnalysisCache cache;
    private final Map<Path, SyntaxTree> parsedFiles;
    
    /**
     * 文件变更时的增量更新
     */
    public void onFileChanged(Path file, TextEdit edit) {
        SyntaxTree oldTree = parsedFiles.get(file);
        if (oldTree != null) {
            // 复用已有树，只更新变化部分
            SyntaxTree newTree = oldTree.edit(List.of(edit));
            parsedFiles.put(file, newTree);
            
            // 增量更新语义索引
            updateSemanticIndexIncrementally(file, oldTree, newTree);
        }
    }
    
    /**
     * 批量分析项目，利用缓存避免重复解析
     */
    public ProjectAnalysisReport analyzeProject(Path root) {
        // 1. 使用 SmartProjectAnalyzer 获取文件列表
        List<Path> files = projectAnalyzer.getSourceFiles(root);
        
        // 2. 过滤未变更文件
        List<Path> changedFiles = files.stream()
            .filter(f -> !cache.isUpToDate(f))
            .toList();
        
        // 3. 并行解析变更文件
        changedFiles.parallelStream().forEach(this::parseAndCache);
        
        // 4. 构建完整报告（复用缓存 + 新解析）
        return buildReport(files);
    }
}
```

### 4. 符号图谱 (Symbol Graph)

```java
/**
 * 代码符号及关系的图表示
 * 支持：定义-引用、调用关系、继承关系、导入关系
 */
public class SymbolGraph {
    private final Graph<SymbolNode, SymbolEdge> graph;
    
    public Optional<SymbolNode> findDefinition(String symbol);
    public List<SymbolNode> findReferences(String symbol);
    public List<SymbolNode> findCallers(String method);
    public List<SymbolNode> findCallees(String method);
    
    // 路径分析
    public List<List<SymbolNode>> findDependencyPath(String from, String to);
    
    // 影响分析
    public Set<SymbolNode> findImpactedSymbols(String changedSymbol);
}

public class SymbolNode {
    private final String name;
    private final SymbolKind kind;     // CLASS, METHOD, FIELD, etc.
    private final String language;
    private final Location location;   // 文件位置
    private final List<String> modifiers; // public, static, etc.
}
```

### 5. 语言注册表 (Language Registry)

```java
/**
 * 多语言支持的配置中心
 * 自动检测项目语言并加载对应解析器
 */
public class LanguageRegistry {
    private final Map<String, LanguageSupport> languages;
    
    public LanguageSupport detectLanguage(Path file) {
        // 1. 根据扩展名检测
        String ext = getExtension(file);
        if (languages.containsKey(ext)) {
            return languages.get(ext);
        }
        
        // 2. 根据 shebang 检测
        Optional<String> shebang = readShebang(file);
        if (shebang.isPresent()) {
            return detectByShebang(shebang.get());
        }
        
        // 3. 根据内容特征检测
        return detectByContent(file);
    }
    
    public void register(LanguageSupport language) {
        languages.put(language.getId(), language);
    }
}

public interface LanguageSupport {
    String getId();                    // "java", "rust", "typescript"
    List<String> getExtensions();      // [".java"]
    List<String> getFilePatterns();    // ["pom.xml"]
    
    SyntaxEngine createEngine();       // 创建解析引擎
    List<SyntaxQuery> getBuiltinQueries(); // 内置查询模板
}
```

## 增强版工具设计

### 1. SmartAnalyzeTool V2

```java
public class SmartAnalyzeToolV2 implements Tool<SmartAnalyzeInput, SmartAnalyzeOutput, Void> {
    
    // 复用现有能力
    private final SmartProjectAnalyzer projectAnalyzer;
    
    // 新增代码智能能力
    private final CodeIntelligenceEngine codeEngine;
    private final IncrementalAnalysisEngine incrementalEngine;
    
    @Override
    public String getPrompt() {
        return """
               使用 SmartAnalyzeTool V2 进行深度项目分析。
               
               新增能力：
               1. 代码语义分析 - 解析语法树，理解代码结构
               2. 符号导航 - 查找定义、引用、调用关系
               3. 增量分析 - 高效处理大项目
               
               参数：
               - project_root: 项目根目录
               - enable_code_analysis: 启用代码语义分析（可选）
               - analysis_depth: 分析深度 "structure" | "syntax" | "semantic"（可选）
               - query: 语法查询语句（可选）
               
               示例查询：
               - 查找所有 public 方法: (method_declaration (modifiers (modifier "public")))
               - 查找所有测试方法: (method_declaration (identifier) @name (#match? @name "Test"))
               """;
    }
    
    @Override
    public CompletableFuture<ToolResult<SmartAnalyzeOutput>> call(
            SmartAnalyzeInput input,
            ToolExecutionContext context,
            Consumer<ToolProgress<Void>> onProgress) {
        
        return CompletableFuture.supplyAsync(() -> {
            // 1. 项目结构分析（复用现有能力）
            ProjectAnalysisReport projectReport = projectAnalyzer.analyze();
            
            // 2. 代码语义分析（新增）
            if (Boolean.TRUE.equals(input.enableCodeAnalysis())) {
                CodeIntelligenceReport codeReport = incrementalEngine.analyzeProject(
                    Paths.get(input.projectRoot())
                );
                projectReport = projectReport.merge(codeReport);
            }
            
            // 3. 执行语法查询（新增）
            if (input.query() != null) {
                List<QueryMatch> matches = codeEngine.executeQuery(input.query());
                projectReport.addQueryResults(matches);
            }
            
            return ToolResult.success(new SmartAnalyzeOutput(projectReport));
        });
    }
}
```

### 2. CodeQueryTool (新增)

```java
/**
 * 专门的代码查询工具
 * 类似 tree-sitter query 命令
 */
public class CodeQueryTool implements Tool<CodeQueryInput, CodeQueryOutput, Void> {
    
    @Override
    public String getPrompt() {
        return """
               在项目中执行语法查询。
               
               示例：
               - 查询所有方法: {"pattern": "(method_declaration)", "path": "/project"}
               - 查询特定文件: {"pattern": "(class_declaration)", "file": "/project/Main.java"}
               - 使用内置查询: {"builtin": "find-public-methods", "path": "/project"}
               """;
    }
    
    @Override
    public ToolResult<CodeQueryOutput> call(CodeQueryInput input) {
        SyntaxQuery query = queryEngine.compile(input.pattern());
        List<QueryMatch> matches = query.execute(project);
        
        return ToolResult.success(new CodeQueryOutput(matches));
    }
}
```

## 实现路线图

### Phase 1: 基础架构 (2-3 周)
- [ ] 设计统一语法树接口
- [ ] 实现 LanguageRegistry
- [ ] 集成 Tree-sitter JNI 绑定
- [ ] 实现 TreeSitterAdapter

### Phase 2: 核心能力 (3-4 周)
- [ ] 实现 SyntaxQuery 引擎
- [ ] 实现 IncrementalAnalysisEngine
- [ ] 实现 FileWatcher 和缓存
- [ ] 增强 SmartAnalyzeTool V2

### Phase 3: 语义层 (3-4 周)
- [ ] 实现 SymbolGraph
- [ ] 实现调用关系分析
- [ ] 实现依赖关系分析
- [ ] 添加内置查询库

### Phase 4: 工具生态 (2-3 周)
- [ ] 实现 CodeQueryTool
- [ ] 实现 SemanticSearchTool
- [ ] 添加 VSCode 集成
- [ ] 性能优化和缓存策略

## 与 Tree-sitter 的关系

```
┌─────────────────────────────────────────────────────────┐
│                    SmartAnalyzeTool V2                  │
│              (项目结构 + 代码语义 = 完整理解)            │
├─────────────────────────────────────────────────────────┤
│  项目分析层 (原有)      │      代码分析层 (新增)        │
│  ├─ NoiseFilter         │      ├─ TreeSitterAdapter    │
│  ├─ EvidenceCollector   │      ├─ SyntaxTree           │
│  └─ HypothesisEngine    │      └─ SymbolGraph          │
├─────────────────────────────────────────────────────────┤
│              Tree-sitter (通过 JNI 调用)                │
│              ├─ libtree-sitter.so                      │
│              ├─ language parsers (.so/.wasm)           │
│              └─ core parsing engine                    │
└─────────────────────────────────────────────────────────┘
```

## 总结

这个设计的核心思想是：

1. **不重复造轮子** - Tree-sitter 作为解析引擎，SmartAnalyzeTool 作为编排层
2. **渐进增强** - 从项目结构到代码语义，层次清晰
3. **开放扩展** - 支持多种解析后端，不绑定 Tree-sitter
4. **高效实用** - 增量分析 + 缓存，支持大项目

最终目标是让 AI 助手能够：
- ✅ 理解项目结构（已有）
- ✅ 理解代码语法（新增）
- ✅ 理解语义关系（新增）
- ✅ 回答复杂代码问题（新增）
