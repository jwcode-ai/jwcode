# JwCode Tree-sitter Parser

基于 Tree-sitter 的多语言代码解析模块，为 JwCode 提供高性能的代码分析能力。

## 🌟 特性

- **多语言支持**: Java, Python, JavaScript, TypeScript, Go, Rust
- **高性能**: 基于 C 实现的 Tree-sitter 解析器
- **丰富信息**: 类、方法、字段、导入、包信息
- **位置查询**: 支持光标位置符号查询和作用域分析
- **批量处理**: 支持批量文件解析

## 📁 项目结构

```
jwcode-parser/
├── src/main/java/com/jwcode/parser/
│   ├── TreeSitterClient.java       # Java 客户端
│   └── model/
│       ├── CodeSymbol.java         # 符号模型
│       └── ParseResult.java        # 解析结果
├── src/main/python/
│   ├── api_server.py               # Python API 服务
│   ├── parser_core.py              # 核心解析逻辑
│   └── requirements.txt            # Python 依赖
└── src/test/java/...               # 测试示例
```

## 🚀 快速开始

### 1. 安装 Python 依赖

```bash
cd src/main/python
pip install -r requirements.txt
```

### 2. 启动解析服务（可选）

嵌入式模式会自动启动，也可以手动启动：

```bash
python api_server.py --port 8765
```

### 3. 使用 Java 客户端

```java
// 嵌入式模式（推荐）
try (TreeSitterClient client = TreeSitterClient.startEmbedded()) {
    // 解析文件
    ParseResult result = client.parseFile(Path.of("src/main/java/MyClass.java"));
    
    // 获取所有类
    for (CodeSymbol cls : result.getClasses()) {
        System.out.println("Class: " + cls.getName());
    }
    
    // 获取所有方法
    for (CodeSymbol method : result.getMethods()) {
        System.out.println("Method: " + method.getFullSignature());
    }
    
    // 查询指定位置的符号
    client.getSymbolAtPosition(file, line, col)
        .ifPresent(sym -> System.out.println("Symbol: " + sym.getName()));
}
```

## 📚 API 参考

### TreeSitterClient

| 方法 | 说明 |
|------|------|
| `startEmbedded()` | 启动嵌入式服务 |
| `parseFile(Path)` | 解析单个文件 |
| `parseBatch(List<Path>)` | 批量解析 |
| `getSymbolAtPosition(Path, int, int)` | 获取位置符号 |
| `getEnclosingScope(Path, int)` | 获取作用域 |
| `detectLanguage(Path)` | 检测语言类型 |
| `healthCheck()` | 健康检查 |

### ParseResult

| 方法 | 说明 |
|------|------|
| `isSuccess()` | 解析是否成功 |
| `getClasses()` | 获取所有类 |
| `getMethods()` | 获取所有方法 |
| `findSymbol(String)` | 按名称查找符号 |
| `findSymbolAt(int, int)` | 按位置查找符号 |

### CodeSymbol

| 属性 | 说明 |
|------|------|
| `name` | 符号名称 |
| `kind` | 符号类型 (CLASS/METHOD/FIELD/...) |
| `startLine/endLine` | 起始/结束行号 |
| `signature` | 方法签名 |
| `modifiers` | 修饰符列表 |
| `parent` | 父符号名称 |

## 🔧 架构说明

```
Java 客户端  <--HTTP/JSON-->  Python 服务  <--Tree-sitter-->  代码文件
     |                              |
  ParseResult                  AST 解析
  CodeSymbol                   符号提取
```

- **Java 端**: 提供友好的 API，处理序列化
- **Python 端**: 调用 Tree-sitter 进行解析
- **通信**: HTTP/JSON，轻量级无状态

## 📝 性能数据

- 解析速度: ~1000 行/秒
- 内存占用: <50MB (Python 服务)
- 启动时间: ~2 秒

## 🔮 未来计划

- [ ] 支持更多语言 (C/C++, Ruby, PHP)
- [ ] 代码语义分析
- [ ] 调用关系图生成
- [ ] 增量更新支持

## 📄 License

MIT License
