package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cloudlib.util.Checks;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public record RecipeDatabaseWorkspace(
    List<CustomRecipeDatabase> customDatabases,
    String activeDatabaseId
) {
    public RecipeDatabaseWorkspace {
        Checks.checkNotNull(customDatabases, "customDatabases");
        customDatabases = normalize(customDatabases);
        activeDatabaseId = activeDatabaseId == null || activeDatabaseId.isBlank()
            ? customDatabases.getFirst().id()
            : activeDatabaseId;
        boolean exists = false;
        for (CustomRecipeDatabase database : customDatabases) {
            if (database.id().equals(activeDatabaseId)) {
                exists = true;
                break;
            }
        }
        if (!exists) {
            activeDatabaseId = customDatabases.getFirst().id();
        }
    }

    public static RecipeDatabaseWorkspace defaults() {
        return new RecipeDatabaseWorkspace(List.of(CustomRecipeDatabase.builtinDatabase()), CustomRecipeDatabase.builtinId);
    }

    public CustomRecipeDatabase activeDatabase() {
        for (CustomRecipeDatabase database : customDatabases) {
            if (database.id().equals(activeDatabaseId)) {
                return database;
            }
        }
        return customDatabases.getFirst();
    }

    public RecipeDatabaseWorkspace withActiveDatabase(String id) {
        return new RecipeDatabaseWorkspace(customDatabases, id);
    }

    public RecipeDatabaseWorkspace withDatabase(CustomRecipeDatabase database) {
        Checks.checkNotNull(database, "database");
        Checks.checkArgument(!database.builtin(), "builtin database cannot be modified");
        ArrayList<CustomRecipeDatabase> next = new ArrayList<>(customDatabases);
        for (int i = 0; i < next.size(); i++) {
            if (next.get(i).id().equals(database.id())) {
                next.set(i, database);
                return new RecipeDatabaseWorkspace(next, activeDatabaseId);
            }
        }
        next.add(database);
        return new RecipeDatabaseWorkspace(next, activeDatabaseId);
    }

    public RecipeDatabaseWorkspace withNewDatabase(String name) {
        Checks.checkArgument(name != null && !name.isBlank(), "database name must not be blank");
        String databaseName = name.strip();
        String id = uniqueId(slug(databaseName));
        ArrayList<CustomRecipeDatabase> next = new ArrayList<>(customDatabases);
        next.add(new CustomRecipeDatabase(id, databaseName, List.of(), List.of(), List.of()));
        return new RecipeDatabaseWorkspace(next, id);
    }

    public RecipeDatabaseWorkspace withoutDatabase(String id) {
        Checks.checkArgument(id != null && !id.isBlank(), "database id must not be blank");
        Checks.checkArgument(!CustomRecipeDatabase.builtinId.equals(id), "builtin database cannot be deleted");
        ArrayList<CustomRecipeDatabase> next = new ArrayList<>();
        for (CustomRecipeDatabase database : customDatabases) {
            if (!database.id().equals(id)) {
                next.add(database);
            }
        }
        String nextActive = activeDatabaseId.equals(id) ? CustomRecipeDatabase.builtinId : activeDatabaseId;
        return new RecipeDatabaseWorkspace(next, nextActive);
    }

    public List<DatabaseDefinition> definitions() {
        ArrayList<DatabaseDefinition> out = new ArrayList<>();
        for (int i = 0; i < customDatabases.size(); i++) {
            out.add(customDatabases.get(i).definition(i));
        }
        return List.copyOf(out);
    }

    public Map<String, List<RecipeEntry<?>>> explicitEntries(RecipeApi api) {
        LinkedHashMap<String, List<RecipeEntry<?>>> out = new LinkedHashMap<>();
        for (CustomRecipeDatabase database : customDatabases) {
            ArrayList<RecipeEntry<?>> entries = new ArrayList<>();
            for (DatabaseRecipeRef ref : database.recipes()) {
                if (!database.recipeEnabled(ref)) {
                    continue;
                }
                RecipeEntry<?> entry = ref.resolve(api);
                if (entry != null) {
                    entries.add(entry);
                }
            }
            out.put(database.definitionId(), List.copyOf(entries));
        }
        return Map.copyOf(out);
    }

    private static List<CustomRecipeDatabase> normalize(List<CustomRecipeDatabase> databases) {
        LinkedHashMap<String, CustomRecipeDatabase> out = new LinkedHashMap<>();
        out.put(CustomRecipeDatabase.builtinId, CustomRecipeDatabase.builtinDatabase());
        for (CustomRecipeDatabase database : databases) {
            if (database != null && !database.builtin()) {
                out.put(database.id(), database);
            }
        }
        return List.copyOf(out.values());
    }

    private String uniqueId(String baseId) {
        String base = baseId.isBlank() ? "database" : baseId;
        if (!Character.isLetter(base.charAt(0))) {
            base = "database_" + base;
        }
        String id = base;
        int suffix = 2;
        while (containsId(id)) {
            id = base + "_" + suffix++;
        }
        return id;
    }

    private boolean containsId(String id) {
        for (CustomRecipeDatabase database : customDatabases) {
            if (database.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    private static String slug(String name) {
        StringBuilder out = new StringBuilder();
        boolean lastUnderscore = false;
        for (int i = 0; i < name.length(); i++) {
            char ch = Character.toLowerCase(name.charAt(i));
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                out.append(ch);
                lastUnderscore = false;
            } else if (!lastUnderscore && !out.isEmpty()) {
                out.append('_');
                lastUnderscore = true;
            }
        }
        while (!out.isEmpty() && out.charAt(out.length() - 1) == '_') {
            out.deleteCharAt(out.length() - 1);
        }
        return out.toString().toLowerCase(Locale.ROOT);
    }
}
