package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
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
    private final StackType<T, S> type;
    private final VariantType<T, S> variantType;
    private final S value;
    private long amount;

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
        return new TypedContentStack<>(CirrusTypes.Contents.ITEM_STACK, 0, ItemStack.EMPTY);
    }

    @Override
    public String toString() {
        return "empty content stack";
    }
}