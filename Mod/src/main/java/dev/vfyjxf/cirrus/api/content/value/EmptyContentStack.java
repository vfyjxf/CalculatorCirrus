package dev.vfyjxf.cirrus.api.content.value;

import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.type.ContentType;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

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
        return new TypedContentStack<>(CirrusTypes.Contents.itemStack, 0, ItemStack.EMPTY);
    }

    @Override
    public String toString() {
        return "empty content stack";
    }
}
