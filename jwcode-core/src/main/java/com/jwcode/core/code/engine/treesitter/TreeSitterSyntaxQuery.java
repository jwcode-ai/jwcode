package com.jwcode.core.code.engine.treesitter;

import com.jwcode.core.code.api.QueryMatch;
import com.jwcode.core.code.api.Range;
import com.jwcode.core.code.api.SyntaxNode;
import com.jwcode.core.code.api.SyntaxQuery;
import com.jwcode.core.code.api.SyntaxTree;
import com.jwcode.core.code.engine.SyntaxEngine;

import java.nio.file.Path;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * SyntaxQuery 实现，基于 S-expression 模式匹配。
 *
 * <p>支持语法：</p>
 * <ul>
 *   <li>节点类型匹配: {@code (method_declaration)}</li>
 *   <li>具名字段: {@code name: (identifier)}</li>
 *   <li>捕获: {@code @captureName}</li>
 *   <li>谓词: {@code (#eq? @capture "value")}</li>
 *   <li>谓词: {@code (#match? @capture "regex")}</li>
 * </ul>
 */
public class TreeSitterSyntaxQuery implements SyntaxQuery {

    private final String pattern;
    private final String targetLanguage;
    private final QueryPattern compiled;

    public TreeSitterSyntaxQuery(String pattern) {
        this(pattern, null);
    }

    public TreeSitterSyntaxQuery(String pattern, String targetLanguage) {
        this.pattern = pattern;
        this.targetLanguage = targetLanguage;
        this.compiled = QueryPatternParser.parse(pattern);
    }

    @Override
    public String getPattern() {
        return pattern;
    }

    // Temporary debug accessor
    List<String> getPredicateDebug() {
        return compiled.predicates.stream()
            .map(p -> p.type + " " + p.captureName + "=" + p.expected)
            .toList();
    }

    @Override
    public String getTargetLanguage() {
        return targetLanguage;
    }

    @Override
    public List<QueryMatch> execute(SyntaxTree tree) {
        List<QueryMatch> matches = new ArrayList<>();
        String file = tree.getFilePath() != null ? tree.getFilePath() : "";
        findMatches(tree.getRootNode(), compiled, new HashMap<>(), matches, file);
        return matches;
    }

    @Override
    public List<QueryMatch> execute(SyntaxNode node) {
        List<QueryMatch> matches = new ArrayList<>();
        findMatches(node, compiled, new HashMap<>(), matches, "");
        return matches;
    }

    @Override
    public List<QueryMatch> execute(SyntaxTree tree, Predicate<QueryMatch> filter) {
        return execute(tree).stream().filter(filter).toList();
    }

    @Override
    public List<QueryMatch> executeBatch(List<Path> files, SyntaxEngine engine) {
        List<QueryMatch> all = new ArrayList<>();
        for (Path file : files) {
            try {
                SyntaxTree tree = engine.parseFile(file);
                for (QueryMatch m : execute(tree)) {
                    // Re-wrap with correct file path
                    all.add(new QueryMatch(
                        file.toString(), m.getRange(), m.getCaptures(), m.getRootMatch(), m.getScore()
                    ));
                }
            } catch (Exception e) {
                // Skip files that fail to parse
            }
        }
        return all;
    }

    @Override
    public List<QueryMatch> executeOnProject(Path projectRoot, SyntaxEngine engine) {
        // Find all source files
        List<Path> files = new ArrayList<>();
        try {
            java.nio.file.Files.walk(projectRoot)
                .filter(p -> {
                    String name = p.getFileName().toString().toLowerCase();
                    return name.endsWith(".java") || name.endsWith(".py")
                        || name.endsWith(".js") || name.endsWith(".ts")
                        || name.endsWith(".go");
                })
                .forEach(files::add);
        } catch (java.io.IOException e) {
            // ignore
        }
        return executeBatch(files, engine);
    }

    @Override
    public QueryCursor createCursor(SyntaxTree tree) {
        List<QueryMatch> matches = execute(tree);
        return new ListQueryCursor(matches);
    }

    // ========== 匹配逻辑 ==========

    private void findMatches(SyntaxNode node, QueryPattern pattern,
                             Map<String, SyntaxNode> captures,
                             List<QueryMatch> results, String file) {
        Map<String, SyntaxNode> matchCaptures = new HashMap<>();
        boolean matched = matchPattern(node, pattern, matchCaptures);
        if (matched) {
            boolean predOk = compiled.predicates.isEmpty() || evaluatePredicates(matchCaptures, compiled.predicates);
            if (predOk) {
                results.add(new QueryMatch(
                    file,
                    node.getRange(),
                    Map.copyOf(matchCaptures),
                    node,
                    1.0
                ));
            }
        }
        for (SyntaxNode child : node.getChildren()) {
            findMatches(child, pattern, captures, results, file);
        }
    }

    private boolean matchPattern(SyntaxNode node, QueryPattern pattern, Map<String, SyntaxNode> captures) {
        if (!pattern.nodeType.isEmpty() && !pattern.nodeType.equals("*")
            && !pattern.nodeType.equals(node.getType())) {
            return false;
        }

        // Match field constraints
        for (Map.Entry<String, QueryPattern> field : pattern.fields.entrySet()) {
            Optional<SyntaxNode> fieldNode = node.getField(field.getKey());
            if (fieldNode.isEmpty() || !matchPattern(fieldNode.get(), field.getValue(), captures)) {
                return false;
            }
        }

        // Match child constraints (positional)
        List<SyntaxNode> children = node.getChildren();
        int childIndex = 0;
        for (QueryPattern childPattern : pattern.children) {
            boolean found = false;
            while (childIndex < children.size()) {
                if (matchPattern(children.get(childIndex), childPattern, captures)) {
                    found = true;
                    childIndex++;
                    break;
                }
                childIndex++;
            }
            if (!found) return false;
        }

        // Record capture
        if (pattern.captureName != null) {
            captures.put(pattern.captureName, node);
        }

        return true;
    }

    private boolean evaluatePredicates(Map<String, SyntaxNode> captures, List<PredicateInfo> predicates) {
        for (PredicateInfo p : predicates) {
            SyntaxNode node = captures.get(p.captureName);
            if (node == null) {
                return false;
            }
            String text = node.getText();
            switch (p.type) {
                case EQ -> {
                    if (!p.expected.equals(text)) {
                        return false;
                    }
                }
                case MATCH -> {
                    if (!p.pattern.matcher(text).find()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    // ========== 内部数据结构 ==========

    /** 编译后的查询模式 */
    static class QueryPattern {
        String nodeType = "";
        final Map<String, QueryPattern> fields = new LinkedHashMap<>();
        final List<QueryPattern> children = new ArrayList<>();
        String captureName = null;
        final List<PredicateInfo> predicates = new ArrayList<>();
    }

    static class PredicateInfo {
        enum Type { EQ, MATCH }
        final Type type;
        final String captureName;
        final String expected;
        final Pattern pattern;

        PredicateInfo(Type type, String captureName, String expected) {
            this.type = type;
            this.captureName = captureName;
            this.expected = expected;
            this.pattern = type == Type.MATCH ? Pattern.compile(expected) : null;
        }
    }

    /** 简单的 S-expression 查询解析器 */
    static class QueryPatternParser {
        static QueryPattern parse(String input) {
            Tokenizer tokenizer = new Tokenizer(input);
            QueryPattern root = new QueryPattern();
            while (tokenizer.hasNext()) {
                String token = tokenizer.peek();
                if (token.equals("(")) {
                    tokenizer.next();
                    QueryPattern child = parseNode(tokenizer);
                    // Capture after top-level pattern: (node) @cap
                    if (tokenizer.hasNext() && tokenizer.peek().startsWith("@")) {
                        child.captureName = tokenizer.next().substring(1);
                    }
                    root.children.add(child);
                } else if (token.startsWith("@")) {
                    root.captureName = tokenizer.next().substring(1);
                } else if (token.startsWith("#")) {
                    tokenizer.next(); // skip bare predicate at root
                } else {
                    tokenizer.next(); // skip unknown
                }
            }
            // If there's only one child and root is otherwise empty, return the child
            if (root.children.size() == 1 && root.fields.isEmpty()
                && root.captureName == null && root.predicates.isEmpty()) {
                QueryPattern single = root.children.get(0);
                // Promote predicates and capture from root to single if needed
                single.predicates.addAll(root.predicates);
                if (root.captureName != null && single.captureName == null) {
                    single.captureName = root.captureName;
                }
                return single;
            }
            return root;
        }

        private static QueryPattern parseNode(Tokenizer tokenizer) {
            QueryPattern pattern = new QueryPattern();

            // Node type or field name
            if (!tokenizer.hasNext()) return pattern;
            String first = tokenizer.peek();

            // Check for predicate
            if (first.startsWith("#")) {
                parsePredicate(tokenizer, pattern);
                return pattern;
            }

            tokenizer.next(); // consume first token
            if (!first.equals("(")) {
                pattern.nodeType = first;
            }

            while (tokenizer.hasNext()) {
                String token = tokenizer.peek();
                if (token.equals(")")) {
                    tokenizer.next();
                    break;
                }
                if (token.equals("(")) {
                    tokenizer.next();
                    // Check if this is a predicate group: (#eq? ...)
                    if (tokenizer.hasNext() && tokenizer.peek().startsWith("#")) {
                        parsePredicate(tokenizer, pattern);
                    } else {
                        QueryPattern child = parseNode(tokenizer);
                        // Capture after child pattern: (child) @cap
                        if (tokenizer.hasNext() && tokenizer.peek().startsWith("@")) {
                            child.captureName = tokenizer.next().substring(1);
                        }
                        pattern.children.add(child);
                    }
                } else if (token.endsWith(":")) {
                    // Field name
                    String fieldName = token.substring(0, token.length() - 1);
                    tokenizer.next();
                    if (tokenizer.hasNext() && tokenizer.peek().equals("(")) {
                        tokenizer.next();
                        QueryPattern fieldPattern = parseNode(tokenizer);
                        // Capture after field pattern: name: (field) @cap
                        if (tokenizer.hasNext() && tokenizer.peek().startsWith("@")) {
                            fieldPattern.captureName = tokenizer.next().substring(1);
                        }
                        pattern.fields.put(fieldName, fieldPattern);
                    }
                } else if (token.startsWith("@")) {
                    pattern.captureName = tokenizer.next().substring(1);
                } else if (token.startsWith("#")) {
                    parsePredicate(tokenizer, pattern);
                } else {
                    // Literal constraint (e.g., "public")
                    tokenizer.next();
                    QueryPattern literal = new QueryPattern();
                    literal.nodeType = stripQuotes(token);
                    pattern.children.add(literal);
                }
            }
            return pattern;
        }

        private static void parsePredicate(Tokenizer tokenizer, QueryPattern target) {
            String predToken = tokenizer.next(); // #eq? or #match?
            String predName = predToken.substring(1); // strip leading #
            String capture = tokenizer.next(); // @capture
            String value = tokenizer.next(); // "value"
            tokenizer.next(); // )

            String capName = capture.startsWith("@") ? capture.substring(1) : capture;
            String val = stripQuotes(value);

            if (predName.equals("eq?")) {
                target.predicates.add(new PredicateInfo(PredicateInfo.Type.EQ, capName, val));
            } else if (predName.equals("match?")) {
                target.predicates.add(new PredicateInfo(PredicateInfo.Type.MATCH, capName, val));
            }
        }

        private static String stripQuotes(String s) {
            if (s.length() >= 2 && s.startsWith("\"") && s.endsWith("\"")) {
                return s.substring(1, s.length() - 1);
            }
            return s;
        }
    }

    static class Tokenizer {
        private final List<String> tokens;
        private int pos = 0;

        Tokenizer(String input) {
            this.tokens = tokenize(input);
        }

        boolean hasNext() {
            return pos < tokens.size();
        }

        String peek() {
            return tokens.get(pos);
        }

        String next() {
            return tokens.get(pos++);
        }

        private static List<String> tokenize(String input) {
            List<String> result = new ArrayList<>();
            int i = 0;
            while (i < input.length()) {
                char c = input.charAt(i);
                if (Character.isWhitespace(c)) {
                    i++;
                    continue;
                }
                if (c == '(' || c == ')') {
                    result.add(String.valueOf(c));
                    i++;
                    continue;
                }
                if (c == '"') {
                    int end = input.indexOf('"', i + 1);
                    if (end < 0) end = input.length() - 1;
                    result.add(input.substring(i, end + 1));
                    i = end + 1;
                    continue;
                }
                // Read identifier / symbol
                int start = i;
                while (i < input.length()) {
                    char ch = input.charAt(i);
                    if (Character.isWhitespace(ch) || ch == '(' || ch == ')') break;
                    i++;
                }
                result.add(input.substring(start, i));
            }
            return result;
        }
    }

    static class ListQueryCursor implements QueryCursor {
        private final List<QueryMatch> matches;
        private int index = 0;

        ListQueryCursor(List<QueryMatch> matches) {
            this.matches = matches;
        }

        @Override
        public boolean hasNext() {
            return index < matches.size();
        }

        @Override
        public QueryMatch next() {
            return matches.get(index++);
        }

        @Override
        public int getProcessedCount() {
            return index;
        }

        @Override
        public void close() {
            // no-op
        }
    }
}
