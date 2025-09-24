package dev.vfyjxf.calculatorcirrus.api.content.resolver;

import dev.vfyjxf.calculatorcirrus.api.content.type.StackType;

public interface StackResolver<T, S> {

    StackType<T, S> stackType();

    <R> R resolve();

}
