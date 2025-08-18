package dev.vfyjxf.calculatorcirrus.api.content.next.type;

import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentPatch;
import org.jetbrains.annotations.Nullable;

public interface ComponentSupport<T> {

    @Nullable DataComponentMap getComponents(T value);

    DataComponentPatch getComponentsPatch(T value);

}
