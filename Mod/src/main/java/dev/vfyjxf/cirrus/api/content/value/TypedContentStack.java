package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.ComponentCanonicalizer;
import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

final class TypedContentStack<T, S> implements ContentStack<T, S> {

    private final StackType<T, S> type;
    private final VariantType<T, S> variantType;
    private final S value;
    private long amount;
    /**
     * Lazily resolved canonical components plus the pool generation they were resolved in;
     * reference equality between two of these proves component-map equality, and differing
     * representatives resolved in the same generation prove inequality. Benign race on concurrent
     * resolution: the pool returns the same representative for equal maps.
     *
     * @see dev.vfyjxf.cirrus.api.content.ComponentCanonicalizer
     */
    private transient volatile Canonical canonical;
    private transient volatile ComponentStackKey componentKey;

    private record Canonical(DataComponentMap components, long generation) {
    }

    TypedContentStack(StackType<T, S> type, long amount, S value) {
        this.type = type;
        this.variantType = ContentApi.instance().findVariantType(type, type.getContent(value));
        this.value = value;
        this.amount = amount;
    }

    TypedContentStack(StackType<T, S> type, S value, long amount) {
        this(type, amount, value);
    }

    TypedContentStack(StackType<T, S> type, VariantType<T, S> variantType, S value, long amount) {
        this.type = type;
        this.variantType = variantType;
        this.value = value;
        this.amount = amount;
    }

    @Override
    public StackType<T, S> type() {
        return type;
    }

    @Override
    public ContentType<T> contentType() {
        return type.contentType();
    }

    @Override
    public VariantType<T, S> variantType() {
        return variantType;
    }

    @Override
    public S value() {
        return value;
    }

    @Override
    public DataComponentMap canonicalComponents() {
        return canonicalHolder().components();
    }

    @Override
    public long canonicalGeneration() {
        return canonicalHolder().generation();
    }

    @Override
    public ComponentStackKey componentKey() {
        ComponentStackKey key = componentKey;
        if (key == null) {
            key = ComponentStackKey.of(this);
            componentKey = key;
        }
        return key;
    }

    private Canonical canonicalHolder() {
        Canonical holder = canonical;
        if (holder == null) {
            holder = new Canonical(type().canonicalComponents(value), ComponentCanonicalizer.generation());
            canonical = holder;
        }
        return holder;
    }

    @Override
    public long amount() {
        return amount;
    }

    @Override
    public ContentStack<T, S> setAmount(long amount) {
        this.amount = amount;
        return this;
    }

    @Override
    public ContentStack<T, S> copy() {
        ContentStack<T, S> copied = ContentStack.super.copy();
        if (copied instanceof TypedContentStack<?, ?> typed) typed.adoptCachesFrom(this);
        return copied;
    }

    @Override
    public ContentStack<T, S> copyWithAmount(long amount) {
        ContentStack<T, S> copied = ContentStack.super.copyWithAmount(amount);
        if (copied instanceof TypedContentStack<?, ?> typed) typed.adoptCachesFrom(this);
        return copied;
    }

    @Override
    public ContentStack<T, S> normalize() {
        ContentStack<T, S> normalized = ContentStack.super.normalize();
        if (normalized instanceof TypedContentStack<?, ?> typed) typed.adoptCachesFrom(this);
        return normalized;
    }

    /**
     * Adopts the canonical/key caches of an equal source: {@code copy}/{@code normalize} chains
     * COW-share the components patch with the source, so the source's canonical representative is
     * equally valid here — sparing a fresh pool canonicalization per copied stack.
     */
    private void adoptCachesFrom(TypedContentStack<?, ?> source) {
        if (this.canonical == null && source.canonical != null) {
            this.canonical = source.canonical;
            ComponentStackKey sourceKey = source.componentKey;
            this.componentKey = sourceKey == null ? null : sourceKey.forStack(this);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
        if (o == null || getClass() != o.getClass()) return false;

        TypedContentStack<?, ?> that = (TypedContentStack<?, ?>) o;
        return type == that.type &&
               variantType == that.variantType &&
               amount == that.amount &&
               type.contentType().matches(content(), (T) that.content());
    }

    @Override
    public int hashCode() {
        int result = type.hashCode();
        result = 31 * result + variantType.hashCode();
        result = 31 * result + type.contentType().hash(content());
        result = 31 * result + Long.hashCode(amount);
        return result;
    }

    @Override
    public ContentStack<Item, ItemStack> cheatsStack() {
        ItemStack itemStack = type.toItemStack(this);
        return new TypedContentStack<>(CirrusTypes.Contents.ITEM_STACK, itemStack, itemStack.getCount());
    }

    @Override
    public String toString() {
        return "ContentStack{" +
               "type=" + type +
               ", amount=" + amount +
               ", value=" + type.toString(value) +
               '}';
    }
}


final class EmptyContentStack<T, S> implements ContentStack<T, S> {

    @SuppressWarnings("rawtypes")
    static final ContentStack instance = new EmptyContentStack<>();

    @Override
    public StackType<T, S> type() {
        throw new UnsupportedOperationException("This operation is not supported for an empty content stack.");
    }

    @Override
    public ContentType<T> contentType() {
        throw new UnsupportedOperationException("This operation is not supported for an empty content stack.");
    }

    @Override
    public VariantType<T, S> variantType() {
        throw new UnsupportedOperationException("This operation is not supported for an empty content stack.");
    }

    @Override
    public S value() {
        throw new UnsupportedOperationException("This operation is not supported for an empty content stack.");
    }

    @Override
    public long amount() {
        return 0;
    }

    @Override
    public ContentStack<T, S> setAmount(long amount) {
        throw new UnsupportedOperationException("This operation is not supported for an empty content stack.");
    }

    @Override
    public ContentStack<Item, ItemStack> cheatsStack() {
        return new TypedContentStack<>(CirrusTypes.Contents.ITEM_STACK, 0, ItemStack.EMPTY);
    }

    @Override
    public String toString() {
        return "empty content stack";
    }
}