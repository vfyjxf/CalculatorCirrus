package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.IContentStack;
import dev.vfyjxf.calculatorcirrus.api.ui.ITooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import net.minecraft.world.item.ItemStack;
import org.eclipse.collections.impl.list.mutable.FastList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

public abstract class AbstractContentStack<T> implements IContentStack<T> {

    protected final T value;
    protected ITooltip append;
    protected List<ITooltip.Entry> appendTooltips = new FastList<>();

    protected AbstractContentStack(T value) {
        this.value = value;
    }

    @Override
    public T value() {
        return value;
    }

    @Nullable
    @Override
    public ITooltip getTooltip(TooltipContext context) {
        return null;
    }

    @Override
    public IContentStack<T> appendTooltip(List<ITooltip.Entry> tooltips) {
        appendTooltips.addAll(tooltips);
        return this;
    }

    @Override
    public IContentStack<T> tooltip(ITooltip tooltip) {
        append = tooltip;
        return this;
    }

    @Override
    public IContentStack<ItemStack> cheatsStack() {
        ItemStack itemStack = type().cheatStack(this, value);
        return IContentStack.of(itemStack);
    }
}
