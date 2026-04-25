package com.jwcode.core.parser.internal.javaparser;

import com.jwcode.core.parser.treesitter.Language;
import com.jwcode.core.parser.treesitter.Tree;

/**
 * Java language definition backed by JDK Compiler API.
 */
public class JavaLanguage extends Language {

    public JavaLanguage() {
        super("java");
    }

    @Override
    public Tree parse(String source, Tree oldTree) {
        return new JavaParserEngine().parse(source);
    }
}
