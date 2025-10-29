package dev.vfyjxf.calculatorcirrus.api.register;

import dev.vfyjxf.calculatorcirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.calculatorcirrus.api.recipe.machine.MachineEntryRenderer;

public interface MachineEntryRendererRegister {

    <T extends MachineEntry> void registerHandlerRenderer(Class<T> type, MachineEntryRenderer<T> renderer);

}
