# Java Test Project

这是一个用于测试 JwCode 各项工具和技能的 Java 测试项目。

## 项目结构

```
JavaTestProject/
├── pom.xml              # Maven 配置文件
├── README.md            # 项目说明
└── src/
    ├── main/java/com/example/
    │   ├── Calculator.java   # 计算器类
    │   └── StringUtils.java  # 字符串工具类
    └── test/java/com/example/
        └── CalculatorTest.java  # 测试类
```

## 功能模块

### 1. Calculator (计算器)
- 加法、减法、乘法、除法
- 阶乘计算
- 异常处理

### 2. StringUtils (字符串工具)
- 空字符串检查
- 字符串反转
- 字符统计
- 回文检查

## 测试覆盖

- 单元测试覆盖率 > 80%
- 边界条件测试
- 异常处理测试

## 构建命令

```bash
mvn clean compile
mvn test
mvn package
```
