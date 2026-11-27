package dev.vfyjxf.cirrus.ui.widget.calculator.search;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record SearchSuggestion(
    String token,
    String replacement,
    int replaceStart,
    int replaceEnd,
    int count,
    @Nullable Component description
) {
    public SearchSuggestion(String token, String replacement, int replaceStart, int replaceEnd) {
        this(token, replacement, replaceStart, replaceEnd, 0, null);
    }

    public SearchSuggestion(String token, String replacement, int replaceStart, int replaceEnd, int count) {
        this(token, replacement, replaceStart, replaceEnd, count, null);
    }

    public SearchSuggestion {
        Objects.requireNonNull(token, "token");
        Objects.requireNonNull(replacement, "replacement");
        if (replaceStart < 0 || replaceEnd < replaceStart) {
            throw new IllegalArgumentException(
                "Invalid suggestion replacement range: " + replaceStart + ".." + replaceEnd
            );
        }
    }

    public String applyTo(String query) {
        Objects.requireNonNull(query, "query");
        String suffix = replacement.endsWith(":") || replacement.endsWith(" ") ? "" : " ";
        return query.substring(0, replaceStart) + replacement + suffix + query.substring(replaceEnd);
    }
}
