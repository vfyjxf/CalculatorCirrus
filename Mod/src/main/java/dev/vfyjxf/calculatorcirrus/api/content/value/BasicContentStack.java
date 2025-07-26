package dev.vfyjxf.calculatorcirrus.api.content.value;

import dev.vfyjxf.calculatorcirrus.api.ui.RichTooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import net.minecraft.world.item.ItemStack;

import java.util.function.BiConsumer;

non-sealed abstract class BasicContentStack<T> extends BasicContentValue<T> implements ContentStack<T> {

    protected BasicContentStack(T value) {
        super(value);
    }

    @Override
    public ContentStack<T> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
        super.appendTooltip(configurator);
        return this;
    }

    @Override
    public ContentStack<ItemStack> cheatsStack() {
        ItemStack itemStack = contentType().cheatStack(this, value);
        return ContentStack.create(itemStack, amount());
    }

}
