package dev.vfyjxf.cirrus.internal.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.cirrus.recipe.system.SmithingRecipeSystem;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class RecipeExporterTest {

    @Test
    void exportProducesWellFormedJsonDocument(MinecraftServer server) throws IOException {
        Path directory = Files.createTempDirectory("cirrus-export-test");
        try {
            RecipeExporter.ExportResult result = RecipeExporter.export(directory, null);

            assertTrue(Files.exists(result.file()), "export file should exist");
            JsonObject root = JsonParser.parseString(Files.readString(result.file(), StandardCharsets.UTF_8)).getAsJsonObject();

            assertEquals(RecipeExporter.FORMAT_VERSION, root.get("formatVersion").getAsInt());
            assertNotNull(root.get("generatedAt"));
            assertNotNull(root.get("minecraftVersion"));
            assertTrue(root.get("recipeCount").getAsInt() > 0, "a vanilla pack should export at least one recipe");

            JsonArray systems = root.getAsJsonArray("systems");
            assertTrue(systems.size() > 0, "at least the built-in vanilla systems should be present");

            boolean sawCrafting = false;
            for (var element : systems) {
                JsonObject summary = element.getAsJsonObject();
                if ("minecraft:crafting".equals(summary.get("id").getAsString())) {
                    sawCrafting = true;
                    assertEquals("vanilla", summary.get("source").getAsString());
                    assertTrue(summary.get("recipeCount").getAsInt() > 0);
                    assertTrue(summary.has("machines"));
                }
            }
            assertTrue(sawCrafting, "crafting system should be exported from the built-in system");

            JsonArray recipes = root.getAsJsonArray("recipes");
            assertTrue(recipes.size() > 0);
            JsonObject first = recipes.get(0).getAsJsonObject();
            assertNotNull(first.get("system"));
            assertNotNull(first.get("source"));
            assertTrue(first.has("id"));
            assertTrue(first.has("machines"));
            assertTrue(first.has("inputs"));
            assertTrue(first.has("outputs"));
            assertTrue(first.getAsJsonArray("inputs").size() > 0, "resolved recipe should expose inputs");
            assertTrue(first.getAsJsonArray("outputs").size() > 0, "resolved recipe should expose outputs");

            assertEquals(result.recipeCount(), recipes.size(), "reported recipe count should match exported recipes");
            assertEquals(result.systemCount(), systems.size(), "reported system count should match exported systems");
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    void systemFilterLimitsExportToRequestedSystems(MinecraftServer server) throws IOException {
        Path directory = Files.createTempDirectory("cirrus-export-filter-test");
        try {
            Set<String> filter = Set.of(CraftingRecipeSystem.systemId.toString(), SmithingRecipeSystem.systemId.toString());
            RecipeExporter.ExportResult result = RecipeExporter.export(directory, filter);

            JsonObject root = JsonParser.parseString(Files.readString(result.file(), StandardCharsets.UTF_8)).getAsJsonObject();
            List<String> exportedSystems = root.getAsJsonArray("systems").asList().stream()
                .map(element -> element.getAsJsonObject().get("id").getAsString())
                .toList();

            assertEquals(2, exportedSystems.size(), "only the two requested systems should be exported");
            assertTrue(exportedSystems.containsAll(filter));
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    void builtInSystemsTakePriorityOverJeiCategories(MinecraftServer server) {
        RecipeApi api = RecipeApi.instance();
        List<RecipeSystem<?>> selected = RecipeExporter.selectSystems(api, null);

        var selectedIds = selected.stream().map(system -> system.systemId().toString()).collect(Collectors.toSet());
        assertEquals(selected.size(), selectedIds.size(), "no system should be selected twice");
        assertTrue(selectedIds.contains(CraftingRecipeSystem.systemId.toString()), "built-in crafting system must be selected");

        boolean everyVanillaSystemSelected = api.allSystems().allSatisfy(
            system -> !(system instanceof VanillaRecipeSystem<?>) || selectedIds.contains(system.systemId().toString())
        );
        assertTrue(everyVanillaSystemSelected, "every built-in vanilla system must be selected");
    }

    @Test
    void builtInExportKeepsRealOutputCounts(MinecraftServer server) throws IOException {
        Path directory = Files.createTempDirectory("cirrus-export-count-test");
        try {
            Set<String> filter = Set.of(CraftingRecipeSystem.systemId.toString());
            RecipeExporter.export(directory, filter);
            try (var files = Files.list(directory)) {
                Path file = files.filter(path -> path.getFileName().toString().startsWith("recipes-")).findFirst().orElseThrow();
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();

                boolean foundRealCount = false;
                for (var element : root.getAsJsonArray("recipes")) {
                    JsonObject recipe = element.getAsJsonObject();
                    if (!"minecraft:oak_planks".equals(recipe.get("id").getAsString())) {
                        continue;
                    }
                    for (var outputElement : recipe.getAsJsonArray("outputs")) {
                        JsonObject output = outputElement.getAsJsonObject();
                        if (!"stack".equals(output.get("kind").getAsString())) {
                            continue;
                        }
                        JsonObject stack = output.getAsJsonObject("stack");
                        if ("minecraft:oak_planks".equals(stack.get("id").getAsString())
                            && stack.get("count").getAsInt() >= 4) {
                            foundRealCount = true;
                        }
                    }
                }
                assertTrue(
                    foundRealCount,
                    "oak planks (from logs) should export with the real output count of 4, not a JEI-normalized count of 1"
                );
            }
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    void skippedDetailsAreWrittenToSeparateFile(MinecraftServer server) throws IOException {
        Path directory = Files.createTempDirectory("cirrus-export-skipped-test");
        try {
            RecipeExporter.ExportResult result = RecipeExporter.export(directory, null);

            JsonObject main = JsonParser.parseString(Files.readString(result.file(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertFalse(main.has("skippedReasons"), "skip reasons must not be mixed into the main export");
            for (var element : main.getAsJsonArray("systems")) {
                assertFalse(element.getAsJsonObject().has("skippedReasons"), "system summaries must not carry skip reasons");
            }

            assertTrue(Files.exists(result.skippedFile()), "skipped details file should exist");
            JsonObject skipped = JsonParser.parseString(Files.readString(result.skippedFile(), StandardCharsets.UTF_8)).getAsJsonObject();
            assertEquals(RecipeExporter.FORMAT_VERSION, skipped.get("formatVersion").getAsInt());
            assertEquals(result.skippedCount(), skipped.get("skippedCount").getAsInt(), "skipped counts should match");
            assertNotNull(skipped.get("skippedReasons"));
            assertNotNull(skipped.get("systems"));
            if (result.skippedCount() > 0) {
                JsonObject first = skipped.getAsJsonArray("systems").get(0).getAsJsonObject();
                assertTrue(first.get("skipped").getAsInt() > 0);
                assertTrue(first.has("reasons"));
                assertTrue(first.has("recipes"));
            }
        } finally {
            deleteRecursively(directory);
        }
    }

    private static void deleteRecursively(Path root) throws IOException {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (var paths = Files.walk(root)) {
            paths.sorted(java.util.Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                }
            });
        }
    }
}
