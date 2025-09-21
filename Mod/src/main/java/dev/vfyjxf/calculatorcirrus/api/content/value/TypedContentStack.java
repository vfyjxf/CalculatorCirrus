package dev.vfyjxf.calculatorcirrus.api.content.value;

import dev.vfyjxf.calculatorcirrus.api.content.CirrusTypes;
import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;
import dev.vfyjxf.calculatorcirrus.api.content.type.ContentType;
import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import dev.vfyjxf.cloudlib.api.util.MutableLists;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

final class TypedContentStack<T, S> implements ContentStack<T, S> {

    private final MutableList<BiConsumer<RichTooltip, TooltipContext>> tooltipConfigurers = MutableLists.empty();
    private final S value;
    private final StackType<T, S> type;
    private long amount;

    TypedContentStack(StackType<T, S> type, S value) {
        this(type, value, type.quantity());
    }

    TypedContentStack(StackType<T, S> type, S value, long amount) {
        this.type = type;
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
    public S value() {
        return value;
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

    @Nullable
    @Override
    public RichTooltip getTooltip(TooltipContext context) {
        if (FMLEnvironment.dist.isDedicatedServer()) return null;
        RichTooltip richTooltip = RichTooltip.create();
        this.configureTooltip(richTooltip, context);
        return richTooltip;
    }

    @Override
    public void configureTooltip(RichTooltip tooltip, TooltipContext context) {
        for (var configurer : tooltipConfigurers) {
            configurer.accept(tooltip, context);
        }
    }

    @Override
    public ContentStack<T, S> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
        if (FMLEnvironment.dist.isDedicatedServer()) return this;
        this.tooltipConfigurers.add(configurator);
        return this;
    }

    @Override
    public ContentStack<Item, ItemStack> cheatsStack() {
        ItemStack itemStack = type.toItemStack(this);
        return new TypedContentStack<>(CirrusTypes.ITEM_STACK, itemStack, itemStack.getCount());
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypedContentStack<?, ?> that = (TypedContentStack<?, ?>) o;
        return (amount == that.amount) &&
            type.equals(that.type) &&
            type.matches(value, (S) that.value, MatchStrategy.IDENTITY_COMPONENT);
    }

    @Override
    public int hashCode() {
        if (isEmpty()) return ContentStack.empty().hashCode();
        int result = type.hashCode();
        result = 31 * result + Long.hashCode(amount);
        result = 31 * result + type.contentType().hash(type.getContent(value));
        return result;
    }

    @Override
    public String toString() {
        return "TypedContentStack{" +
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
    public ContentStack<T, S> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
        throw new UnsupportedOperationException("This operation is not supported for an empty content stack.");
    }

    @Override
    public void configureTooltip(RichTooltip tooltip, TooltipContext context) {
        throw new UnsupportedOperationException("This operation is not supported for an empty content stack.");
    }

    @Override
    public @Nullable RichTooltip getTooltip(TooltipContext context) {
        return null;
    }

    @Override
    public ContentStack<Item, ItemStack> cheatsStack() {
        return new TypedContentStack<>(CirrusTypes.ITEM_STACK, ItemStack.EMPTY, 0);
    }

    @Override
    public String toString() {
        return "empty content stack";
    }
}