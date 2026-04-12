package com.jwcode.core.llm;

import java.util.HashMap;
import java.util.Map;

/**
 * LLM 工具定义 - OpenAI Function Calling 格式
 */
public class LLMTool {
    
    private String type;
    private Function function;
    
    public LLMTool() {
        this.type = "function";
    }
    
    public static LLMTool create(String name, String description, Map<String, Object> parameters) {
        LLMTool tool = new LLMTool();
        tool.setType("function");
        tool.setFunction(new Function(name, description, parameters));
        return tool;
    }
    
    // Getters and Setters
    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
    
    public Function getFunction() { return function; }
    public void setFunction(Function function) { this.function = function; }
    
    public Object toOpenAIFormat() {
        Map<String, Object> map = new HashMap<>();
        map.put("type", type);
        
        Map<String, Object> funcMap = new HashMap<>();
        funcMap.put("name", function.getName());
        funcMap.put("description", function.getDescription());
        funcMap.put("parameters", function.getParameters());
        
        map.put("function", funcMap);
        return map;
    }
    
    public static class Function {
        private String name;
        private String description;
        private Map<String, Object> parameters;
        
        public Function() {}
        
        public Function(String name, String description, Map<String, Object> parameters) {
            this.name = name;
            this.description = description;
            this.parameters = parameters;
        }
        
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        
        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }
        
        public Map<String, Object> getParameters() { return parameters; }
        public void setParameters(Map<String, Object> parameters) { this.parameters = parameters; }
    }
}
