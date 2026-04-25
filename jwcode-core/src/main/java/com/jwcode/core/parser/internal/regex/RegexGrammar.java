package com.jwcode.core.parser.internal.regex;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Grammar patterns for a heuristic regex-based parser.
 */
public interface RegexGrammar {
    String languageName();
    Pattern classPattern();
    Pattern functionPattern();
    Pattern fieldPattern();
    Pattern importPattern();
    List<String> complexityTokens();
}
