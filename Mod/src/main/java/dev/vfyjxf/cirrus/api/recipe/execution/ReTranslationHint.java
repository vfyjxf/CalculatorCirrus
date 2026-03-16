package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.network.chat.Component;

/**
 * Requests rebuilding an interaction execution plan from fresh runtime state.
 */
public record ReTranslationHint(Component summary) {

    public ReTranslationHint {
        Checks.checkNotNull(summary, "summary");
    }
}
