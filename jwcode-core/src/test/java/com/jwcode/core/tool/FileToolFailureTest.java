package com.jwcode.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileWriteTool / FileReadTool 失败场景详细测试
 */
class FileToolFailureTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // ==================== FileWriteTool 失败场景 ====================

    @Test
    void testFileWritePermissionDenied() throws Exception {
        ToolExecutor executor = new ToolExecutor();
        JsonNode input = MAPPER.readTree("{\"path\":\"C:/Windows/System32/__jwcode_test_no_permission.txt\",\"content\":\"test\"}");
        ToolExecutionContext ctx = new ToolExecutionContext(null, Path.of(System.getProperty("user.dir")), null);

        ToolExecutor.ToolExecutionResult result = executor.execute("FileWriteTool", input, ctx).get();

        System.out.println("=== FileWrite PermissionDenied ===");
        System.out.println("isSuccess = " + result.isSuccess());
        System.out.println("errorMessage = " + result.getErrorMessage());
        System.out.println("result = " + result.getResult());

        assertFalse(result.isSuccess(), "权限不足应返回失败");
        assertNotNull(result.getErrorMessage(), "应包含错误信息");
    }

    @Test
    void testFileWriteToNonexistentParentAutoCreate() throws Exception {
        ToolExecutor executor = new ToolExecutor();
        String path = "C:/Users/HUAWEI/Desktop/jwcode/__test_auto_create_dir/test.txt";
        JsonNode input = MAPPER.readTree("{\"path\":\"" + path + "\",\"content\":\"auto create parent dir\"}");
        ToolExecutionContext ctx = new ToolExecutionContext(null, Path.of(System.getProperty("user.dir")), null);

        ToolExecutor.ToolExecutionResult result = executor.execute("FileWriteTool", input, ctx).get();

        System.out.println("=== FileWrite AutoCreateParent ===");
        System.out.println("isSuccess = " + result.isSuccess());
        System.out.println("errorMessage = " + result.getErrorMessage());
        if (result.getResult() != null) {
            System.out.println("toolResult.isSuccess = " + result.getResult().isSuccess());
            System.out.println("toolResult.data = " + result.getResult().getData());
        }

        // 清理
        java.nio.file.Files.deleteIfExists(Path.of(path));
        java.nio.file.Files.deleteIfExists(Path.of(path).getParent());
    }

    @Test
    void testFileWriteEmptyPath() throws Exception {
        ToolExecutor executor = new ToolExecutor();
        JsonNode input = MAPPER.readTree("{\"path\":\"\",\"content\":\"test\"}");
        ToolExecutionContext ctx = new ToolExecutionContext(null, Path.of(System.getProperty("user.dir")), null);

        ToolExecutor.ToolExecutionResult result = executor.execute("FileWriteTool", input, ctx).get();

        System.out.println("=== FileWrite EmptyPath ===");
        System.out.println("isSuccess = " + result.isSuccess());
        System.out.println("errorMessage = " + result.getErrorMessage());
        System.out.println("result = " + result.getResult());
    }

    // ==================== FileReadTool 失败场景 ====================

    @Test
    void testFileReadNotExists() throws Exception {
        ToolExecutor executor = new ToolExecutor();
        JsonNode input = MAPPER.readTree("{\"file_path\":\"C:/__jwcode_non_existent_file_12345.txt\"}");
        ToolExecutionContext ctx = new ToolExecutionContext(null, Path.of(System.getProperty("user.dir")), null);

        ToolExecutor.ToolExecutionResult result = executor.execute("FileReadTool", input, ctx).get();

        System.out.println("=== FileRead NotExists ===");
        System.out.println("isSuccess = " + result.isSuccess());
        System.out.println("errorMessage = " + result.getErrorMessage());
        System.out.println("result = " + result.getResult());

        assertFalse(result.isSuccess(), "文件不存在应返回失败");
        assertNotNull(result.getErrorMessage(), "应包含错误信息");
    }

    @Test
    void testFileReadEmptyPath() throws Exception {
        ToolExecutor executor = new ToolExecutor();
        JsonNode input = MAPPER.readTree("{\"file_path\":\"\"}");
        ToolExecutionContext ctx = new ToolExecutionContext(null, Path.of(System.getProperty("user.dir")), null);

        ToolExecutor.ToolExecutionResult result = executor.execute("FileReadTool", input, ctx).get();

        System.out.println("=== FileRead EmptyPath ===");
        System.out.println("isSuccess = " + result.isSuccess());
        System.out.println("errorMessage = " + result.getErrorMessage());
        System.out.println("result = " + result.getResult());
    }

    @Test
    void testFileReadSystemFilePermission() throws Exception {
        ToolExecutor executor = new ToolExecutor();
        // 尝试读取一个通常存在但可能需要权限的系统文件
        JsonNode input = MAPPER.readTree("{\"file_path\":\"C:/Windows/System32/config/SAM\"}");
        ToolExecutionContext ctx = new ToolExecutionContext(null, Path.of(System.getProperty("user.dir")), null);

        ToolExecutor.ToolExecutionResult result = executor.execute("FileReadTool", input, ctx).get();

        System.out.println("=== FileRead SystemFile SAM ===");
        System.out.println("isSuccess = " + result.isSuccess());
        System.out.println("errorMessage = " + result.getErrorMessage());
        if (result.getResult() != null) {
            System.out.println("toolResult.isSuccess = " + result.getResult().isSuccess());
            System.out.println("toolResult.content = " + result.getResult().getContent());
        }
    }
}
