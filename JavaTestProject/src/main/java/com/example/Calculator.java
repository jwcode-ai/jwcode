package com.example;

/**
 * 计算器类 - 用于测试工具功能
 */
public class Calculator {
    
    /**
     * 加法运算
     */
    public int add(int a, int b) {
        return a + b;
    }
    
    /**
     * 减法运算
     */
    public int subtract(int a, int b) {
        return a - b;
    }
    
    /**
     * 乘法运算
     */
    public int multiply(int a, int b) {
        return a * b;
    }
    
    /**
     * 除法运算
     */
    public double divide(int a, int b) {
        if (b == 0) {
            throw new ArithmeticException("Cannot divide by zero");
        }
        return (double) a / b;
    }
    
    /**
     * 计算阶乘 - 使用递归算法
     */
    public long factorial(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("Number must be non-negative");
        }
        if (n == 0 || n == 1) {
            return 1;
        }
        long result = 1;
        for (int i = 2; i <= n; i++) {
            result *= i;
        }
        return result;
    }
}
