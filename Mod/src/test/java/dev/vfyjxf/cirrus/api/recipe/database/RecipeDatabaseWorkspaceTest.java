package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RecipeDatabaseWorkspaceTest {
    @Test
    void builtinDatabaseIsAlwaysPresentAndFirst() {
        RecipeDatabaseWorkspace workspace = new RecipeDatabaseWorkspace(List.of(
            new CustomRecipeDatabase("machines", "Machines", List.of(), List.of(), List.of())
        ), "machines");

        assertEquals(CustomRecipeDatabase.builtinId, workspace.customDatabases().getFirst().id());
        assertEquals("machines", workspace.activeDatabaseId());
        assertEquals(2, workspace.customDatabases().size());
    }

    @Test
    void suppliedBuiltinDatabaseCannotOverrideFallbackDefinition() {
        DatabaseRecipeRef ref = new DatabaseRecipeRef(
            Namespace.of("test", "crafting"),
            ResourceLocation.fromNamespaceAndPath("test", "oak_planks")
        );
        CustomRecipeDatabase mutatedBuiltin = new CustomRecipeDatabase(
            CustomRecipeDatabase.builtinId,
            "Mutated Builtin",
            List.of(ref),
            List.of(Namespace.of("test", "crafting")),
            List.of()
        );

        RecipeDatabaseWorkspace workspace = new RecipeDatabaseWorkspace(List.of(mutatedBuiltin), CustomRecipeDatabase.builtinId);
        CustomRecipeDatabase builtin = workspace.activeDatabase();

        assertEquals(CustomRecipeDatabase.builtinName, builtin.name());
        assertEquals(0, builtin.recipes().size());
        assertEquals(0, builtin.sources().size());
        assertThrows(IllegalArgumentException.class, () -> workspace.withDatabase(mutatedBuiltin));
    }

    @Test
    void deletingActiveCustomDatabaseSelectsBuiltin() {
        RecipeDatabaseWorkspace workspace = new RecipeDatabaseWorkspace(List.of(
            CustomRecipeDatabase.builtinDatabase(),
            new CustomRecipeDatabase("scratch", "Scratch", List.of(), List.of(), List.of())
        ), "scratch");

        RecipeDatabaseWorkspace deleted = workspace.withoutDatabase("scratch");

        assertEquals(CustomRecipeDatabase.builtinId, deleted.activeDatabaseId());
        assertEquals(1, deleted.customDatabases().size());
        assertThrows(IllegalArgumentException.class, () -> workspace.withoutDatabase(CustomRecipeDatabase.builtinId));
    }
}
