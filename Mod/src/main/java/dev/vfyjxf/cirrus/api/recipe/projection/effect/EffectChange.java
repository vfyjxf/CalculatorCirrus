package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import org.jetbrains.annotations.Nullable;

public record EffectChange(
    @Nullable Object before,
    @Nullable Object after
) {}
