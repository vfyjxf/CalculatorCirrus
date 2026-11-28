package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

/**
 * Component-aware map key wrapping a normalized {@link ContentStack}. Equality is exactly
 * {@link dev.vfyjxf.cirrus.api.content.MatchStrategy#identityAndComponent} — identity plus data
 * components, amount-independent — unlike {@code TypedContentStack}'s deliberately component-blind
 * {@code equals}, which must not be changed because other maps rely on it.
 *
 * <p>Used across {@link dev.vfyjxf.cirrus.api.content.stock.Stock} and the planning engine as the
 * canonical strongly-typed resource identity preserving 100% of Cirrus stack types and data components.
 */
public final class ComponentStackKey implements Comparable<ComponentStackKey> {

    private final StackType<?, ?> type;
    private final Object content;
    private final @Nullable DataComponentMap components;
    private final @Nullable DataComponentMap canonicalComponents;
    private final ContentStack<?, ?> stack;
    private final int hash;
    private transient volatile @Nullable String cachedCanonicalKey;

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
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot create ComponentStackKey from empty ContentStack");
        }
        StackType<?, ?> type = stack.type();
        Object content = stack.content();
        DataComponentMap components = stack.components();
        int componentsHash = components == null ? 0 : components.hashCode();
        int hash = 31 * System.identityHashCode(type) + 31 * contentTypeOf(type).hash(content) + componentsHash;
        return new ComponentStackKey(type, content, components, stack.canonicalComponents(), stack, hash);
    }

    public static ComponentStackKey ofNormalized(ContentStack<?, ?> stack) {
        Objects.requireNonNull(stack, "stack");
        if (stack.isEmpty()) {
            throw new IllegalArgumentException("Cannot create ComponentStackKey from empty ContentStack");
        }
        return stack.normalize().componentKey();
    }

    public static ComponentStackKey ofItem(Item item) {
        Objects.requireNonNull(item, "item");
        return ContentStack.fromContent(item).normalize().componentKey();
    }

    public static ComponentStackKey ofFluid(Fluid fluid) {
        Objects.requireNonNull(fluid, "fluid");
        return ContentStack.fromContent(fluid).normalize().componentKey();
    }

    public static Optional<ComponentStackKey> fromItemStack(@Nullable ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        ContentStack<Item, ItemStack> cs = ContentStack.fromStack(stack);
        return cs.isEmpty() ? Optional.empty() : Optional.of(cs.normalize().componentKey());
    }

    public static Optional<ComponentStackKey> fromFluidStack(@Nullable FluidStack stack) {
        if (stack == null || stack.isEmpty()) {
            return Optional.empty();
        }
        ContentStack<Fluid, FluidStack> cs = ContentStack.fromStack(stack);
        return cs.isEmpty() ? Optional.empty() : Optional.of(cs.normalize().componentKey());
    }

    /** @return the wrapped stack (normalized by the caller). */
    public ContentStack<?, ?> stack() {
        return stack;
    }

    public ContentStack<?, ?> toStack(long amount) {
        return stack.copyWithAmount(amount);
    }

    public StackType<?, ?> stackType() {
        return type;
    }

    public ContentType<?> contentType() {
        return type.contentType();
    }

    public ContentKind<?> kind() {
        return stack.kind();
    }

    public Namespace contentTypeId() {
        return type.contentType().typeId();
    }

    public Namespace stackTypeId() {
        return type.typeId();
    }

    public @Nullable ResourceLocation location() {
        try {
            return contentTypeOf(type).getLocation(content);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public @Nullable String namespace() {
        ResourceLocation loc = location();
        if (loc != null) {
            return loc.getNamespace();
        }
        try {
            return contentTypeOf(type).getNamespace(content);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    public boolean hasComponents() {
        return !stack.componentsPatch().isEmpty();
    }

    public ComponentStackKey withoutComponents() {
        return hasComponents() ? stack.wildcard().componentKey() : this;
    }

    public boolean sameContent(ComponentStackKey other) {
        if (this == other) return true;
        return type == other.type && contentTypeOf(type).matches(content, other.content);
    }

    /**
     * Deterministic canonical string for structural hashing, deterministic sorting, and debug logs.
     * Never used as a lossy in-memory replacement for {@link ComponentStackKey}.
     */
    public String canonicalKey() {
        String cached = cachedCanonicalKey;
        if (cached != null) {
            return cached;
        }
        ResourceLocation loc = location();
        String idStr = loc != null ? loc.toString() : contentTypeOf(type).asString(content);
        String base = contentTypeId() + "|" + idStr;
        DataComponentPatch patch = stack.componentsPatch();
        if (patch.isEmpty()) {
            cachedCanonicalKey = base;
            return base;
        }
        TreeMap<String, String> sortedPatch = new TreeMap<>();
        for (Map.Entry<DataComponentType<?>, Optional<?>> entry : patch.entrySet()) {
            DataComponentType<?> compType = entry.getKey();
            if (compType == null || compType.isTransient()) {
                continue;
            }
            ResourceLocation compLoc = BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(compType);
            String compKey = compLoc != null ? compLoc.toString() : compType.getClass().getName();
            Optional<?> val = entry.getValue();
            sortedPatch.put(compKey, val.map(Object::toString).orElse("!<removed>"));
        }
        if (sortedPatch.isEmpty()) {
            cachedCanonicalKey = base;
            return base;
        }
        StringBuilder sb = new StringBuilder(base).append('|');
        boolean first = true;
        for (var e : sortedPatch.entrySet()) {
            if (!first) sb.append(',');
            first = false;
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        String computed = sb.toString();
        cachedCanonicalKey = computed;
        return computed;
    }

    public String displayId() {
        ResourceLocation loc = location();
        if (loc != null) {
            return loc.toString();
        }
        return contentTypeOf(type).asString(content);
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
    public int compareTo(ComponentStackKey other) {
        if (this == other || this.equals(other)) {
            return 0;
        }
        int c = contentTypeId().compareTo(other.contentTypeId());
        if (c != 0) return c;
        c = displayId().compareTo(other.displayId());
        if (c != 0) return c;
        boolean hcA = hasComponents();
        boolean hcB = other.hasComponents();
        if (hcA != hcB) return hcA ? 1 : -1;
        c = canonicalKey().compareTo(other.canonicalKey());
        if (c != 0) return c;
        c = Integer.compare(hash, other.hash);
        if (c != 0) return c;
        return Integer.compare(System.identityHashCode(this), System.identityHashCode(other));
    }

    @Override
    public boolean equals(@Nullable Object o) {
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
        return "ComponentStackKey{" + canonicalKey() + "}";
    }

    @SuppressWarnings("unchecked")
    private static ContentType<Object> contentTypeOf(StackType<?, ?> type) {
        return (ContentType<Object>) type.contentType();
    }
}
