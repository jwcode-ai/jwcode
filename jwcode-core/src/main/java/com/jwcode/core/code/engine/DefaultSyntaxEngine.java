package com.jwcode.core.code.engine;

import com.jwcode.core.code.api.SyntaxQuery;
import com.jwcode.core.code.api.SyntaxTree;
import com.jwcode.core.code.api.TextEdit;
import com.jwcode.core.code.engine.treesitter.TreeSitterSyntaxQuery;
import com.jwcode.core.code.engine.treesitter.TreeSitterSyntaxTree;
import com.jwcode.core.parser.TreeSitterParser;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

/**
 * SyntaxEngine 的默认实现，基于现有的 {@link TreeSitterParser}。
 *
 * <p>支持语言：Java、Python、JavaScript、TypeScript、Go。</p>
 */
public class DefaultSyntaxEngine implements SyntaxEngine {

    private final TreeSitterParser parser;
    private boolean initialized = false;

    public DefaultSyntaxEngine() {
        this.parser = new TreeSitterParser();
    }

    @Override
    public String getName() {
        return "default";
    }

    @Override
    public String getVersion() {
        return "1.0.0";
    }

    @Override
    public List<Language> getSupportedLanguages() {
        return List.of(
            new Language("java", "Java", List.of(".java")),
            new Language("python", "Python", List.of(".py")),
            new Language("javascript", "JavaScript", List.of(".js", ".jsx", ".mjs")),
            new Language("typescript", "TypeScript", List.of(".ts", ".tsx")),
            new Language("go", "Go", List.of(".go"))
        );
    }

    @Override
    public Optional<Language> detectLanguage(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        for (Language lang : getSupportedLanguages()) {
            for (String ext : lang.extensions()) {
                if (name.endsWith(ext)) {
                    return Optional.of(lang);
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public Optional<Language> detectLanguage(String sourceCode, String hint) {
        if (hint != null) {
            return detectLanguage(Path.of("file." + hint.toLowerCase()));
        }
        // Simple heuristic
        if (sourceCode.contains("public class ") || sourceCode.contains("import java.")) {
            return Optional.of(getSupportedLanguages().get(0));
        }
        if (sourceCode.contains("def ") && sourceCode.contains(":")) {
            return Optional.of(getSupportedLanguages().get(1));
        }
        if (sourceCode.contains("function ") || sourceCode.contains("const ")) {
            return Optional.of(getSupportedLanguages().get(2));
        }
        if (sourceCode.contains("package main")) {
            return Optional.of(getSupportedLanguages().get(4));
        }
        return Optional.empty();
    }

    @Override
    public SyntaxTree parse(String source, Language language) throws ParseException {
        // TreeSitterParser works on files, so we write to a temp file
        try {
            Path temp = java.nio.file.Files.createTempFile("parse", "." + language.extensions().get(0));
            java.nio.file.Files.writeString(temp, source);
            var result = parser.parseFile(temp, language.id());
            java.nio.file.Files.deleteIfExists(temp);

            if (!result.isSuccess()) {
                throw new ParseException("Parse failed: " + result.getError());
            }
            // TreeSitterParser returns ParseResult, not Tree. We need to access the internal tree.
            // Since ParseResult doesn't expose the Tree, we use parser's internal mechanism.
            // Actually, TreeSitterParser.parseFile caches and returns ParseResult.
            // We need to parse directly through the language.
            var langImpl = com.jwcode.core.parser.internal.LanguageRegistry.forExtension(language.id());
            if (langImpl == null) {
                throw new ParseException("Language not supported: " + language.id());
            }
            var tree = langImpl.parse(source, null);
            if (tree == null) {
                throw new ParseException("Parse returned null for language: " + language.id());
            }
            return new TreeSitterSyntaxTree(tree, temp.toString());
        } catch (java.io.IOException e) {
            throw new ParseException("IO error during parse", e);
        }
    }

    @Override
    public SyntaxTree parseFile(Path file) throws ParseException {
        var langOpt = detectLanguage(file);
        if (langOpt.isEmpty()) {
            throw new ParseException("无法检测文件语言: " + file);
        }
        String source;
        try {
            source = java.nio.file.Files.readString(file);
        } catch (java.io.IOException e) {
            throw new ParseException("读取文件失败: " + file, e);
        }
        return parse(source, langOpt.get());
    }

    @Override
    public SyntaxTree parseIncremental(SyntaxTree oldTree, String source, List<TextEdit> edits) {
        return oldTree.edit(edits);
    }

    @Override
    public SyntaxQuery createQuery(String pattern) throws QueryException {
        return new TreeSitterSyntaxQuery(pattern);
    }

    @Override
    public SyntaxQuery createQuery(String pattern, Language language) throws QueryException {
        return new TreeSitterSyntaxQuery(pattern, language.id());
    }

    @Override
    public List<NamedQuery> getBuiltinQueries(Language language) {
        return List.of(); // TODO: populate from BuiltinQueryTemplates
    }

    @Override
    public boolean supports(Language language) {
        return detectLanguage(Path.of("x" + language.extensions().get(0))).isPresent();
    }

    @Override
    public boolean supportsIncremental() {
        return false;
    }

    @Override
    public boolean supportsErrorRecovery() {
        return false;
    }

    @Override
    public boolean supportsQuery() {
        return true;
    }

    @Override
    public EngineCapabilities getCapabilities() {
        return new EngineCapabilities(
            getName(), getVersion(), getSupportedLanguages(),
            supportsIncremental(), supportsErrorRecovery(), supportsQuery(),
            false, 10, 100
        );
    }

    @Override
    public void initialize() {
        this.initialized = true;
    }

    @Override
    public boolean isInitialized() {
        return initialized;
    }

    @Override
    public void shutdown() {
        this.initialized = false;
    }
}
