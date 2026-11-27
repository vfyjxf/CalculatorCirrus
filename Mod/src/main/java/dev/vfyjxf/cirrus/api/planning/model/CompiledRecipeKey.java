package dev.vfyjxf.cirrus.api.planning.model;

import java.util.Objects;
import java.util.Optional;

public record CompiledRecipeKey(String systemId, Optional<String> explicitRecipeId, long structuralHash64)
        implements
            Comparable<CompiledRecipeKey> {

    public CompiledRecipeKey {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(explicitRecipeId, "explicitRecipeId");
        if (systemId.isEmpty()) {
            throw new IllegalArgumentException("systemId must not be empty");
        }
        if (explicitRecipeId.isPresent() && explicitRecipeId.get().isEmpty()) {
            explicitRecipeId = Optional.empty();
        }
    }

    public String shortString() {
        return explicitRecipeId.map(id -> systemId + "::" + id)
                .orElseGet(() -> systemId + "::#" + Long.toHexString(structuralHash64));
    }

    @Override
    public int compareTo(CompiledRecipeKey other) {
        int c = systemId.compareTo(other.systemId);
        if (c != 0) return c;
        c = explicitRecipeId.orElse("").compareTo(other.explicitRecipeId.orElse(""));
        if (c != 0) return c;
        return Long.compare(structuralHash64, other.structuralHash64);
    }
}
