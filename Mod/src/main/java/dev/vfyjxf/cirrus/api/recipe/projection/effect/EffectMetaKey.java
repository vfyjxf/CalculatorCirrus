package dev.vfyjxf.cirrus.api.recipe.projection.effect;

import dev.vfyjxf.cloudlib.util.Checks;

/**
 * Standardized and typed key for {@link EffectMeta}.
 *
 * @param id        stable key id
 * @param valueType value type of this meta key
 * @param <T>       value type
 */
public record EffectMetaKey<T>(
    String id,
    Class<T> valueType
) {
    public EffectMetaKey {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(valueType, "valueType");
        Checks.checkArgument(!id.isEmpty(), "id cannot be empty");
    }

    //region factory

    public static <T> EffectMetaKey<T> of(String id, Class<T> valueType) {
        return new EffectMetaKey<>(id, valueType);
    }

    //endregion
}
