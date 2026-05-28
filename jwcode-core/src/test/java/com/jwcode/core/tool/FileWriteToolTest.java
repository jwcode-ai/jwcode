package com.jwcode.core.tool;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * FileWriteTool Test Class
 */
public class FileWriteToolTest {
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== FileWriteTool Test ===\n");
        
        FileWriteTool tool = new FileWriteTool();
        
        // Test 1: Desktop write
        System.out.println("Test 1: Desktop Directory Write");
        testDesktopWrite(tool);
        
        // Test 2: Desktop write with Chinese filename
        System.out.println("\nTest 2: Desktop Write with Chinese Filename");
        testDesktopWriteChinese(tool);
        
        // Test 3: Input parsing test
        System.out.println("\nTest 3: Input Parsing Test");
        testInputParsing();
        
        System.out.println("\n=== Test Complete ===");
    }
    
    static void testDesktopWrite(FileWriteTool tool) throws Exception {
        String desktopPath = System.getProperty("java.io.tmpdir") + "/test_jwcode_tool.txt";
        String content = "Test Time: 2026-04-12 16:56\nContent written by FileWriteTool\nLine 3";
        
        FileWriteTool.Input input = new FileWriteTool.Input(desktopPath, content);
        
        try {
            ToolResult<FileWriteTool.Output> result = tool.call(input, null, null).get();
            System.out.println("  Success: " + result.isSuccess());
            if (result.isSuccess()) {
                System.out.println("  Output: path=" + result.getData().path + ", size=" + result.getData().size);
                
                // Verify file was written
                Path filePath = Paths.get(desktopPath);
                if (Files.exists(filePath)) {
                    String readContent = Files.readString(filePath);
                    System.out.println("  Verify: File exists, content length=" + readContent.length());
                    Files.deleteIfExists(filePath);
                    System.out.println("  Cleanup: Deleted test file");
                }
            } else {
                System.out.println("  Error: " + result.getContent());
            }
        } catch (Exception e) {
            System.out.println("  Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static void testDesktopWriteChinese(FileWriteTool tool) throws Exception {
        String desktopPath = System.getProperty("java.io.tmpdir") + "/test_chinese.txt";
        String content = "Test Time: 2026-04-12\nChinese content test";
        
        FileWriteTool.Input input = new FileWriteTool.Input(desktopPath, content);
        
        try {
            ToolResult<FileWriteTool.Output> result = tool.call(input, null, null).get();
            System.out.println("  Success: " + result.isSuccess());
            if (result.isSuccess()) {
                System.out.println("  Output: path=" + result.getData().path);
                
                // Verify file
                Path filePath = Paths.get(desktopPath);
                if (Files.exists(filePath)) {
                    Files.deleteIfExists(filePath);
                    System.out.println("  Cleanup: Deleted test file");
                }
            } else {
                System.out.println("  Error: " + result.getContent());
            }
        } catch (Exception e) {
            System.out.println("  Exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static void testInputParsing() {
        System.out.println("  FileWriteTool Input class fields:");
        try {
            java.lang.reflect.Field pathField = FileWriteTool.Input.class.getField("path");
            java.lang.reflect.Field contentField = FileWriteTool.Input.class.getField("content");
            System.out.println("    - path: " + pathField.getType().getSimpleName());
            System.out.println("    - content: " + contentField.getType().getSimpleName());
        } catch (Exception e) {
            System.out.println("  Exception: " + e.getMessage());
        }
        
        System.out.println("\n  FileWriteInput record fields (from input package):");
        try {
            Class<?> recordClass = Class.forName("com.jwcode.core.tool.input.FileWriteInput");
            java.lang.reflect.RecordComponent[] components = recordClass.getRecordComponents();
            for (var comp : components) {
                System.out.println("    - " + comp.getName() + ": " + comp.getType().getSimpleName());
            }
        } catch (Exception e) {
            System.out.println("  FileWriteInput record not found");
        }
    }
}
