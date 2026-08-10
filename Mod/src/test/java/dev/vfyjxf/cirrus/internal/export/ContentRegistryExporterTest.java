package dev.vfyjxf.cirrus.internal.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class ContentRegistryExporterTest {

    @Test
    void exportProducesOneWellFormedFilePerRegistryCategory(MinecraftServer server) throws IOException {
        Path directory = Files.createTempDirectory("cirrus-registry-export-test");
        try {
            ContentRegistryExporter.ExportResult result = ContentRegistryExporter.export(directory);

            assertTrue(Files.exists(result.contentTypesFile()));
            assertTrue(Files.exists(result.stackTypesFile()));
            assertTrue(Files.exists(result.tagTypesFile()));
            assertTrue(Files.exists(result.valueTypesFile()));
            assertTrue(Files.exists(result.variantTypesFile()));
            try (var files = Files.list(directory)) {
                assertTrue(files.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")), "no tmp files should remain");
            }

            for (Path file : new Path[]{
                result.contentTypesFile(), result.stackTypesFile(), result.tagTypesFile(),
                result.valueTypesFile(), result.variantTypesFile()
            }) {
                JsonObject root = JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject();
                assertEquals(ContentRegistryExporter.FORMAT_VERSION, root.get("formatVersion").getAsInt(), file + " header");
                assertNotNull(root.get("generatedAt"), file + " generatedAt");
                assertNotNull(root.get("minecraftVersion"), file + " minecraftVersion");
            }
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    void contentTypesFileContainsBuiltInContentTypes(MinecraftServer server) throws IOException {
        Path directory = Files.createTempDirectory("cirrus-registry-content-types-test");
        try {
            ContentRegistryExporter.ExportResult result = ContentRegistryExporter.export(directory);
            JsonObject root = JsonParser.parseString(Files.readString(result.contentTypesFile(), StandardCharsets.UTF_8)).getAsJsonObject();

            JsonArray contentTypes = root.getAsJsonArray("contentTypes");
            assertEquals(root.get("contentTypeCount").getAsInt(), contentTypes.size());

            JsonObject item = findById(contentTypes, "minecraft:item");
            assertNotNull(item, "item content type should be exported");
            assertEquals("minecraft:item", item.get("registry").getAsString());
            assertEquals("minecraft:tag/item", item.get("tagType").getAsString());
            assertTrue(item.get("hasCodec").getAsBoolean());

            JsonObject fluid = findById(contentTypes, "minecraft:fluid");
            assertNotNull(fluid, "fluid content type should be exported");
            assertEquals("minecraft:fluid", fluid.get("registry").getAsString());
            assertEquals("minecraft:tag/fluid", fluid.get("tagType").getAsString());
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    void stackTypesFileContainsBuiltInStackTypes(MinecraftServer server) throws IOException {
        Path directory = Files.createTempDirectory("cirrus-registry-stack-types-test");
        try {
            ContentRegistryExporter.ExportResult result = ContentRegistryExporter.export(directory);
            JsonObject root = JsonParser.parseString(Files.readString(result.stackTypesFile(), StandardCharsets.UTF_8)).getAsJsonObject();

            JsonArray stackTypes = root.getAsJsonArray("stackTypes");
            assertEquals(root.get("stackTypeCount").getAsInt(), stackTypes.size());

            JsonObject itemStack = findById(stackTypes, "minecraft:item_stack");
            assertNotNull(itemStack, "item stack type should be exported");
            assertEquals("minecraft:item", itemStack.get("contentType").getAsString());
            assertTrue(itemStack.get("maxAmount").getAsLong() > 0);
            assertTrue(itemStack.get("supportComponents").getAsBoolean());

            JsonObject fluidStack = findById(stackTypes, "minecraft:fluid_stack");
            assertNotNull(fluidStack, "fluid stack type should be exported");
            assertEquals("minecraft:fluid", fluidStack.get("contentType").getAsString());
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    void tagTypesFileContainsBuiltInTags(MinecraftServer server) throws IOException {
        Path directory = Files.createTempDirectory("cirrus-registry-tag-types-test");
        try {
            ContentRegistryExporter.ExportResult result = ContentRegistryExporter.export(directory);
            JsonObject root = JsonParser.parseString(Files.readString(result.tagTypesFile(), StandardCharsets.UTF_8)).getAsJsonObject();

            JsonArray tagTypes = root.getAsJsonArray("tagTypes");
            assertEquals(root.get("tagTypeCount").getAsInt(), tagTypes.size());

            JsonObject itemTag = findById(tagTypes, "minecraft:tag/item");
            assertNotNull(itemTag, "item tag type should be exported");
            assertEquals("minecraft:item", itemTag.get("registry").getAsString());
            assertTrue(itemTag.get("tagCount").getAsInt() > 0, "vanilla should have item tags");
            assertTrue(itemTag.getAsJsonArray("tags").size() > 0);

            JsonObject fluidTag = findById(tagTypes, "minecraft:tag/fluid");
            assertNotNull(fluidTag, "fluid tag type should be exported");
            assertEquals("minecraft:fluid", fluidTag.get("registry").getAsString());
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    void valueTypesFileContainsTimeAndEnergy(MinecraftServer server) throws IOException {
        Path directory = Files.createTempDirectory("cirrus-registry-value-types-test");
        try {
            ContentRegistryExporter.ExportResult result = ContentRegistryExporter.export(directory);
            JsonObject root = JsonParser.parseString(Files.readString(result.valueTypesFile(), StandardCharsets.UTF_8)).getAsJsonObject();

            JsonArray valueTypes = root.getAsJsonArray("valueTypes");
            assertEquals(root.get("valueTypeCount").getAsInt(), valueTypes.size());

            Set<String> ids = new HashSet<>();
            for (var element : valueTypes) {
                ids.add(element.getAsJsonObject().get("id").getAsString());
            }
            assertTrue(ids.contains("calculator_cirrus:time"), "time value type should be exported");
            assertTrue(ids.contains("calculator_cirrus:energy"), "energy value type should be exported");
        } finally {
            deleteRecursively(directory);
        }
    }

    @Test
    void variantTypesFileContainsRegisteredVariantsAndBindings(MinecraftServer server) throws IOException {
        Path directory = Files.createTempDirectory("cirrus-registry-variant-types-test");
        try {
            ContentRegistryExporter.ExportResult result = ContentRegistryExporter.export(directory);
            JsonObject root = JsonParser.parseString(Files.readString(result.variantTypesFile(), StandardCharsets.UTF_8)).getAsJsonObject();

            JsonArray variantTypes = root.getAsJsonArray("variantTypes");
            assertEquals(root.get("variantTypeCount").getAsInt(), variantTypes.size());

            for (String expectedId : new String[]{
                "minecraft:enchanted_book", "minecraft:firework", "minecraft:instrument", "minecraft:ominous_bottle",
                "minecraft:painting", "minecraft:potion", "minecraft:suspicious_stew"
            }) {
                JsonObject variant = findById(variantTypes, expectedId);
                assertNotNull(variant, expectedId + " variant type should be exported");
                assertEquals("minecraft:item_stack", variant.get("stackType").getAsString());
                assertTrue(variant.get("implementation").getAsString().startsWith("dev.vfyjxf.cirrus.content.type.variant."));
            }

            JsonObject enchantedBook = findById(variantTypes, "minecraft:enchanted_book");
            JsonArray bound = enchantedBook.getAsJsonArray("boundContents");
            assertEquals(1, bound.size());
            assertEquals("minecraft:enchanted_book", bound.get(0).getAsString());

            JsonObject firework = findById(variantTypes, "minecraft:firework");
            JsonArray fireworkBound = firework.getAsJsonArray("boundContents");
            assertEquals(1, fireworkBound.size());
            assertEquals("minecraft:firework_rocket", fireworkBound.get(0).getAsString());

            JsonObject potion = findById(variantTypes, "minecraft:potion");
            assertEquals(0, potion.getAsJsonArray("boundContents").size(), "potion variant has no registered content binding");
        } finally {
            deleteRecursively(directory);
        }
    }

    private static JsonObject findById(JsonArray array, String id) {
        for (var element : array) {
            JsonObject object = element.getAsJsonObject();
            if (id.equals(object.get("id").getAsString())) {
                return object;
            }
        }
        return null;
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
