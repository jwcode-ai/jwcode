package com.jwcode.core.tool;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * LegacyTool - 旧版工具接口（向后兼容）
 * 
 * 功能说明：
 * 为旧版工具提供向后兼容的接口，不需要泛型参数。
 * 
 * @author JWCode Team
 * @since 1.0.0
 */
public interface LegacyTool extends Tool<Object, Object, String> {
    
    /**
     * 获取输入 schema（旧版接口）
     * 
     * @return JSON schema 字符串
     */
    default String getInputSchemaLegacy() {
        return "{}";
    }
    
    /**
     * 获取输入 schema 的 JSON 格式
     * 由于 Tool 接口返回 JsonNode，这里转换为字符串返回以保持兼容性
     */
    @Override
    default JsonNode getInputSchema() {
        return null;
    }
}
