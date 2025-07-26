package dev.vfyjxf.calculatorcirrus.ui;

import dev.vfyjxf.calculatorcirrus.api.math.Point;
import dev.vfyjxf.calculatorcirrus.api.ui.RichTooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.Tooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipEntry;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipStack;
import dev.vfyjxf.calculatorcirrus.util.ScreenUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.function.Supplier;

public class ListRichTooltip implements RichTooltip {

    private Point mousePos;
    private final MutableList<Tooltip> entries;
    private TooltipStack<?> stack = null;

    public ListRichTooltip(@Nullable Point mousePos, List<? extends Tooltip> entries) {
        this.mousePos = mousePos;
        if (this.mousePos == null) {
            this.mousePos = ScreenUtil.ofMouse();
        }
        this.entries = Lists.mutable.ofAll(entries);
    }


    @Override
    public int getPosX() {
        return mousePos.x;
    }

    @Override
    public int getPosY() {
        return mousePos.y;
    }

    @Override
    public MutableList<Tooltip> tooltip() {
        return Lists.mutable.withAll(entries);
    }

    @Override
    public RichTooltip tooltip(Tooltip tooltip) {
        entries.add(tooltip);
        return this;
    }

    @Override
    public RichTooltip tooltip(Collection<Tooltip> tooltips) {
        this.entries.addAll(tooltips);
        return this;
    }

    @Override
    public RichTooltip add(Component text) {
        entries.add(new TooltipEntry(text));
        return this;
    }

    @Override
    public RichTooltip add(TooltipComponent component) {
        entries.add(new TooltipEntry(component));
        return this;
    }

    @Override
    public RichTooltip add(Supplier<Component> supplier) {
        entries.add(new SupplierTooltipEntry(supplier));
        return this;
    }

    @Override
    public RichTooltip ofAll(RichTooltip other) {
        entries.addAll(other.tooltip());
        return this;
    }


    @Override
    public RichTooltip copy() {
        ListRichTooltip tooltip = new ListRichTooltip(mousePos, entries);
        tooltip.setContextStack(stack);
        return tooltip;
    }

    @Override
    public TooltipStack<?> stack() {
        return stack;
    }

    @Override
    public RichTooltip setContextStack(@Nullable TooltipStack<?> stack) {
        this.stack = stack;
        return this;
    }

    @Override
    public boolean isEmpty() {
        return entries.isEmpty();
    }

}
