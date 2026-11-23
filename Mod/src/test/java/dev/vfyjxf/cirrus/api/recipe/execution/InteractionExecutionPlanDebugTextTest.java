package dev.vfyjxf.cirrus.api.recipe.execution;

import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class InteractionExecutionPlanDebugTextTest {

    @Test
    void describePlanFormatsRuntimeStepsAndBindings(MinecraftServer server) {
        Fixture fixture = new Fixture();
        IngredientBinding binding = fixture.craftPlanks(2);

        InteractionExecutionPlan plan = new InteractionExecutionPlan(
            fixture.entry,
            2,
            Lists.immutable.of(
                new InteractionStep.OpenInteractionStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.interactionAccess,
                    fixture.entry,
                    2,
                    Namespace.ofMc("crafting_table")
                ),
                new InteractionStep.PrepareInputsStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.inventoryTransfer,
                    fixture.entry,
                    binding.inputs()
                ),
                new InteractionStep.ExecuteRecipeStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.interactionPerform,
                    fixture.entry,
                    2,
                    binding.inputs(),
                    binding.outputs()
                ),
                new InteractionStep.CollectOutputsStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.inventoryTransfer,
                    fixture.entry,
                    binding.outputs()
                ),
                new InteractionStep.StoreOutputsStep(
                    ExecutionSideRequirement.clientSide,
                    ExecutionCapability.outputStorage,
                    fixture.oakPlanks(),
                    8L
                )
            )
        );

        String text = InteractionExecutionPlanDebugText.describe(plan);

        assertTrue(text.contains("interaction-plan target=test:craft_planks x2"), text);
        assertTrue(text.contains("steps=5 capabilities=["), text);
        assertTrue(text.contains("[0] open test:craft_planks x2 interaction=minecraft:crafting_table"), text);
        assertTrue(text.contains("[1] prepare test:craft_planks"), text);
        assertTrue(text.contains("inputs: [minecraft:oak_log x2]"), text);
        assertTrue(text.contains("[2] execute test:craft_planks x2"), text);
        assertTrue(text.contains("outputs: [minecraft:oak_planks x8]"), text);
        assertTrue(text.contains("[3] collect test:craft_planks"), text);
        assertTrue(text.contains("[4] store-output minecraft:oak_planks x8"), text);
    }

    @Test
    void describeLinesFormatsSingleStep() {
        Fixture fixture = new Fixture();
        IngredientBinding binding = fixture.craftPlanks(1);
        InteractionStep.PrepareInputsStep prepare = new InteractionStep.PrepareInputsStep(
            ExecutionSideRequirement.clientSide,
            ExecutionCapability.inventoryTransfer,
            fixture.entry,
            binding.inputs()
        );
        InteractionStep.ExecuteRecipeStep execute = new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.clientSide,
            ExecutionCapability.interactionPerform,
            fixture.entry,
            1,
            binding.inputs(),
            binding.outputs()
        );

        String prepareText = InteractionExecutionPlanDebugText.describeLines(10, prepare).makeString(System.lineSeparator());
        String executeText = InteractionExecutionPlanDebugText.describeLines(11, execute).makeString(System.lineSeparator());

        assertTrue(prepareText.contains("[10] prepare test:craft_planks"), prepareText);
        assertTrue(prepareText.contains("inputs: [minecraft:oak_log x1]"), prepareText);
        assertTrue(executeText.contains("[11] execute test:craft_planks x1"), executeText);
        assertTrue(executeText.contains("outputs: [minecraft:oak_planks x4]"), executeText);
    }

    private record IngredientBinding(
        ImmutableList<InputBinding> inputs,
        ImmutableList<OutputBinding> outputs
    ) {
    }

    private static final class Fixture {
        private final FakeRecipeSystem system = new FakeRecipeSystem();
        private final RecipeEntry<FakeRecipe> entry;

        private Fixture() {
            FakeRecipe recipe = new FakeRecipe(
                "craft_planks",
                java.util.List.of(InputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 1)),
                java.util.List.of(OutputContent.fromItemStack(new ItemStack(Items.OAK_PLANKS), 4))
            );
            system.register(recipe);
            entry = RecipeEntry.of(system, recipe);
        }

        private ContentResource oakPlanks() {
            return ContentStack.fromStack(new ItemStack(Items.OAK_PLANKS));
        }

        private IngredientBinding craftPlanks(int times) {
            return new IngredientBinding(
                Lists.immutable.of(new InputBinding(InputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 1), times)),
                Lists.immutable.of(new OutputBinding(oakPlanks(), Math.multiplyExact(4L, times)))
            );
        }
    }

    private record FakeRecipe(
        String id,
        Collection<InputContent> inputs,
        Collection<OutputContent> outputs
    ) {
    }

    private static final class FakeRecipeSystem implements RecipeSystem<FakeRecipe> {
        private final Map<String, FakeRecipe> byId = new ConcurrentHashMap<>();

        private void register(FakeRecipe recipe) {
            byId.put(recipe.id(), recipe);
        }

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "execution");
        }

        @Override
        public Class<?> basicType() {
            return FakeRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(FakeRecipe recipe) {
            return ResourceLocation.fromNamespaceAndPath("test", recipe.id());
        }

        @Override
        public @Nullable FakeRecipe findRecipe(String uniqueId) {
            return byId.get(uniqueId);
        }

        @Override
        public @Nullable FakeRecipe findRecipe(ResourceLocation registryName) {
            return byId.get(registryName.getPath());
        }

        @Override
        public int hash(FakeRecipe recipe) {
            return recipe.id().hashCode();
        }

        @Override
        public boolean sameRecipe(FakeRecipe left, FakeRecipe right) {
            return Objects.equals(left.id(), right.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, FakeRecipe recipe) {
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs()));
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            return Lists.immutable.ofAll(byId.values());
        }
    }
}
