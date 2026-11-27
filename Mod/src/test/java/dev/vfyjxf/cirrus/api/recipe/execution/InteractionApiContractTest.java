package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.recipe.execution.interaction.CraftingTableLikeInteraction;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionApiContractTest {

    //region contract

    @Test
    void probeGuideAndPerformExposeTypedResults() {
        var recipe = recipeEntry("craft_planks");
        InteractionApi api = CraftingTableLikeInteraction.automatic(Namespace.ofMc("crafting_table"));

        var probe = api.probe(recipe);
        var guide = api.guide(recipe);
        var perform = api.perform(
            new InteractionContext(
                recipe,
                1,
                Lists.immutable.of(new InputBinding(input(1L), 1L)),
                Lists.immutable.of(new OutputBinding(resource(4L), 4L))
            )
        );

        assertTrue(probe instanceof InteractionProbeResult.Supported);
        var supported = (InteractionProbeResult.Supported) probe;
        assertEquals(Namespace.ofMc("crafting_table"), supported.interactionId());
        assertTrue(supported.performerAvailable());
        assertTrue(perform instanceof InteractionPerformResult.Performed);
    }

    @Test
    void guidedOnlyInteractionFallsBackToManualGuidance() {
        var recipe = recipeEntry("craft_planks");
        InteractionApi api = CraftingTableLikeInteraction.guided(
            Namespace.ofMc("crafting_table"),
            ExecutionGuidance.simple(Component.literal("Open the crafting table to continue"))
        );

        var probe = api.probe(recipe);
        var guide = api.guide(recipe);
        var perform = api.perform(
            new InteractionContext(
                recipe,
                1,
                Lists.immutable.of(new InputBinding(input(1L), 1L)),
                Lists.immutable.of(new OutputBinding(resource(4L), 4L))
            )
        );

        assertTrue(probe instanceof InteractionProbeResult.Supported);
        var supported = (InteractionProbeResult.Supported) probe;
        assertEquals(Namespace.ofMc("crafting_table"), supported.interactionId());
        assertFalse(supported.performerAvailable());
        assertTrue(perform instanceof InteractionPerformResult.ManualRequired);
        var manualRequired = (InteractionPerformResult.ManualRequired) perform;
    }

    @Test
    void craftingTableInteractionReportsGridSafeExecuteBatchSize() {
        InteractionApi api = CraftingTableLikeInteraction.automatic(Namespace.ofMc("crafting_table"));
        var recipe = recipeEntry("craft_planks");

        int maxBatch = api.maxExecuteBatchSize(
            new InteractionContext(
                recipe,
                130,
                Lists.immutable.of(new InputBinding(input(130L), 130L)),
                Lists.immutable.of(new OutputBinding(resource(520L), 520L))
            )
        );

        assertEquals(64, maxBatch);
    }

    //endregion

    //region fixtures

    private static RecipeEntry<TestRecipe> recipeEntry(String id) {
        return RecipeEntry.of(recipeSystem, new TestRecipe(id));
    }

    private static ContentResource resource(long amount) {
        return ContentValue.fromValue(longValueType, amount);
    }

    private static InputContent input(long amount) {
        return InputContent.fromValue(longValueType, amount);
    }

    private static final ValueType<Long> longValueType = new ValueType<>() {
        @Override
        public Namespace typeId() {
            return Namespace.of("test", "amount");
        }

        @Override
        public long getValue(Long content) {
            return content;
        }

        @Override
        public boolean matches(Long fist, Long second) {
            return fist.equals(second);
        }

        @Override
        public Long fromValue(long value) {
            return value;
        }

        @Override
        public Long copy(Long content) {
            return content;
        }
    };

    private static final TestRecipeSystem recipeSystem = new TestRecipeSystem();

    private record TestRecipe(String id) {}

    private static final class TestRecipeSystem implements RecipeSystem<TestRecipe> {

        private final Map<String, TestRecipe> byId = new ConcurrentHashMap<>();

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "execution");
        }

        @Override
        public Class<?> basicType() {
            return TestRecipe.class;
        }

        @Override
        public ResourceLocation getId(TestRecipe recipe) {
            byId.put(recipe.id(), recipe);
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
        }

        @Override
        public @Nullable TestRecipe findRecipe(String uniqueId) {
            return byId.get(uniqueId);
        }

        @Override
        public @Nullable TestRecipe findRecipe(ResourceLocation registryName) {
            return byId.get(registryName.getPath());
        }

        @Override
        public int hash(TestRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(TestRecipe r1, TestRecipe r2) {
            return r1.id().equals(r2.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, TestRecipe recipe) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.ofAll(byId.values());
        }
    }

    //endregion
}
