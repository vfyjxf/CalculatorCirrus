package dev.vfyjxf.cirrus.ui.calc.database;

import dev.vfyjxf.cloudlib.api.util.Namespace;

import java.util.ArrayList;
import java.util.List;

public final class DatabaseDefaults {
    private DatabaseDefaults() {
    }

    public static List<DatabaseDefinition> generate(List<Namespace> systems, List<Namespace> jeiSystems) {
        List<DatabaseDefinition> defs = new ArrayList<>();
        int priority = 0;
        for (Namespace systemId : systems) {
            defs.add(new DatabaseDefinition(
                "system:" + systemId,
                systemId.toString(),
                DatabaseKind.system,
                true,
                priority++,
                List.of(systemId)
            ));
        }
        if (!jeiSystems.isEmpty()) {
            defs.add(new DatabaseDefinition(
                "jei:all",
                "JEI",
                DatabaseKind.jei,
                true,
                priority,
                List.copyOf(jeiSystems)
            ));
        }
        return defs;
    }
}
