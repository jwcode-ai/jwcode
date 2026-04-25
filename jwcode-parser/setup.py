#!/usr/bin/env python3
"""
JwCode Parser 安装脚本
自动安装 Python 依赖并验证安装
"""

import subprocess
import sys
import os

def run_command(cmd, description):
    """运行命令并显示结果"""
    print(f"\n🔄 {description}...")
    try:
        result = subprocess.run(
            cmd,
            shell=True,
            check=True,
            capture_output=True,
            text=True
        )
        print(f"✅ {description}成功")
        return True
    except subprocess.CalledProcessError as e:
        print(f"❌ {description}失败:")
        print(e.stderr)
        return False

def check_python_version():
    """检查 Python 版本"""
    version = sys.version_info
    if version.major < 3 or (version.major == 3 and version.minor < 8):
        print("❌ 需要 Python 3.8 或更高版本")
        sys.exit(1)
    print(f"✅ Python 版本: {version.major}.{version.minor}.{version.micro}")

def main():
    print("=" * 50)
    print("JwCode Tree-sitter Parser 安装程序")
    print("=" * 50)
    
    # 检查 Python 版本
    check_python_version()
    
    # 获取脚本所在目录
    script_dir = os.path.dirname(os.path.abspath(__file__))
    python_dir = os.path.join(script_dir, "src/main/python")
    
    # 安装依赖
    requirements = os.path.join(python_dir, "requirements.txt")
    if not os.path.exists(requirements):
        print(f"❌ 未找到 requirements.txt: {requirements}")
        sys.exit(1)
    
    if not run_command(f"pip install -r {requirements}", "安装 Python 依赖"):
        sys.exit(1)
    
    # 验证安装
    print("\n🔄 验证 Tree-sitter 安装...")
    try:
        from tree_sitter import Language, Parser
        import tree_sitter_java
        print("✅ Tree-sitter 核心库安装成功")
        print("✅ Tree-sitter Java 语言库安装成功")
    except ImportError as e:
        print(f"❌ 验证失败: {e}")
        sys.exit(1)
    
    print("\n" + "=" * 50)
    print("✅ 安装完成！")
    print("=" * 50)
    print("\n快速开始:")
    print("1. 启动服务: python src/main/python/api_server.py")
    print("2. 运行测试: mvn test -pl jwcode-parser")
    print("\n或在 Java 中使用:")
    print("  try (TreeSitterClient client = TreeSitterClient.startEmbedded()) {")
    print("      ParseResult result = client.parseFile(Path.of(\"MyClass.java\"));")
    print("  }")

if __name__ == "__main__":
    main()
