"""
Tree-sitter 解析服务 API
提供 HTTP 接口供 Java 端调用
"""

from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field
from typing import List, Optional, Dict, Any
import uvicorn
import sys
import os

# 添加当前目录到路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from parser_core import TreeSitterAnalyzer, CodeSymbol, ParseResult, SymbolKind

app = FastAPI(
    title="JwCode Tree-sitter Parser",
    description="代码解析服务，支持 Java/Python/JavaScript/TypeScript/Go/Rust",
    version="1.0.0"
)

# 全局分析器实例
analyzer = TreeSitterAnalyzer()


# ============ 请求/响应模型 ============

class ParseRequest(BaseModel):
    file_path: str = Field(..., description="文件路径")
    content: Optional[str] = Field(None, description="文件内容，不传则读取文件")


class SymbolAtPositionRequest(BaseModel):
    file_path: str = Field(..., description="文件路径")
    line: int = Field(..., description="行号（从0开始）")
    col: int = Field(..., description="列号（从0开始）")
    content: Optional[str] = Field(None, description="文件内容")


class ParseResponse(BaseModel):
    success: bool
    data: Optional[Dict[str, Any]] = None
    error: Optional[str] = None


class BatchParseRequest(BaseModel):
    files: List[ParseRequest]


class BatchParseResponse(BaseModel):
    results: Dict[str, ParseResponse]


class HealthResponse(BaseModel):
    status: str
    version: str
    supported_languages: List[str]


# ============ API 端点 ============

@app.get("/health", response_model=HealthResponse)
async def health_check():
    """健康检查端点"""
    return HealthResponse(
        status="healthy",
        version="1.0.0",
        supported_languages=["java", "python", "javascript", "typescript", "go", "rust"]
    )


@app.post("/parse", response_model=ParseResponse)
async def parse_file(request: ParseRequest):
    """
    解析单个文件，返回符号信息
    
    - 支持语言：Java, Python, JavaScript, TypeScript, Go, Rust
    - 自动检测语言类型
    - 返回类、方法、字段、导入等信息
    """
    try:
        result = analyzer.parse_file(request.file_path, request.content)
        return ParseResponse(
            success=len(result.errors) == 0,
            data=result.to_dict()
        )
    except Exception as e:
        return ParseResponse(
            success=False,
            error=str(e)
        )


@app.post("/parse/batch", response_model=BatchParseResponse)
async def parse_batch(request: BatchParseRequest):
    """批量解析文件"""
    results = {}
    for file_req in request.files:
        try:
            result = analyzer.parse_file(file_req.file_path, file_req.content)
            results[file_req.file_path] = ParseResponse(
                success=len(result.errors) == 0,
                data=result.to_dict()
            )
        except Exception as e:
            results[file_req.file_path] = ParseResponse(
                success=False,
                error=str(e)
            )
    
    return BatchParseResponse(results=results)


@app.post("/symbol-at-position", response_model=ParseResponse)
async def get_symbol_at_position(request: SymbolAtPositionRequest):
    """获取指定位置的符号信息"""
    try:
        symbol = analyzer.find_symbol_at_position(
            request.file_path,
            request.line,
            request.col,
            request.content
        )
        
        if symbol:
            return ParseResponse(
                success=True,
                data=symbol.to_dict()
            )
        else:
            return ParseResponse(
                success=True,
                data=None
            )
    except Exception as e:
        return ParseResponse(
            success=False,
            error=str(e)
        )


@app.post("/enclosing-scope", response_model=ParseResponse)
async def get_enclosing_scope(request: SymbolAtPositionRequest):
    """获取包含指定位置的最内层作用域"""
    try:
        symbol = analyzer.get_enclosing_scope(
            request.file_path,
            request.line,
            request.content
        )
        
        if symbol:
            return ParseResponse(
                success=True,
                data=symbol.to_dict()
            )
        else:
            return ParseResponse(
                success=True,
                data=None
            )
    except Exception as e:
        return ParseResponse(
            success=False,
            error=str(e)
        )


@app.get("/detect-language")
async def detect_language(file_path: str):
    """检测文件语言类型"""
    try:
        language = analyzer.detect_language(file_path)
        return {
            "file_path": file_path,
            "language": language,
            "supported": language is not None
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


# ============ 主函数 ============

def main():
    """启动服务"""
    import argparse
    
    parser = argparse.ArgumentParser(description="JwCode Tree-sitter Parser Service")
    parser.add_argument("--host", default="127.0.0.1", help="绑定地址")
    parser.add_argument("--port", type=int, default=8765, help="端口")
    parser.add_argument("--reload", action="store_true", help="开发模式自动重载")
    
    args = parser.parse_args()
    
    print(f"🚀 Starting Tree-sitter Parser Service on {args.host}:{args.port}")
    print(f"📚 Supported languages: Java, Python, JavaScript, TypeScript, Go, Rust")
    
    uvicorn.run(
        "api_server:app",
        host=args.host,
        port=args.port,
        reload=args.reload,
        log_level="info"
    )


if __name__ == "__main__":
    main()
