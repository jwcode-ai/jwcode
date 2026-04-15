package com.jwcode.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.tool.context.ToolExecutionContext;
import org.junit.jupiter.api.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileWriteTool 写入功能验证测试
 * 专门测试文件是否能够正确写入，验证之前的"文件创建成功"但实际未写入的问题
 */
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FileWriteToolWriteVerificationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DESKTOP_PATH = "C:/Users/HUAWEI/Desktop";
    private static final String TEST_FILE_PREFIX = "jwcode_write_test_";
    
    private ToolExecutor executor;
    private ToolExecutionContext context;

    @BeforeEach
    void setUp() {
        executor = new ToolExecutor();
        context = new ToolExecutionContext(null, Path.of(System.getProperty("user.dir")), null);
    }

    @AfterEach
    void tearDown() throws Exception {
        // 清理测试文件
        cleanupTestFiles();
    }

    private void cleanupTestFiles() throws Exception {
        Path desktopPath = Paths.get(DESKTOP_PATH);
        if (Files.exists(desktopPath)) {
            try (var stream = Files.list(desktopPath)) {
                stream.filter(p -> p.getFileName().toString().startsWith(TEST_FILE_PREFIX))
                      .forEach(p -> {
                          try {
                              Files.deleteIfExists(p);
                              System.out.println("  [清理] 删除文件: " + p.getFileName());
                          } catch (Exception e) {
                              System.out.println("  [清理] 无法删除: " + p.getFileName() + " - " + e.getMessage());
                          }
                      });
            }
        }
    }

    // ==================== 基本写入测试 ====================

    @Test
    @Order(1)
    @DisplayName("测试1: 直接调用 FileWriteTool.call() 方法写入文件")
    void testDirectCallWriteFile() throws Exception {
        System.out.println("\n========== 测试1: 直接调用 call() 方法 ==========");
        
        String fileName = TEST_FILE_PREFIX + "direct_call.txt";
        String filePath = Paths.get(DESKTOP_PATH, fileName).toString();
        String content = "测试时间: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n" +
                        "这是直接调用 call() 方法写入的内容\n" +
                        "第三行内容";
        
        FileWriteTool tool = new FileWriteTool();
        FileWriteTool.Input input = new FileWriteTool.Input(filePath, content);
        
        // 执行写入
        ToolResult<FileWriteTool.Output> result = tool.call(input, context, null).get();
        
        System.out.println("  执行结果: isSuccess=" + result.isSuccess());
        System.out.println("  输出: " + result.getData());
        System.out.println("  错误信息: " + result.getContent());
        
        // 验证结果
        assertTrue(result.isSuccess(), "写入应该成功");
        assertNotNull(result.getData(), "输出数据不应为空");
        assertTrue(result.getData().success, "success 标志应为 true");
        
        // 验证文件是否真的存在
        Path actualFilePath = Paths.get(filePath);
        assertTrue(Files.exists(actualFilePath), "文件应该存在: " + filePath);
        
        // 验证文件内容
        String actualContent = Files.readString(actualFilePath);
        assertEquals(content, actualContent, "文件内容应该一致");
        System.out.println("  ✓ 文件内容验证通过，长度: " + actualContent.length());
    }

    @Test
    @Order(2)
    @DisplayName("测试2: 通过 ToolExecutor.execute() 使用 JSON 格式写入文件")
    void testExecutorWithJsonWriteFile() throws Exception {
        System.out.println("\n========== 测试2: ToolExecutor + JSON 输入 ==========");
        
        String fileName = TEST_FILE_PREFIX + "executor_json.txt";
        String filePath = Paths.get(DESKTOP_PATH, fileName).toString();
        String content = "测试时间: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + "\n" +
                        "这是通过 ToolExecutor.execute() 写入的内容\n" +
                        "第三行内容";
        
        // JSON 格式输入
        String jsonInput = String.format("{\"path\": \"%s\", \"content\": \"%s\"}", 
            filePath.replace("\\", "\\\\"), 
            content.replace("\n", "\\n"));
        JsonNode inputJson = MAPPER.readTree(jsonInput);
        
        System.out.println("  输入JSON: " + inputJson.toString());
        
        // 执行
        ToolExecutor.ToolExecutionResult result = executor.execute("FileWriteTool", inputJson, context).get();
        
        System.out.println("  执行结果: isSuccess=" + result.isSuccess());
        System.out.println("  错误信息: " + result.getErrorMessage());
        if (result.getResult() != null) {
            System.out.println("  ToolResult: " + result.getResult().isSuccess());
        }
        
        // 验证结果
        assertTrue(result.isSuccess(), "写入应该成功");
        
        // 验证文件
        Path actualFilePath = Paths.get(filePath);
        assertTrue(Files.exists(actualFilePath), "文件应该存在: " + filePath);
        
        String actualContent = Files.readString(actualFilePath);
        System.out.println("  ✓ 文件写入验证通过，内容长度: " + actualContent.length());
    }

    @Test
    @Order(3)
    @DisplayName("测试3: 使用 file_path 字段名（兼容性测试）")
    void testExecutorWithFilePathField() throws Exception {
        System.out.println("\n========== 测试3: 使用 file_path 字段名 ==========");
        
        String fileName = TEST_FILE_PREFIX + "file_path_field.txt";
        String filePath = Paths.get(DESKTOP_PATH, fileName).toString();
        String content = "使用 file_path 字段名写入的内容\n" +
                        "测试时间: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        // 使用 file_path 而非 path
        String jsonInput = String.format("{\"file_path\": \"%s\", \"content\": \"%s\"}", 
            filePath.replace("\\", "\\\\"), 
            content.replace("\n", "\\n"));
        JsonNode inputJson = MAPPER.readTree(jsonInput);
        
        System.out.println("  输入JSON: " + inputJson.toString());
        
        // 执行
        ToolExecutor.ToolExecutionResult result = executor.execute("FileWriteTool", inputJson, context).get();
        
        System.out.println("  执行结果: isSuccess=" + result.isSuccess());
        System.out.println("  错误信息: " + result.getErrorMessage());
        
        // 验证
        assertTrue(result.isSuccess(), "使用 file_path 字段名写入应该成功");
        
        Path actualFilePath = Paths.get(filePath);
        assertTrue(Files.exists(actualFilePath), "文件应该存在");
        
        String actualContent = Files.readString(actualFilePath);
        System.out.println("  ✓ file_path 字段验证通过");
    }

    @Test
    @Order(4)
    @DisplayName("测试4: 验证写入后文件可读取")
    void testWrittenFileIsReadable() throws Exception {
        System.out.println("\n========== 测试4: 验证写入后文件可读取 ==========");
        
        String fileName = TEST_FILE_PREFIX + "readable.txt";
        String filePath = Paths.get(DESKTOP_PATH, fileName).toString();
        String content = "这是一个测试文件\n" +
                        "用于验证写入后可以正常读取\n" +
                        "包含中文内容: 你好世界\n" +
                        "包含特殊字符: !@#$%^&*()";
        
        // 写入文件
        FileWriteTool tool = new FileWriteTool();
        FileWriteTool.Input input = new FileWriteTool.Input(filePath, content);
        ToolResult<FileWriteTool.Output> writeResult = tool.call(input, context, null).get();
        
        assertTrue(writeResult.isSuccess(), "写入应该成功");
        System.out.println("  写入成功: " + writeResult.getData().path);
        
        // 读取文件验证
        Path actualFilePath = Paths.get(filePath);
        assertTrue(Files.exists(actualFilePath), "文件应该存在");
        
        String actualContent = Files.readString(actualFilePath);
        assertEquals(content, actualContent, "读取的内容应该与写入的一致");
        
        // 验证各行
        String[] lines = actualContent.split("\n");
        assertEquals(4, lines.length, "应该有4行内容");
        // 注意："你好世界" 在第3行（索引2），不是第4行（索引3）
        assertTrue(lines[2].contains("你好世界"), "应包含中文内容 (第3行)");
        assertTrue(lines[3].contains("!@#$%^&*()"), "应包含特殊字符 (第4行)");
        System.out.println("  ✓ 文件内容验证通过，包含中文和特殊字符");
    }

    @Test
    @Order(5)
    @DisplayName("测试5: 测试自动创建父目录")
    void testAutoCreateParentDirectory() throws Exception {
        System.out.println("\n========== 测试5: 自动创建父目录 ==========");
        
        String fileName = TEST_FILE_PREFIX + "auto_created_dir/test_sub_dir/file.txt";
        String filePath = Paths.get(DESKTOP_PATH, fileName).toString();
        String content = "测试自动创建父目录\n" +
                        "时间: " + LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
        
        String jsonInput = String.format("{\"path\": \"%s\", \"content\": \"%s\"}", 
            filePath.replace("\\", "\\\\"), 
            content.replace("\n", "\\n"));
        JsonNode inputJson = MAPPER.readTree(jsonInput);
        
        System.out.println("  目标路径: " + filePath);
        
        ToolExecutor.ToolExecutionResult result = executor.execute("FileWriteTool", inputJson, context).get();
        
        System.out.println("  执行结果: isSuccess=" + result.isSuccess());
        System.out.println("  错误信息: " + result.getErrorMessage());
        
        assertTrue(result.isSuccess(), "自动创建父目录应该成功");
        
        Path actualFilePath = Paths.get(filePath);
        assertTrue(Files.exists(actualFilePath), "文件应该存在");
        
        String actualContent = Files.readString(actualFilePath);
        assertEquals(content, actualContent, "内容应该一致");
        System.out.println("  ✓ 自动创建父目录验证通过");
        
        // 清理测试目录
        Files.deleteIfExists(actualFilePath);
        Files.deleteIfExists(actualFilePath.getParent());
        Files.deleteIfExists(actualFilePath.getParent().getParent());
        System.out.println("  [清理] 已删除测试目录");
    }

    // ==================== 失败场景测试 ====================

    @Test
    @Order(100)
    @DisplayName("测试100: 空路径应该失败")
    void testEmptyPathShouldFail() throws Exception {
        System.out.println("\n========== 测试100: 空路径失败场景 ==========");
        
        JsonNode inputJson = MAPPER.readTree("{\"path\":\"\",\"content\":\"test\"}");
        
        ToolExecutor.ToolExecutionResult result = executor.execute("FileWriteTool", inputJson, context).get();
        
        System.out.println("  执行结果: isSuccess=" + result.isSuccess());
        System.out.println("  错误信息: " + result.getErrorMessage());
        
        assertFalse(result.isSuccess(), "空路径应该失败");
    }

    @Test
    @Order(101)
    @DisplayName("测试101: 空内容应该失败")
    void testEmptyContentShouldFail() throws Exception {
        System.out.println("\n========== 测试101: 空内容失败场景 ==========");
        
        JsonNode inputJson = MAPPER.readTree("{\"path\":\"C:/test.txt\"}");
        
        ToolExecutor.ToolExecutionResult result = executor.execute("FileWriteTool", inputJson, context).get();
        
        System.out.println("  执行结果: isSuccess=" + result.isSuccess());
        System.out.println("  错误信息: " + result.getErrorMessage());
        
        assertFalse(result.isSuccess(), "空内容应该失败");
    }

    // ==================== 摘要测试 ====================

    @Test
    @Order(999)
    @DisplayName("测试999: 测试摘要")
    void testSummary() {
        System.out.println("\n========== 测试摘要 ==========");
        System.out.println("FileWriteTool 写入功能验证测试完成");
        System.out.println("测试覆盖:");
        System.out.println("  1. 直接调用 call() 方法写入");
        System.out.println("  2. 通过 ToolExecutor + JSON 写入");
        System.out.println("  3. file_path 字段兼容性");
        System.out.println("  4. 写入后文件可读性验证");
        System.out.println("  5. 自动创建父目录");
        System.out.println("  100. 空路径失败场景");
        System.out.println("  101. 空内容失败场景");
        System.out.println("========================\n");
    }
}
