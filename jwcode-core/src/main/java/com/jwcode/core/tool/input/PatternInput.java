package com.jwcode.core.tool.input;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Pattern 工具输入参数
 * 用于高级正则表达式模式匹配和替换
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PatternInput(
    
    /** 搜索模式（正则表达式） */
    @JsonProperty("pattern")
    String pattern,
    
    /** 替换文本 */
    @JsonProperty("replacement")
    String replacement,
    
    /** 文件路径 */
    @JsonProperty("file")
    String file,
    
    /** 是否忽略大小写 */
    @JsonProperty("ignoreCase")
    Boolean ignoreCase,
    
    /** 是否全局匹配 */
    @JsonProperty("global")
    Boolean global,
    
    /** 是否启用递归搜索 */
    @JsonProperty("recursive")
    Boolean recursive,
    
    /** 文件过滤模式（如 "*.java,*.ts"） */
    @JsonProperty("fileFilter")
    String fileFilter
) {}