package dev.vfyjxf.calculatorcirrus.ui;

import dev.vfyjxf.calculatorcirrus.api.math.Point;
import dev.vfyjxf.calculatorcirrus.api.ui.ITooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipEntry;
import dev.vfyjxf.calculatorcirrus.api.ui.TooltipStack;
import dev.vfyjxf.calculatorcirrus.utils.ScreenUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

public class ListTooltip implements ITooltip {

    private Point mousePos;
    private MutableList<ITooltip.Entry> entries;
    private TooltipStack<?> stack = null;

    public ListTooltip(@Nullable Point mousePos, List<? extends Entry> entries) {
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
    public MutableList<Entry> entries() {
        return entries;
    }

    @Override
    public ITooltip add(Component text) {
        entries.add(new TooltipEntry(text));
        return this;
    }

    @Override
    public ITooltip add(TooltipComponent component) {
        entries.add(new TooltipEntry(component));
        return this;
    }

    @Override
    public ITooltip add(Supplier<Component> supplier) {
        entries.add(new SupplierTooltipEntry(supplier));
        return this;
    }

    @Override
    public ITooltip ofAll(ITooltip other) {
        entries.addAll(other.entries());
        return this;
    }


    @Override
    public ITooltip copy() {
        ListTooltip tooltip = new ListTooltip(mousePos, entries);
        tooltip.setContextStack(stack);
        return tooltip;
    }

    @Override
    public TooltipStack<?> stack() {
        return stack;
    }

    @Override
    public ITooltip setContextStack(@Nullable TooltipStack<?> stack) {
        this.stack = stack;
        return this;
    }

}
