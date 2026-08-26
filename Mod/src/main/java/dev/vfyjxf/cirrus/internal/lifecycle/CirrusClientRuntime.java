package dev.vfyjxf.cirrus.internal.lifecycle;

import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseCatalogCache;
import dev.vfyjxf.cirrus.internal.integration.jei.JeiIntegration;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
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
            .thenRun(CirrusClientRuntime::prewarmDatabaseIndexWhenSystemsReady);
    }

    /**
     * The catalog's system set is only complete once JEI hands us its runtime:
     * JeiFallbackRecipeSystemPlugin registers the vanilla basics at plugin load,
     * but the pack-specific categories only exist after JEI's
     * {@code onRuntimeAvailable} fills {@code knownRecipeTypes}. Prewarming
     * earlier builds an index over a partial system set whose cache key never
     * matches the full set, so the first user-facing database open pays the
     * full rebuild anyway (measured: 10,629 prewarmed vs 27,758 needed on
     * atm10-lite). With JEI present, wait for its runtime; without JEI the
     * plugin-loaded set is already final and we can build immediately.
     */
    private static void prewarmDatabaseIndexWhenSystemsReady() {
        if (JeiIntegration.systemsReady() || JeiIntegration.jeiAbsent()) {
            DatabasePage.prewarmSystemCatalogIndex("clientLifecycleLoad");
            return;
        }
        JeiIntegration.onSystemsReady(() ->
            DatabasePage.prewarmSystemCatalogIndex("clientLifecycleLoad"));
    }

    public static void beforeUnload() {
        // runs on world unload AND on datapack reload (the lifecycle driver's
        // default reload action is unload-then-load); the catalog cache is not
        // part of RecipePathSettings.clearRuntimeCaches, and its key only
        // fingerprints system identity — recipe content changes under stable
        // system instances on reload, so without this invalidate the prewarm
        // would hit the cache and serve the pre-reload catalog until TTL expiry
        RecipeDatabaseCatalogCache.invalidate();
        // same reload-shape hazard as the catalog cache above: the empty-context
        // resolve memo keys recipe instances by identity, and datapack reload can
        // hand out rebuilt recipe content without changing the api instance, so
        // stale memoized results must be dropped here even though the weak-keyed
        // inner maps already evaporate replaced recipe instances on their own
        RecipeApiImpl.clearEmptyContextResolveMemos();
        RecipePathSettings.clearRuntimeCaches("clientLifecycleUnload");
        RecipeClassificationService.clearRuntimeCaches();
        CalculatorBuildResultCache.clear();
    }
}
