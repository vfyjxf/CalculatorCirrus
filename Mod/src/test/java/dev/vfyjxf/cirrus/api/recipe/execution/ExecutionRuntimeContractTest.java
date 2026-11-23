package dev.vfyjxf.cirrus.api.recipe.execution;

import com.mojang.serialization.Codec;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.content.value.ContentResource;
import dev.vfyjxf.cirrus.api.content.value.ContentValue;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionGuideResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowStatus;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageAccessibility;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageApi;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageGuidance;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSource;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceRenderer;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceType;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageTransferRequest;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageTransferResult;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionRuntime;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.ImmutableList;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnmodifiableView;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExecutionRuntimeContractTest {

    //region advancement

    @Test
    void runtimeAdvancesNormalStepsAndCompletesSession() {
        var plan = new InteractionExecutionPlan(
            recipeEntry("craft_planks"),
            1,
            Lists.immutable.of(
                new InteractionStep.EnsureAccessibleStep(
                    ExecutionSideRequirement.environmentAgnostic,
                    ExecutionCapability.storageAccess,
                    new InteractionStep.AccessTarget.Resource(resource(1L)),
                    1L
                ),
                new InteractionStep.WithdrawResourceStep(
                    ExecutionSideRequirement.client,
                    ExecutionCapability.inventoryTransfer,
                    new InteractionStep.WithdrawTarget.Resource(resource(1L)),
                    1L
                )
            )
        );
        var runtime = new ExecutionRuntime(
            new FakeStorageApi(new FakeStorageSource("provider", Lists.immutable.of(resource(4L)), true, false, StorageAccessibility.accessible)),
            new FakeInteractionApi(true)
        );
        var session = ExecutionSession.start(plan, flowRoot(plan));

        session = runtime.advance(session);
        session = runtime.advance(session);

        assertTrue(session.completed());
        assertEquals(2, session.completedSteps().size());
        assertSame(ExecutionFlowStatus.completed, session.flowViewModel().root().status());
    }

    //endregion

    //region pause classes

    @Test
    void runtimePausesOnMissingCapacity() {
        var step = new InteractionStep.StoreOutputsStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.outputStorage,
            resource(4L),
            4L
        );
        var runtime = new ExecutionRuntime(new FakeStorageApi(), new FakeInteractionApi(true));
        var session = runtime.advance(ExecutionSession.start(new InteractionExecutionPlan(recipeEntry("craft"), 1, Lists.immutable.of(step)), flowRoot(step)));

        assertTrue(session.paused());
        assertEquals(PauseReasons.inventoryCapacityBlocked, session.pauseReason().key());
    }

    @Test
    void runtimePausesOnInaccessibleStorage() {
        var step = new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.inventoryTransfer,
            new InteractionStep.WithdrawTarget.Resource(resource(4L)),
            4L
        );
        var runtime = new ExecutionRuntime(
            new FakeStorageApi(new FakeStorageSource(
                "blocked",
                Lists.immutable.of(resource(4L)),
                true,
                false,
                StorageAccessibility.inaccessible(StorageGuidance.simple(Component.literal("Open storage")))
            )),
            new FakeInteractionApi(true)
        );
        var session = runtime.advance(ExecutionSession.start(new InteractionExecutionPlan(recipeEntry("craft"), 1, Lists.immutable.of(step)), flowRoot(step)));

        assertTrue(session.paused());
        assertEquals(PauseReasons.noAccessibleProvider, session.pauseReason().key());
        assertEquals("Open storage", session.pauseReason().guidance().summary().getString());
    }

    @Test
    void runtimePausesWhenManualInteractionIsRequired() {
        var step = new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.interactionPerform,
            recipeEntry("craft"),
            1,
            Lists.immutable.of(new InputBinding(input(1L), 1L)),
            Lists.immutable.of(new OutputBinding(resource(4L), 4L))
        );
        var runtime = new ExecutionRuntime(new FakeStorageApi(), new FakeInteractionApi(false));
        var session = runtime.advance(ExecutionSession.start(new InteractionExecutionPlan(recipeEntry("craft"), 1, Lists.immutable.of(step)), flowRoot(step)));

        assertTrue(session.paused());
        assertEquals(PauseReasons.awaitingPlayerAction, session.pauseReason().key());
    }

    //endregion

    //region resume and retranslation

    @Test
    void runtimeResumesFromManualPause() {
        var step = new InteractionStep.ManualCheckpointStep(
            ExecutionSideRequirement.manualPlayerAction,
            ExecutionCapability.manualCheckpoint,
            ExecutionGuidance.simple(Component.literal("Press A"))
        );
        var runtime = new ExecutionRuntime(new FakeStorageApi(), new FakeInteractionApi(false));
        var session = runtime.advance(ExecutionSession.start(new InteractionExecutionPlan(recipeEntry("craft"), 1, Lists.immutable.of(step)), flowRoot(step)));

        session = runtime.resume(session);

        assertFalse(session.paused());
        assertTrue(session.completed());
        assertEquals(1, session.completedSteps().size());
    }

    @Test
    void runtimeResumeRetriesPausedStorageStepInsteadOfSkippingIt() {
        MutableStorageApi storageApi = new MutableStorageApi(resource(4L));
        storageApi.setAccessible(false);
        var runtime = new ExecutionRuntime(storageApi, new FakeInteractionApi(true));
        var step = new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.inventoryTransfer,
            new InteractionStep.WithdrawTarget.Resource(resource(4L)),
            4L
        );
        var session = runtime.advance(
            ExecutionSession.start(
                new InteractionExecutionPlan(recipeEntry("craft"), 1, Lists.immutable.of(step)),
                flowRoot(step)
            )
        );

        assertTrue(session.paused());
        assertEquals(4L, storageApi.count(resource(4L)));

        storageApi.setAccessible(true);
        session = runtime.resume(session);

        assertTrue(session.completed());
        assertEquals(0L, storageApi.count(resource(4L)));
    }

    @Test
    void runtimeCanRequestRetranslation() {
        var runtime = new ExecutionRuntime(new FakeStorageApi(), new FakeInteractionApi(true));
        var session = runtime.requestReTranslation(
            ExecutionSession.start(new InteractionExecutionPlan(recipeEntry("craft"), 1, Lists.immutable.empty()), flowRoot()),
            new ReTranslationHint(Component.literal("Storage snapshot changed"))
        );

        assertNotNull(session.reTranslationHint());
        assertEquals("Storage snapshot changed", session.reTranslationHint().summary().getString());
    }

    @Test
    void runtimeExecuteCanRequestRetranslationFromInteractionResult() {
        var step = new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.interactionPerform,
            recipeEntry("craft"),
            8,
            Lists.immutable.of(new InputBinding(input(8L), 8L)),
            Lists.immutable.of(new OutputBinding(resource(8L), 8L))
        );
        var runtime = new ExecutionRuntime(new FakeStorageApi(), new ReTranslatingInteractionApi());
        var session = runtime.advance(
            ExecutionSession.start(
                new InteractionExecutionPlan(recipeEntry("craft"), 1, Lists.immutable.of(step)),
                flowRoot(step)
            )
        );

        assertFalse(session.paused());
        assertEquals(0, session.nextStepIndex());
        assertNotNull(session.reTranslationHint());
        assertEquals("Inventory changed", session.reTranslationHint().summary().getString());
    }

    @Test
    void runtimeWithdrawConsumesRealStockInsteadOfOnlySimulating() {
        MutableStorageApi storageApi = new MutableStorageApi(resource(4L));
        var runtime = new ExecutionRuntime(storageApi, new FakeInteractionApi(true));
        var step = new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.inventoryTransfer,
            new InteractionStep.WithdrawTarget.Resource(resource(4L)),
            4L
        );
        var session = runtime.advance(
            ExecutionSession.start(
                new InteractionExecutionPlan(recipeEntry("craft"), 1, Lists.immutable.of(step)),
                flowRoot(step)
            )
        );

        assertTrue(session.completed());
        assertEquals(0L, storageApi.count(resource(4L)));
    }

    @Test
    void runtimeStoreWritesOutputsIntoStorageInsteadOfOnlySimulating() {
        MutableStorageApi storageApi = new MutableStorageApi();
        var runtime = new ExecutionRuntime(storageApi, new FakeInteractionApi(true));
        var step = new InteractionStep.StoreOutputsStep(
            ExecutionSideRequirement.client,
            ExecutionCapability.outputStorage,
            resource(7L),
            3L
        );
        var session = runtime.advance(
            ExecutionSession.start(
                new InteractionExecutionPlan(recipeEntry("craft"), 1, Lists.immutable.of(step)),
                flowRoot(step)
            )
        );

        assertTrue(session.completed());
        assertEquals(3L, storageApi.count(resource(7L)));
    }

    //endregion

    //region fixtures

    private static ExecutionFlowNode flowRoot(InteractionExecutionPlan plan) {
        return ExecutionFlowNode.pending("root", Component.literal("root"), Lists.immutable.empty(), null,
            plan.steps().collectWith(ExecutionRuntimeContractTest::flowNode, 0));
    }

    private static ExecutionFlowNode flowRoot(InteractionStep step) {
        return ExecutionFlowNode.pending("root", Component.literal("root"), Lists.immutable.empty(), null, Lists.immutable.of(flowNode(step, 0)));
    }

    private static ExecutionFlowNode flowRoot() {
        return ExecutionFlowNode.pending("root", Component.literal("root"), Lists.immutable.empty(), null, Lists.immutable.empty());
    }

    private static ExecutionFlowNode flowNode(InteractionStep step, int index) {
        return ExecutionFlowNode.pending("step-" + index, Component.literal(step.getClass().getSimpleName()), Lists.immutable.of(step), null, Lists.immutable.empty());
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
        public ResolveResult resolveFallback(ResolveContext context, TestRecipe recipe) {
            throw new UnsupportedOperationException();
        }

        @Override
        public RichIterable<TestRecipe> allRecipes() {
            return Lists.immutable.ofAll(byId.values());
        }
    }

    private static final class FakeStorageApi implements StorageApi {
        private final ImmutableList<FakeStorageSource> sources;

        private FakeStorageApi(FakeStorageSource... sources) {
            this.sources = Lists.immutable.of(sources);
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
            return sources;
        }

        @Override
        public @UnmodifiableView RichIterable<? extends StorageSource> getTaggedStorage(TagKey<StorageSource> tagKey) {
            return Lists.immutable.empty();
        }

        @Override
        public @Nullable StorageSource findStorage(Object identifier) {
            return sources.detect(source -> source.identifier().equals(identifier));
        }

        @Override
        public <T extends StorageSource> @Nullable StorageSourceRenderer<T> findRenderer(StorageSourceType<T> type) {
            return null;
        }
    }

    private static final class MutableStorageApi implements StorageApi {
        private final MutableStorageSource source;

        private MutableStorageApi(ContentResource... initialResources) {
            this.source = new MutableStorageSource("mutable", initialResources);
        }

        long count(ContentResource resource) {
            return source.count(resource);
        }

        void setAccessible(boolean accessible) {
            source.setAccessible(accessible);
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

    private record FakeStorageSource(
        String id,
        ImmutableList<? extends ContentVariant> contents,
        boolean provider,
        boolean receiver,
        StorageAccessibility accessibility
    ) implements StorageSource {
        private static final StorageSourceType<FakeStorageSource> type =
            new StorageSourceType<>(Namespace.of("test", "runtime_source"), (Codec<FakeStorageSource>) null);

        @Override
        public StorageSourceType<?> type() {
            return type;
        }

        @Override
        public Object identifier() {
            return id;
        }

        @Override
        public boolean canProvide() {
            return provider;
        }

        @Override
        public boolean canReceive() {
            return receiver;
        }
    }

    private static final class MutableStorageSource implements StorageSource {
        private static final StorageSourceType<MutableStorageSource> type =
            new StorageSourceType<>(Namespace.of("test", "mutable_runtime_source"), (Codec<MutableStorageSource>) null);

        private final String id;
        private final LinkedHashMap<ContentResource, Long> buckets = new LinkedHashMap<>();
        private boolean accessible = true;

        private MutableStorageSource(String id, ContentResource... initialResources) {
            this.id = id;
            for (ContentResource resource : initialResources) {
                buckets.merge(normalize(resource), resource.amount(), Long::sum);
            }
        }

        long count(ContentResource resource) {
            ContentResource normalized = normalize(resource);
            long total = 0L;
            for (Map.Entry<ContentResource, Long> entry : buckets.entrySet()) {
                if (entry.getKey().matches(normalized, MatchStrategy.identityAndComponent)) {
                    total += entry.getValue();
                }
            }
            return total;
        }

        void setAccessible(boolean accessible) {
            this.accessible = accessible;
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
            return accessible
                ? StorageAccessibility.accessible
                : StorageAccessibility.inaccessible(StorageGuidance.simple(Component.literal("Open storage")));
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
            buckets.merge(normalize(request.exactResource()), request.amount(), Long::sum);
            return StorageTransferResult.transferred(this, request, request.amount());
        }
    }

    private static ContentResource normalize(ContentResource resource) {
        return resource.normalize();
    }

    private record FakeInteractionApi(boolean automatic) implements InteractionApi {
        @Override
        public InteractionProbeResult probe(RecipeEntry<?> recipe) {
            return new InteractionProbeResult.Supported(Namespace.of("test", "crafting_table"), automatic, false);
        }

        @Override
        public InteractionGuideResult guide(RecipeEntry<?> recipe) {
            return new InteractionGuideResult(ExecutionGuidance.simple(Component.literal("Open crafting table")));
        }

        @Override
        public InteractionPerformResult perform(InteractionContext context) {
            if (automatic) {
                return new InteractionPerformResult.Performed();
            }
            return new InteractionPerformResult.ManualRequired(ExecutionGuidance.simple(Component.literal("Press A")));
        }
    }

    private static final class ReTranslatingInteractionApi implements InteractionApi {
        @Override
        public InteractionProbeResult probe(RecipeEntry<?> recipe) {
            return new InteractionProbeResult.Supported(Namespace.of("test", "crafting_table"), true, false);
        }

        @Override
        public InteractionGuideResult guide(RecipeEntry<?> recipe) {
            return new InteractionGuideResult(ExecutionGuidance.simple(Component.literal("retranslate")));
        }

        @Override
        public InteractionPerformResult perform(InteractionContext context) {
            return new InteractionPerformResult.ReTranslationRequired(new ReTranslationHint(Component.literal("Inventory changed")));
        }
    }

    //endregion
}
