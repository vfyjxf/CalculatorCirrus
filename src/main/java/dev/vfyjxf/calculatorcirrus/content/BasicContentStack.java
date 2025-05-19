package dev.vfyjxf.calculatorcirrus.content;

import dev.vfyjxf.calculatorcirrus.api.content.ContentStack;
import dev.vfyjxf.calculatorcirrus.api.content.MatchContext;
import dev.vfyjxf.calculatorcirrus.api.ui.RichTooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.Tooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public abstract class BasicContentStack<T> implements ContentStack<T> {

    protected final T value;
    protected RichTooltip tooltip;

    protected BasicContentStack(T value) {
        this.value = value;
    }

    @Override
    public T value() {
        return value;
    }

    @Nullable
    @Override
    public RichTooltip getTooltip(TooltipContext context) {
        return tooltip;
    }

    @Override
    public ContentStack<T> appendTooltip(List<Tooltip> tooltips) {
        tooltip.tooltip(tooltips);
        return this;
    }

    @Override
    public ContentStack<T> tooltip(RichTooltip richTooltip) {
        this.tooltip = richTooltip;
        return this;
    }

    @Override
    public ContentStack<ItemStack> cheatsStack() {
        ItemStack itemStack = contentType().cheatStack(this, value);
        return ContentStack.create(itemStack);
    }

    public int hashCode() {
        return contentType().hash(value(), MatchContext.FUZZY);
    }
}
