package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cloudlib.util.Checks;
import org.jetbrains.annotations.Nullable;

public record EffectPath(
    String channel,
    int index,
    @Nullable String field
) {
    public EffectPath {
        Checks.checkNotNull(channel, "channel");
    }

    //region helper

    public static EffectPath input(int index) {
        return new EffectPath("input", index, null);
    }

    public static EffectPath output(int index) {
        return new EffectPath("output", index, null);
    }

    public EffectPath withField(String field) {
        return new EffectPath(channel, index, field);
    }

    //endregion
}
