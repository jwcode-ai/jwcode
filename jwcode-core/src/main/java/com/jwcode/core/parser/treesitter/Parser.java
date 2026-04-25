package com.jwcode.core.parser.treesitter;

import java.util.Optional;

/**
 * A stateful object that is used to produce a {@link Tree} based on some source code.
 *
 * <p>Refer to tree-sitter C core: {@code TSParser} (api.h L45)</p>
 */
public class Parser {
    private Language language;

    public Parser() {
    }

    public boolean setLanguage(Language language) {
        this.language = language;
        return true;
    }

    public Language getLanguage() {
        return language;
    }

    public Optional<Tree> parse(String source) {
        return parse(source, null);
    }

    public Optional<Tree> parse(String source, Tree oldTree) {
        if (language == null) {
            return Optional.empty();
        }
        Tree tree = language.parse(source, oldTree);
        return Optional.ofNullable(tree);
    }

    public void reset() {
        this.language = null;
    }
}
