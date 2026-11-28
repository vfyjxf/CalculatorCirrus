package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import org.jspecify.annotations.Nullable;

import java.util.Objects;

public record PaletteItem(
    String id,
    String categoryKey,
    String displayText,
    @Nullable String shortcutHint,
    @Nullable ComponentStackKey iconResource,
    int score,
    PaletteAction primaryAction,
    @Nullable PaletteAction shiftAction
) {

    public PaletteItem {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(categoryKey, "categoryKey");
        Objects.requireNonNull(displayText, "displayText");
        Objects.requireNonNull(primaryAction, "primaryAction");
    }
}
