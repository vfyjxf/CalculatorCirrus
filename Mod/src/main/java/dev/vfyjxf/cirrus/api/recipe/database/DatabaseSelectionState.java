package dev.vfyjxf.cirrus.api.recipe.database;

import com.mojang.serialization.Codec;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class DatabaseSelectionState {
    public static final Codec<DatabaseSelectionState> CODEC = DatabaseDefinition.CODEC.listOf()
        .xmap(DatabaseSelectionState::new, DatabaseSelectionState::ordered);

    private final List<DatabaseDefinition> databases = new ArrayList<>();

    public DatabaseSelectionState(List<DatabaseDefinition> initial) {
        if (initial != null) {
            databases.addAll(initial);
        }
        sortAndNormalizePriorities();
    }

    public List<DatabaseDefinition> ordered() {
        return List.copyOf(databases);
    }

    public List<DatabaseDefinition> enabledOrdered() {
        return databases.stream()
            .filter(DatabaseDefinition::enabled)
            .toList();
    }

    public void toggleEnabled(String id) {
        for (int i = 0; i < databases.size(); i++) {
            DatabaseDefinition def = databases.get(i);
            if (Objects.equals(def.id(), id)) {
                databases.set(i, def.withEnabled(!def.enabled()));
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
                renumberPriorities();
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
                renumberPriorities();
                return;
            }
        }
    }

    private void sortAndNormalizePriorities() {
        databases.sort((left, right) -> {
            int priority = Integer.compare(left.priority(), right.priority());
            if (priority != 0) {
                return priority;
            }
            int kind = Integer.compare(left.kind().defaultRank(), right.kind().defaultRank());
            if (kind != 0) {
                return kind;
            }
            return left.id().compareTo(right.id());
        });
        renumberPriorities();
    }

    private void renumberPriorities() {
        for (int i = 0; i < databases.size(); i++) {
            DatabaseDefinition def = databases.get(i);
            if (def.priority() != i) {
                databases.set(i, def.withPriority(i));
            }
        }
    }
}
