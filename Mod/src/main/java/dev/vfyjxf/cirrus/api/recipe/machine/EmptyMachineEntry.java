package dev.vfyjxf.cirrus.api.recipe.machine;

public final class EmptyMachineEntry implements MachineEntry {

    public static final MachineEntry instance = new EmptyMachineEntry();

    private EmptyMachineEntry() {}

    @Override
    public boolean isEmpty() {
        return true;
    }

    @Override
    public String toString() {
        return "empty machine";
    }
}
