package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import org.jetbrains.annotations.Nullable;

public sealed interface InputEffect extends ProjectionEffect permits InputInjected, InputRejected, InputReplaced {

    int index();

    @Nullable InputContent before();

    @Nullable InputContent after();

    default long beforeAmount() {
        InputContent before = before();
        return before == null ? 0L : before.amount();
    }

    default long afterAmount() {
        InputContent after = after();
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
