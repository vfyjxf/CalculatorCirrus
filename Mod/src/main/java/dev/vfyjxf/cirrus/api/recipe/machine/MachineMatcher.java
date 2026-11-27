package dev.vfyjxf.cirrus.api.recipe.machine;

import dev.vfyjxf.cloudlib.api.util.Namespace;

// Decides machine equivalence in a context: GT machines that parallelise add no constraint but still affect the result as recipe context.
//TODO: should this be generalised into CalculationContextMatcher?
public interface MachineMatcher {

    Namespace id();

    boolean matches(MachineEntry entry1, MachineEntry entry2);

}
