package dev.vfyjxf.cirrus.content.type;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cloudlib.api.util.Namespace;

public abstract class BasicValueType<T> implements ValueType<T> {
    private final Namespace typeId;

    protected BasicValueType(Namespace typeId) {
        this.typeId = typeId;
    }

    @Override
    public Namespace typeId() {
        return typeId;
    }

}
