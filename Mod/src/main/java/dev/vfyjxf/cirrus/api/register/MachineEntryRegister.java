package dev.vfyjxf.cirrus.api.register;

import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.Collection;

public interface MachineEntryRegister {

    void registerMachineForSystem(Namespace systemId, MachineEntry... entries);

    void registerMachineForSystem(Namespace systemId, Collection<? extends MachineEntry> entries);

}
