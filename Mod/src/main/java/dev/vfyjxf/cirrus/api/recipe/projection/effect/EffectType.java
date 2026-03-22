package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

public record EffectType(
    Namespace id
) {
    public EffectType {
        Checks.checkNotNull(id, "id");
    }

    //region factory

    public static EffectType of(Namespace id) {
        return new EffectType(id);
    }

    //endregion
}
