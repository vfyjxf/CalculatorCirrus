package dev.vfyjxf.cirrus.api.recipe.database;

import com.mojang.serialization.JsonOps;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RecipeDatabaseCodecTest {
    @Test
    void databaseDefinitionRoundTripsThroughCodec() {
        DatabaseDefinition definition = new DatabaseDefinition(
            "system:test",
            "Test Recipes",
            DatabaseKind.system,
            true,
            3,
            List.of(Namespace.of("test", "crafting"), Namespace.of("test", "smelting"))
        );

        var encoded = DatabaseDefinition.codec.encodeStart(JsonOps.INSTANCE, definition).result().orElseThrow();
        DatabaseDefinition decoded = DatabaseDefinition.codec.parse(JsonOps.INSTANCE, encoded).result().orElseThrow();

        assertEquals(definition, decoded);
    }

    @Test
    void selectionStateRoundTripsNormalizedOrder() {
        List<DatabaseDefinition> definitions = List.of(
            new DatabaseDefinition("b", "B", DatabaseKind.jei, true, 9, List.of(Namespace.of("test", "b"))),
            new DatabaseDefinition("a", "A", DatabaseKind.system, false, 1, List.of(Namespace.of("test", "a")))
        );
        DatabaseSelectionState state = new DatabaseSelectionState(definitions);

        var encoded = DatabaseSelectionState.codec.encodeStart(JsonOps.INSTANCE, state).result().orElseThrow();
        DatabaseSelectionState decoded = DatabaseSelectionState.codec.parse(JsonOps.INSTANCE, encoded).result()
                .orElseThrow();

        assertEquals(List.of("a", "b"), decoded.ordered().stream().map(DatabaseDefinition::id).toList());
        assertEquals(List.of(0, 1), decoded.ordered().stream().map(DatabaseDefinition::priority).toList());
    }

    @Test
    void customDatabaseRoundTripsRecipeRefsThroughCodec() {
        CustomRecipeDatabase database = new CustomRecipeDatabase(
            CustomRecipeDatabase.builtinId,
            CustomRecipeDatabase.builtinName,
            List.of(
                new DatabaseRecipeRef(
                    Namespace.of("minecraft", "crafting"),
                    ResourceLocation.fromNamespaceAndPath("minecraft", "oak_planks")
                )
            ),
            List.of(Namespace.of("minecraft", "crafting")),
            List.of(Namespace.of("minecraft", "smelting"))
        );

        var encoded = CustomRecipeDatabase.codec.encodeStart(JsonOps.INSTANCE, database).result().orElseThrow();
        CustomRecipeDatabase decoded = CustomRecipeDatabase.codec.parse(JsonOps.INSTANCE, encoded).result()
                .orElseThrow();

        assertEquals(database, decoded);
        assertEquals("custom:builtin", decoded.definitionId());
        assertEquals(List.of(Namespace.of("minecraft", "crafting")), decoded.enabledSources());
    }

    @Test
    void customDatabaseRoundTripsDisabledExplicitRecipeSourceThroughCodec() {
        Namespace source = Namespace.of("minecraft", "crafting");
        DatabaseRecipeRef ref = new DatabaseRecipeRef(
            source,
            ResourceLocation.fromNamespaceAndPath("minecraft", "oak_planks")
        );
        CustomRecipeDatabase database = new CustomRecipeDatabase(
            "scratch",
            "Scratch",
            List.of(ref),
            List.of(),
            List.of(source)
        );

        var encoded = CustomRecipeDatabase.codec.encodeStart(JsonOps.INSTANCE, database).result().orElseThrow();
        CustomRecipeDatabase decoded = CustomRecipeDatabase.codec.parse(JsonOps.INSTANCE, encoded).result()
                .orElseThrow();

        assertEquals(database, decoded);
        assertEquals(List.of(source), decoded.disabledSources());
        assertEquals(false, decoded.recipeEnabled(ref));
    }
}
