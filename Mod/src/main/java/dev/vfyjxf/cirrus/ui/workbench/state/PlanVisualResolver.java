package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public interface PlanVisualResolver {

    @Nullable
    ContentStack<?, ?> stackOf(ComponentStackKey resource, long amount);

    Component nameOf(ComponentStackKey resource);

    Component machineNameOf(MachineEntry machine);
}
