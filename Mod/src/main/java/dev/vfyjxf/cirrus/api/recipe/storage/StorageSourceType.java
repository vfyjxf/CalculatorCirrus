package dev.vfyjxf.cirrus.api.recipe.storage;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.Nullable;

public record StorageSourceType<T extends StorageSource>(
    Namespace id,
    @Nullable Codec<T> codec
) {}
