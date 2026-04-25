package com.jwcode.parser;

import com.jwcode.parser.model.CodeSymbol;
import com.jwcode.parser.model.ParseResult;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Tree-sitter 使用示例
 */
public class TreeSitterExample {
    
    public static void main(String[] args) throws Exception {
        // 方式1：嵌入式启动（推荐）
        // 自动启动 Python 服务，关闭客户端时自动停止
        try (TreeSitterClient client = TreeSitterClient.startEmbedded()) {
            runExamples(client);
        }
        
        // 方式2：连接已运行的服务
        // 适用于服务独立部署的场景
        // TreeSitterClient client = new TreeSitterClient("127.0.0.1", 8765);
    }
    
    static void runExamples(TreeSitterClient client) throws Exception {
        // 示例1：解析当前 Java 文件
        Path testFile = Paths.get("src/test/java/com/jwcode/parser/TreeSitterExample.java");
        
        System.out.println("=== 示例1：解析文件 ===");
        ParseResult result = client.parseFile(testFile);
        
        if (result.isSuccess()) {
            System.out.println("语言: " + result.getLanguage());
            System.out.println("包名: " + result.getPackage());
            System.out.println("导入: " + result.getImports());
            System.out.println("符号数量: " + result.getSymbols().size());
            
            // 打印所有类
            System.out.println("\n类定义:");
            for (CodeSymbol cls : result.getClasses()) {
                System.out.println("  - " + cls.getName() + " [" + cls.getStartLine() + ":" + cls.getStartCol() + "]");
            }
            
            // 打印所有方法
            System.out.println("\n方法定义:");
            for (CodeSymbol method : result.getMethods()) {
                System.out.println("  - " + method.getName() + " " + method.getSignature());
            }
        }
        
        // 示例2：获取指定位置的符号
        System.out.println("\n=== 示例2：位置查询 ===");
        var symbolOpt = client.getSymbolAtPosition(testFile, 10, 20);
        symbolOpt.ifPresent(sym -> {
            System.out.println("位置(10,20)的符号: " + sym.getFullSignature());
        });
        
        // 示例3：获取作用域
        System.out.println("\n=== 示例3：作用域查询 ===");
        var scopeOpt = client.getEnclosingScope(testFile, 15);
        scopeOpt.ifPresent(scope -> {
            System.out.println("行15所在作用域: " + scope.getName() + " (" + scope.getKind() + ")");
        });
        
        // 示例4：语言检测
        System.out.println("\n=== 示例4：语言检测 ===");
        String lang = client.detectLanguage(testFile);
        System.out.println("文件类型: " + lang);
        
        // 示例5：批量解析
        System.out.println("\n=== 示例5：批量解析 ===");
        List<Path> files = List.of(
            Paths.get("src/main/java/com/jwcode/parser/TreeSitterClient.java"),
            Paths.get("src/main/java/com/jwcode/parser/model/CodeSymbol.java"),
            Paths.get("src/main/java/com/jwcode/parser/model/ParseResult.java")
        );
        
        var batchResults = client.parseBatch(files);
        batchResults.forEach((path, res) -> {
            System.out.println(path + ": " + res.getSymbols().size() + " symbols");
        });
    }
}
