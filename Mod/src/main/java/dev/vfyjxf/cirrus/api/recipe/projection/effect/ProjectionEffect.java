package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cloudlib.util.Checks;

public record ProjectionEffect(
    EffectType type,
    EffectSource source,
    EffectPath path,
    EffectChange change,
    EffectMeta meta
) {
    public ProjectionEffect {
        Checks.checkNotNull(type, "type");
        Checks.checkNotNull(source, "source");
        Checks.checkNotNull(path, "path");
        Checks.checkNotNull(change, "change");
        Checks.checkNotNull(meta, "meta");
    }
}
