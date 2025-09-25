package dev.vfyjxf.calculatorcirrus.content.type;

import dev.vfyjxf.calculatorcirrus.api.content.type.ValueType;
import net.minecraft.resources.ResourceLocation;

public abstract class BasicValueType<T> implements ValueType<T> {
    private final ResourceLocation typeId;

    protected BasicValueType(ResourceLocation typeId) {
        this.typeId = typeId;
    }

    @Override
    public ResourceLocation typeId() {
        return typeId;
    }

}
