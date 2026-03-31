package dev.vfyjxf.cirrus.api.recipe;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RecipeSystemTagRegistrationSourceTest {

    @Test
    void cirrusLoadDispatchesRecipeSystemTagRegistrationBeforeAttachingApi() throws Exception {
        String source = Files.readString(resolvePath("src/main/java/dev/vfyjxf/cirrus/Cirrus.java"));

        int systems = source.indexOf("plugin.registerRecipeSystem(recipeApiRegistry)");
        int tailSystems = source.indexOf("tailPlugins.forEach(plugin -> plugin.registerRecipeSystem(recipeApiRegistry))");
        int tags = source.indexOf("plugin.registerRecipeSystemTag(recipeApiRegistry)");
        int jeiBridge = source.indexOf("plugin.registerJeiRecipeTypeBridge(recipeApiRegistry)");
        int tailJeiBridge = source.indexOf("tailPlugins.forEach(plugin -> plugin.registerJeiRecipeTypeBridge(recipeApiRegistry))");
        int machines = source.indexOf("plugin.registerMachineEntry(recipeApiRegistry)");
        int attach = source.indexOf("RecipeApiImpl.attach(new RecipeApiImpl(recipeApiRegistry))");

        assertTrue(systems >= 0, "recipe systems should be registered");
        assertTrue(tailSystems > systems, "tail recipe systems should be registered after normal systems");
        assertTrue(tags > tailSystems, "system tags should be registered after all systems");
        assertTrue(jeiBridge > tags, "JEI recipe type bridges should be registered after systems");
        assertTrue(tailJeiBridge > jeiBridge, "tail JEI bridges should be registered after normal JEI bridges");
        assertTrue(machines > tailJeiBridge, "machine entries should be registered after all JEI bridges");
        assertTrue(attach > machines, "RecipeApiImpl should see registered machine entries");
    }

    private static Path resolvePath(String relative) {
        String moduleRelative = "Mod/" + relative;
        Path cursor = Path.of("").toAbsolutePath();
        while (cursor != null) {
            Path local = cursor.resolve(relative);
            if (Files.exists(local)) {
                return local;
            }
            Path module = cursor.resolve(moduleRelative);
            if (Files.exists(module)) {
                return module;
            }
            cursor = cursor.getParent();
        }
        throw new IllegalStateException("Unable to locate " + relative + " from " + Path.of("").toAbsolutePath());
    }
}
