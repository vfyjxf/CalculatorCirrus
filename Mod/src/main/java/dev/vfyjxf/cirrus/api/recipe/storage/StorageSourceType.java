package dev.vfyjxf.cirrus.api.recipe.storage;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

public record StorageSourceType<T extends StorageSource>(Namespace id, @Nullable Codec<T> codec) {}
