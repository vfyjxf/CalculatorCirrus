package dev.vfyjxf.calculatorcirrus.api.content.next.type;

import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceLocation;

public interface RegistrySupport<T> {

    Registry<T> registry();

    default ResourceLocation getId(T value) {
        return registry().getKey(value);
    }

    default Codec<T> namedCodec() {
        return registry().byNameCodec();
    }

}
