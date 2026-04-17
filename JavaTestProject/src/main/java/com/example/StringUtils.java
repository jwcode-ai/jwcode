package com.example;

/**
 * 字符串工具类 - 用于测试搜索和编辑功能
 */
public class StringUtils {
    
    /**
     * 判断字符串是否为空
     */
    public boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }
    
    /**
     * 反转字符串
     */
    public String reverse(String str) {
        if (str == null) {
            return null;
        }
        return new StringBuilder(str).reverse().toString();
    }
    
    /**
     * 计算字符串中字符出现次数
     */
    public int countOccurrences(String str, char target) {
        if (isEmpty(str)) {
            return 0;
        }
        int count = 0;
        for (char c : str.toCharArray()) {
            if (c == target) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 检查是否是回文字符串
     */
    public boolean isPalindrome(String str) {
        if (isEmpty(str)) {
            return false;
        }
        String cleaned = str.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return cleaned.equals(reverse(cleaned));
    }
}
