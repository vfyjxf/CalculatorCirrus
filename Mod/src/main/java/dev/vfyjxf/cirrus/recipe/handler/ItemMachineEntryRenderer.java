package dev.vfyjxf.cirrus.recipe.handler;

import dev.vfyjxf.cirrus.api.recipe.machine.ItemMachineEntry;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntryRenderer;
import dev.vfyjxf.cirrus.content.renderer.ItemStackRenderer;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Objects;

public final class ItemMachineEntryRenderer implements MachineEntryRenderer<ItemMachineEntry> {

    public static final ItemMachineEntryRenderer instance = new ItemMachineEntryRenderer();

    private ItemMachineEntryRenderer() {}

    @Override
    public void render(ItemMachineEntry entry, GuiGraphics graphics, int width, int height, float delta) {
        ItemStackRenderer.instance.render(graphics, entry.machineStack(), width, height, delta);
    }

    @Override
    public Tooltip getTooltip(ItemMachineEntry entry) {
        Tooltip tooltip = ItemStackRenderer.instance.getTooltip(entry.machineStack().value(), TooltipContext.create());
        return Objects.requireNonNull(tooltip);
    }
}
