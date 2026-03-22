package dev.vfyjxf.cirrus.api.recipe.content;

import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;

public non-sealed interface ComplexContent extends InputContent {

    /**
     * Complex content is not enumerable by default.
     *
     * @return false
     */
    @Override
    default boolean isEnumerable() {
        return false;
    }

    /**
     * Enumerate all possible variants of this content,if {@link #isEnumerable()} returns false,an empty iterable will be returned.
     *
     * @return an unmodifiable view of all possible variants.
     * @see #isEnumerable()
     */
    @Override
    default RichIterable<? extends ContentVariant> enumerate() {
        return Lists.immutable.empty();
    }
}
