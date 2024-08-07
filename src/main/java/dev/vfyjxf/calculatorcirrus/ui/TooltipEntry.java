package dev.vfyjxf.calculatorcirrus.ui;

import dev.vfyjxf.calculatorcirrus.api.ui.ITooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.tooltip.TooltipComponent;

import java.util.Objects;

public final class TooltipEntry implements ITooltip.Entry {
    private final Object tooltip;
    private final boolean isText;

    private TooltipEntry(Object tooltip) {
        this.tooltip = tooltip;

        if (!(tooltip instanceof Component) && !(tooltip instanceof TooltipComponent)) {
            throw new IllegalArgumentException("tooltip must be a Component or ClientTextComponent");
        }
        this.isText = tooltip instanceof Component;
    }

    public TooltipEntry(Component tooltip) {
        this((Object) tooltip);
    }

    public TooltipEntry(TooltipComponent tooltip) {
        this((Object) tooltip);
    }


    @Override
    public boolean isText() {
        return isText;
    }


    @Override
    public boolean isComponent() {
        return !isText;
    }

    @Override
    public Component asText() {
        return ((Component) tooltip);
    }

    @Override
    public TooltipComponent asComponent() {
        return ((TooltipComponent) tooltip);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (TooltipEntry) obj;
        return Objects.equals(this.tooltip, that.tooltip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(tooltip);
    }

    @Override
    public String toString() {
        return "TooltipEntry[" +
                "tooltip=" + tooltip + ']';
    }


}
