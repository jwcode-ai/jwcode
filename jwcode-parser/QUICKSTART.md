# JwCode Parser 快速开始

## 1. 安装（3步）

### 步骤1：安装 Python 依赖
```bash
cd jwcode-parser
pip install -r src/main/python/requirements.txt
```

或者使用安装脚本：
```bash
python setup.py
```

### 步骤2：编译 Java 模块
```bash
cd ..
mvn clean install -pl jwcode-parser -DskipTests
```

### 步骤3：验证安装
```bash
# 启动 Python 服务
python src/main/python/api_server.py

# 在另一个终端测试
curl http://localhost:8765/health
```

## 2. 基本使用

### 方式1：嵌入式模式（推荐）
```java
import com.jwcode.parser.TreeSitterClient;
import com.jwcode.parser.model.*;

public class Demo {
    public static void main(String[] args) throws Exception {
        // 自动启动 Python 服务
        try (TreeSitterClient client = TreeSitterClient.startEmbedded()) {
            
            // 解析文件
            ParseResult result = client.parseFile(
                Path.of("src/main/java/MyClass.java")
            );
            
            // 打印结果
            System.out.println("语言: " + result.getLanguage());
            System.out.println("类数量: " + result.getClasses().size());
            System.out.println("方法数量: " + result.getMethods().size());
        }
    }
}
```

### 方式2：连接已有服务
```java
// 手动启动服务后连接
TreeSitterClient client = new TreeSitterClient("127.0.0.1", 8765);
ParseResult result = client.parseFile(filePath);
```

## 3. 功能演示

### 解析 Java 文件
```java
ParseResult result = client.parseFile(Path.of("Example.java"));

// 获取类信息
for (CodeSymbol cls : result.getClasses()) {
    System.out.println("类: " + cls.getName());
    System.out.println("行号: " + cls.getStartLine());
    System.out.println("修饰符: " + cls.getModifiers());
}

// 获取方法信息
for (CodeSymbol method : result.getMethods()) {
    System.out.println("方法: " + method.getFullSignature());
}
```

### 光标位置查询
```java
// 获取光标所在位置的符号
int line = 10;  // 第10行
int col = 15;   // 第15列

client.getSymbolAtPosition(file, line, col)
    .ifPresent(sym -> {
        System.out.println("当前符号: " + sym.getName());
        System.out.println("类型: " + sym.getKind());
    });
```

### 批量解析
```java
List<Path> files = List.of(
    Path.of("A.java"),
    Path.of("B.java"),
    Path.of("C.java")
);

Map<String, ParseResult> results = client.parseBatch(files);
results.forEach((path, result) -> {
    System.out.println(path + ": " + result.getSymbols().size() + " symbols");
});
```

## 4. 支持的语言

| 语言 | 扩展名 | 状态 |
|------|--------|------|
| Java | .java | ✅ 完全支持 |
| Python | .py | ✅ 完全支持 |
| JavaScript | .js | ✅ 完全支持 |
| TypeScript | .ts, .tsx | ✅ 完全支持 |
| Go | .go | ✅ 完全支持 |
| Rust | .rs | ✅ 完全支持 |

## 5. 故障排除

### Python 服务启动失败
```bash
# 检查 Python 版本
python --version  # 需要 3.8+

# 检查依赖安装
pip list | grep tree-sitter

# 手动启动查看错误
python src/main/python/api_server.py --port 8765
```

### Java 连接失败
```bash
# 检查服务是否运行
curl http://localhost:8765/health

# 检查防火墙
# 确保端口 8765 未被占用
```

### 性能问题
- 解析大文件(>10MB)可能需要较长时间
- 建议使用批量解析接口
- 考虑使用本地缓存

## 6. 下一步

- 查看完整 API 文档: [README.md](README.md)
- 运行示例代码: `src/test/java/TreeSitterExample.java`
- 集成到 LSP 服务实现代码跳转
