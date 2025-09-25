package dev.vfyjxf.calculatorcirrus.content.type.variant;

import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;
import dev.vfyjxf.calculatorcirrus.api.content.type.VariantType;
import net.minecraft.resources.ResourceLocation;

public abstract class BasicVariantType<T, S> implements VariantType<T, S> {

    private final ResourceLocation typeId;
    private final StackType<T, S> stackType;

    protected BasicVariantType(ResourceLocation typeId, StackType<T, S> stackType) {
        this.typeId = typeId;
        this.stackType = stackType;
    }

    @Override
    public ResourceLocation typeId() {
        return typeId;
    }

    @Override
    public StackType<T, S> stackType() {
        return stackType;
    }
}
