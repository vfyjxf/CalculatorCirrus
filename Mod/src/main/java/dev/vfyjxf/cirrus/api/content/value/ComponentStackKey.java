package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import net.minecraft.core.component.DataComponentMap;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

/**
 * Component-aware map key wrapping a normalized {@link ContentStack}. Equality is exactly
 * {@link dev.vfyjxf.cirrus.api.content.MatchStrategy#identityAndComponent} — identity plus data
 * components, amount-independent — unlike {@code TypedContentStack}'s deliberately component-blind
 * {@code equals}, which must not be changed because other maps rely on it.
 *
 * <p>Used for the amount map in {@link dev.vfyjxf.cirrus.api.content.stock.Stock}, where keying by
 * the stack itself conflated same-content/different-component entries.
 *
 * <p>The hash includes a value-based components hash (stable across canonical-pool clears) so that
 * variants of the same content land in distinct buckets. Bucket collisions between variants of the
 * same content are separated by reference comparison of the canonical components, falling back to
 * the vanilla deep comparison only when the canonical representatives come from different pool
 * generations.
 */
public final class ComponentStackKey {

    private final StackType<?, ?> type;
    private final Object content;
    private final @Nullable DataComponentMap components;
    private final @Nullable DataComponentMap canonicalComponents;
    private final ContentStack<?, ?> stack;
    private final int hash;

    private ComponentStackKey(
            StackType<?, ?> type,
            Object content,
            @Nullable DataComponentMap components,
            @Nullable DataComponentMap canonicalComponents,
            ContentStack<?, ?> stack,
            int hash
    ) {
        this.type = type;
        this.content = content;
        this.components = components;
        this.canonicalComponents = canonicalComponents;
        this.stack = stack;
        this.hash = hash;
    }

    public static ComponentStackKey of(ContentStack<?, ?> stack) {
        StackType<?, ?> type = stack.type();
        Object content = stack.content();
        DataComponentMap components = stack.components();
        int componentsHash = components == null ? 0 : components.hashCode();
        int hash = 31 * System.identityHashCode(type) + 31 * contentTypeOf(type).hash(content) + componentsHash;
        return new ComponentStackKey(type, content, components, stack.canonicalComponents(), stack, hash);
    }

    /** @return the wrapped stack (normalized by the caller). */
    public ContentStack<?, ?> stack() {
        return stack;
    }

    /**
     * @return an equal key that wraps the given stack instead — the copy/normalize source of
     * {@code stack} produced equal components (patch maps are COW-shared), so this key's fields
     * remain valid for the new stack reference.
     */
    ComponentStackKey forStack(ContentStack<?, ?> stack) {
        return new ComponentStackKey(type, content, components, canonicalComponents, stack, hash);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ComponentStackKey key)) return false;
        if (hash != key.hash || type != key.type) return false;
        if (!contentTypeOf(type).matches(content, key.content)) return false;
        if (canonicalComponents == key.canonicalComponents) return true;
        // Canonical representatives from different pool generations: fall back to exact comparison.
        return Objects.equals(components, key.components);
    }

    @Override
    public int hashCode() {
        return hash;
    }

    @Override
    public String toString() {
        return "ComponentStackKey{" + stack + "}";
    }

    @SuppressWarnings("unchecked")
    private static ContentType<Object> contentTypeOf(StackType<?, ?> type) {
        return (ContentType<Object>) type.contentType();
    }
}
