package dev.vfyjxf.cirrus.api.recipe.execution;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionGuideResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanBuilder;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlan;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageApi;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSource;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceRenderer;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceType;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

final class ExecutionTranslatorFixtures {

    private final FakeStorageApi storageApi;
    private final FakeInteractionApi automaticInteractionApi;
    private final FakeInteractionApi guidedOnlyInteractionApi;
    private final FakeRecipeSystem recipeSystem = new FakeRecipeSystem();
    private final FakeRecipe craftPlanksRecipe;
    private final RecipeEntry<FakeRecipe> craftPlanksEntry;
    private final InputContent workbenchCatalyst;

    ExecutionTranslatorFixtures() {
        this.storageApi = new FakeStorageApi(
            new FakeStorageSource(
                "player_inventory",
                Lists.immutable.of(oakLogResource(), oakPlanksResource()),
                true,
                true
            )
        );
        this.automaticInteractionApi = FakeInteractionApi.automatic("crafting_table");
        this.guidedOnlyInteractionApi = FakeInteractionApi.guided(
            "crafting_table",
            "Open a crafting table to continue the recipe."
        );
        this.workbenchCatalyst = InputContent.fromItemStack(new ItemStack(Items.CRAFTING_TABLE), 1, RecipeContent.MIN_CHANCE);
        this.craftPlanksRecipe = new FakeRecipe(
            "craft_planks",
            List.of(InputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 1)),
            List.of(OutputContent.fromItemStack(new ItemStack(Items.OAK_PLANKS), 4))
        );
        this.recipeSystem.register(craftPlanksRecipe);
        this.craftPlanksEntry = RecipeEntry.of(recipeSystem, craftPlanksRecipe);
    }

    //region storage

    StorageApi storageApi() {
        return storageApi;
    }

    //endregion

    //region interaction

    FakeInteractionApi automaticInteractionApi() {
        return automaticInteractionApi;
    }

    FakeInteractionApi guidedOnlyInteractionApi() {
        return guidedOnlyInteractionApi;
    }

    //endregion

    //region plan actions

    PlanAction.ConsumeStock consumeOakLog(long amount) {
        return new PlanAction.ConsumeStock(
            0,
            0,
            false,
            InputContent.fromResource(oakLogResource()).copyWithAmount(amount),
            oakLogResource(),
            amount
        );
    }

    PlanAction.ConsumeStock consumeAnyPlankAsResolvedOak(long amount) {
        InputContent input = InputContent.fromItemTag(ItemTags.PLANKS, amount);
        return new PlanAction.ConsumeStock(
            0,
            0,
            false,
            input,
            oakPlanksResource(),
            amount
        );
    }

    PlanAction.PendingInput consumeAnyPlank(long amount) {
        return new PlanAction.PendingInput(
            0,
            0,
            false,
            // Keep this tag-shaped on purpose: translator tests assert that execution
            // still sees a semantic input instead of an arbitrary concrete plank stack.
            InputContent.fromItemTag(ItemTags.PLANKS, amount),
            amount,
            0,
            0
        );
    }

    PlanAction.ReserveCatalyst reserveWorkbenchCatalyst(long amount) {
        return new PlanAction.ReserveCatalyst(0, 0, false, workbenchCatalyst(), amount, craftPlanksEntry);
    }

    PlanAction.ExecuteRecipe craftPlanks(int times) {
        return new PlanAction.ExecuteRecipe(
            0,
            0,
            false,
            craftPlanksEntry,
            times,
            Lists.immutable.of(new PlanAction.InputBinding(InputContent.fromItemStack(new ItemStack(Items.OAK_LOG), 1), times)),
            Lists.immutable.of(new PlanAction.OutputBinding(oakPlanksResource(), Math.multiplyExact(4L, times)))
        );
    }

    RoutingPlan plan(PlanAction action) {
        int targetTimes = action instanceof PlanAction.ExecuteRecipe execute ? execute.times() : 1;
        PlanBuilder builder = new PlanBuilder(1L, 1L, craftPlanksEntry, targetTimes);
        switch (action) {
            case PlanAction.ConsumeStock consume -> builder.addConsumeStock(consume.input(), consume.resource(), consume.amount());
            case PlanAction.PendingInput pending -> builder.addPendingInput(pending.input(), pending.amount());
            case PlanAction.ReserveCatalyst reserve ->
                builder.addReserveCatalyst(reserve.input(), reserve.amount(), reserve.fromRecipe());
            case PlanAction.ExecuteRecipe execute ->
                builder.addExecuteRecipe(execute.recipe(), execute.times(), execute.boundInputs(), execute.expectedOutputs());
        }
        return builder.build();
    }

    //endregion

    //region sample resources

    ContentResource oakLogResource() {
        return ContentStack.fromStack(new ItemStack(Items.OAK_LOG));
    }

    ContentResource oakPlanksResource() {
        return ContentStack.fromStack(new ItemStack(Items.OAK_PLANKS));
    }

    InputContent workbenchCatalyst() {
        return workbenchCatalyst;
    }

    //endregion

    //region fake interaction

    record FakeInteractionApi(
        Namespace interactionId,
        boolean automaticPerformerAvailable,
        ExecutionGuidance guidance
    ) implements InteractionApi {
        static FakeInteractionApi automatic(String interactionId) {
            return new FakeInteractionApi(
                Namespace.of("test", interactionId),
                true,
                ExecutionGuidance.simple(Component.literal("Automatic performer available"))
            );
        }

        static FakeInteractionApi guided(String interactionId, String summary) {
            return new FakeInteractionApi(
                Namespace.of("test", interactionId),
                false,
                ExecutionGuidance.simple(Component.literal(summary))
            );
        }

        String guidanceSummary() {
            return guidance.summary().getString();
        }

        @Override
        public InteractionProbeResult probe(RecipeEntry<?> recipe) {
            return new InteractionProbeResult.Supported(interactionId, automaticPerformerAvailable, false);
        }

        @Override
        public InteractionGuideResult guide(RecipeEntry<?> recipe) {
            return new InteractionGuideResult(guidance);
        }

        @Override
        public InteractionPerformResult perform(InteractionContext context) {
            if (automaticPerformerAvailable) {
                return new InteractionPerformResult.Performed();
            }
            return new InteractionPerformResult.ManualRequired(guidance);
        }
    }

    //endregion

    //region fake storage

    private static final class FakeStorageApi implements StorageApi {
        private final ImmutableList<FakeStorageSource> sources;
        private final Map<Object, FakeStorageSource> byIdentifier;

        private FakeStorageApi(FakeStorageSource... sources) {
            this.sources = Lists.immutable.of(sources);
            this.byIdentifier = new ConcurrentHashMap<>();
            this.sources.forEach(source -> byIdentifier.put(source.identifier(), source));
        }

        @Override
        public void addStorage(StorageSource source) {
            throw new UnsupportedOperationException("Execution translator fixtures use a fixed storage snapshot");
        }

        @Override
        public void addStorage(TagKey<StorageSource> tagKey, StorageSource... sources) {
            throw new UnsupportedOperationException("Execution translator fixtures use a fixed storage snapshot");
        }

        @Override
        public @UnmodifiableView RichIterable<? extends StorageSource> storage() {
            return sources;
        }

        @Override
        public @UnmodifiableView RichIterable<? extends StorageSource> getTaggedStorage(TagKey<StorageSource> tagKey) {
            return Lists.immutable.empty();
        }

        @Override
        public @Nullable StorageSource findStorage(Object identifier) {
            return byIdentifier.get(identifier);
        }

        @Override
        public <T extends StorageSource> @Nullable StorageSourceRenderer<T> findRenderer(StorageSourceType<T> type) {
            return null;
        }
    }

    private record FakeStorageSource(
        String id,
        ImmutableList<? extends ContentVariant> contents,
        boolean canProvide,
        boolean canReceive
    ) implements StorageSource {
        private static final StorageSourceType<FakeStorageSource> type =
            new StorageSourceType<>(Namespace.of("test", "execution_source"), (Codec<FakeStorageSource>) null);

        @Override
        public StorageSourceType<?> type() {
            return type;
        }

        @Override
        public Object identifier() {
            return id;
        }
    }

    //endregion

    //region fake recipes

    private record FakeRecipe(
        String id,
        Collection<InputContent> inputs,
        Collection<OutputContent> outputs
    ) {}

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
        public boolean sameRecipe(FakeRecipe r1, FakeRecipe r2) {
            return Objects.equals(r1.id(), r2.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext context, FakeRecipe recipe) {
            register(recipe);
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this, recipe), recipe.inputs(), recipe.outputs()));
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            return Lists.immutable.ofAll(byId.values());
        }
    }

    //endregion
}
