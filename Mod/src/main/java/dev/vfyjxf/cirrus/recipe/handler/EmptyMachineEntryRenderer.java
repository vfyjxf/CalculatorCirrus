package dev.vfyjxf.cirrus.recipe.handler;

import dev.vfyjxf.cirrus.api.recipe.machine.EmptyMachineEntry;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntryRenderer;
import dev.vfyjxf.cloudlib.api.ui.text.RichTooltip;
import net.minecraft.client.gui.GuiGraphics;

public class EmptyMachineEntryRenderer implements MachineEntryRenderer<EmptyMachineEntry> {

    public static final EmptyMachineEntryRenderer instance = new EmptyMachineEntryRenderer();

    private EmptyMachineEntryRenderer() {}

    @Override
    public void render(EmptyMachineEntry entry, GuiGraphics graphics, int width, int height, float delta) {
    }

    @Override
    public RichTooltip getTooltip(EmptyMachineEntry entry) {
        return RichTooltip.empty();
    }
}
