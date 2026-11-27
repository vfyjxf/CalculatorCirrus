package dev.vfyjxf.cirrus.ui.widget.calculator;

import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CalculatorTargetDemandQueueTest {

    private static final CalculatorTargetDemandQueueTest.FakeRecipeSystem system = new CalculatorTargetDemandQueueTest.FakeRecipeSystem();
    private static final RecipeEntry<?> entry = RecipeEntry.of(system, new FakeRecipe("target"));

    @AfterEach
    void clearState() {
        CalculatorTargetRecipeState.clear();
    }

    @Test
    void legacySetKeepsRecipeTimesAndVersion() {
        long versionBefore = CalculatorTargetRecipeState.currentVersion();
        CalculatorTargetRecipeState.Selection selection = CalculatorTargetRecipeState.set(entry, 3);

        assertEquals(entry, selection.recipe());
        assertEquals(3, selection.times());
        assertTrue(selection.version() > versionBefore);
        assertSame(selection, CalculatorTargetRecipeState.current());
        assertEquals(List.of(), selection.demands());
    }

    @Test
    void setDemandsPublishesQueueWithoutPrimaryRecipe() {
        List<TargetDemand> demands = List.of(
            new TargetDemand(ResourceKey.item("minecraft:cobblestone"), 32L),
            new TargetDemand(ResourceKey.item("minecraft:iron_ingot"), 8L)
        );
        CalculatorTargetRecipeState.Selection selection = CalculatorTargetRecipeState.setDemands(demands, null);

        assertNull(selection.recipe());
        assertEquals(demands, CalculatorTargetRecipeState.currentDemands());
        assertSame(selection, CalculatorTargetRecipeState.current());
        assertEquals(1, selection.times());
    }

    @Test
    void setDemandsAcceptsPrimaryRecipeAndEmptyQueue() {
        List<TargetDemand> demands = List.of(new TargetDemand(ResourceKey.item("minecraft:stone"), 12L));
        CalculatorTargetRecipeState.Selection withPrimary = CalculatorTargetRecipeState.setDemands(demands, entry);
        assertEquals(entry, withPrimary.recipe());
        assertEquals(demands, CalculatorTargetRecipeState.currentDemands());

        CalculatorTargetRecipeState.setDemands(List.of(), null);
        assertTrue(CalculatorTargetRecipeState.currentDemands().isEmpty());
        assertNull(Objects.requireNonNull(CalculatorTargetRecipeState.current()).recipe());
    }

    @Test
    void clearDropsSelectionAndDemands() {
        CalculatorTargetRecipeState.setDemands(List.of(new TargetDemand(ResourceKey.item("minecraft:dirt"), 1L)), null);
        CalculatorTargetRecipeState.clear();

        assertNull(CalculatorTargetRecipeState.current());
        assertTrue(CalculatorTargetRecipeState.currentDemands().isEmpty());
    }

    @Test
    void legacySelectionConstructorDerivesDemands() {
        CalculatorTargetRecipeState.Selection selection = new CalculatorTargetRecipeState.Selection(entry, 2, 5L);
        assertEquals(2, selection.times());
        assertEquals(5L, selection.version());
        assertEquals(List.of(), selection.demands());
    }

    @Test
    void pendingSelectionIsConsumedOnceWithoutStaleReopenLeak() {
        CalculatorTargetRecipeState.Selection selection = CalculatorTargetRecipeState.set(entry, 2);
        assertTrue(CalculatorTargetRecipeState.hasPending());

        assertSame(selection, CalculatorTargetRecipeState.consumePending());
        assertFalse(CalculatorTargetRecipeState.hasPending());
        assertNull(CalculatorTargetRecipeState.consumePending());
        assertSame(selection, CalculatorTargetRecipeState.current());

        CalculatorTargetRecipeState
                .setDemands(List.of(new TargetDemand(ResourceKey.item("minecraft:stone"), 4L)), entry);
        assertTrue(CalculatorTargetRecipeState.hasPending());
        CalculatorTargetRecipeState.markConsumed();
        assertFalse(CalculatorTargetRecipeState.hasPending());
        assertNull(CalculatorTargetRecipeState.consumePending());
    }

    @Test
    void setWithFallbackDemandsUsesFallbackWhenRecipeOutputsUnavailable() {
        List<TargetDemand> fallback = List.of(new TargetDemand(ResourceKey.item("minecraft:diamond"), 3L));
        CalculatorTargetRecipeState.Selection selection = CalculatorTargetRecipeState.set(entry, 1, fallback);

        assertEquals(entry, selection.recipe());
        assertEquals(fallback, selection.demands());
        assertEquals(fallback, CalculatorTargetRecipeState.currentDemands());
        assertSame(selection, CalculatorTargetRecipeState.consumePending());
    }

    private record FakeRecipe(String id) {}

    private static final class FakeRecipeSystem implements RecipeSystem<FakeRecipe> {

        @Override
        public Namespace systemId() {
            return Namespace.of("cirrus_test", "calculator");
        }

        @Override
        public Class<?> basicType() {
            return FakeRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(FakeRecipe recipe) {
            return null;
        }

        @Override
        public @Nullable FakeRecipe findRecipe(String uniqueId) {
            return null;
        }

        @Override
        public @Nullable FakeRecipe findRecipe(ResourceLocation registryName) {
            return null;
        }

        @Override
        public int hash(FakeRecipe recipe) {
            return recipe.hashCode();
        }

        @Override
        public boolean sameRecipe(FakeRecipe r1, FakeRecipe r2) {
            return r1.equals(r2);
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            return Lists.mutable.empty();
        }
    }
}
