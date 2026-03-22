package dev.vfyjxf.cirrus.api.recipe.routing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;

class BreakingCutoverMigrationTest {

    @Test
    void legacyRoutingEntrypointsAreRemovedFromRuntimeSurface() {
        assertThrows(ClassNotFoundException.class, () -> Class.forName("dev.vfyjxf.cirrus.api.recipe.routing.RecipeRouter"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("dev.vfyjxf.cirrus.api.recipe.routing.RecipeCalculator"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("dev.vfyjxf.cirrus.api.recipe.routing.RoutingResult"));
        assertThrows(ClassNotFoundException.class, () -> Class.forName("dev.vfyjxf.cirrus.api.recipe.routing.RoutingStep"));
    }
}

