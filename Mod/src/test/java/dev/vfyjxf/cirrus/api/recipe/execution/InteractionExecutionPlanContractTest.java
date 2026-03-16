package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.RichIterable;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InteractionExecutionPlanContractTest {

    //region ordering and side

    @Test
    void planPreservesStepOrderingAndRequiredSide() {
        var recipe = recipeEntry("craft_planks");
        var accessible = new InteractionStep.EnsureAccessibleStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.storageAccess,
            new InteractionStep.AccessTarget.Resource(resource(3L)),
            3L
        );
        var open = new InteractionStep.OpenInteractionStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.interactionAccess,
            recipe,
            1,
            "crafting_table"
        );
        var manual = new InteractionStep.ManualCheckpointStep(
            ExecutionSideRequirement.player,
            ExecutionCapability.manualAction,
            ExecutionGuidance.simple(Component.literal("Open the crafting table"))
        );

        var plan = new InteractionExecutionPlan(recipe, 1, Lists.immutable.of(accessible, open, manual));

        assertSame(accessible, plan.steps().get(0));
        assertSame(open, plan.steps().get(1));
        assertSame(manual, plan.steps().get(2));
        assertEquals(ExecutionSideRequirement.environmentAgnostic, plan.steps().get(0).requiredSide());
        assertEquals(ExecutionSideRequirement.client, plan.steps().get(1).requiredSide());
        assertEquals(ExecutionSideRequirement.player, plan.steps().get(2).requiredSide());
    }

    //endregion

    //region typed payloads

    @Test
    void typedPayloadsPreservedAcrossAccessibilityCapacityExecutionAndManualSteps() {
        var recipe = recipeEntry("craft_planks");
        var accessibleInput = input(1L);
        var outputResource = resource(8L);
        var expectedOutputs = Lists.immutable.of(new PlanAction.OutputBinding(outputResource, 8L));
        var inputBindings = Lists.immutable.of(new PlanAction.InputBinding(accessibleInput, 1L));

        var accessible = new InteractionStep.EnsureAccessibleStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.storageAccess,
            new InteractionStep.AccessTarget.Input(accessibleInput),
            1L
        );
        var capacity = new InteractionStep.EnsureCapacityStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.inventoryCapacity,
            outputResource,
            8L
        );
        var execute = new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.interactionPerform,
            recipe,
            2,
            inputBindings,
            expectedOutputs
        );
        var manual = new InteractionStep.ManualCheckpointStep(
            ExecutionSideRequirement.player,
            ExecutionCapability.manualAction,
            ExecutionGuidance.simple(Component.literal("Press A to continue"))
        );

        assertSame(accessibleInput, ((InteractionStep.AccessTarget.Input) accessible.target()).input());
        assertEquals(1L, accessible.requestedAmount());
        assertSame(outputResource, capacity.resource());
        assertEquals(8L, capacity.amount());
        assertEquals(recipe, execute.recipe());
        assertEquals(2, execute.times());
        assertEquals(inputBindings, execute.inputs());
        assertEquals(expectedOutputs, execute.expectedOutputs());
        assertEquals("Press A to continue", manual.guidance().summary().getString());
    }

    //endregion

    //region shape

    @Test
    void executionPlanKeepsInMemoryShapeWithoutVersionOrSerializationApis() {
        List<String> componentNames = Arrays.stream(InteractionExecutionPlan.class.getRecordComponents())
            .map(RecordComponent::getName)
            .toList();

        assertEquals(List.of("targetRecipe", "targetTimes", "steps"), componentNames);
        assertFalse(hasMemberNamed(InteractionExecutionPlan.class, "planVersion"));
        assertFalse(hasMemberNamed(InteractionExecutionPlan.class, "tagSnapshotVersion"));
        assertFalse(hasMemberNamed(InteractionExecutionPlan.class, "codec"));
        assertFalse(Arrays.stream(InteractionExecutionPlan.class.getMethods())
            .filter(method -> method.getDeclaringClass() == InteractionExecutionPlan.class)
            .map(method -> method.getName().toLowerCase())
            .anyMatch(name -> name.contains("serialize") || name.contains("codec")));
    }

    @Test
    void sideAndCapabilitySingletonsRemainAvailableForStepConstruction() {
        assertTrue(ExecutionSideRequirement.client.allowsClientExecution());
        assertFalse(ExecutionSideRequirement.client.allowsServerExecution());
        assertFalse(ExecutionSideRequirement.manual.onlyRunsAutomatically());
        assertTrue(ExecutionCapability.storageAccess.requiresEnvironmentCapability());
        assertFalse(ExecutionCapability.manualAction.requiresEnvironmentCapability());
    }

    private static boolean hasMemberNamed(Class<?> type, String name) {
        return Arrays.stream(type.getDeclaredFields()).anyMatch(field -> field.getName().equalsIgnoreCase(name));
    }

    private static RecipeEntry<TestRecipe> recipeEntry(String id) {
        return RecipeEntry.of(recipeSystem, new TestRecipe(id));
    }

    private static ContentResource resource(long amount) {
        return ContentValue.fromValue(longValueType, amount);
    }

    private static InputContent input(long amount) {
        return InputContent.fromValue(longValueType, amount);
    }

    //endregion

    //region fixtures

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
        public TestRecipe findRecipe(String uniqueId) {
            return byId.get(uniqueId);
        }

        @Override
        public TestRecipe findRecipe(ResourceLocation registryName) {
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
        public RecipeProjection resolveRecipe(TestRecipe recipe) {
            throw new UnsupportedOperationException("Test fixture does not resolve recipes");
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.ofAll(byId.values());
        }
    }

    //endregion
}
