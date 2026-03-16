package dev.vfyjxf.cirrus.api.recipe.execution.util;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.network.chat.Component;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.factory.Sets;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.set.ImmutableSet;
import org.jetbrains.annotations.Nullable;

/**
 * Provides structured user-facing guidance for execution actions.
 *
 * @param summary  required single-line summary (Component, not null)
 * @param detail   optional detailed explanation (Component, nullable)
 * @param steps    ordered list of guidance steps (empty if none)
 * @param hintTags tags classifying this guidance (empty if none)
 */
public record ExecutionGuidance(
        Component summary,
        @Nullable Component detail,
        ImmutableList<Component> steps,
        ImmutableSet<Namespace> hintTags
) {

    public ExecutionGuidance {
        Checks.checkNotNull(summary, "summary");
        Checks.checkNotNull(steps, "steps");
        Checks.checkNotNull(hintTags, "hintTags");
    }

    //region factories

    public static ExecutionGuidance simple(Component summary) {
        return new ExecutionGuidance(summary, null, Lists.immutable.empty(), Sets.immutable.empty());
    }

    //endregion
}
