package dev.vfyjxf.cirrus.recipe.execution;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionInteraction;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionGuideResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.planning.bridge.ExecutionPlanBridge;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionTargetPlanHandoverTest {

    private static final FakeRecipeSystem system = new FakeRecipeSystem();
    private static final RecipeEntry<?> stoneEntry = RecipeEntry.of(system, new FakeRecipe("stone"));
    private static final RecipeEntry<?> brickEntry = RecipeEntry.of(system, new FakeRecipe("bricks"));
    private static final CompiledRecipeKey stoneKey = new CompiledRecipeKey(
        Namespace.parse("minecraft:crafting"),
        Optional.of(ResourceLocation.parse("test:stone")),
        11L
    );
    private static final CompiledRecipeKey brickKey = new CompiledRecipeKey(
        Namespace.parse("minecraft:crafting"),
        Optional.of(ResourceLocation.parse("test:bricks")),
        22L
    );
    private static final ExecutionInteraction interaction = new ExecutionInteraction(
        Namespace.of("cirrus_test", "workbench"),
        new FakeInteractionApi()
    );

    @AfterEach
    void resetSharedState() {
        ExecutionTargetRecipeState.setPinnedRecipeResolver(null);
        ExecutionTargetRecipeState.clear();
    }

    @Test
    void selectionKeepsPinnedPlanAndRejectsNonPositiveTimes() {
        ExecutionTargetRecipeState.Selection plain = new ExecutionTargetRecipeState.Selection(stoneEntry, 3, 7L);
        assertNull(plain.pinnedPlan());
        assertEquals(3, plain.times());
        assertEquals(7L, plain.version());

        ProductionPlan pinnedPlan = multiStepPlan();
        ExecutionTargetRecipeState.Selection pinned = new ExecutionTargetRecipeState.Selection(
            stoneEntry,
            5,
            8L,
            pinnedPlan
        );
        assertSame(pinnedPlan, pinned.pinnedPlan());

        assertThrows(IllegalArgumentException.class, () -> new ExecutionTargetRecipeState.Selection(stoneEntry, 0, 9L));
        assertThrows(
            IllegalArgumentException.class,
            () -> new ExecutionTargetRecipeState.Selection(stoneEntry, 0, 9L, pinnedPlan)
        );
    }

    @Test
    void setPublishesPinnedPlanAsCurrentSelection() {
        ExecutionTargetRecipeState.Selection previous = ExecutionTargetRecipeState.set(stoneEntry, 5);
        assertNull(previous.pinnedPlan());

        ProductionPlan pinnedPlan = multiStepPlan();
        ExecutionTargetRecipeState.Selection next = ExecutionTargetRecipeState.set(stoneEntry, 5, pinnedPlan);
        assertTrue(next.version() > previous.version());
        assertSame(next, ExecutionTargetRecipeState.current());
        assertSame(pinnedPlan, Objects.requireNonNull(ExecutionTargetRecipeState.current()).pinnedPlan());
    }

    @Test
    void pinnedPlanTranslatesOneInteractionGroupPerOutlineBatch() {
        ProductionPlan pinnedPlan = multiStepPlan();
        Function<CompiledRecipeKey, Optional<RecipeEntry<?>>> resolver = entryResolver();
        ExecutionPlanBridge.ExecutionOutline outline = ExecutionPlanBridge.buildOutline(pinnedPlan, resolver);
        assertEquals(2, outline.batches().size());

        ExecutionTargetRecipeState.setPinnedRecipeResolver(resolver);
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(stoneEntry, 5, pinnedPlan);
        InteractionExecutionPlan plan = ExecutionTargetRecipeState.buildPlan(selection, interaction);

        assertEquals(outline.batches().size(), openStepCount(plan));
        assertEquals(batchTimes(outline), executeTimes(plan));
        assertEquals(brickEntry, plan.targetRecipe());
        assertEquals(outline.batches().getLast().times(), plan.targetTimes());
    }

    @Test
    void pinnedPlanFallsBackToTargetTranslationWhenOutlineHasNoBatches() {
        ProductionPlan noBatchPlan = new ProductionPlan(
            "plan-empty",
            "cirrus.strategy.balanced",
            List.of(),
            PreparationSummary.empty,
            List.of(),
            CostVector.zero,
            validCertificate()
        );
        ExecutionTargetRecipeState.setPinnedRecipeResolver(entryResolver());
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(stoneEntry, 4, noBatchPlan);
        InteractionExecutionPlan plan = ExecutionTargetRecipeState.buildPlan(selection, interaction);

        assertTrue(plan.steps().isEmpty());
        assertEquals(stoneEntry, plan.targetRecipe());
        assertEquals(4, plan.targetTimes());
    }

    @Test
    void unresolvedPinnedPlanFallsBackToTargetTranslation() {
        ProductionPlan pinnedPlan = multiStepPlan();
        ExecutionTargetRecipeState.setPinnedRecipeResolver(key -> Optional.empty());
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(stoneEntry, 4, pinnedPlan);
        InteractionExecutionPlan plan = ExecutionTargetRecipeState.buildPlan(selection, interaction);

        assertTrue(plan.steps().isEmpty());
        assertEquals(stoneEntry, plan.targetRecipe());
        assertEquals(4, plan.targetTimes());
    }

    @Test
    void unsetPinnedPlanTranslatesTargetWithoutOutline() {
        ExecutionTargetRecipeState.setPinnedRecipeResolver(entryResolver());
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(stoneEntry, 6);
        InteractionExecutionPlan plan = ExecutionTargetRecipeState.buildPlan(selection, interaction);

        assertTrue(plan.steps().isEmpty());
        assertEquals(stoneEntry, plan.targetRecipe());
        assertEquals(6, plan.targetTimes());
    }

    private static ProductionPlan multiStepPlan() {
        return new ProductionPlan(
            "plan-multi",
            "cirrus.strategy.balanced",
            List.of(craftStep("s1", stoneKey, 5L), craftStep("s2", brickKey, 3L)),
            PreparationSummary.empty,
            List.of(),
            CostVector.zero,
            validCertificate()
        );
    }

    private static CraftStep craftStep(String stepId, CompiledRecipeKey recipeKey, long batches) {
        return new CraftStep(
            stepId,
            List.of(),
            recipeKey,
            batches,
            Optional.empty(),
            false,
            Map.of(),
            Map.of(),
            CostVector.zero
        );
    }

    private static VerificationCertificate validCertificate() {
        return new VerificationCertificate(true, true, true, true, true, true, List.of());
    }

    private static Function<CompiledRecipeKey, Optional<RecipeEntry<?>>> entryResolver() {
        Map<CompiledRecipeKey, RecipeEntry<?>> entries = new HashMap<>();
        entries.put(stoneKey, stoneEntry);
        entries.put(brickKey, brickEntry);
        return key -> Optional.ofNullable(entries.get(key));
    }

    private static int openStepCount(InteractionExecutionPlan plan) {
        int count = 0;
        for (InteractionStep step : plan.steps()) {
            if (step instanceof InteractionStep.OpenInteractionStep) {
                count++;
            }
        }
        return count;
    }

    private static List<Integer> executeTimes(InteractionExecutionPlan plan) {
        List<Integer> times = new ArrayList<>();
        for (InteractionStep step : plan.steps()) {
            if (step instanceof InteractionStep.ExecuteRecipeStep execute) {
                times.add(execute.times());
            }
        }
        return times;
    }

    private static List<Integer> batchTimes(ExecutionPlanBridge.ExecutionOutline outline) {
        List<Integer> times = new ArrayList<>();
        for (ExecutionPlanBridge.RecipeBatch batch : outline.batches()) {
            times.add(batch.times());
        }
        return times;
    }

    private record FakeRecipe(String id) {}

    private static final class FakeRecipeSystem implements RecipeSystem<FakeRecipe> {

        @Override
        public Namespace systemId() {
            return Namespace.of("cirrus_test", "crafting");
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

    private static final class FakeInteractionApi implements InteractionApi {

        @Override
        public InteractionProbeResult probe(RecipeEntry<?> recipe) {
            return new InteractionProbeResult.Unsupported(null);
        }

        @Override
        public InteractionGuideResult guide(RecipeEntry<?> recipe) {
            throw new UnsupportedOperationException("guide is not used by plan translation");
        }

        @Override
        public InteractionPerformResult perform(InteractionContext context) {
            throw new UnsupportedOperationException("perform is not used by plan translation");
        }
    }
}
