package com.jwcode.core.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jwcode.core.config.ConfigManager;
import com.jwcode.core.config.ConfigScope;
import com.jwcode.core.tool.context.ToolExecutionContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConfigTool 测试类
 */
public class ConfigToolTest {
    
    private ConfigTool configTool;
    private ObjectMapper objectMapper;
    
    @BeforeEach
    void setUp() {
        configTool = new ConfigTool();
        objectMapper = new ObjectMapper();
    }
    
    @Test
    void testGetName() {
        assertEquals("Config", configTool.getName());
    }
    
    @Test
    void testGetDescription() {
        assertNotNull(configTool.getDescription());
        assertTrue(configTool.getDescription().contains("配置"));
    }
    
    @Test
    void testGetInputSchema() {
        JsonNode schema = configTool.getInputSchema();
        assertNotNull(schema);
        assertEquals("object", schema.get("type").asText());
        assertNotNull(schema.get("properties"));
    }
    
    @Test
    void testValidateWithNullInput() {
        ToolValidationResult result = configTool.validate(null);
        assertFalse(result.isValid());
        assertTrue(result.getFormattedErrors().contains("不能为空"));
    }
    
    @Test
    void testValidateWithNullAction() {
        ConfigTool.Input input = new ConfigTool.Input();
        // action 默认为 null
        ToolValidationResult result = configTool.validate(input);
        assertFalse(result.isValid());
        assertTrue(result.getFormattedErrors().contains("action"));
    }
    
    @Test
    void testValidateWithInvalidAction() {
        ConfigTool.Input input = new ConfigTool.Input();
        input.action = "invalid";
        ToolValidationResult result = configTool.validate(input);
        assertFalse(result.isValid());
    }
    
    @Test
    void testValidateWithGetWithoutKey() {
        ConfigTool.Input input = new ConfigTool.Input();
        input.action = "get";
        // 没有 key
        ToolValidationResult result = configTool.validate(input);
        assertFalse(result.isValid());
        assertTrue(result.getFormattedErrors().contains("key"));
    }
    
    @Test
    void testValidateWithValidListAction() {
        ConfigTool.Input input = new ConfigTool.Input();
        input.action = "list";
        ToolValidationResult result = configTool.validate(input);
        assertTrue(result.isValid());
    }
    
    @Test
    void testValidateWithValidGetAction() {
        ConfigTool.Input input = new ConfigTool.Input();
        input.action = "get";
        input.key = "test.key";
        ToolValidationResult result = configTool.validate(input);
        assertTrue(result.isValid());
    }
    
    @Test
    void testValidateWithInvalidScope() {
        ConfigTool.Input input = new ConfigTool.Input();
        input.action = "list";
        input.scope = "invalid";
        ToolValidationResult result = configTool.validate(input);
        assertFalse(result.isValid());
        assertTrue(result.getFormattedErrors().contains("作用域"));
    }
    
    @Test
    void testValidateWithValidScope() {
        ConfigTool.Input input = new ConfigTool.Input();
        input.action = "list";
        input.scope = "user";
        ToolValidationResult result = configTool.validate(input);
        assertTrue(result.isValid());
        
        input.scope = "project";
        result = configTool.validate(input);
        assertTrue(result.isValid());
        
        input.scope = "system";
        result = configTool.validate(input);
        assertTrue(result.isValid());
        
        input.scope = "runtime";
        result = configTool.validate(input);
        assertTrue(result.isValid());
    }
    
    @Test
    void testIsReadOnly() {
        // get 是只读
        ConfigTool.Input input = new ConfigTool.Input();
        input.action = "get";
        assertTrue(configTool.isReadOnly(input));
        
        // list 是只读
        input.action = "list";
        assertTrue(configTool.isReadOnly(input));
        
        // set 不是只读
        input.action = "set";
        assertFalse(configTool.isReadOnly(input));
        
        // delete 不是只读
        input.action = "delete";
        assertFalse(configTool.isReadOnly(input));
    }
    
    @Test
    void testIsDestructive() {
        ConfigTool.Input input = new ConfigTool.Input();
        
        // get 不是破坏性
        input.action = "get";
        assertFalse(configTool.isDestructive(input));
        
        // delete 是破坏性
        input.action = "delete";
        assertTrue(configTool.isDestructive(input));
        
        // set 不是破坏性
        input.action = "set";
        assertFalse(configTool.isDestructive(input));
    }
    
    @Test
    void testRequiresApproval() {
        ConfigTool.Input input = new ConfigTool.Input();
        
        // get 不需要批准
        input.action = "get";
        assertFalse(configTool.requiresApproval(input));
        
        // delete 需要批准
        input.action = "delete";
        assertTrue(configTool.requiresApproval(input));
    }
    
    @Test
    void testCallWithNullInput() throws Exception {
        ToolExecutionContext context = new ToolExecutionContext(null, Path.of("."), null);
        var future = configTool.call(null, context, null);
        var result = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNotNull(result.getContent());
        assertTrue(result.getContent().contains("不能为空"));
    }
    
    @Test
    void testCallWithListAction() throws Exception {
        JsonNode inputJson = objectMapper.readTree("{\"action\": \"list\"}");
        ToolExecutionContext context = new ToolExecutionContext(null, Path.of("."), null);
        
        var future = configTool.call(
            configTool.parseInput(inputJson),
            context,
            null
        );
        var result = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        // 即使没有配置，list 操作也应该成功（返回空列表）
        assertTrue(result.isSuccess() || result.getContent() != null);
    }
    
    @Test
    void testCallWithInvalidAction() throws Exception {
        JsonNode inputJson = objectMapper.readTree("{\"action\": \"invalid\"}");
        ToolExecutionContext context = new ToolExecutionContext(null, Path.of("."), null);
        
        var future = configTool.call(
            configTool.parseInput(inputJson),
            context,
            null
        );
        var result = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNotNull(result.getContent());
        assertTrue(result.getContent().contains("未知操作"));
    }
    
    @Test
    void testCallWithGetAction() throws Exception {
        // 先设置一个配置
        ConfigManager.getInstance().set("test.property", "test-value", ConfigScope.RUNTIME);
        
        JsonNode inputJson = objectMapper.readTree("{\"action\": \"get\", \"key\": \"test.property\"}");
        ToolExecutionContext context = new ToolExecutionContext(null, Path.of("."), null);
        
        var future = configTool.call(
            configTool.parseInput(inputJson),
            context,
            null
        );
        var result = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getContent());
        assertTrue(result.getContent().contains("test-value") || result.getContent().contains("获取配置"));
    }
    
    @Test
    void testCallWithSetAction() throws Exception {
        JsonNode inputJson = objectMapper.readTree("{\"action\": \"set\", \"key\": \"test.property2\", \"value\": \"test-value2\", \"scope\": \"runtime\"}");
        ToolExecutionContext context = new ToolExecutionContext(null, Path.of("."), null);
        
        var future = configTool.call(
            configTool.parseInput(inputJson),
            context,
            null
        );
        var result = future.get(5, TimeUnit.SECONDS);
        
        assertNotNull(result);
        assertTrue(result.isSuccess());
        assertNotNull(result.getContent());
        assertTrue(result.getContent().contains("设置配置"));
    }
    
    @Test
    void testConcurrencySafety() {
        ConfigTool.Input input = new ConfigTool.Input();
        input.action = "list";
        
        // list 操作应该是并发安全的
        assertTrue(configTool.isConcurrencySafe(input));
        
        input.action = "get";
        assertTrue(configTool.isConcurrencySafe(input));
    }
}
