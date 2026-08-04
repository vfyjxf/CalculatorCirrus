package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.server.MinecraftServer;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

@ExtendWith(EphemeralTestServerProvider.class)
class RecipeDatabaseCoverageTest {

    @Test
    void allSystemsInSystemCatalog(MinecraftServer server) {
        RecipeApi api = RecipeApi.instance();
        var allSystems = api.allSystems().toList();
        assumeFalse(allSystems.isEmpty(), "RecipeApi not initialized");

        int total = 0, jei = 0;
        for (var s : allSystems) {
            int c = s.allRecipes().size(); total += c;
            if (s.systemId().toString().startsWith("jei:")) jei++;
            System.out.printf("  %s: %d recipes%s%n", s.systemId(), c, jei > 0 && s.systemId().toString().startsWith("jei:") ? " [JEI]" : "");
        }
        System.out.println("Total: " + allSystems.size() + " systems, JEI: " + jei + ", recipes: " + total);
    }

    @Test
    void noSystemWithRecipesIsMissing(MinecraftServer server) {
        RecipeApi api = RecipeApi.instance();
        var allSystems = api.allSystems().toList();
        assumeFalse(allSystems.isEmpty(), "RecipeApi not initialized");

        List<Namespace> src = new ArrayList<>();
        Map<Namespace, List<Namespace>> tags = new HashMap<>();
        for (var s : allSystems) {
            src.add(s.systemId());
            var t = new ArrayList<Namespace>();
            api.getSystemTags(s.systemId()).forEach(t::add);
            if (!t.isEmpty()) tags.put(s.systemId(), t);
        }
        var db = new DatabaseDefinition("system:catalog", "Catalog", DatabaseKind.system, true, 0, src);
        var idx = RecipeDatabaseIndex.build(List.of(db), allSystems, tags);

        Map<Namespace, Integer> cts = new LinkedHashMap<>();
        for (var r : idx.records()) cts.merge(r.recipe().systemId(), 1, Integer::sum);

        System.out.println("=== Coverage: " + allSystems.size() + " systems, " + idx.records().size() + " records ===");
        int miss = 0;
        for (var s : allSystems) {
            int c = cts.getOrDefault(s.systemId(), 0);
            int t = s.allRecipes().size();
            if (c == 0 && t > 0) { miss++; System.out.printf("  %s: %d/%d *** MISSING ***%n", s.systemId(), c, t); }
            else System.out.printf("  %s: %d/%d%n", s.systemId(), c, t);
        }
        assertEquals(0, miss, miss + " systems have recipes but 0 records");
    }
}
