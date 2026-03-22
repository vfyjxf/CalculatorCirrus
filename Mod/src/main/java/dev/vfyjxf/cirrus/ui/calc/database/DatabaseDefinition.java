package dev.vfyjxf.cirrus.ui.calc.database;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.List;

public record DatabaseDefinition(
    String id,
    String name,
    DatabaseKind kind,
    boolean enabled,
    int priority,
    List<Namespace> sources
) {
    public DatabaseDefinition {
        Checks.checkNotNull(id, "id");
        Checks.checkNotNull(name, "name");
        Checks.checkNotNull(kind, "kind");
        Checks.checkNotNull(sources, "sources");
    }

    public DatabaseDefinition withPriority(int nextPriority) {
        return new DatabaseDefinition(id, name, kind, enabled, nextPriority, sources);
    }
}
