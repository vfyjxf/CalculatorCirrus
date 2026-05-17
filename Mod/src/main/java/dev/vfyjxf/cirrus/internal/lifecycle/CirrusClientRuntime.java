package dev.vfyjxf.cirrus.internal.lifecycle;

import dev.vfyjxf.cirrus.recipe.routing.RecipePathSettings;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class CirrusClientRuntime {

    private CirrusClientRuntime() {
        throw new AssertionError("This class should not be instantiated!");
    }

    public static void afterLoad() {
        RecipePathSettings.prewarmCalculatorRoutingGraph("clientLifecycleLoad");
    }

    public static void beforeUnload() {
        RecipePathSettings.clearRuntimeCaches("clientLifecycleUnload");
    }
}
