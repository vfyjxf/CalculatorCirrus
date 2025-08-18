package dev.vfyjxf.calculatorcirrus.api.content.next.type;

import com.mojang.serialization.Codec;

public interface SaveSupport<T> {

    Codec<T> codec();

}
