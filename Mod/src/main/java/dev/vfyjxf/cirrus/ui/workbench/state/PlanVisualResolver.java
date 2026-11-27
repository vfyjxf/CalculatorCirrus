package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

public interface PlanVisualResolver {

    @Nullable
    ContentStack<?, ?> stackOf(ResourceKey resource, long amount);

    Component nameOf(ResourceKey resource);

    Component machineNameOf(String machineId);
}
