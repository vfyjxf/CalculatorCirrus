package dev.vfyjxf.calculatorcirrus.api.content.value;

import dev.vfyjxf.calculatorcirrus.api.content.type.StackContentType;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.function.BiConsumer;

final class EmptyContentStack implements ContentStack<Object> {

    static final Object EMPTY = new Object();
    static final EmptyContentStack INSTANCE = new EmptyContentStack();

    @Override
    public Object value() {
        return EMPTY;
    }

    @Override
    public StackContentType<Object> contentType() {
        return EmptyStackContentType.INSTANCE;
    }

    @Override
    public ContentStack<Object> setAmount(long amount) {
        return this;
    }

    @Override
    public ContentStack<Object> appendTooltip(BiConsumer<RichTooltip, TooltipContext> configurator) {
        return this;
    }

    @Override
    public @Nullable RichTooltip getTooltip(TooltipContext context) {
        return null;
    }

    @Override
    public void configureTooltip(RichTooltip tooltip, TooltipContext context) {

    }

    @Override
    public ContentStack<ItemStack> cheatsStack() {
        return ContentStack.create(ItemStack.EMPTY);
    }
}
