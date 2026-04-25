package com.jwcode.core.parser.internal.regex;

import java.util.List;
import java.util.regex.Pattern;

public class PythonGrammar implements RegexGrammar {
    @Override
    public String languageName() {
        return "python";
    }

    @Override
    public Pattern classPattern() {
        return Pattern.compile("^\\s*class\\s+(\\w+)");
    }

    @Override
    public Pattern functionPattern() {
        return Pattern.compile("^\\s*def\\s+(\\w+)");
    }

    @Override
    public Pattern fieldPattern() {
        return Pattern.compile("^\\s*self\\.(\\w+)\\s*=");
    }

    @Override
    public Pattern importPattern() {
        return Pattern.compile("^(?:from\\s+\\S+\\s+import|import\\s+\\S+)");
    }

    @Override
    public List<String> complexityTokens() {
        return List.of("if", "for", "while", "except", "with");
    }
}
