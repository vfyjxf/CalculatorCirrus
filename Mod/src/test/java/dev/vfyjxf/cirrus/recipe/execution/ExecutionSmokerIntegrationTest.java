package dev.vfyjxf.cirrus.recipe.execution;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionCapability;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionObservedStockPolicy;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionInteraction;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionPlanningSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionGuideResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.routing.RecipeGraph;
import dev.vfyjxf.cirrus.api.recipe.routing.Stock;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.PlanAction;
import dev.vfyjxf.cirrus.api.recipe.routing.plan.RoutingPlanDebugText;
import dev.vfyjxf.cirrus.api.recipe.routing.rule.DefaultRuleProfiles;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingBudget;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingOutcome;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingRequest;
import dev.vfyjxf.cirrus.api.recipe.routing.service.RoutingService;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageAccessibility;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageApi;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSource;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceRenderer;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceType;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageTransferRequest;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageTransferResult;
import dev.vfyjxf.cirrus.api.recipe.system.VanillaRecipeSystem;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.CraftingTableExecutionDriver;
import dev.vfyjxf.cirrus.recipe.system.CraftingRecipeSystem;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
@Disabled("Temporarily ignored: this integration test is currently coupled to global routing/database settings.")
class ExecutionSmokerIntegrationTest {

    @SuppressWarnings("unchecked")
    @Test
    void smokerExecutionPipelineUsesOnlyBaseMaterialsAcrossPlanningSnapshotDriverAndRuntime(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        var craftingSystem = (VanillaRecipeSystem<CraftingRecipe>) recipeApi.getSystem(CraftingRecipeSystem.systemId);

        RecipeEntry<?> smokerRecipe = resolveSmokerRecipe(craftingSystem);
        assertNotNull(smokerRecipe);

        RecipeGraph graph = RecipeGraph.buildGraph(
            craftingSystem.allRecipes()
                .select(recipe -> !(recipe.value() instanceof CustomRecipe))
                .collect(recipe -> RecipeEntry.of(craftingSystem, recipe))
        );
        RoutingService routingService = new RoutingService(graph);

        Stock stock = smokerBaseStock();
        RoutingRequest request = RoutingRequest.builder()
            .targetRecipe(smokerRecipe, 16)
            .ruleProfileId(DefaultRuleProfiles.standardExecutionId)
            .stock(stock.copy())
            .budget(RoutingBudget.assuredDefault())
            .build();

        RoutingOutcome outcome = routingService.route(request);
		String describe = RoutingPlanDebugText.describe(outcome.plan());
		assertNotNull(outcome.plan());
        assertTrue(outcome.plan().actions().noneSatisfy(action ->
            action instanceof PlanAction.ConsumeStock consume
                && consume.resource().matches(ContentStack.fromStack(new ItemStack(Items.FURNACE)), MatchStrategy.identityAndComponent)
        ));
        assertTrue(outcome.plan().actions().noneSatisfy(action ->
            action instanceof PlanAction.PendingInput consume
                && consume.input().satisfy(ContentStack.fromStack(new ItemStack(Items.FURNACE)))
        ));
        assertTrue(outcome.plan().actions().anySatisfy(action ->
            action instanceof PlanAction.ConsumeStock consume
                && consume.resource().matches(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE)), MatchStrategy.identityAndComponent)
                && consume.amount() == 128L
        ));
        assertTrue(
            outcome.plan().actions().anySatisfy(action ->
                action instanceof PlanAction.ConsumeStock consume
                    && consume.resource().matches(ContentStack.fromStack(new ItemStack(Items.OAK_LOG)), MatchStrategy.identityAndComponent)
                    && consume.amount() == 64L
            )
                || outcome.plan().actions().anySatisfy(action ->
                    action instanceof PlanAction.PendingInput consume
                        && consume.input() instanceof TagStackContent<?> tag
                        && "minecraft:logs".equals(tag.tagKey().location().toString())
                        && consume.amount() == 64L
                )
        );

        ExecutionInteraction interaction = new ExecutionInteraction(
            Namespace.ofMc("crafting_table"),
            new SnapshotInteractionApi(Namespace.ofMc("crafting_table"), stock.copy(), 301L)
        );
        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(smokerRecipe, 16);
        InteractionExecutionPlan executionPlan = ExecutionTargetRecipeState.buildPlan(selection, interaction);

        assertFalse(executionPlan.isEmpty());
        assertEquals(2, executionPlan.steps().selectInstancesOf(InteractionStep.WithdrawResourceStep.class).size());
        assertNotNull(findWithdrawForItem(executionPlan, Items.COBBLESTONE));
        assertTrue(
            findWithdrawForItem(executionPlan, Items.OAK_LOG) != null
                || findWithdrawForTag(executionPlan, "minecraft:logs") != null
        );
        assertNull(findWithdrawForItem(executionPlan, Items.FURNACE));

        CraftingTableExecutionDriver driver = new CraftingTableExecutionDriver();
        driver.initialize(startSession(executionPlan));
        ExecutionDriverSnapshot snapshot = driver.snapshot();

        assertEquals(2, snapshot.materials().size());
        assertTrue(snapshot.materials().anySatisfy(requirement ->
            requirement.material().satisfy(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE)))
                && requirement.required() == 128L
        ));
        assertTrue(snapshot.materials().anySatisfy(requirement ->
            requirement.required() == 64L
                && (
                requirement.material() instanceof TagStackContent<?>
                    && "#minecraft:logs".equals(ExecutionOverlayContentHelper.label(requirement.material()))
                    || requirement.material().satisfy(ContentStack.fromStack(new ItemStack(Items.OAK_LOG)))
            )
        ));
        assertTrue(snapshot.materials().noneSatisfy(requirement ->
            requirement.material().satisfy(ContentStack.fromStack(new ItemStack(Items.FURNACE)))
        ));

        MutableStockStorageApi storageApi = new MutableStockStorageApi(stock.copy());
        RecordingInteractionApi runtimeInteraction = new RecordingInteractionApi();
        ExecutionRuntime runtime = new ExecutionRuntime(storageApi, runtimeInteraction);
        ExecutionSession session = ExecutionSession.start(executionPlan, flowRoot(executionPlan));

        int safety = 0;
        while (!session.completed() && !session.paused()) {
            if (safety++ > 256) {
                throw new AssertionError("runtime did not complete smoker execution plan in bounded steps");
            }
            session = runtime.advance(session);
        }

        assertFalse(session.paused());
        assertTrue(session.completed());
        assertEquals(3, runtimeInteraction.contexts().size());
        assertEquals(16, sumRecipeTimes(runtimeInteraction.contexts(), Items.FURNACE));
        assertEquals(16, sumRecipeTimes(runtimeInteraction.contexts(), Items.SMOKER));
        assertEquals(128L, sumInputAmount(runtimeInteraction.contexts(), Items.COBBLESTONE));
        assertEquals(16L, sumInputAmount(runtimeInteraction.contexts(), Items.FURNACE));
        assertEquals(64L, sumTagInputAmount(runtimeInteraction.contexts(), "minecraft:logs"));
        assertEquals(0L, storageApi.count(Items.COBBLESTONE));
        assertEquals(0L, storageApi.countTag(ItemTags.LOGS));
        assertEquals(16L, storageApi.count(Items.SMOKER));
    }

    @SuppressWarnings("unchecked")
    @Test
    void smokerExecutionPlanSplitsLargeCraftBatchesToGridSafeInputAmounts(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        var craftingSystem = (VanillaRecipeSystem<CraftingRecipe>) recipeApi.getSystem(CraftingRecipeSystem.systemId);
        RecipeEntry<?> smokerRecipe = resolveSmokerRecipe(craftingSystem);
        assertNotNull(smokerRecipe);

        ExecutionInteraction interaction = new ExecutionInteraction(
            Namespace.ofMc("crafting_table"),
            new SnapshotInteractionApi(Namespace.ofMc("crafting_table"), smokerBaseStock(), 777L)
        );
        InteractionExecutionPlan executionPlan = ExecutionTargetRecipeState.buildPlan(
            ExecutionTargetRecipeState.set(smokerRecipe, 16),
            interaction
        );

        assertTrue(executionPlan.steps().selectInstancesOf(InteractionStep.PrepareInputsStep.class).allSatisfy(step ->
            step.inputs().allSatisfy(input -> input.amount() <= 64L)
        ));
        assertTrue(executionPlan.steps().selectInstancesOf(InteractionStep.ExecuteRecipeStep.class).allSatisfy(step ->
            step.inputs().allSatisfy(input -> input.amount() <= 64L)
        ));
    }

    @SuppressWarnings("unchecked")
    @Test
    void smokerRetranslationUsesCurrentIntermediateInventoryAndSkipsCompletedFurnaceWork(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        var craftingSystem = (VanillaRecipeSystem<CraftingRecipe>) recipeApi.getSystem(CraftingRecipeSystem.systemId);
        RecipeEntry<?> smokerRecipe = resolveSmokerRecipe(craftingSystem);
        assertNotNull(smokerRecipe);

        Stock stock = smokerBaseStock();
        stock.add(ContentStack.fromStack(new ItemStack(Items.FURNACE, 8)));

        ExecutionInteraction interaction = new ExecutionInteraction(
            Namespace.ofMc("crafting_table"),
            new SnapshotInteractionApi(Namespace.ofMc("crafting_table"), stock.copy(), 991L)
        );
        InteractionExecutionPlan executionPlan = ExecutionTargetRecipeState.buildPlan(
            ExecutionTargetRecipeState.set(smokerRecipe, 16),
            interaction
        );

        var executes = executionPlan.steps().selectInstancesOf(InteractionStep.ExecuteRecipeStep.class);
        var furnaceExecutes = executes.select(step ->
            step.expectedOutputs().anySatisfy(output ->
                output.resource().matches(ContentStack.fromStack(new ItemStack(Items.FURNACE)), MatchStrategy.identityAndComponent)
            )
        );
        var smokerExecutes = executes.select(step ->
            step.expectedOutputs().anySatisfy(output ->
                output.resource().matches(ContentStack.fromStack(new ItemStack(Items.SMOKER)), MatchStrategy.identityAndComponent)
            )
        );

        assertEquals(1, furnaceExecutes.size(), "retranslation should skip already available furnace work");
        assertEquals(8, furnaceExecutes.getFirst().times());
        assertEquals(1, smokerExecutes.size());
        assertEquals(16, smokerExecutes.getFirst().times());
    }

    @SuppressWarnings("unchecked")
    @Test
    void smokerSuffixRetranslationConsumesObservedIntermediateStockByDefault(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        var craftingSystem = (VanillaRecipeSystem<CraftingRecipe>) recipeApi.getSystem(CraftingRecipeSystem.systemId);
        RecipeEntry<?> smokerRecipe = resolveSmokerRecipe(craftingSystem);
        assertNotNull(smokerRecipe);

        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(smokerRecipe, 16);
        Stock baseline = smokerBaseStock();
        ExecutionInteraction baselineInteraction = new ExecutionInteraction(
            Namespace.ofMc("crafting_table"),
            new SnapshotInteractionApi(Namespace.ofMc("crafting_table"), baseline.copy(), 1201L)
        );
        ExecutionSession session = ExecutionTargetRecipeState.buildSession(selection, baselineInteraction);

        Stock observed = smokerBaseStock();
        observed.add(ContentStack.fromStack(new ItemStack(Items.FURNACE, 8)));
        ExecutionInteraction observedInteraction = new ExecutionInteraction(
            Namespace.ofMc("crafting_table"),
            new SnapshotInteractionApi(Namespace.ofMc("crafting_table"), observed.copy(), 1202L)
        );
        ExecutionSession rebuilt = ExecutionTargetRecipeState.rebuildSession(selection, observedInteraction, session);

        var executes = rebuilt.plan().steps().selectInstancesOf(InteractionStep.ExecuteRecipeStep.class);
        var furnaceExecutes = executes.select(step ->
            step.expectedOutputs().anySatisfy(output ->
                output.resource().matches(ContentStack.fromStack(new ItemStack(Items.FURNACE)), MatchStrategy.identityAndComponent)
            )
        );
        assertEquals(1, furnaceExecutes.size());
        assertEquals(8, furnaceExecutes.getFirst().times());
    }

    @SuppressWarnings("unchecked")
    @Test
    void smokerSuffixRetranslationCanPreservePlannedStockAndIgnoreUnexpectedIntermediateStock(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        var craftingSystem = (VanillaRecipeSystem<CraftingRecipe>) recipeApi.getSystem(CraftingRecipeSystem.systemId);
        RecipeEntry<?> smokerRecipe = resolveSmokerRecipe(craftingSystem);
        assertNotNull(smokerRecipe);

        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(smokerRecipe, 16);
        Stock baseline = smokerBaseStock();
        ExecutionInteraction baselineInteraction = new ExecutionInteraction(
            Namespace.ofMc("crafting_table"),
            new SnapshotInteractionApi(Namespace.ofMc("crafting_table"), baseline.copy(), 1211L)
        );
        ExecutionSession session = ExecutionTargetRecipeState.buildSession(selection, baselineInteraction);
        session = new ExecutionSession(
            session.plan(),
            session.flowRoot(),
            session.nextStepIndex(),
            session.completedSteps(),
            session.pausedSteps(),
            session.runningStep(),
            session.pauseReason(),
            session.reTranslationHint(),
            session.flowViewModel(),
            session.baselinePlanningSnapshot(),
            ExecutionObservedStockPolicy.preservePlannedStock
        );

        Stock observed = smokerBaseStock();
        observed.add(ContentStack.fromStack(new ItemStack(Items.FURNACE, 8)));
        ExecutionInteraction observedInteraction = new ExecutionInteraction(
            Namespace.ofMc("crafting_table"),
            new SnapshotInteractionApi(Namespace.ofMc("crafting_table"), observed.copy(), 1212L)
        );
        ExecutionSession rebuilt = ExecutionTargetRecipeState.rebuildSession(selection, observedInteraction, session);

        var executes = rebuilt.plan().steps().selectInstancesOf(InteractionStep.ExecuteRecipeStep.class);
        var furnaceExecutes = executes.select(step ->
            step.expectedOutputs().anySatisfy(output ->
                output.resource().matches(ContentStack.fromStack(new ItemStack(Items.FURNACE)), MatchStrategy.identityAndComponent)
            )
        );
        assertEquals(2, furnaceExecutes.size());
        assertEquals(16, furnaceExecutes.sumOfInt(InteractionStep.ExecuteRecipeStep::times));
    }

    @SuppressWarnings("unchecked")
    @Test
    void smokerSuffixRetranslationUsesRemainingTargetTimesAfterObservedTargetProgress(MinecraftServer server) {
        RecipeApi recipeApi = RecipeApi.instance();
        var craftingSystem = (VanillaRecipeSystem<CraftingRecipe>) recipeApi.getSystem(CraftingRecipeSystem.systemId);
        RecipeEntry<?> smokerRecipe = resolveSmokerRecipe(craftingSystem);
        assertNotNull(smokerRecipe);

        ExecutionTargetRecipeState.Selection selection = ExecutionTargetRecipeState.set(smokerRecipe, 16);
        ExecutionInteraction baselineInteraction = new ExecutionInteraction(
            Namespace.ofMc("crafting_table"),
            new SnapshotInteractionApi(Namespace.ofMc("crafting_table"), smokerBaseStock(), 1221L)
        );
        ExecutionSession session = ExecutionTargetRecipeState.buildSession(selection, baselineInteraction);

        Stock observed = smokerBaseStock();
        observed.add(ContentStack.fromStack(new ItemStack(Items.SMOKER, 8)));
        ExecutionInteraction observedInteraction = new ExecutionInteraction(
            Namespace.ofMc("crafting_table"),
            new SnapshotInteractionApi(Namespace.ofMc("crafting_table"), observed, 1222L)
        );
        ExecutionSession rebuilt = ExecutionTargetRecipeState.rebuildSession(selection, observedInteraction, session);

        assertEquals(8, rebuilt.plan().targetTimes());
    }

    private static ExecutionSession startSession(InteractionExecutionPlan plan) {
        return ExecutionSession.start(
            plan,
            ExecutionFlowNode.pending("root", Component.literal("root"), Lists.immutable.empty(), null, Lists.immutable.empty())
        );
    }

    private static ExecutionFlowNode flowRoot(InteractionExecutionPlan plan) {
        return ExecutionFlowNode.pending(
            "root",
            Component.literal("root"),
            Lists.immutable.empty(),
            null,
            plan.steps().collectWith((step, index) ->
                ExecutionFlowNode.pending("step-" + index, Component.literal(step.getClass().getSimpleName()), Lists.immutable.of(step), null, Lists.immutable.empty()),
                0
            )
        );
    }

    @SuppressWarnings("unchecked")
    private static RecipeEntry<?> resolveSmokerRecipe(VanillaRecipeSystem<CraftingRecipe> craftingSystem) {
        var smokerRecipes = craftingSystem.createLookup()
            .withOutput(ContentStack.fromStack(new ItemStack(Items.SMOKER)))
            .get()
            .select(recipe -> !(recipe.value() instanceof CustomRecipe))
            .collect(recipe -> RecipeEntry.of(craftingSystem, recipe));
        assertFalse(smokerRecipes.isEmpty(), "expected at least one smoker recipe");
        return smokerRecipes.getFirst();
    }

    private static Stock smokerBaseStock() {
        Stock stock = Stock.empty();
        stock.add(ContentStack.fromStack(new ItemStack(Items.OAK_LOG, 64)));
        stock.add(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE, 128)));
        return stock;
    }

    private static @Nullable InteractionStep.WithdrawResourceStep findWithdrawForItem(InteractionExecutionPlan plan, Item item) {
        return plan.steps()
            .selectInstancesOf(InteractionStep.WithdrawResourceStep.class)
            .detect(step -> switch (step.target()) {
                case InteractionStep.WithdrawTarget.Resource resource ->
                    resource.resource().matches(ContentStack.fromStack(new ItemStack(item)), MatchStrategy.identityAndComponent);
                case InteractionStep.WithdrawTarget.Input input ->
                    input.input().satisfy(ContentStack.fromStack(new ItemStack(item)));
            });
    }

    private static @Nullable InteractionStep.WithdrawResourceStep findWithdrawForTag(InteractionExecutionPlan plan, String tagId) {
        return plan.steps()
            .selectInstancesOf(InteractionStep.WithdrawResourceStep.class)
            .detect(step -> step.target() instanceof InteractionStep.WithdrawTarget.Input input
                && input.input() instanceof TagStackContent<?> tag
                && tagId.equals(tag.tagKey().location().toString()));
    }

    private static int sumRecipeTimes(ImmutableList<InteractionContext> contexts, Item outputItem) {
        int total = 0;
        for (InteractionContext context : contexts) {
            boolean matches = context.expectedOutputs().anySatisfy(output ->
                output.resource().matches(ContentStack.fromStack(new ItemStack(outputItem)), MatchStrategy.identityAndComponent)
            );
            if (matches) {
                total += context.times();
            }
        }
        return total;
    }

    private static long sumInputAmount(ImmutableList<InteractionContext> contexts, Item inputItem) {
        long total = 0L;
        for (InteractionContext context : contexts) {
            for (PlanAction.InputBinding input : context.inputs()) {
                if (input.input().satisfy(ContentStack.fromStack(new ItemStack(inputItem)))) {
                    total += input.amount();
                }
            }
        }
        return total;
    }

    private static long sumTagInputAmount(ImmutableList<InteractionContext> contexts, String tagId) {
        long total = 0L;
        for (InteractionContext context : contexts) {
            for (PlanAction.InputBinding input : context.inputs()) {
                if (input.input() instanceof TagStackContent<?> tag && tagId.equals(tag.tagKey().location().toString())) {
                    total += input.amount();
                }
            }
        }
        return total;
    }

    private record SnapshotInteractionApi(
        Namespace interactionId,
        Stock stock,
        long fingerprint
    ) implements InteractionApi {
        @Override
        public InteractionProbeResult probe(RecipeEntry<?> recipe) {
            return new InteractionProbeResult.Supported(interactionId, true, false);
        }

        @Override
        public InteractionGuideResult guide(RecipeEntry<?> recipe) {
            return new InteractionGuideResult(ExecutionGuidance.simple(Component.literal("snapshot")));
        }

        @Override
        public InteractionPerformResult perform(InteractionContext context) {
            return new InteractionPerformResult.Performed();
        }

        @Override
        public int maxExecuteBatchSize(InteractionContext context) {
            return dev.vfyjxf.cirrus.recipe.execution.interaction.CraftingTableLikeInteraction
                .automatic(interactionId)
                .maxExecuteBatchSize(context);
        }

        @Override
        public ExecutionPlanningSnapshot planningSnapshot() {
            return new ExecutionPlanningSnapshot(stock, fingerprint);
        }
    }

    private static final class RecordingInteractionApi implements InteractionApi {
        private final MutableList<InteractionContext> contexts = Lists.mutable.empty();

        @Override
        public InteractionProbeResult probe(RecipeEntry<?> recipe) {
            return new InteractionProbeResult.Supported(Namespace.ofMc("crafting_table"), true, false);
        }

        @Override
        public InteractionGuideResult guide(RecipeEntry<?> recipe) {
            return new InteractionGuideResult(ExecutionGuidance.simple(Component.literal("simulate")));
        }

        @Override
        public InteractionPerformResult perform(InteractionContext context) {
            contexts.add(context);
            return new InteractionPerformResult.Performed();
        }

        ImmutableList<InteractionContext> contexts() {
            return contexts.toImmutable();
        }
    }

    private static final class MutableStockStorageApi implements StorageApi {
        private final MutableStorageSource source;

        private MutableStockStorageApi(Stock stock) {
            this.source = new MutableStorageSource("player", stock);
        }

        long count(Item item) {
            return source.count(ContentStack.fromStack(new ItemStack(item)));
        }

        long countTag(TagKey<Item> tagKey) {
            return source.countTag(tagKey);
        }

        @Override
        public void addStorage(StorageSource source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void addStorage(TagKey<StorageSource> tagKey, StorageSource... sources) {
            throw new UnsupportedOperationException();
        }

        @Override
        public @UnmodifiableView RichIterable<? extends StorageSource> storage() {
            return Lists.immutable.of(source);
        }

        @Override
        public @UnmodifiableView RichIterable<? extends StorageSource> getTaggedStorage(TagKey<StorageSource> tagKey) {
            return Lists.immutable.empty();
        }

        @Override
        public @Nullable StorageSource findStorage(Object identifier) {
            return source.identifier().equals(identifier) ? source : null;
        }

        @Override
        public <T extends StorageSource> @Nullable StorageSourceRenderer<T> findRenderer(StorageSourceType<T> type) {
            return null;
        }
    }

    private static final class MutableStorageSource implements StorageSource {
        private static final StorageSourceType<MutableStorageSource> type =
            new StorageSourceType<>(Namespace.of("test", "mutable_runtime_source"), (Codec<MutableStorageSource>) null);

        private final String id;
        private final LinkedHashMap<ContentResource, Long> buckets = new LinkedHashMap<>();

        private MutableStorageSource(String id, Stock stock) {
            this.id = id;
            for (ContentStack<?, ?> stack : stock.getStacks()) {
                long amount = stock.countMatching(stack);
                if (amount <= 0L) {
                    continue;
                }
                buckets.put(normalize(stack), amount);
            }
        }

        @Override
        public StorageSourceType<?> type() {
            return type;
        }

        @Override
        public Object identifier() {
            return id;
        }

        @Override
        public RichIterable<? extends ContentVariant> contents() {
            return Lists.immutable.ofAll(buckets.keySet());
        }

        @Override
        public boolean canProvide() {
            return true;
        }

        @Override
        public boolean canReceive() {
            return true;
        }

        @Override
        public StorageAccessibility accessibility() {
            return StorageAccessibility.accessible;
        }

        @Override
        public StorageTransferResult simulateExtract(StorageTransferRequest request) {
            if (!accessibility().accessible()) {
                return StorageTransferResult.none(this, request, accessibility().guidance());
            }
            long available = 0L;
            for (Map.Entry<ContentResource, Long> entry : buckets.entrySet()) {
                if (!request.matches(entry.getKey())) {
                    continue;
                }
                available += entry.getValue();
            }
            return StorageTransferResult.transferred(this, request, Math.min(available, request.amount()));
        }

        @Override
        public StorageTransferResult extract(StorageTransferRequest request) {
            long remaining = request.amount();
            for (Map.Entry<ContentResource, Long> entry : buckets.entrySet()) {
                if (remaining <= 0L) {
                    break;
                }
                if (!request.matches(entry.getKey())) {
                    continue;
                }
                long consumed = Math.min(entry.getValue(), remaining);
                if (consumed <= 0L) {
                    continue;
                }
                entry.setValue(entry.getValue() - consumed);
                remaining -= consumed;
            }
            return StorageTransferResult.transferred(this, request, request.amount() - remaining);
        }

        @Override
        public StorageTransferResult simulateInsert(StorageTransferRequest request) {
            return StorageSource.super.simulateInsert(request);
        }

        @Override
        public StorageTransferResult insert(StorageTransferRequest request) {
            StorageTransferResult simulated = simulateInsert(request);
            if (!simulated.completed()) {
                return simulated;
            }
            ContentResource resource = normalize(request.exactResource());
            buckets.merge(resource, request.amount(), Long::sum);
            return StorageTransferResult.transferred(this, request, request.amount());
        }

        long count(ContentResource target) {
            long total = 0L;
            ContentResource normalized = normalize(target);
            for (Map.Entry<ContentResource, Long> entry : buckets.entrySet()) {
                if (entry.getKey().matches(normalized, MatchStrategy.identityAndComponent)) {
                    total += entry.getValue();
                }
            }
            return total;
        }

        long countTag(TagKey<Item> tagKey) {
            long total = 0L;
            for (Map.Entry<ContentResource, Long> entry : buckets.entrySet()) {
                if (!(entry.getKey() instanceof ContentStack<?, ?> stack) || !stack.isItem()) {
                    continue;
                }
                ItemStack itemStack = stack.<Item, ItemStack>cast().value();
                if (!itemStack.isEmpty() && itemStack.is(tagKey)) {
                    total += entry.getValue();
                }
            }
            return total;
        }

        private static ContentResource normalize(ContentResource resource) {
            return resource instanceof ContentStack<?, ?> stack ? stack.normalize() : resource.normalize();
        }
    }
}
