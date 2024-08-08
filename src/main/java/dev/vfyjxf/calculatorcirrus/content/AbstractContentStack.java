package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.ui.Tooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class AbstractContentStack<T> implements ContentStack<T> {

    protected final T value;
    protected Tooltip append;
    protected List<Tooltip.Entry> appendTooltips = new FastList<>();

    protected AbstractContentStack(T value) {
        this.value = value;
    }

    @Override
    public T value() {
        return value;
    }

    @Nullable
    @Override
    public Tooltip getTooltip(TooltipContext context) {
        return null;
    }

    @Override
    public ContentStack<T> appendTooltip(List<Tooltip.Entry> tooltips) {
        appendTooltips.addAll(tooltips);
        return this;
    }

    @Override
    public ContentStack<T> tooltip(Tooltip tooltip) {
        append = tooltip;
        return this;
    }

    @Override
    public ContentStack<ItemStack> cheatsStack() {
        ItemStack itemStack = contentType().cheatStack(this, value);
        return ContentStack.of(itemStack);
    }
}
