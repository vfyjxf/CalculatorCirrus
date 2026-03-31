package dev.vfyjxf.cirrus.api.recipe.execution.overlay;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;

/**
 * Snapshot of target output progress for execution overlays.
 */
public record OverlayTargetProgress(RecipeContent content, long required, long current) {
    public OverlayTargetProgress {
        content = content == null ? RecipeContent.empty() : content.copyWithAmount(Math.max(1L, content.amount()));
        required = Math.max(0L, required);
        current = Math.max(0L, current);
    }
}
