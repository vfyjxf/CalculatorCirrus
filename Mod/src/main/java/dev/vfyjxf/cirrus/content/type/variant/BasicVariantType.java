package dev.vfyjxf.cirrus.content.type.variant;

import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.type.VariantType;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

public abstract class BasicVariantType<T, S> implements VariantType<T, S> {

    private final Namespace typeId;
    private final StackType<T, S> stackType;

    protected BasicVariantType(Namespace typeId, StackType<T, S> stackType) {
        Checks.checkNotNull(typeId, "typeId");
        Checks.checkNotNull(stackType, "stackType");
        this.typeId = typeId;
        this.stackType = stackType;
    }

    @Override
    public Namespace typeId() {
        return typeId;
    }

    @Override
    public StackType<T, S> stackType() {
        return stackType;
    }
}
