package com.jwcode.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * FileWriteTool JSON Input Test
 */
public class FileWriteToolJsonTest {
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    public static void main(String[] args) throws Exception {
        System.out.println("=== FileWriteTool JSON Input Test ===\n");
        
        FileWriteTool tool = new FileWriteTool();
        
        // Test 1: JSON with "path" field (as defined in Input class)
        System.out.println("Test 1: JSON with 'path' field");
        String tmpDir = System.getProperty("java.io.tmpdir").replace("\\", "/");
        String json1 = "{\"path\": \"" + tmpDir + "/test_path.txt\", \"content\": \"Content with path field\"}";
        testJsonInput(tool, json1);
        
        // Test 2: JSON with "file_path" field (as used in FileWriteInput record)
        System.out.println("\nTest 2: JSON with 'file_path' field");
        String json2 = "{\"file_path\": \"" + tmpDir + "/test_file_path.txt\", \"content\": \"Content with file_path field\"}";
        testJsonInput(tool, json2);
        
        // Test 3: JSON schema comparison
        System.out.println("\nTest 3: Input Schema vs Actual JSON");
        testSchemaVsJson(tool);
        
        System.out.println("\n=== Test Complete ===");
    }
    
    static void testJsonInput(FileWriteTool tool, String jsonStr) throws Exception {
        try {
            JsonNode json = MAPPER.readTree(jsonStr);
            System.out.println("  JSON: " + jsonStr);
            
            // Parse input using tool's parseInput method
            FileWriteTool.Input input = tool.parseInput(json);
            System.out.println("  Parsed: path=" + input.path + ", content length=" + (input.content != null ? input.content.length() : "null"));
            
            // Execute
            ToolResult<FileWriteTool.Output> result = tool.call(input, null, null).get();
            System.out.println("  Execute: " + (result.isSuccess() ? "SUCCESS" : "FAILED - " + result.getContent()));
            
            if (result.isSuccess()) {
                // Cleanup
                Files.deleteIfExists(Paths.get(input.path));
                System.out.println("  Cleanup: Deleted test file");
            }
        } catch (Exception e) {
            System.out.println("  ERROR: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    static void testSchemaVsJson(FileWriteTool tool) {
        System.out.println("  Tool InputSchema:");
        JsonNode schema = tool.getInputSchema();
        if (schema != null) {
            System.out.println("    " + schema.toString());
        } else {
            System.out.println("    null");
        }
        
        System.out.println("\n  Tool Input Type Fields:");
        try {
            java.lang.reflect.Field pathField = FileWriteTool.Input.class.getField("path");
            System.out.println("    - Field 'path': " + pathField.getType().getSimpleName());
        } catch (Exception e) {
            System.out.println("    - Field 'path': NOT FOUND");
        }
    }
}
