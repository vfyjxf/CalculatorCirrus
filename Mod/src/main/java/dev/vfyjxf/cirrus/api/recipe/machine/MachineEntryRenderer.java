package dev.vfyjxf.cirrus.api.recipe.machine;

import dev.vfyjxf.cirrus.content.ClientContentApiImpl;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.util.Checks;
import net.minecraft.client.gui.GuiGraphics;

public interface MachineEntryRenderer<T extends MachineEntry> {

    @SuppressWarnings("unchecked")
    static <T extends MachineEntry> MachineEntryRenderer<T> getRenderer(T entry) {
        Checks.checkNotNull(entry, "entry");
        var type = entry.getClass();
        return (MachineEntryRenderer<T>) ClientContentApiImpl.instance().getHandlerEntryRenderer(type);
    }

    void render(T entry, GuiGraphics graphics, int width, int height, float delta);

    Tooltip getTooltip(T entry);

}
