package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.ui.Tooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class BasicContentStack<T> implements ContentStack<T> {

    protected final T value;
    protected Tooltip tooltip;

    protected BasicContentStack(T value) {
        this.value = value;
    }

    @Override
    public T value() {
        return value;
    }

    @Nullable
    @Override
    public Tooltip getTooltip(TooltipContext context) {
        return tooltip;
    }

    @Override
    public ContentStack<T> appendTooltip(List<Tooltip.Entry> tooltips) {
        tooltip.addEntries(tooltips);
        return this;
    }

    @Override
    public ContentStack<T> tooltip(Tooltip tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Override
    public ContentStack<ItemStack> cheatsStack() {
        ItemStack itemStack = contentType().cheatStack(this, value);
        return ContentStack.create(itemStack);
    }
}
