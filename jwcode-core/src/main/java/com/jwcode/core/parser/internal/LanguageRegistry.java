package com.jwcode.core.parser.internal;

import com.jwcode.core.parser.internal.javaparser.JavaLanguage;
import com.jwcode.core.parser.internal.regex.GoGrammar;
import com.jwcode.core.parser.internal.regex.JavaScriptGrammar;
import com.jwcode.core.parser.internal.regex.PythonGrammar;
import com.jwcode.core.parser.internal.regex.RegexGrammar;
import com.jwcode.core.parser.internal.regex.RegexLanguage;
import com.jwcode.core.parser.treesitter.Language;

import java.util.Map;

/**
 * Registry that maps file extensions to language implementations.
 */
public final class LanguageRegistry {

    private static final Map<String, Language> LANGUAGE_MAP = Map.ofEntries(
        Map.entry("java", new JavaLanguage()),
        Map.entry("py", new RegexLanguage(new PythonGrammar())),
        Map.entry("python", new RegexLanguage(new PythonGrammar())),
        Map.entry("js", new RegexLanguage(new JavaScriptGrammar())),
        Map.entry("jsx", new RegexLanguage(new JavaScriptGrammar())),
        Map.entry("mjs", new RegexLanguage(new JavaScriptGrammar())),
        Map.entry("ts", new RegexLanguage(new JavaScriptGrammar())),
        Map.entry("tsx", new RegexLanguage(new JavaScriptGrammar())),
        Map.entry("javascript", new RegexLanguage(new JavaScriptGrammar())),
        Map.entry("typescript", new RegexLanguage(new JavaScriptGrammar())),
        Map.entry("go", new RegexLanguage(new GoGrammar()))
    );

    private LanguageRegistry() {}

    public static Language forExtension(String extension) {
        Language lang = LANGUAGE_MAP.get(extension.toLowerCase());
        if (lang != null) {
            return lang;
        }
        // Fallback: generic regex parser
        return new RegexLanguage(new GenericGrammar());
    }

    private static class GenericGrammar implements RegexGrammar {
        @Override
        public String languageName() {
            return "generic";
        }

        @Override
        public java.util.regex.Pattern classPattern() {
            return java.util.regex.Pattern.compile("\\bclass\\s+(\\w+)");
        }

        @Override
        public java.util.regex.Pattern functionPattern() {
            return java.util.regex.Pattern.compile("(?:function|def|func)\\s+(\\w+)");
        }

        @Override
        public java.util.regex.Pattern fieldPattern() {
            return java.util.regex.Pattern.compile("(?:const|let|var|self\\.)\\s*(\\w+)\\s*=");
        }

        @Override
        public java.util.regex.Pattern importPattern() {
            return java.util.regex.Pattern.compile("^(?:import|from|require)");
        }

        @Override
        public java.util.List<String> complexityTokens() {
            return java.util.List.of("if", "for", "while");
        }
    }
}
