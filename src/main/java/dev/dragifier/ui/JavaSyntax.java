package dev.dragifier.ui;

import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Regex-based Java syntax highlighting for the event code editor. */
public final class JavaSyntax {

    private static final String[] KEYWORDS = {
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "final", "finally", "float", "for", "goto", "if", "implements",
            "import", "instanceof", "int", "interface", "long", "native", "new",
            "package", "private", "protected", "public", "return", "short", "static",
            "strictfp", "super", "switch", "synchronized", "this", "throw", "throws",
            "transient", "try", "var", "void", "volatile", "while", "true", "false", "null"
    };

    private static final Pattern PATTERN = Pattern.compile(
            "(?<COMMENT>//[^\n]*|/\\*(.|\\R)*?\\*/)"
            + "|(?<STRING>\"([^\"\\\\]|\\\\.)*\")"
            + "|(?<KEYWORD>\\b(" + String.join("|", KEYWORDS) + ")\\b)"
            + "|(?<NUMBER>\\b\\d+(\\.\\d+)?\\b)"
            + "|(?<PAREN>[()])"
            + "|(?<BRACE>[{}])"
            + "|(?<BRACKET>[\\[\\]])"
            + "|(?<SEMICOLON>;)");

    private JavaSyntax() {}

    public static StyleSpans<Collection<String>> highlight(String text) {
        Matcher matcher = PATTERN.matcher(text);
        StyleSpansBuilder<Collection<String>> spans = new StyleSpansBuilder<>();
        int last = 0;
        while (matcher.find()) {
            String styleClass = matcher.group("COMMENT") != null ? "comment"
                    : matcher.group("STRING") != null ? "string"
                    : matcher.group("KEYWORD") != null ? "keyword"
                    : matcher.group("NUMBER") != null ? "number"
                    : matcher.group("PAREN") != null ? "paren"
                    : matcher.group("BRACE") != null ? "brace"
                    : matcher.group("BRACKET") != null ? "bracket"
                    : "semicolon";
            spans.add(Collections.emptyList(), matcher.start() - last);
            spans.add(Collections.singleton(styleClass), matcher.end() - matcher.start());
            last = matcher.end();
        }
        spans.add(Collections.emptyList(), text.length() - last);
        return spans.create();
    }
}
