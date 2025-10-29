package dev.vfyjxf.calculatorcirrus.recipe.handler;

import dev.vfyjxf.calculatorcirrus.api.recipe.machine.ItemMachineEntry;
import dev.vfyjxf.calculatorcirrus.api.recipe.machine.MachineEntryRenderer;
import dev.vfyjxf.calculatorcirrus.content.renderer.ItemStackRenderer;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import dev.vfyjxf.cloudlib.api.ui.widget.TooltipContext;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

public final class ItemMachineEntryRenderer implements MachineEntryRenderer<ItemMachineEntry> {

    @Override
    public void render(ItemMachineEntry entry, GuiGraphics graphics, int width, int height, float delta) {
        ItemStackRenderer.instance.render(graphics, entry.machineStack(), width, height, delta);
    }

    @Override
    public RichTooltip getTooltip(ItemMachineEntry entry) {
        RichTooltip tooltip = entry.machineStack().getTooltip(TooltipContext.create());
        return Objects.requireNonNull(tooltip);
    }
}
