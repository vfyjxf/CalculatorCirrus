package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FileRecipeDatabaseRepositoryTest {

    @Test
    void addToActiveDatabasePersistsRecipeReferenceAndDedupes(@TempDir Path tempDir) {
        FileRecipeDatabaseRepository repository = new FileRecipeDatabaseRepository(tempDir);
        assertDoesNotThrow(() -> repository.createDatabase("Scratch"));
        TestRecipeSystem system = new TestRecipeSystem();
        RecipeEntry<TestRecipe> entry = RecipeEntry.of(system, new TestRecipe("oak_planks"));

        RecipeDatabaseAddResult added = repository.addToActiveDatabase(entry);
        RecipeDatabaseAddResult duplicate = repository.addToActiveDatabase(entry);
        RecipeDatabaseWorkspace workspace = repository.loadWorkspace();

        assertEquals(RecipeDatabaseAddResult.Status.added, added.status());
        assertEquals(RecipeDatabaseAddResult.Status.alreadyPresent, duplicate.status());
        assertEquals("scratch", workspace.activeDatabaseId());
        assertEquals(1, workspace.activeDatabase().recipes().size());
        assertEquals(
            ResourceLocation.fromNamespaceAndPath("test", "oak_planks"),
            workspace.activeDatabase().recipes().getFirst().recipe()
        );
    }

    @Test
    void addToActiveDatabaseRejectsBuiltinDatabase(@TempDir Path tempDir) {
        FileRecipeDatabaseRepository repository = new FileRecipeDatabaseRepository(tempDir);
        TestRecipeSystem system = new TestRecipeSystem();
        RecipeEntry<TestRecipe> entry = RecipeEntry.of(system, new TestRecipe("oak_planks"));

        RecipeDatabaseAddResult result = repository.addToActiveDatabase(entry);
        RecipeDatabaseWorkspace workspace = repository.loadWorkspace();

        assertEquals(RecipeDatabaseAddResult.Status.readOnly, result.status());
        assertEquals(CustomRecipeDatabase.builtinId, workspace.activeDatabaseId());
        assertEquals(0, workspace.activeDatabase().recipes().size());
    }

    @Test
    void createDatabasePersistsAndSelectsUniqueDatabase(@TempDir Path tempDir) throws Exception {
        FileRecipeDatabaseRepository repository = new FileRecipeDatabaseRepository(tempDir);

        RecipeDatabaseWorkspace first = repository.createDatabase("Machine Routes");
        RecipeDatabaseWorkspace second = repository.createDatabase("Machine Routes");
        RecipeDatabaseWorkspace loaded = repository.loadWorkspace();

        assertEquals("machine_routes", first.activeDatabaseId());
        assertEquals("machine_routes_2", second.activeDatabaseId());
        assertEquals("machine_routes_2", loaded.activeDatabaseId());
        assertEquals(3, loaded.customDatabases().size());
        assertEquals(CustomRecipeDatabase.builtinName, loaded.customDatabases().getFirst().name());
        assertEquals("Machine Routes", loaded.activeDatabase().name());
        assertEquals(0, loaded.activeDatabase().recipes().size());
    }

    @Test
    void deleteDatabaseRequiresNonBuiltinAndFallsBackToBuiltin(@TempDir Path tempDir) throws Exception {
        FileRecipeDatabaseRepository repository = new FileRecipeDatabaseRepository(tempDir);

        RecipeDatabaseWorkspace created = repository.createDatabase("Scratch");
        RecipeDatabaseWorkspace deleted = repository.deleteDatabase(created.activeDatabaseId());

        assertEquals(CustomRecipeDatabase.builtinId, deleted.activeDatabaseId());
        assertEquals(1, deleted.customDatabases().size());
        assertThrows(IllegalArgumentException.class, () -> repository.deleteDatabase(CustomRecipeDatabase.builtinId));
    }

    @Test
    void createDatabaseRequiresExplicitName(@TempDir Path tempDir) {
        FileRecipeDatabaseRepository repository = new FileRecipeDatabaseRepository(tempDir);

        assertThrows(IllegalArgumentException.class, () -> repository.createDatabase("   "));
    }

    @Test
    void saveWorkspacePersistsDisabledSourcesForExplicitRecipes(@TempDir Path tempDir) throws Exception {
        FileRecipeDatabaseRepository repository = new FileRecipeDatabaseRepository(tempDir);
        RecipeDatabaseWorkspace workspace = repository.createDatabase("Scratch");
        Namespace source = Namespace.of("test", "crafting");
        DatabaseRecipeRef ref = new DatabaseRecipeRef(
            source,
            ResourceLocation.fromNamespaceAndPath("test", "oak_planks")
        );

        CustomRecipeDatabase active = workspace.activeDatabase().withRecipe(ref).withSourceEnabled(source, false);
        repository.saveWorkspace(workspace.withDatabase(active));

        RecipeDatabaseWorkspace loaded = repository.loadWorkspace();

        assertEquals("scratch", loaded.activeDatabaseId());
        assertEquals(List.of(ref), loaded.activeDatabase().recipes());
        assertEquals(List.of(source), loaded.activeDatabase().disabledSources());
        assertEquals(false, loaded.activeDatabase().recipeEnabled(ref));
    }

    private record TestRecipe(String id) {}

    private static final class TestRecipeSystem implements RecipeSystem<TestRecipe> {
        @Override
        public Namespace systemId() {
            return Namespace.of("test", "crafting");
        }

        @Override
        public Class<?> basicType() {
            return TestRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(TestRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
        }

        @Override
        public @Nullable TestRecipe findRecipe(String uniqueId) {
            return new TestRecipe(uniqueId);
        }

        @Override
        public @Nullable TestRecipe findRecipe(ResourceLocation registryName) {
            return new TestRecipe(registryName.getPath());
        }

        @Override
        public int hash(TestRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(TestRecipe r1, TestRecipe r2) {
            return Objects.equals(r1.id(), r2.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, TestRecipe recipe) {
            return ResolveResult.unsupported();
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.empty();
        }
    }
}
