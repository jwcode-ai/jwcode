# Tree-sitter 集成架构文档

## 整体架构

```
┌─────────────────────────────────────────────────────────────────┐
│                     JwCode Core (Java)                          │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────────┐ │
│  │ SmartAnalyze│  │ LSP Service │  │    Code Intelligence    │ │
│  │    Tool     │  │   Handler   │  │       Engine            │ │
│  └──────┬──────┘  └──────┬──────┘  └───────────┬─────────────┘ │
│         │                │                      │               │
│         └────────────────┼──────────────────────┘               │
│                          │                                      │
│              ┌───────────┴───────────┐                          │
│              │   TreeSitterClient    │                          │
│              │  (Java HTTP Client)   │                          │
│              └───────────┬───────────┘                          │
└──────────────────────────┼──────────────────────────────────────┘
                           │ HTTP/JSON (localhost:8765)
┌──────────────────────────┼──────────────────────────────────────┐
│              ┌───────────┴───────────┐                          │
│              │   FastAPI Server      │                          │
│              │    (Python)           │                          │
│              └───────────┬───────────┘                          │
│                          │                                      │
│  ┌───────────────────────┼───────────────────────────────────┐ │
│  │           TreeSitterAnalyzer (Python)                     │ │
│  │  ┌─────────┐  ┌─────────┐  ┌─────────┐  ┌─────────┐      │ │
│  │  │ Java    │  │ Python  │  │   JS    │  │   Go    │ ... │ │
│  │  │ Parser  │  │ Parser  │  │ Parser  │  │ Parser  │      │ │
│  │  └────┬────┘  └────┬────┘  └────┬────┘  └────┬────┘      │ │
│  │       └─────────────┴─────────────┴─────────────┘          │ │
│  │                    Tree-sitter (C Library)                 │ │
│  └────────────────────────────────────────────────────────────┘ │
└─────────────────────────────────────────────────────────────────┘
```

## 核心组件说明

### 1. Java 客户端层

**TreeSitterClient**
- 封装 HTTP 通信细节
- 提供同步/异步 API
- 支持嵌入式服务生命周期管理
- 自动重连和错误处理

**数据模型**
- `CodeSymbol`: 代码符号信息（类、方法、字段等）
- `ParseResult`: 解析结果封装
- `SymbolKind`: 符号类型枚举

### 2. Python 服务层

**FastAPI Server**
- RESTful API 设计
- 自动文档生成 (/docs)
- 请求验证和错误处理
- 支持并发请求

**TreeSitterAnalyzer**
- 语言检测和路由
- AST 遍历和符号提取
- 缓存管理（待实现）
- 增量更新（待实现）

### 3. Tree-sitter 核心

基于 C 语言实现的高性能解析库：
- 增量解析支持
- 错误恢复机制
- 多语言支持
- 语法高亮和折叠

## 集成点

### 与 SmartAnalyzeTool 集成
```java
public class SmartAnalyzeTool {
    @Autowired
    private TreeSitterClient parserClient;
    
    public ProjectAnalysis analyze(Path projectRoot) {
        // 使用 Tree-sitter 解析所有源文件
        List<Path> sourceFiles = findSourceFiles(projectRoot);
        Map<String, ParseResult> results = parserClient.parseBatch(sourceFiles);
        
        // 构建符号索引
        SymbolIndex index = buildIndex(results);
        
        return new ProjectAnalysis(index);
    }
}
```

### 与 LSP 集成
```java
public class DefinitionProvider {
    public Location gotoDefinition(Document doc, Position pos) {
        // 获取当前位置的符号
        CodeSymbol symbol = parserClient
            .getSymbolAtPosition(doc.getPath(), pos.line, pos.col)
            .orElseThrow();
        
        // 在索引中查找定义
        return symbolIndex.findDefinition(symbol.getName());
    }
}
```

### 与 Agent 集成
```java
public class CodeUnderstandingSkill {
    @Tool
    public String analyzeCode(String filePath) {
        ParseResult result = parserClient.parseFile(Path.of(filePath));
        
        // 生成代码结构摘要
        StringBuilder sb = new StringBuilder();
        sb.append("类: ").append(result.getClasses().size()).append("\n");
        sb.append("方法: ").append(result.getMethods().size()).append("\n");
        // ...
        
        return sb.toString();
    }
}
```

## 性能优化策略

### 1. 连接池管理
```java
public class TreeSitterClient {
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectionPool(ConnectionPool.create(10, 5, TimeUnit.MINUTES))
        .build();
}
```

### 2. 本地缓存
```java
@Cacheable("parse-results")
public ParseResult parseFile(Path filePath) {
    // 基于文件内容哈希的缓存
    String cacheKey = computeHash(filePath);
    return cache.getOrCompute(cacheKey, () -> fetchFromServer(filePath));
}
```

### 3. 增量更新
```python
class IncrementalUpdater:
    def update_on_change(self, file_path, changes):
        # 使用 Tree-sitter 的增量解析
        old_tree = self.cache.get(file_path)
        new_tree = self.parser.parse(changes, old_tree)
        return self.extract_symbols(new_tree)
```

### 4. 批量处理
- 使用 `/parse/batch` 接口减少 HTTP 往返
- 客户端合并请求，服务端并行处理
- 流式响应支持大文件

## 扩展方向

### 阶段1：基础能力（已完成）
- ✅ 多语言解析
- ✅ 符号提取
- ✅ 位置查询
- ✅ 批量处理

### 阶段2：语义分析（进行中）
- [ ] 调用关系图
- [ ] 类型推断
- [ ] 控制流分析
- [ ] 数据流分析

### 阶段3：智能特性（规划中）
- [ ] 代码相似度检测
- [ ] 重构建议
- [ ] 代码气味检测
- [ ] 自动化文档生成

## 部署模式

### 模式1：嵌入式（默认）
- Java 应用启动时自动启动 Python 服务
- 适用于单机使用场景
- 资源占用小

### 模式2：独立服务
- Python 服务独立部署
- 多个 Java 客户端共享
- 适合团队协作场景

### 模式3：云端服务
- 部署到远程服务器
- 支持大规模代码分析
- 需要认证和限流

## 监控与可观测性

### 指标收集
```python
# 在 Python 服务端
from prometheus_client import Counter, Histogram

parse_counter = Counter('tree_sitter_parse_total', 'Total parses')
parse_duration = Histogram('tree_sitter_parse_duration', 'Parse duration')

@app.post("/parse")
async def parse_file(request: ParseRequest):
    with parse_duration.time():
        result = analyzer.parse_file(request.file_path, request.content)
        parse_counter.inc()
        return result
```

### 健康检查
- `/health`: 服务状态
- `/metrics`: Prometheus 指标
- `/ready`: 就绪检查

## 安全考虑

1. **输入验证**: 文件路径白名单，防止目录遍历
2. **资源限制**: 文件大小限制，超时控制
3. **沙箱执行**: Python 服务在受限环境中运行
4. **网络隔离**: 只监听 localhost，不暴露公网

## 故障恢复

```java
public class ResilientTreeSitterClient {
    @Retryable(value = IOException.class, maxAttempts = 3)
    public ParseResult parseFile(Path filePath) {
        return delegate.parseFile(filePath);
    }
    
    @Recover
    public ParseResult recover(IOException e, Path filePath) {
        // 降级到本地简单解析
        return fallbackParser.parse(filePath);
    }
}
```

## 总结

Tree-sitter 集成提供了：
1. **高性能**: C 语言核心的解析速度
2. **多语言**: 统一接口支持多种语言
3. **可扩展**: 模块化架构易于扩展
4. **易集成**: 简单的 HTTP API
5. **生产就绪**: 完整的错误处理和监控

下一步重点：
- 性能调优和缓存实现
- 语义分析层构建
- 与现有工具的深度集成
