package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import org.jspecify.annotations.Nullable;

public sealed interface OutputEffect extends ProjectionEffect permits OutputInjected, OutputRejected, OutputReplaced {

    int index();

    @Nullable
    OutputContent before();

    @Nullable
    OutputContent after();

    default long beforeAmount() {
        OutputContent before = before();
        return before == null ? 0L : before.amount();
    }

    default long afterAmount() {
        OutputContent after = after();
        return after == null ? 0L : after.amount();
    }

    default long amountDelta() {
        return afterAmount() - beforeAmount();
    }

    default @Nullable Double amountFactor() {
        long before = beforeAmount();
        return before <= 0L ? null : (double) afterAmount() / (double) before;
    }
}
