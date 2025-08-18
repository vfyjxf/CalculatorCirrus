package dev.vfyjxf.calculatorcirrus.api.content.next.type;

import dev.vfyjxf.calculatorcirrus.api.content.MatchStrategy;

public interface MatchSupport<T> {

    boolean matches(T value1, T value2, MatchStrategy strategy);

}
