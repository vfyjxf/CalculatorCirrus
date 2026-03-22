package dev.vfyjxf.cirrus.ui.calc.database;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DatabaseSelectionState {
    private final List<DatabaseDefinition> databases = new ArrayList<>();

    public DatabaseSelectionState(List<DatabaseDefinition> initial) {
        if (initial != null) {
            databases.addAll(initial);
        }
    }

    public List<DatabaseDefinition> ordered() {
        return List.copyOf(databases);
    }

    public void toggleEnabled(String id) {
        for (int i = 0; i < databases.size(); i++) {
            DatabaseDefinition def = databases.get(i);
            if (Objects.equals(def.id(), id)) {
                databases.set(i, new DatabaseDefinition(
                    def.id(), def.name(), def.kind(), !def.enabled(), def.priority(), def.sources()
                ));
                return;
            }
        }
    }

    public void moveUp(String id) {
        for (int i = 1; i < databases.size(); i++) {
            if (Objects.equals(databases.get(i).id(), id)) {
                var tmp = databases.get(i - 1);
                databases.set(i - 1, databases.get(i));
                databases.set(i, tmp);
                return;
            }
        }
    }

    public void moveDown(String id) {
        for (int i = 0; i < databases.size() - 1; i++) {
            if (Objects.equals(databases.get(i).id(), id)) {
                var tmp = databases.get(i + 1);
                databases.set(i + 1, databases.get(i));
                databases.set(i, tmp);
                return;
            }
        }
    }
}
