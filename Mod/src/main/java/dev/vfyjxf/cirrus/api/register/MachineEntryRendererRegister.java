package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntryRenderer;

public interface MachineEntryRendererRegister {

    <T extends MachineEntry> void registerHandlerRenderer(Class<T> type, MachineEntryRenderer<T> renderer);

}
