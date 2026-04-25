package com.jwcode.core.parser.internal.regex;

import com.jwcode.core.parser.treesitter.Language;
import com.jwcode.core.parser.treesitter.Tree;

/**
 * Language implementation backed by heuristic regex parsing.
 */
public class RegexLanguage extends Language {
    private final RegexGrammar grammar;

    public RegexLanguage(RegexGrammar grammar) {
        super(grammar.languageName());
        this.grammar = grammar;
    }

    @Override
    public Tree parse(String source, Tree oldTree) {
        return new RegexParserEngine().parse(source, grammar);
    }
}
