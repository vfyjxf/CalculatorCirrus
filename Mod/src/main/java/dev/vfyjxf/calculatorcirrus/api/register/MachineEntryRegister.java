package dev.vfyjxf.calculatorcirrus.api.register;

import dev.vfyjxf.calculatorcirrus.api.recipe.machine.MachineEntry;
import net.minecraft.resources.ResourceLocation;

import java.util.Collection;

public interface MachineEntryRegister {

    void registerForSystem(ResourceLocation systemId, MachineEntry... entries);

    void registerForSystem(ResourceLocation systemId, Collection<? extends MachineEntry> entries);

}
