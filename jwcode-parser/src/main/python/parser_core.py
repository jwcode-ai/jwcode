"""
Tree-sitter 核心解析模块
支持多语言的代码解析和符号提取
"""

from typing import List, Dict, Optional, Tuple
from dataclasses import dataclass, asdict
from enum import Enum
import tree_sitter
from tree_sitter import Language, Parser, Tree, Node

# 导入语言库 - 延迟加载以优化启动时间
_languages = {}
_parsers = {}


def _get_language(lang_name: str) -> Language:
    """延迟加载语言库"""
    if lang_name not in _languages:
        if lang_name == "java":
            import tree_sitter_java
            _languages[lang_name] = Language(tree_sitter_java.language())
        elif lang_name == "python":
            import tree_sitter_python
            _languages[lang_name] = Language(tree_sitter_python.language())
        elif lang_name == "javascript":
            import tree_sitter_javascript
            _languages[lang_name] = Language(tree_sitter_javascript.language())
        elif lang_name == "typescript":
            import tree_sitter_typescript
            _languages[lang_name] = Language(tree_sitter_typescript.language_typescript())
        elif lang_name == "go":
            import tree_sitter_go
            _languages[lang_name] = Language(tree_sitter_go.language())
        elif lang_name == "rust":
            import tree_sitter_rust
            _languages[lang_name] = Language(tree_sitter_rust.language())
    return _languages[lang_name]


def _get_parser(lang_name: str) -> Parser:
    """获取或创建解析器"""
    if lang_name not in _parsers:
        parser = Parser()
        parser.set_language(_get_language(lang_name))
        _parsers[lang_name] = parser
    return _parsers[lang_name]


class SymbolKind(Enum):
    """符号类型"""
    CLASS = "class"
    INTERFACE = "interface"
    METHOD = "method"
    FUNCTION = "function"
    VARIABLE = "variable"
    FIELD = "field"
    ENUM = "enum"
    ANNOTATION = "annotation"
    IMPORT = "import"
    PACKAGE = "package"


@dataclass
class CodeSymbol:
    """代码符号信息"""
    name: str
    kind: SymbolKind
    start_line: int
    start_col: int
    end_line: int
    end_col: int
    signature: Optional[str] = None
    docstring: Optional[str] = None
    parent: Optional[str] = None
    children: List[str] = None
    modifiers: List[str] = None
    
    def __post_init__(self):
        if self.children is None:
            self.children = []
        if self.modifiers is None:
            self.modifiers = []
    
    def to_dict(self) -> Dict:
        return {
            **asdict(self),
            'kind': self.kind.value
        }


@dataclass
class ParseResult:
    """解析结果"""
    symbols: List[CodeSymbol]
    imports: List[str]
    package: Optional[str]
    language: str
    errors: List[str]
    
    def to_dict(self) -> Dict:
        return {
            'symbols': [s.to_dict() for s in self.symbols],
            'imports': self.imports,
            'package': self.package,
            'language': self.language,
            'errors': self.errors
        }


class TreeSitterAnalyzer:
    """Tree-sitter 代码分析器"""
    
    # 文件扩展名到语言的映射
    EXTENSION_MAP = {
        '.java': 'java',
        '.py': 'python',
        '.js': 'javascript',
        '.ts': 'typescript',
        '.tsx': 'typescript',
        '.go': 'go',
        '.rs': 'rust',
    }
    
    # 语言特定的查询
    QUERIES = {
        'java': '''
            (package_declaration
                (scoped_identifier) @package)
            
            (import_declaration
                (scoped_identifier) @import)
            
            (class_declaration
                (modifiers)? @class.modifiers
                name: (identifier) @class.name
                (superclass)? @class.extends
                (super_interfaces)? @class.implements
                body: (class_body) @class.body) @class
            
            (interface_declaration
                (modifiers)? @interface.modifiers
                name: (identifier) @interface.name) @interface
            
            (method_declaration
                (modifiers)? @method.modifiers
                type: (_)? @method.return_type
                name: (identifier) @method.name
                parameters: (formal_parameters) @method.params) @method
            
            (field_declaration
                (modifiers)? @field.modifiers
                type: (_) @field.type
                declarator: (variable_declarator
                    name: (identifier) @field.name)) @field
        ''',
        'python': '''
            (module
                (future_import_statement
                    (dotted_name) @import)
                (import_statement
                    (dotted_name) @import)
                (import_from_statement
                    module_name: (dotted_name) @import.from)
            )
            
            (class_definition
                name: (identifier) @class.name) @class
            
            (function_definition
                name: (identifier) @function.name) @function
            
            (decorated_definition
                (function_definition
                    name: (identifier) @function.name)) @decorated_function
        ''',
        'javascript': '''
            (import_statement
                source: (string) @import.source) @import
            
            (class_declaration
                name: (identifier) @class.name) @class
            
            (function_declaration
                name: (identifier) @function.name) @function
            
            (method_definition
                name: (property_identifier) @method.name) @method
            
            (variable_declarator
                name: (identifier) @variable.name) @variable
        ''',
    }
    
    def detect_language(self, file_path: str) -> Optional[str]:
        """根据文件路径检测语言"""
        from pathlib import Path
        ext = Path(file_path).suffix.lower()
        return self.EXTENSION_MAP.get(ext)
    
    def parse_file(self, file_path: str, content: Optional[str] = None) -> ParseResult:
        """
        解析文件内容
        
        Args:
            file_path: 文件路径（用于检测语言）
            content: 文件内容，为 None 时读取文件
        
        Returns:
            ParseResult 解析结果
        """
        language = self.detect_language(file_path)
        if not language:
            return ParseResult(
                symbols=[],
                imports=[],
                package=None,
                language="unknown",
                errors=[f"Unsupported file type: {file_path}"]
            )
        
        try:
            if content is None:
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    content = f.read()
            
            parser = _get_parser(language)
            tree = parser.parse(bytes(content, 'utf8'))
            
            symbols, imports, package = self._extract_symbols(
                tree.root_node, content, language
            )
            
            return ParseResult(
                symbols=symbols,
                imports=imports,
                package=package,
                language=language,
                errors=[]
            )
            
        except Exception as e:
            return ParseResult(
                symbols=[],
                imports=[],
                package=None,
                language=language,
                errors=[str(e)]
            )
    
    def _extract_symbols(self, root: Node, content: str, language: str) -> Tuple[List[CodeSymbol], List[str], Optional[str]]:
        """提取符号信息"""
        symbols = []
        imports = []
        package = None
        
        content_bytes = content.encode('utf8')
        lines = content.split('\n')
        
        def get_text(node: Node) -> str:
            return content_bytes[node.start_byte:node.end_byte].decode('utf8')
        
        def get_docstring(node: Node) -> Optional[str]:
            """提取文档字符串/注释"""
            # 简单实现：查找紧邻的前导注释
            # 实际应用中可以更复杂
            return None
        
        def traverse(node: Node, parent_symbol: Optional[str] = None):
            nonlocal package
            
            node_type = node.type
            
            # Java 特定处理
            if language == 'java':
                if node_type == 'package_declaration':
                    # 提取包名
                    for child in node.children:
                        if child.type == 'scoped_identifier':
                            package = get_text(child)
                            break
                
                elif node_type == 'import_declaration':
                    # 提取导入
                    import_text = get_text(node).replace('import ', '').replace(';', '')
                    imports.append(import_text)
                
                elif node_type == 'class_declaration':
                    name = None
                    modifiers = []
                    for child in node.children:
                        if child.type == 'modifiers':
                            modifiers = [get_text(m) for m in child.children]
                        elif child.type == 'identifier':
                            name = get_text(child)
                    
                    if name:
                        symbol = CodeSymbol(
                            name=name,
                            kind=SymbolKind.CLASS,
                            start_line=node.start_point[0],
                            start_col=node.start_point[1],
                            end_line=node.end_point[0],
                            end_col=node.end_point[1],
                            docstring=get_docstring(node),
                            modifiers=modifiers
                        )
                        symbols.append(symbol)
                        parent_symbol = name
                
                elif node_type == 'interface_declaration':
                    name = None
                    modifiers = []
                    for child in node.children:
                        if child.type == 'modifiers':
                            modifiers = [get_text(m) for m in child.children]
                        elif child.type == 'identifier':
                            name = get_text(child)
                    
                    if name:
                        symbol = CodeSymbol(
                            name=name,
                            kind=SymbolKind.INTERFACE,
                            start_line=node.start_point[0],
                            start_col=node.start_point[1],
                            end_line=node.end_point[0],
                            end_col=node.end_point[1],
                            modifiers=modifiers
                        )
                        symbols.append(symbol)
                        parent_symbol = name
                
                elif node_type == 'method_declaration':
                    name = None
                    modifiers = []
                    signature_parts = []
                    
                    for child in node.children:
                        if child.type == 'modifiers':
                            modifiers = [get_text(m) for m in child.children]
                        elif child.type == 'identifier':
                            name = get_text(child)
                        elif child.type in ['void_type', 'type_identifier', 'scoped_type_identifier', 'generic_type']:
                            signature_parts.append(('return', get_text(child)))
                        elif child.type == 'formal_parameters':
                            signature_parts.append(('params', get_text(child)))
                    
                    if name:
                        sig = ' '.join([f"{k}:{v}" for k, v in signature_parts])
                        symbol = CodeSymbol(
                            name=name,
                            kind=SymbolKind.METHOD,
                            start_line=node.start_point[0],
                            start_col=node.start_point[1],
                            end_line=node.end_point[0],
                            end_col=node.end_point[1],
                            signature=sig,
                            docstring=get_docstring(node),
                            parent=parent_symbol,
                            modifiers=modifiers
                        )
                        symbols.append(symbol)
                
                elif node_type == 'field_declaration':
                    type_name = None
                    field_names = []
                    modifiers = []
                    
                    for child in node.children:
                        if child.type == 'modifiers':
                            modifiers = [get_text(m) for m in child.children]
                        elif child.type in ['type_identifier', 'scoped_type_identifier', 'generic_type']:
                            type_name = get_text(child)
                        elif child.type == 'variable_declarator':
                            for sub in child.children:
                                if sub.type == 'identifier':
                                    field_names.append(get_text(sub))
                    
                    for field_name in field_names:
                        symbol = CodeSymbol(
                            name=field_name,
                            kind=SymbolKind.FIELD,
                            start_line=node.start_point[0],
                            start_col=node.start_point[1],
                            end_line=node.end_point[0],
                            end_col=node.end_point[1],
                            signature=type_name,
                            parent=parent_symbol,
                            modifiers=modifiers
                        )
                        symbols.append(symbol)
            
            # Python 特定处理
            elif language == 'python':
                if node_type == 'import_statement' or node_type == 'import_from_statement':
                    imports.append(get_text(node))
                
                elif node_type == 'class_definition':
                    name = None
                    for child in node.children:
                        if child.type == 'identifier':
                            name = get_text(child)
                            break
                    
                    if name:
                        symbol = CodeSymbol(
                            name=name,
                            kind=SymbolKind.CLASS,
                            start_line=node.start_point[0],
                            start_col=node.start_point[1],
                            end_line=node.end_point[0],
                            end_col=node.end_point[1],
                            docstring=get_docstring(node)
                        )
                        symbols.append(symbol)
                        parent_symbol = name
                
                elif node_type == 'function_definition':
                    name = None
                    for child in node.children:
                        if child.type == 'identifier':
                            name = get_text(child)
                            break
                    
                    if name:
                        symbol = CodeSymbol(
                            name=name,
                            kind=SymbolKind.FUNCTION,
                            start_line=node.start_point[0],
                            start_col=node.start_point[1],
                            end_line=node.end_point[0],
                            end_col=node.end_point[1],
                            docstring=get_docstring(node),
                            parent=parent_symbol
                        )
                        symbols.append(symbol)
            
            # JavaScript/TypeScript 处理
            elif language in ['javascript', 'typescript']:
                if node_type == 'import_statement':
                    imports.append(get_text(node))
                
                elif node_type == 'class_declaration':
                    name = None
                    for child in node.children:
                        if child.type == 'identifier':
                            name = get_text(child)
                            break
                    
                    if name:
                        symbol = CodeSymbol(
                            name=name,
                            kind=SymbolKind.CLASS,
                            start_line=node.start_point[0],
                            start_col=node.start_point[1],
                            end_line=node.end_point[0],
                            end_col=node.end_point[1]
                        )
                        symbols.append(symbol)
                        parent_symbol = name
                
                elif node_type == 'function_declaration':
                    name = None
                    for child in node.children:
                        if child.type == 'identifier':
                            name = get_text(child)
                            break
                    
                    if name:
                        symbol = CodeSymbol(
                            name=name,
                            kind=SymbolKind.FUNCTION,
                            start_line=node.start_point[0],
                            start_col=node.start_point[1],
                            end_line=node.end_point[0],
                            end_col=node.end_point[1],
                            parent=parent_symbol
                        )
                        symbols.append(symbol)
                
                elif node_type == 'method_definition':
                    name = None
                    for child in node.children:
                        if child.type == 'property_identifier':
                            name = get_text(child)
                            break
                    
                    if name:
                        symbol = CodeSymbol(
                            name=name,
                            kind=SymbolKind.METHOD,
                            start_line=node.start_point[0],
                            start_col=node.start_point[1],
                            end_line=node.end_point[0],
                            end_col=node.end_point[1],
                            parent=parent_symbol
                        )
                        symbols.append(symbol)
            
            # 递归遍历子节点
            for child in node.children:
                traverse(child, parent_symbol)
        
        traverse(root)
        return symbols, imports, package
    
    def find_symbol_at_position(self, file_path: str, line: int, col: int, content: Optional[str] = None) -> Optional[CodeSymbol]:
        """查找指定位置的符号"""
        result = self.parse_file(file_path, content)
        
        for symbol in result.symbols:
            if (symbol.start_line <= line <= symbol.end_line and
                symbol.start_col <= col <= symbol.end_col):
                return symbol
        
        return None
    
    def get_enclosing_scope(self, file_path: str, line: int, content: Optional[str] = None) -> Optional[CodeSymbol]:
        """获取包含指定位置的最内层作用域"""
        result = self.parse_file(file_path, content)
        
        best_match = None
        for symbol in result.symbols:
            if symbol.start_line <= line <= symbol.end_line:
                if best_match is None:
                    best_match = symbol
                elif (symbol.start_line >= best_match.start_line and 
                      symbol.end_line <= best_match.end_line):
                    best_match = symbol
        
        return best_match
