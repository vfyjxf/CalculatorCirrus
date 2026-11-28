package dev.vfyjxf.cirrus.api.planning.model;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;

import java.util.Objects;
import java.util.Optional;

public record CompiledRecipeKey(Namespace systemId, Optional<ResourceLocation> explicitRecipeId, long structuralHash64)
        implements
            Comparable<CompiledRecipeKey> {

    public CompiledRecipeKey {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(explicitRecipeId, "explicitRecipeId");
    }

    public String shortString() {
        return explicitRecipeId.map(id -> systemId + "::" + id)
                .orElseGet(() -> systemId + "::#" + Long.toHexString(structuralHash64));
    }

    @Override
    public int compareTo(CompiledRecipeKey other) {
        int c = systemId.compareTo(other.systemId);
        if (c != 0) return c;
        boolean aPresent = explicitRecipeId.isPresent();
        boolean bPresent = other.explicitRecipeId.isPresent();
        if (aPresent && bPresent) {
            c = explicitRecipeId.get().compareTo(other.explicitRecipeId.get());
            if (c != 0) return c;
        } else if (aPresent != bPresent) {
            return aPresent ? 1 : -1;
        }
        return Long.compare(structuralHash64, other.structuralHash64);
    }
}
