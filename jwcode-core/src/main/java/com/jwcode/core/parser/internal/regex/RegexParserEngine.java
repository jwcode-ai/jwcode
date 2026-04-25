package com.jwcode.core.parser.internal.regex;

import com.jwcode.core.parser.treesitter.Node;
import com.jwcode.core.parser.treesitter.Point;
import com.jwcode.core.parser.treesitter.Tree;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Heuristic parser based on line-by-line regex matching.
 * Produces a simplified AST compatible with the tree-sitter API.
 */
public class RegexParserEngine {

    public Tree parse(String source, RegexGrammar grammar) {
        List<Node> children = new ArrayList<>();
        String[] lines = source.split("\n", -1);
        int byteOffset = 0;

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            int lineLength = line.length();

            addMatch(children, grammar.classPattern(), line, i, byteOffset, "class_declaration");
            addMatch(children, grammar.functionPattern(), line, i, byteOffset, "function_declaration");
            addMatch(children, grammar.fieldPattern(), line, i, byteOffset, "field_declaration");
            addMatch(children, grammar.importPattern(), line, i, byteOffset, "import_declaration");

            // Complexity tokens: create pseudo nodes so that complexity calculation works
            for (String token : grammar.complexityTokens()) {
                if (line.contains(token)) {
                    children.add(Node.of(
                        token + "_statement", token,
                        byteOffset, byteOffset + lineLength,
                        new Point(i, 0), new Point(i, lineLength),
                        List.of(), true, false
                    ));
                }
            }

            byteOffset += lineLength + 1; // +1 for '\n'
        }

        Point endPoint = lines.length == 0
            ? new Point(0, 0)
            : new Point(lines.length - 1, lines[lines.length - 1].length());

        Node root = Node.of("program", source, 0, source.length(),
                            new Point(0, 0), endPoint,
                            children, true, false);
        return new Tree(root, new RegexLanguage(grammar), source);
    }

    private void addMatch(List<Node> children, java.util.regex.Pattern pattern, String line,
                          int lineIndex, int byteOffset, String nodeType) {
        Matcher m = pattern.matcher(line);
        while (m.find()) {
            String name = null;
            for (int g = 1; g <= m.groupCount(); g++) {
                if (m.group(g) != null) {
                    name = m.group(g).trim();
                    break;
                }
            }
            if (name == null) {
                name = m.group().trim();
            }
            int startCol = m.start();
            int endCol = m.end();
            Point startPoint = new Point(lineIndex, startCol);
            Point endPoint = new Point(lineIndex, endCol);
            int startByte = byteOffset + startCol;
            int endByte = byteOffset + endCol;
            children.add(Node.of(nodeType, name, startByte, endByte,
                                 startPoint, endPoint, List.of(), true, false));
        }
    }
}
