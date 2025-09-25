package dev.vfyjxf.calculatorcirrus.api.content.type;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Contract;

public sealed interface ContentKind permits ContentType, StackType, ValueType {

    /**
     * @return the unique id of this type
     */
    @Contract(pure = true)
    ResourceLocation typeId();

}
