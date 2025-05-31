package dev.vfyjxf.calculatorcirrus.content.types;

import dev.vfyjxf.calculatorcirrus.api.content.type.StackContentType;

public abstract class BasicStackContentType<T>
        extends BasicContentType<T>
        implements StackContentType<T> {

    private final Class<?> baseType;

    public BasicStackContentType(Class<T> typeClass, Class<?> baseType) {
        super(typeClass);
        this.baseType = baseType;
    }

    @Override
    public Class<?> baseType() {
        return baseType;
    }
}
