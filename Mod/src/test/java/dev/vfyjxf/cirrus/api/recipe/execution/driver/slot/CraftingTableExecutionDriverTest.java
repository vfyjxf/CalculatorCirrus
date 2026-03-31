package dev.vfyjxf.cirrus.api.recipe.execution.driver.slot;

import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionCapability;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.CraftingTableExecutionDriver;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class CraftingTableExecutionDriverTest {

    @Test
    void craftingTableDriverPausesWhenAutoPlacementUnavailable() {
        var driver = new CraftingTableExecutionDriver();
        driver.initialize(startSession(openInteractionStep(), prepareInputsStep(), executeRecipeStep(), collectOutputsStep()));

        driver.advanceSteps(2);

        assertTrue(driver.session().paused());
    }

    @Test
    void craftingTableDriverKeepsSessionAvailableAfterAdvance() {
        var driver = new CraftingTableExecutionDriver();
        driver.initialize(startSession(openInteractionStep(), prepareInputsStep(), executeRecipeStep(), collectOutputsStep()));

        driver.advanceSteps(3);

        assertNotNull(driver.session());
    }

    @Test
    void craftingTableTrackingKeepsAggregatedAmountsWithoutReapplyingTimes(MinecraftServer server) {
        var driver = new CraftingTableExecutionDriver();
        var withdraw = new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.inventoryTransfer,
            new InteractionStep.WithdrawTarget.Resource(
                ContentStack.fromStack(new ItemStack(Items.OAK_LOG))
            ),
            16
        );
        var execute = new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.interactionPerform,
            recipeEntry("craft"),
            16,
            Lists.immutable.of(new PlanAction.InputBinding(
                InputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 16),
                16
            )),
            Lists.immutable.of(new PlanAction.OutputBinding(
                ContentStack.fromStack(new ItemStack(Items.SMOKER)),
                16
            ))
        );
        driver.initialize(startSession(withdraw, execute));

        var snapshot = driver.snapshot();

        assertEquals(1, snapshot.materials().size());
        assertEquals(16L, snapshot.materials().getFirst().required());
        assertNotNull(snapshot.targetProgress());
        assertEquals(16L, snapshot.targetProgress().required());
    }

    @Test
    void craftingTableTrackingPreservesTagDisplayForMissingMaterials(MinecraftServer server) {
        var driver = new CraftingTableExecutionDriver();
        var withdraw = new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.inventoryTransfer,
            new InteractionStep.WithdrawTarget.Input(
                InputContent.fromItemTag(ItemTags.PLANKS, 4)
            ),
            4
        );
        var execute = new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.interactionPerform,
            recipeEntry("tag_craft"),
            1,
            Lists.immutable.of(new PlanAction.InputBinding(
                InputContent.fromItemTag(ItemTags.PLANKS, 4),
                4
            )),
            Lists.immutable.of(new PlanAction.OutputBinding(
                ContentStack.fromStack(new ItemStack(Items.CHEST)),
                1
            ))
        );
        driver.initialize(startSession(withdraw, execute));

        var snapshot = driver.snapshot();

        assertEquals(1, snapshot.materials().size());
        RecipeContent material = snapshot.materials().getFirst().material();
        assertTrue(material instanceof TagStackContent<?>);
        assertTrue(ExecutionOverlayContentHelper.label(material).startsWith("#minecraft:planks"));
    }

    @Test
    void craftingTableTrackingIncludesReservedCatalysts(MinecraftServer server) {
        var driver = new CraftingTableExecutionDriver();
        var reserve = new InteractionStep.ReserveCatalystStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.inventoryTransfer,
            InputContent.fromItemStack(new ItemStack(Items.CRAFTING_TABLE), 1),
            1
        );
        driver.initialize(startSession(reserve, executeRecipeStep()));

        var snapshot = driver.snapshot();

        assertEquals(1, snapshot.materials().size());
        assertTrue(snapshot.materials().anySatisfy(requirement ->
            requirement.material().satisfy(ContentStack.fromStack(new ItemStack(Items.CRAFTING_TABLE)))
                && requirement.required() == 1L
        ));
    }

    @Test
    void craftingTableTrackingOnlyShowsExternalWithdrawRequirementsForSmokerChain() {
        var driver = new CraftingTableExecutionDriver();
        var session = startSession(
            new InteractionStep.WithdrawResourceStep(
                ExecutionSideRequirement.environmentAgnostic,
                ExecutionCapability.inventoryTransfer,
                new InteractionStep.WithdrawTarget.Resource(
                    ContentStack.fromStack(new ItemStack(Items.COBBLESTONE))
                ),
                128
            ),
            new InteractionStep.WithdrawResourceStep(
                ExecutionSideRequirement.environmentAgnostic,
                ExecutionCapability.inventoryTransfer,
                new InteractionStep.WithdrawTarget.Resource(
                    ContentStack.fromStack(new ItemStack(Items.OAK_LOG))
                ),
                64
            ),
            new InteractionStep.ExecuteRecipeStep(
                ExecutionSideRequirement.environmentAgnostic,
                ExecutionCapability.interactionPerform,
                recipeEntry("make_furnace"),
                16,
                Lists.immutable.of(
                    new PlanAction.InputBinding(
                        InputContent.fromItemStack(new ItemStack(Items.COBBLESTONE), 128),
                        128
                    )
                ),
                Lists.immutable.of(
                    new PlanAction.OutputBinding(
                        ContentStack.fromStack(new ItemStack(Items.FURNACE)),
                        16
                    )
                )
            ),
            new InteractionStep.ExecuteRecipeStep(
                ExecutionSideRequirement.environmentAgnostic,
                ExecutionCapability.interactionPerform,
                recipeEntry("make_smoker"),
                16,
                Lists.immutable.of(
                    new PlanAction.InputBinding(
                        InputContent.fromItemStack(new ItemStack(Items.FURNACE), 16),
                        16
                    ),
                    new PlanAction.InputBinding(
                        InputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 64),
                        64
                    )
                ),
                Lists.immutable.of(
                    new PlanAction.OutputBinding(
                        ContentStack.fromStack(new ItemStack(Items.SMOKER)),
                        16
                    )
                )
            )
        );
        driver.initialize(session);

        var snapshot = driver.snapshot();

        assertEquals(2, snapshot.materials().size());
        assertTrue(snapshot.materials().anySatisfy(requirement ->
            requirement.material().satisfy(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE)))
                && requirement.required() == 128L
        ));
        assertTrue(snapshot.materials().anySatisfy(requirement ->
            requirement.material().satisfy(ContentStack.fromStack(new ItemStack(Items.OAK_LOG)))
                && requirement.required() == 64L
        ));
        assertTrue(snapshot.materials().noneSatisfy(requirement ->
            requirement.material().satisfy(ContentStack.fromStack(new ItemStack(Items.FURNACE)))
        ));
    }

    @Test
    void newSlotApiTypesAreAccessible() {
        SlotLayout.Role role = SlotLayout.Role.input;
        assertNotNull(role);
    }

    private static ExecutionSession startSession(InteractionStep... steps) {
        var plan = new InteractionExecutionPlan(recipeEntry("craft"), 1, Lists.immutable.of(steps));
        return ExecutionSession.start(plan, ExecutionFlowNode.pending("root", Component.literal("root"), Lists.immutable.empty(), null, Lists.immutable.empty()));
    }

    private static InteractionStep.OpenInteractionStep openInteractionStep() {
        return new InteractionStep.OpenInteractionStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.interactionAccess,
            recipeEntry("craft"),
            1,
            Namespace.of("minecraft", "crafting_table")
        );
    }

    private static InteractionStep.PrepareInputsStep prepareInputsStep() {
        return new InteractionStep.PrepareInputsStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.interactionAccess,
            recipeEntry("craft"),
            Lists.immutable.empty()
        );
    }

    private static InteractionStep.ExecuteRecipeStep executeRecipeStep() {
        return new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.interactionPerform,
            recipeEntry("craft"),
            1,
            Lists.immutable.empty(),
            Lists.immutable.empty()
        );
    }

    private static InteractionStep.CollectOutputsStep collectOutputsStep() {
        return new InteractionStep.CollectOutputsStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.outputStorage,
            recipeEntry("craft"),
            Lists.immutable.empty()
        );
    }

    private static RecipeEntry<TestRecipe> recipeEntry(String id) {
        return RecipeEntry.of(TEST_RECIPE_SYSTEM, new TestRecipe(id));
    }

    private record TestRecipe(String id) {
    }

    private static final TestRecipeSystem TEST_RECIPE_SYSTEM = new TestRecipeSystem();

    private static final class TestRecipeSystem implements RecipeSystem<TestRecipe> {
        private final Map<String, TestRecipe> byId = new ConcurrentHashMap<>();

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "crafting_table_driver");
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
        public ResolveResult resolveFallback(ResolveContext context, TestRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(
                RecipeEntry.of(this, recipe),
                List.of(InputContent.fromItemStack(new ItemStack(Items.OAK_LOG))),
                List.of(OutputContent.fromItemStack(new ItemStack(Items.SMOKER)))
            ));
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.ofAll(byId.values());
        }
    }
}
