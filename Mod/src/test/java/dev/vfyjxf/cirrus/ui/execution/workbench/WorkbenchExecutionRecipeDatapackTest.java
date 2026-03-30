package dev.vfyjxf.cirrus.ui.execution.workbench;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class WorkbenchExecutionRecipeDatapackTest {

    @Test
    void workbenchDemoRecipeDatapackIsPackaged() throws Exception {
        String stickCraft = loadRecipeJson("data/calculator_cirrus/recipe/workbench_oak_sticks_demo.json");
        String logCraft = loadRecipeJson("data/calculator_cirrus/recipe/workbench_oak_planks_demo.json");
        assertTrue(stickCraft.contains("\"minecraft:crafting_shapeless\""));
        assertTrue(stickCraft.contains("\"minecraft:oak_log\""));
        assertTrue(stickCraft.contains("\"minecraft:stick\""));
        assertTrue(stickCraft.contains("\"count\": 4"));
        assertTrue(logCraft.contains("\"minecraft:crafting_shapeless\""));
        assertTrue(logCraft.contains("\"minecraft:oak_log\""));
        assertTrue(logCraft.contains("\"minecraft:stick\""));
        assertTrue(logCraft.contains("\"minecraft:oak_planks\""));
        assertTrue(logCraft.contains("\"count\": 8"));
        assertEquals(2, logCraft.split("\"minecraft:oak_log\"", -1).length - 1);
        assertEquals(3, logCraft.split("\"minecraft:stick\"", -1).length - 1);
    }

    private String loadRecipeJson(String resourcePath) throws Exception {
        var stream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        assertNotNull(stream, "workbench datapack recipe should be available on classpath: " + resourcePath);
        try (stream) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
