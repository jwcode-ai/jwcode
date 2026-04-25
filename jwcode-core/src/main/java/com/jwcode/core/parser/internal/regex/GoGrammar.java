package com.jwcode.core.parser.internal.regex;

import java.util.List;
import java.util.regex.Pattern;

public class GoGrammar implements RegexGrammar {
    @Override
    public String languageName() {
        return "go";
    }

    @Override
    public Pattern classPattern() {
        return Pattern.compile("^\\s*type\\s+(\\w+)\\s+(?:struct|interface)");
    }

    @Override
    public Pattern functionPattern() {
        return Pattern.compile("^\\s*func\\s+(?:\\(\\w+\\s+\\*?\\w+\\)\\s+)?(\\w+)");
    }

    @Override
    public Pattern fieldPattern() {
        return Pattern.compile("^\\s*(\\w+)\\s+\\w+");
    }

    @Override
    public Pattern importPattern() {
        return Pattern.compile("^\\s*import\\s+(.+)");
    }

    @Override
    public List<String> complexityTokens() {
        return List.of("if", "for", "switch", "select");
    }
}
