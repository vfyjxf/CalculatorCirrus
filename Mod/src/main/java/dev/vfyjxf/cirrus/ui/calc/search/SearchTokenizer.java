package dev.vfyjxf.cirrus.ui.calc.search;

import java.util.ArrayList;
import java.util.List;

final class SearchTokenizer {
    private SearchTokenizer() {
    }

    static List<Token> tokenize(String input) {
        List<Token> tokens = new ArrayList<>();
        if (input == null || input.isBlank()) {
            return tokens;
        }
        StringBuilder current = new StringBuilder();
        boolean quoted = false;
        boolean currentQuoted = false;
        for (int i = 0; i < input.length(); i++) {
            char c = input.charAt(i);
            if (c == '"') {
                quoted = !quoted;
                currentQuoted = true;
                continue;
            }
            if (!quoted && Character.isWhitespace(c)) {
                flush(tokens, current, currentQuoted);
                currentQuoted = false;
                continue;
            }
            if (!quoted && c == '|') {
                flush(tokens, current, currentQuoted);
                tokens.add(new Token("|", false));
                currentQuoted = false;
                continue;
            }
            current.append(c);
        }
        flush(tokens, current, currentQuoted);
        return tokens;
    }

    private static void flush(List<Token> tokens, StringBuilder current, boolean quoted) {
        if (current.length() > 0) {
            tokens.add(new Token(current.toString(), quoted));
            current.setLength(0);
        }
    }

    record Token(String text, boolean quoted) {
    }
}
