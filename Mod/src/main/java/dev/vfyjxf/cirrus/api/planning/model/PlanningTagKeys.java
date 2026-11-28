package dev.vfyjxf.cirrus.api.planning.model;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Objects;
import java.util.Optional;

/**
 * Utilities for using Minecraft's registry-qualified {@link TagKey} in deterministic maps,
 * hashes, and persistence codecs without losing registry identity.
 */
public final class PlanningTagKeys {

    public static final Comparator<TagKey<?>> comparator = (a, b) -> {
        if (a == b || Objects.equals(a, b)) {
            return 0;
        }
        int c = a.registry().location().compareTo(b.registry().location());
        if (c != 0) return c;
        return a.location().compareTo(b.location());
    };

    public static final Codec<TagKey<?>> codec = Codec.STRING.comapFlatMap(
        raw -> parseCanonical(raw)
                .<DataResult<TagKey<?>>>map(DataResult::success)
                .orElseGet(() -> DataResult.error(() -> "Invalid tag key: " + raw)),
        PlanningTagKeys::canonicalString
    );

    private PlanningTagKeys() {}

    public static String canonicalString(TagKey<?> tag) {
        Objects.requireNonNull(tag, "tag");
        return tag.registry().location() + "#" + tag.location();
    }

    public static String displayString(TagKey<?> tag) {
        Objects.requireNonNull(tag, "tag");
        return "#" + tag.location();
    }

    public static String format(TagKey<?> tag) {
        Objects.requireNonNull(tag, "tag");
        return tag.location().toString();
    }

    public static Optional<TagKey<?>> parseCanonical(@Nullable String raw) {
        if (raw == null || raw.isBlank()) {
            return Optional.empty();
        }
        String trimmed = raw.trim();
        int hash = trimmed.indexOf('#');
        if (hash > 0) {
            ResourceLocation regLoc = ResourceLocation.tryParse(trimmed.substring(0, hash));
            ResourceLocation tagLoc = ResourceLocation.tryParse(trimmed.substring(hash + 1));
            if (regLoc == null || tagLoc == null) {
                return Optional.empty();
            }
            return Optional.of(createUntyped(regLoc, tagLoc));
        }
        String bare = trimmed.startsWith("#") ? trimmed.substring(1) : trimmed;
        ResourceLocation tagLoc = ResourceLocation.tryParse(bare);
        if (tagLoc == null) {
            return Optional.empty();
        }
        return Optional.of(TagKey.create(Registries.ITEM, tagLoc));
    }

    public static TagKey<?> itemTag(ResourceLocation location) {
        return TagKey.create(Registries.ITEM, Objects.requireNonNull(location, "location"));
    }

    public static TagKey<?> fluidTag(ResourceLocation location) {
        return TagKey.create(Registries.FLUID, Objects.requireNonNull(location, "location"));
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    public static TagKey<?> createUntyped(ResourceLocation registryLocation, ResourceLocation tagLocation) {
        ResourceKey<Registry<Object>> regKey = (ResourceKey) ResourceKey.createRegistryKey(registryLocation);
        return TagKey.create(regKey, tagLocation);
    }
}
