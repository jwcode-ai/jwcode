package com.jwcode.core.parser.internal.regex;

import java.util.List;
import java.util.regex.Pattern;

public class JavaScriptGrammar implements RegexGrammar {
    @Override
    public String languageName() {
        return "javascript";
    }

    @Override
    public Pattern classPattern() {
        return Pattern.compile("\\bclass\\s+(\\w+)");
    }

    @Override
    public Pattern functionPattern() {
        return Pattern.compile("(?:function\\s+(\\w+)|(?:const|let|var)\\s+(\\w+)\\s*=\\s*(?:async\\s*)?\\(|(\\w+)\\s*:\\s*(?:async\\s*)?\\(|(?!(?:if|for|while|switch|catch)\\b)\\b(\\w+)\\s*\\()");
    }

    @Override
    public Pattern fieldPattern() {
        return Pattern.compile("(?:this\\.(\\w+)\\s*=|(?:const|let|var)\\s+(\\w+)\\s*=)");
    }

    @Override
    public Pattern importPattern() {
        return Pattern.compile("^(?:import|require\\()");
    }

    @Override
    public List<String> complexityTokens() {
        return List.of("if", "for", "while", "catch", "switch");
    }
}
