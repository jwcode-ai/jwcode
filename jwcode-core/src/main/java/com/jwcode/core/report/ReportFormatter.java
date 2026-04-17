package com.jwcode.core.report;

/**
 * 报告格式化器接口
 */
public interface ReportFormatter {
    
    /**
     * 格式化报告
     */
    String format(TestReport report);
    
    /**
     * 获取内容类型
     */
    String getContentType();
    
    /**
     * 获取文件扩展名
     */
    String getFileExtension();
}
