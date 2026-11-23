package dev.vfyjxf.cirrus.api.planning.model;

import java.util.Objects;

public record CompiledCycleMacroKey(String macroId) implements Comparable<CompiledCycleMacroKey> {

    public CompiledCycleMacroKey {
        Objects.requireNonNull(macroId, "macroId");
        if (macroId.isEmpty()) {
            throw new IllegalArgumentException("macroId must not be empty");
        }
    }

    @Override
    public int compareTo(CompiledCycleMacroKey other) {
        return macroId.compareTo(other.macroId);
    }
}
