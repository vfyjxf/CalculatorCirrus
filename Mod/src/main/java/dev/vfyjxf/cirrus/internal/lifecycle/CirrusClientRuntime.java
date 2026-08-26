package dev.vfyjxf.cirrus.internal.lifecycle;

import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseCatalogCache;
import dev.vfyjxf.cirrus.recipe.classify.RecipeClassificationService;
import dev.vfyjxf.cirrus.recipe.routing.RecipePathSettings;
import dev.vfyjxf.cirrus.ui.process.CalculatorBuildResultCache;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CirrusClientRuntime {

    private CirrusClientRuntime() {
        throw new AssertionError("This class should not be instantiated!");
    }

    public static void afterLoad() {
        // the database index build starts only after the routing graph prewarm
        // finishes so the two big builds never compete for cpu/heap
        RecipePathSettings.prewarmCalculatorRoutingGraph("clientLifecycleLoad")
            .thenRun(() -> DatabasePage.prewarmSystemCatalogIndex("clientLifecycleLoad"));
    }

    public static void beforeUnload() {
        // runs on world unload AND on datapack reload (the lifecycle driver's
        // default reload action is unload-then-load); the catalog cache is not
        // part of RecipePathSettings.clearRuntimeCaches, and its key only
        // fingerprints system identity — recipe content changes under stable
        // system instances on reload, so without this invalidate the prewarm
        // would hit the cache and serve the pre-reload catalog until TTL expiry
        RecipeDatabaseCatalogCache.invalidate();
        RecipePathSettings.clearRuntimeCaches("clientLifecycleUnload");
        RecipeClassificationService.clearRuntimeCaches();
        CalculatorBuildResultCache.clear();
    }
}
