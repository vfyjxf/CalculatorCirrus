package dev.vfyjxf.cirrus.ui.widget.calculator.search;

import java.util.Objects;

public record SearchHighlightSegment(String text, SearchHighlightKind kind, int start, int end) {
    public SearchHighlightSegment {
        Objects.requireNonNull(text, "text");
        Objects.requireNonNull(kind, "kind");
        if (start < 0 || end < start) {
            throw new IllegalArgumentException("Invalid highlight range: " + start + ".." + end);
        }
    }
}
