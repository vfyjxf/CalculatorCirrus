package dev.vfyjxf.cirrus.api.recipe.database;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.codec.CodecJsonIO;
import dev.vfyjxf.cloudlib.util.Checks;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class FileRecipeDatabaseRepository {
    private static final Codec<List<CustomRecipeDatabase>> databasesCodec = CustomRecipeDatabase.codec.listOf();
    private static final Codec<String> activeCodec = Codec.STRING;

    private final Path root;

    public FileRecipeDatabaseRepository(Path root) {
        this.root = Checks.checkNotNull(root, "root");
    }

    public RecipeDatabaseWorkspace loadWorkspace() {
        return new RecipeDatabaseWorkspace(loadDatabases(), loadActiveDatabaseId());
    }

    public void saveActiveDatabase(String id) throws IOException {
        CodecJsonIO.write(activeFile(), activeCodec, id);
    }

    public RecipeDatabaseWorkspace createDatabase(String name) throws IOException {
        RecipeDatabaseWorkspace workspace = loadWorkspace().withNewDatabase(name);
        saveWorkspace(workspace);
        return workspace;
    }

    public RecipeDatabaseAddResult addToActiveDatabase(RecipeEntry<?> entry) {
        DatabaseRecipeRef ref = DatabaseRecipeRef.fromEntry(entry);
        if (ref == null) {
            return new RecipeDatabaseAddResult(
                RecipeDatabaseAddResult.Status.unsupported,
                CustomRecipeDatabase.builtinId,
                CustomRecipeDatabase.builtinName,
                entry
            );
        }
        RecipeDatabaseWorkspace workspace = loadWorkspace();
        CustomRecipeDatabase active = workspace.activeDatabase();
        if (active.builtin()) {
            return new RecipeDatabaseAddResult(
                RecipeDatabaseAddResult.Status.readOnly,
                active.id(),
                active.name(),
                entry
            );
        }
        if (active.contains(ref)) {
            return new RecipeDatabaseAddResult(
                RecipeDatabaseAddResult.Status.alreadyPresent,
                active.id(),
                active.name(),
                entry
            );
        }
        try {
            RecipeDatabaseWorkspace next = workspace.withDatabase(active.withRecipe(ref));
            saveWorkspace(next);
            return new RecipeDatabaseAddResult(RecipeDatabaseAddResult.Status.added, active.id(), active.name(), entry);
        } catch (IOException ignored) {
            return new RecipeDatabaseAddResult(
                RecipeDatabaseAddResult.Status.failed,
                active.id(),
                active.name(),
                entry
            );
        }
    }

    public Path root() {
        return root;
    }

    public RecipeDatabaseWorkspace deleteDatabase(String id) throws IOException {
        RecipeDatabaseWorkspace workspace = loadWorkspace().withoutDatabase(id);
        saveWorkspace(workspace);
        return workspace;
    }

    private List<CustomRecipeDatabase> loadDatabases() {
        Path file = databasesFile();
        if (!Files.exists(file)) {
            return List.of(CustomRecipeDatabase.builtinDatabase());
        }
        try {
            List<CustomRecipeDatabase> decoded = CodecJsonIO.read(file, databasesCodec);
            return decoded.isEmpty() ? List.of(CustomRecipeDatabase.builtinDatabase()) : decoded;
        } catch (IOException ignored) {
            return List.of(CustomRecipeDatabase.builtinDatabase());
        }
    }

    private String loadActiveDatabaseId() {
        Path file = activeFile();
        if (!Files.exists(file)) {
            return CustomRecipeDatabase.builtinId;
        }
        try {
            return CodecJsonIO.read(file, activeCodec);
        } catch (IOException ignored) {
            return CustomRecipeDatabase.builtinId;
        }
    }

    private void saveDatabases(List<CustomRecipeDatabase> databases) throws IOException {
        ArrayList<CustomRecipeDatabase> saved = new ArrayList<>();
        for (CustomRecipeDatabase database : databases) {
            if (database != null && !database.builtin()) {
                saved.add(database);
            }
        }
        CodecJsonIO.write(databasesFile(), databasesCodec, saved);
    }

    public void saveWorkspace(RecipeDatabaseWorkspace workspace) throws IOException {
        saveDatabases(workspace.customDatabases());
        saveActiveDatabase(workspace.activeDatabaseId());
    }

    private Path databasesFile() {
        return root.resolve("recipe_databases.json");
    }

    private Path activeFile() {
        return root.resolve("active_database.json");
    }
}
