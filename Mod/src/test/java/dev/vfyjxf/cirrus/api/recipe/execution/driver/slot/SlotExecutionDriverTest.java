package dev.vfyjxf.cirrus.api.recipe.execution.driver.slot;

import dev.vfyjxf.cirrus.api.recipe.execution.OutputBinding;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionCapability;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSession;
import dev.vfyjxf.cirrus.api.recipe.execution.ExecutionSideRequirement;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionStep;
import dev.vfyjxf.cirrus.api.recipe.execution.PauseReason;
import dev.vfyjxf.cirrus.api.recipe.execution.core.PauseReasons;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionGuideResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.ExecutionPlanningSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.ExecutionOverlayTraceSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.util.ExecutionGuidance;
import dev.vfyjxf.cirrus.api.recipe.execution.view.ExecutionFlowNode;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.SlotExecutionDriver;
import dev.vfyjxf.cirrus.recipe.execution.driver.slot.SlotFlow;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.server.MinecraftServer;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.neoforged.testframework.junit.EphemeralTestServerProvider;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(EphemeralTestServerProvider.class)
class SlotExecutionDriverTest {

    private static final Namespace testInteractionId = Namespace.of("test", "slot_driver");

    @Test
    void advancePausesWhenInteractionIsClosed() {
        var step = openInteractionStep();
        var access = new FakeAccess(false);
        var flow = new FakeFlow(access);
        var driver = SlotExecutionDriver.standard(new SlotExecutionDriver.Profile(testInteractionId, new FakeLayout(), access, null, null, flow));
        driver.initialize(startSession(step));

        driver.advanceSteps(1);

        var snapshot = driver.snapshot();
        assertNotNull(snapshot.telemetry());
        assertTrue(snapshot.telemetry().paused());
        assertFalse(snapshot.telemetry().completed());
        assertEquals("paused", snapshot.telemetry().status());
    }

    @Test
    void snapshotAlwaysProvidesTelemetryEvenWithoutSession() {
        var access = new FakeAccess(true);
        var driver = SlotExecutionDriver.standard(new SlotExecutionDriver.Profile(testInteractionId, new FakeLayout(), access, null, null, new FakeFlow(access)));

        ExecutionDriverSnapshot snapshot = driver.snapshot();

        assertNotNull(snapshot.telemetry());
        assertEquals("pending", snapshot.telemetry().status());
        assertFalse(snapshot.telemetry().paused());
    }

    @Test
    void slotHandlerHandlesCoreStepTypes() {
        var access = new FakeAccess(true);
        var flow = new FakeFlow(access);
        var driver = SlotExecutionDriver.standard(new SlotExecutionDriver.Profile(testInteractionId, new FakeLayout(), access, null, null, flow));
        driver.initialize(startSession(openInteractionStep(), prepareInputsStep(), executeRecipeStep(), collectOutputsStep()));

        driver.advanceSteps(4);

        assertTrue(flow.openCalled);
        assertTrue(flow.prepareCalled);
        assertTrue(flow.executeCalled);
        assertTrue(flow.collectCalled);
        assertTrue(driver.snapshot().telemetry().completed());
    }

    @Test
    void newSlotApiTypesAreAccessible() {
        SlotLayout.Role role = SlotLayout.Role.input;
        SlotLayout.Key key = new SlotLayout.Key(Namespace.of("test", "slot_key"), 0);
        SlotLayout.Reference ref = new SlotLayout.Reference.Index(0);
        SlotLayout.Index index = new SlotLayout.Index() {
            @Override
            public SlotLayout.Reference get(SlotLayout.Key requested) {
                return ref;
            }

            @Override
            public Set<SlotLayout.Key> keys() {
                return Set.of(key);
            }

            @Override
            public Map<SlotLayout.Key, SlotLayout.Reference> mapping() {
                return Map.of(key, ref);
            }
        };
        SlotLayout layout = requested -> index;
        SlotAccess access = new FakeAccess(true);
        SlotFlow flow = new FakeFlow((FakeAccess) access);
        SlotExecutionDriver.Profile profile = SlotExecutionDriver.profile(testInteractionId, layout, access);

        assertNotNull(role);
        assertNotNull(layout);
        assertNotNull(access);
        assertNotNull(flow);
        assertNotNull(profile);
    }

    @Test
    void standardDriverFallsBackToDefaultFlowWhenFlowIsNull() {
        var access = new FakeAccess(false);
        var driver = SlotExecutionDriver.standard(SlotExecutionDriver.profile(testInteractionId, new FakeLayout(), access));
        driver.initialize(startSession(openInteractionStep()));

        driver.advanceSteps(1);

        assertTrue(driver.snapshot().telemetry().paused());
    }

    @Test
    void interactionPlanningSnapshotAggregatesInventoryInputAndOutputRoles(MinecraftServer server) {
        SlotLayout layout = SlotLayout.builder()
            .add(
                SlotLayout.Role.input,
                new SlotLayout.Key(Namespace.of("test", "input"), 0),
                new SlotLayout.Reference.Index(0)
            )
            .add(
                SlotLayout.Role.inventory,
                new SlotLayout.Key(Namespace.of("test", "inventory"), 0),
                new SlotLayout.Reference.Index(1)
            )
            .add(
                SlotLayout.Role.output,
                new SlotLayout.Key(Namespace.of("test", "output"), 0),
                new SlotLayout.Reference.Index(2)
            )
            .build();
        TestInteractionAccess access = new TestInteractionAccess(true);
        access.put(new SlotLayout.Reference.Index(0), stack(Items.OAK_LOG, 2));
        access.put(new SlotLayout.Reference.Index(1), stack(Items.COBBLESTONE, 5));
        access.put(new SlotLayout.Reference.Index(2), stack(Items.STICK, 3));
        SlotExecutionDriver driver = SlotExecutionDriver.standard(new SlotExecutionDriver.Profile(testInteractionId, layout, access, access, null, null));

        ExecutionPlanningSnapshot snapshot = driver.interaction().planningSnapshot();

        assertEquals(2L, snapshot.stock().countMatching(ContentStack.fromStack(stack(Items.OAK_LOG, 1))));
        assertEquals(5L, snapshot.stock().countMatching(ContentStack.fromStack(stack(Items.COBBLESTONE, 1))));
        assertEquals(3L, snapshot.stock().countMatching(ContentStack.fromStack(stack(Items.STICK, 1))));
        assertTrue(snapshot.fingerprint() != 0L);
    }

    @Test
    void interactionPlanningViewForwardsMaxExecuteBatchSize() {
        AtomicInteger observedTimes = new AtomicInteger();
        InteractionApi interactionApi = new InteractionApi() {
            @Override
            public InteractionProbeResult probe(RecipeEntry<?> recipe) {
                return new InteractionProbeResult.Supported(testInteractionId, true, false);
            }

            @Override
            public InteractionGuideResult guide(RecipeEntry<?> recipe) {
                return new InteractionGuideResult(ExecutionGuidance.simple(Component.literal("guide")));
            }

            @Override
            public InteractionPerformResult perform(InteractionContext context) {
                return new InteractionPerformResult.Performed();
            }

            @Override
            public int maxExecuteBatchSize(InteractionContext context) {
                observedTimes.set(context.times());
                return 7;
            }
        };
        SlotExecutionDriver driver = new SlotExecutionDriver(testInteractionId, interactionApi, null, null, null);

        int maxBatch = driver.interaction().interactionApi().maxExecuteBatchSize(new InteractionContext(
            recipeEntry("craft"),
            9,
            Lists.immutable.empty(),
            Lists.immutable.empty()
        ));

        assertEquals(7, maxBatch);
        assertEquals(9, observedTimes.get());
    }

    @Test
    void standardDriverUsesManualFlowWhenNoInteractionProvided() {
        SlotLayout layout = SlotLayout.builder()
            .add(
                SlotLayout.Role.input,
                new SlotLayout.Key(Namespace.of("test", "input"), 0),
                new SlotLayout.Reference.Index(0)
            )
            .add(
                SlotLayout.Role.output,
                new SlotLayout.Key(Namespace.of("test", "output"), 0),
                new SlotLayout.Reference.Index(1)
            )
            .build();
        SlotAccess access = new SlotAccess() {
            @Override
            public State state() {
                return new State(true, testInteractionId, ExecutionSideRequirement.client);
            }

            @Override
            public ItemStack stack(SlotLayout.Reference slot) {
                return ItemStack.EMPTY;
            }
        };
        SlotExecutionDriver.Profile profile = new SlotExecutionDriver.Profile(testInteractionId, layout, access, null, null, null);
        SlotExecutionDriver driver = SlotExecutionDriver.standard(profile);
        driver.initialize(startSession(prepareInputsStep()));

        driver.advanceSteps(1);

        assertNotNull(driver.session());
        assertTrue(driver.session().paused());
        PauseReason pauseReason = driver.session().pauseReason();
        assertNotNull(pauseReason);
        assertEquals(PauseReasons.awaitingPlayerAction, pauseReason.key());
        ExecutionGuidance guidance = pauseReason.guidance();
        assertNotNull(guidance);
        assertTrue(guidance.summary().getContents() instanceof TranslatableContents);
        TranslatableContents contents = (TranslatableContents) guidance.summary().getContents();
        assertEquals("cirrus.ui.execution.slot.manual.prepare", contents.getKey());
    }

    @Test
    void autoFlowCallsOpenWhenClosed() {
        AtomicBoolean open = new AtomicBoolean(false);
        SlotAccess access = new SlotAccess() {
            @Override
            public State state() {
                return new State(open.get(), testInteractionId, ExecutionSideRequirement.client);
            }

            @Override
            public ItemStack stack(SlotLayout.Reference slot) {
                return ItemStack.EMPTY;
            }
        };
        SlotAccess.Interaction interaction = new SlotAccess.Interaction() {
            @Override
            public boolean click(SlotAccess.Click click) {
                return true;
            }

            @Override
            public void open() {
                open.set(true);
            }

            @Override
            public void close() {
            }

            @Override
            public ItemStack carried() {
                return ItemStack.EMPTY;
            }
        };
        SlotFlow.PlacementResolver resolver = (context, recipe, inputs) -> Lists.immutable.empty();
        SlotExecutionDriver.Profile profile = new SlotExecutionDriver.Profile(testInteractionId, layoutForInputOutput(), access, interaction, resolver, null);
        SlotExecutionDriver driver = SlotExecutionDriver.standard(profile);
        driver.initialize(startSession(openInteractionStep()));

        driver.advanceSteps(1);

        assertTrue(open.get());
        assertNotNull(driver.session());
        assertFalse(driver.session().paused());
        assertEquals(1, driver.session().nextStepIndex());
    }

    @Test
    void autoFlowPausesWhenResolverReturnsEmpty() {
        SlotAccess access = new SlotAccess() {
            @Override
            public State state() {
                return new State(true, testInteractionId, ExecutionSideRequirement.client);
            }

            @Override
            public ItemStack stack(SlotLayout.Reference slot) {
                return ItemStack.EMPTY;
            }
        };
        SlotAccess.Interaction interaction = new SlotAccess.Interaction() {
            @Override
            public boolean click(SlotAccess.Click click) {
                return true;
            }

            @Override
            public void open() {
            }

            @Override
            public void close() {
            }

            @Override
            public ItemStack carried() {
                return ItemStack.EMPTY;
            }
        };
        SlotFlow.PlacementResolver resolver = (context, recipe, inputs) -> Lists.immutable.empty();
        SlotExecutionDriver.Profile profile = new SlotExecutionDriver.Profile(testInteractionId, layoutForInputOutput(), access, interaction, resolver, null);
        SlotExecutionDriver driver = SlotExecutionDriver.standard(profile);
        driver.initialize(startSession(prepareInputsStep()));

        driver.advanceSteps(1);

        assertNotNull(driver.session());
        assertTrue(driver.session().paused());
        PauseReason pauseReason = driver.session().pauseReason();
        assertNotNull(pauseReason);
        assertEquals(PauseReasons.noAccessibleProcessor, pauseReason.key());
        assertNotNull(pauseReason.guidance());
        assertTrue(pauseReason.guidance().summary().getContents() instanceof TranslatableContents);
        assertEquals(
            "cirrus.ui.execution.slot.pause.prepare.no_placements.summary",
            ((TranslatableContents) pauseReason.guidance().summary().getContents()).getKey()
        );
        assertNotNull(pauseReason.guidance().detail());
        assertTrue(pauseReason.guidance().detail().getContents() instanceof TranslatableContents);
        assertEquals(
            "cirrus.ui.execution.slot.pause.prepare.no_placements.detail",
            ((TranslatableContents) pauseReason.guidance().detail().getContents()).getKey()
        );
    }

    @Test
    void autoAdvanceRateStopsAtVisibleTraceSteps() {
        var access = new FakeAccess(true);
        var flow = new TraceTrackingFlow(access);
        var driver = SlotExecutionDriver.standard(new SlotExecutionDriver.Profile(testInteractionId, new FakeLayout(), access, null, null, flow));
        driver.initialize(startSession(openInteractionStep(), prepareInputsStep(), executeRecipeStep(), collectOutputsStep()));
        driver.toggleAutoAdvance();
        driver.setRatePerSecond(1.0d);

        for (int tick = 0; tick < 40; tick++) {
            driver.tick();
        }

        assertFalse(driver.snapshot().telemetry().completed());
        assertEquals(1, driver.session().nextStepIndex());
        assertEquals("prepare", driver.snapshot().trace().recipeLabel());

        for (int tick = 0; tick < 40; tick++) {
            driver.tick();
        }

        assertFalse(driver.snapshot().telemetry().completed());
        assertEquals(2, driver.session().nextStepIndex());
        assertEquals("execute", driver.snapshot().trace().recipeLabel());

        for (int tick = 0; tick < 40; tick++) {
            driver.tick();
        }

        assertTrue(driver.snapshot().telemetry().completed());
        assertEquals(4, driver.session().nextStepIndex());
    }

    @Test
    void autoAdvanceStopsWhenExecutionCompletes() {
        var access = new FakeAccess(true);
        var driver = SlotExecutionDriver.standard(new SlotExecutionDriver.Profile(testInteractionId, new FakeLayout(), access, null, null, new FakeFlow(access)));
        driver.initialize(startSession(executeRecipeStep()));
        driver.toggleAutoAdvance();

        driver.advanceSteps(1);

        assertTrue(driver.snapshot().telemetry().completed());
        assertFalse(driver.snapshot().autoAdvanceEnabled());
    }

    @Test
    void autoAdvanceSkipsNonTraceStepsWithoutHoldingStaleTrace() {
        var access = new FakeAccess(true);
        var driver = SlotExecutionDriver.standard(new SlotExecutionDriver.Profile(testInteractionId, new FakeLayout(), access, null, null, new TraceTrackingFlow(access)));
        driver.initialize(startSession(
            openInteractionStep(),
            prepareInputsStep(),
            executeRecipeStep(),
            collectOutputsStep(),
            prepareInputsStep()
        ));
        driver.toggleAutoAdvance();
        driver.setRatePerSecond(1.0d);

        for (int tick = 0; tick < 120; tick++) {
            driver.tick();
        }

        assertEquals(4, driver.session().nextStepIndex());
        assertEquals("prepare", driver.snapshot().trace().recipeLabel());
        assertTrue(driver.snapshot().trace().available());
    }

    @Test
    void autoFlowExecuteAcceptsInputsAlreadyPresentInGrid(MinecraftServer server) {
        SlotLayout layout = SlotLayout.builder()
            .add(
                SlotLayout.Role.input,
                new SlotLayout.Key(Namespace.of("test", "input"), 0),
                new SlotLayout.Reference.Index(0)
            )
            .add(
                SlotLayout.Role.output,
                new SlotLayout.Key(Namespace.of("test", "output"), 0),
                new SlotLayout.Reference.Index(1)
            )
            .add(
                SlotLayout.Role.inventory,
                new SlotLayout.Key(Namespace.of("test", "inventory"), 0),
                new SlotLayout.Reference.Index(2)
            )
            .build();
        SlotLayout.Reference inputSlot = new SlotLayout.Reference.Index(0);
        SlotLayout.Reference outputSlot = new SlotLayout.Reference.Index(1);
        SlotLayout.Reference inventorySlot = new SlotLayout.Reference.Index(2);
        OutputMovingInteractionAccess access = new OutputMovingInteractionAccess(true, outputSlot, inventorySlot);
        access.put(inputSlot, stack(Items.OAK_LOG, 1));
        access.put(outputSlot, stack(Items.OAK_PLANKS, 4));

        SlotFlow.PlacementResolver resolver = (context, recipe, inputs) -> Lists.immutable.of(
            new SlotFlow.Placement(inputSlot, stack(Items.OAK_LOG, 1), 1)
        );
        SlotExecutionDriver.Profile profile = new SlotExecutionDriver.Profile(testInteractionId, layout, access, access, resolver, null);
        SlotExecutionDriver driver = SlotExecutionDriver.standard(profile);
        driver.initialize(startSession(executeRecipeStepWithSingleInputAndOutput()));

        driver.advanceSteps(1);

        assertNotNull(driver.session());
        assertFalse(driver.session().paused());
        assertEquals(1, driver.session().nextStepIndex());
        assertTrue(access.clickedSlots.contains(outputSlot));
        assertFalse(access.clickedSlots.contains(inventorySlot));
    }

    @Test
    void autoFlowExecuteQuickMovesOutputOnceForPreparedMultiCraft() {
        SlotLayout layout = SlotLayout.builder()
            .add(
                SlotLayout.Role.input,
                new SlotLayout.Key(Namespace.of("test", "input"), 0),
                new SlotLayout.Reference.Index(0)
            )
            .add(
                SlotLayout.Role.output,
                new SlotLayout.Key(Namespace.of("test", "output"), 0),
                new SlotLayout.Reference.Index(1)
            )
            .add(
                SlotLayout.Role.inventory,
                new SlotLayout.Key(Namespace.of("test", "inventory"), 0),
                new SlotLayout.Reference.Index(2)
            )
            .build();
        SlotLayout.Reference inputSlot = new SlotLayout.Reference.Index(0);
        SlotLayout.Reference outputSlot = new SlotLayout.Reference.Index(1);
        SlotLayout.Reference inventorySlot = new SlotLayout.Reference.Index(2);
        OutputMovingInteractionAccess access = new OutputMovingInteractionAccess(true, outputSlot, inventorySlot);
        access.put(inputSlot, stack(Items.OAK_LOG, 16));
        access.put(outputSlot, stack(Items.OAK_PLANKS, 4));

        SlotFlow.PlacementResolver resolver = (context, recipe, inputs) -> Lists.immutable.of(
            new SlotFlow.Placement(inputSlot, stack(Items.OAK_LOG, 1), 16)
        );
        SlotExecutionDriver.Profile profile = new SlotExecutionDriver.Profile(testInteractionId, layout, access, access, resolver, null);
        SlotExecutionDriver driver = SlotExecutionDriver.standard(profile);
        driver.initialize(startSession(new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.interactionPerform,
            recipeEntry("craft"),
            16,
            Lists.immutable.empty(),
            Lists.immutable.empty()
        )));

        driver.advanceSteps(1);

        long outputClicks = access.clickedSlots.stream().filter(outputSlot::equals).count();
        assertNotNull(driver.session());
        assertFalse(driver.session().paused());
        assertEquals(1, driver.session().nextStepIndex());
        assertEquals(1L, outputClicks);
        assertFalse(access.clickedSlots.contains(inventorySlot));
    }

    @Test
    void autoFlowRetriesPausedStepWhenInventoryStateChanges() {
        SlotLayout layout = SlotLayout.builder()
            .add(
                SlotLayout.Role.input,
                new SlotLayout.Key(Namespace.of("test", "input"), 0),
                new SlotLayout.Reference.Index(0)
            )
            .add(
                SlotLayout.Role.output,
                new SlotLayout.Key(Namespace.of("test", "output"), 0),
                new SlotLayout.Reference.Index(1)
            )
            .add(
                SlotLayout.Role.inventory,
                new SlotLayout.Key(Namespace.of("test", "inventory"), 0),
                new SlotLayout.Reference.Index(2)
            )
            .build();
        SlotLayout.Reference inputSlot = new SlotLayout.Reference.Index(0);
        SlotLayout.Reference inventorySlot = new SlotLayout.Reference.Index(2);
        AtomicInteger available = new AtomicInteger(0);
        RetryInteractionAccess access = new RetryInteractionAccess(true, available, inputSlot, inventorySlot);
        SlotFlow.PlacementResolver resolver = (context, recipe, inputs) -> Lists.immutable.of(
            new SlotFlow.Placement(inputSlot, stack(Items.OAK_LOG, 1), 1)
        );
        SlotExecutionDriver driver = SlotExecutionDriver.standard(new SlotExecutionDriver.Profile(testInteractionId, layout, access, access, resolver, null));
        driver.initialize(startSession(prepareInputsStep()));
        driver.toggleAutoAdvance();
        driver.setRatePerSecond(1.0d);

        driver.advanceSteps(1);

        assertNotNull(driver.session());
        assertTrue(driver.session().paused());
        assertEquals(0, driver.session().nextStepIndex());

        available.set(1);
        driver.tick();

        assertNotNull(driver.session());
        assertFalse(driver.session().paused());
        assertEquals(1, driver.session().nextStepIndex());
        assertTrue(access.clickedSlots.contains(inventorySlot));
    }

    @Test
    void autoFlowPrepareKeepsExistingTagVariantInGrid(MinecraftServer server) {
        SlotLayout layout = SlotLayout.builder()
            .add(
                SlotLayout.Role.input,
                new SlotLayout.Key(Namespace.of("test", "input"), 0),
                new SlotLayout.Reference.Index(0)
            )
            .add(
                SlotLayout.Role.output,
                new SlotLayout.Key(Namespace.of("test", "output"), 0),
                new SlotLayout.Reference.Index(1)
            )
            .add(
                SlotLayout.Role.inventory,
                new SlotLayout.Key(Namespace.of("test", "inventory"), 0),
                new SlotLayout.Reference.Index(2)
            )
            .build();
        SlotLayout.Reference inputSlot = new SlotLayout.Reference.Index(0);
        SlotLayout.Reference inventorySlot = new SlotLayout.Reference.Index(2);
        TagAwareInteractionAccess access = new TagAwareInteractionAccess(true, inputSlot, inventorySlot);
        access.putInput(stack(Items.BIRCH_PLANKS, 1));
        access.putInventory(stack(Items.OAK_PLANKS, 1));

        SlotFlow.PlacementResolver resolver = (context, recipe, inputs) -> Lists.immutable.of(
            new SlotFlow.Placement(
                inputSlot,
                stack(Items.OAK_PLANKS, 1),
                1,
                InputContent.fromItemTag(ItemTags.PLANKS, 1)
            )
        );
        SlotExecutionDriver driver = SlotExecutionDriver.standard(new SlotExecutionDriver.Profile(testInteractionId, layout, access, access, resolver, null));
        driver.initialize(startSession(prepareInputsStep()));

        driver.advanceSteps(1);

        assertNotNull(driver.session());
        assertFalse(driver.session().paused());
        assertEquals(1, driver.session().nextStepIndex());
        assertFalse(access.clickedSlots.contains(inputSlot), "existing valid tag input should not be cleared");
        assertEquals(Items.BIRCH_PLANKS, access.stack(inputSlot).getItem());
    }

    @Test
    void autoFlowExecuteDoesNotRequestReTranslationWhenBatchOutputIsCollectedIncrementally(MinecraftServer server) {
        SlotLayout layout = SlotLayout.builder()
            .add(
                SlotLayout.Role.input,
                new SlotLayout.Key(Namespace.of("test", "input"), 0),
                new SlotLayout.Reference.Index(0)
            )
            .add(
                SlotLayout.Role.output,
                new SlotLayout.Key(Namespace.of("test", "output"), 0),
                new SlotLayout.Reference.Index(1)
            )
            .add(
                SlotLayout.Role.inventory,
                new SlotLayout.Key(Namespace.of("test", "inventory"), 0),
                new SlotLayout.Reference.Index(2)
            )
            .build();
        SlotLayout.Reference inputSlot = new SlotLayout.Reference.Index(0);
        SlotLayout.Reference outputSlot = new SlotLayout.Reference.Index(1);
        SlotLayout.Reference inventorySlot = new SlotLayout.Reference.Index(2);
        PartialOutputInteractionAccess access = new PartialOutputInteractionAccess(true, outputSlot, inventorySlot, 1);
        access.put(inputSlot, stack(Items.OAK_LOG, 8));
        access.put(outputSlot, stack(Items.OAK_PLANKS, 8));

        SlotFlow.PlacementResolver resolver = (context, recipe, inputs) -> Lists.immutable.of(
            new SlotFlow.Placement(inputSlot, stack(Items.OAK_LOG, 1), 8)
        );
        SlotExecutionDriver driver = SlotExecutionDriver.standard(new SlotExecutionDriver.Profile(testInteractionId, layout, access, access, resolver, null));
        driver.initialize(startSession(new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.interactionPerform,
            recipeEntry("craft"),
            8,
            Lists.immutable.of(new InputBinding(
                InputContent.fromItemStack(stack(Items.OAK_LOG, 1), 8),
                8
            )),
            Lists.immutable.of(new OutputBinding(
                ContentStack.fromStack(stack(Items.OAK_PLANKS, 1)),
                8
            ))
        )));

        driver.advanceSteps(1);

        assertNotNull(driver.session());
        assertFalse(driver.session().paused());
        assertEquals(1, driver.session().nextStepIndex());
        assertNotNull(access.stack(outputSlot));
        assertTrue(access.stack(outputSlot).getCount() > 0);
        assertEquals(1, access.stack(inventorySlot).getCount());
        assertEquals(1, access.quickMoveClicks(outputSlot));
        assertTrue(driver.snapshot().reTranslationHint() == null);
    }

    @Test
    void tickDoesNotRequestReTranslationForSlotManagedExecuteInventoryDrift() {
        SlotLayout layout = SlotLayout.builder()
            .add(
                SlotLayout.Role.input,
                new SlotLayout.Key(Namespace.of("test", "input"), 0),
                new SlotLayout.Reference.Index(0)
            )
            .add(
                SlotLayout.Role.output,
                new SlotLayout.Key(Namespace.of("test", "output"), 0),
                new SlotLayout.Reference.Index(1)
            )
            .add(
                SlotLayout.Role.inventory,
                new SlotLayout.Key(Namespace.of("test", "inventory"), 0),
                new SlotLayout.Reference.Index(2)
            )
            .build();
        SlotLayout.Reference inputSlot = new SlotLayout.Reference.Index(0);
        SlotLayout.Reference outputSlot = new SlotLayout.Reference.Index(1);
        SlotLayout.Reference inventorySlot = new SlotLayout.Reference.Index(2);
        PartialOutputInteractionAccess access = new PartialOutputInteractionAccess(true, outputSlot, inventorySlot, 1);
        access.put(inputSlot, stack(Items.OAK_LOG, 8));
        access.put(outputSlot, stack(Items.OAK_PLANKS, 8));

        SlotFlow.PlacementResolver resolver = (context, recipe, inputs) -> Lists.immutable.of(
            new SlotFlow.Placement(inputSlot, stack(Items.OAK_LOG, 1), 8)
        );
        SlotExecutionDriver driver = SlotExecutionDriver.standard(new SlotExecutionDriver.Profile(testInteractionId, layout, access, access, resolver, null));
        driver.initialize(startSession(new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.interactionPerform,
            recipeEntry("craft"),
            8,
            Lists.immutable.of(new InputBinding(
                InputContent.fromItemStack(stack(Items.OAK_LOG, 1), 8),
                8
            )),
            Lists.immutable.of(new OutputBinding(
                ContentStack.fromStack(stack(Items.OAK_PLANKS, 1)),
                8
            ))
        )));
        driver.toggleAutoAdvance();
        driver.setRatePerSecond(40.0d);

        driver.tick();

        assertNotNull(driver.session());
        assertEquals(1, driver.session().nextStepIndex());
        assertTrue(driver.snapshot().reTranslationHint() == null);
    }

    @Test
    void autoFlowCollectKeepsQuickMovingUntilOutputSlotIsEmpty() {
        SlotLayout layout = SlotLayout.builder()
            .add(
                SlotLayout.Role.input,
                new SlotLayout.Key(Namespace.of("test", "input"), 0),
                new SlotLayout.Reference.Index(0)
            )
            .add(
                SlotLayout.Role.output,
                new SlotLayout.Key(Namespace.of("test", "output"), 0),
                new SlotLayout.Reference.Index(1)
            )
            .add(
                SlotLayout.Role.inventory,
                new SlotLayout.Key(Namespace.of("test", "inventory"), 0),
                new SlotLayout.Reference.Index(2)
            )
            .build();
        SlotLayout.Reference outputSlot = new SlotLayout.Reference.Index(1);
        SlotLayout.Reference inventorySlot = new SlotLayout.Reference.Index(2);
        PartialOutputInteractionAccess access = new PartialOutputInteractionAccess(true, outputSlot, inventorySlot, 1);
        access.put(outputSlot, stack(Items.OAK_PLANKS, 8));

        SlotExecutionDriver driver = SlotExecutionDriver.standard(new SlotExecutionDriver.Profile(testInteractionId, layout, access, access, (context, recipe, inputs) -> Lists.immutable.empty(), null));
        driver.initialize(startSession(new InteractionStep.CollectOutputsStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.outputStorage,
            recipeEntry("craft"),
            Lists.immutable.of(new OutputBinding(
                ContentStack.fromStack(stack(Items.OAK_PLANKS, 1)),
                8
            ))
        )));

        driver.advanceSteps(1);

        assertNotNull(driver.session());
        assertEquals(1, driver.session().nextStepIndex());
        assertTrue(access.stack(outputSlot).isEmpty());
        assertEquals(8, access.stack(inventorySlot).getCount());
        assertEquals(8, access.quickMoveClicks(outputSlot));
    }

    @Test
    void tickRequestsReTranslationWhenPlanningInventoryChangesBeforeNonSlotStepCompletes() {
        SlotLayout layout = SlotLayout.builder()
            .add(
                SlotLayout.Role.inventory,
                new SlotLayout.Key(Namespace.of("test", "inventory"), 0),
                new SlotLayout.Reference.Index(0)
            )
            .build();
        TestInteractionAccess access = new TestInteractionAccess(true);
        SlotLayout.Reference inventorySlot = new SlotLayout.Reference.Index(0);
        access.put(inventorySlot, stack(Items.OAK_LOG, 1));
        SlotExecutionDriver driver = SlotExecutionDriver.standard(new SlotExecutionDriver.Profile(testInteractionId, layout, access, null, null, null));
        driver.initialize(startSession(new InteractionStep.WithdrawResourceStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.inventoryTransfer,
            new InteractionStep.WithdrawTarget.Input(InputContent.fromItemStack(stack(Items.OAK_LOG, 1), 1)),
            1
        )));

        access.put(inventorySlot, stack(Items.OAK_LOG, 2));
        driver.tick();

        assertNotNull(driver.session());
        assertEquals(0, driver.session().nextStepIndex());
        assertNotNull(driver.snapshot().reTranslationHint());
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

    private static InteractionStep.ExecuteRecipeStep executeRecipeStepWithSingleInputAndOutput() {
        return new InteractionStep.ExecuteRecipeStep(
            ExecutionSideRequirement.environmentAgnostic,
            ExecutionCapability.interactionPerform,
            recipeEntry("craft"),
            1,
            Lists.immutable.of(new InputBinding(
                InputContent.fromItemStack(stack(Items.OAK_LOG, 1), 1),
                1
            )),
            Lists.immutable.of(new OutputBinding(
                ContentStack.fromStack(stack(Items.OAK_PLANKS, 1)),
                4
            ))
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

    private static SlotLayout layoutForInputOutput() {
        return SlotLayout.builder()
            .add(
                SlotLayout.Role.input,
                new SlotLayout.Key(Namespace.of("test", "input"), 0),
                new SlotLayout.Reference.Index(0)
            )
            .add(
                SlotLayout.Role.output,
                new SlotLayout.Key(Namespace.of("test", "output"), 0),
                new SlotLayout.Reference.Index(1)
            )
            .build();
    }

    private static final class FakeLayout implements SlotLayout {
        private static final SlotLayout.Index emptyIndex = new SlotLayout.Index() {
            @Override
            public SlotLayout.Reference get(SlotLayout.Key key) {
                return null;
            }

            @Override
            public Set<SlotLayout.Key> keys() {
                return Set.of();
            }

            @Override
            public Map<SlotLayout.Key, SlotLayout.Reference> mapping() {
                return Map.of();
            }
        };

        @Override
        public SlotLayout.Index slots(SlotLayout.Role role) {
            return emptyIndex;
        }
    }

    private static final class FakeAccess implements SlotAccess {
        private final boolean open;

        private FakeAccess(boolean open) {
            this.open = open;
        }

        @Override
        public State state() {
            return new State(open, testInteractionId, ExecutionSideRequirement.environmentAgnostic);
        }

        @Override
        public ItemStack stack(SlotLayout.Reference slot) {
            return ItemStack.EMPTY;
        }
    }

    private static class TestInteractionAccess implements SlotAccess, SlotAccess.Interaction {
        protected final boolean open;
        protected final Map<SlotLayout.Reference, ItemStack> stacks = new ConcurrentHashMap<>();
        protected final List<SlotLayout.Reference> clickedSlots = new ArrayList<>();

        private TestInteractionAccess(boolean open) {
            this.open = open;
        }

        void put(SlotLayout.Reference slot, ItemStack stack) {
            stacks.put(slot, stack.copy());
        }

        @Override
        public State state() {
            return new State(open, testInteractionId, ExecutionSideRequirement.environmentAgnostic);
        }

        @Override
        public ItemStack stack(SlotLayout.Reference slot) {
            ItemStack stack = stacks.get(slot);
            return stack == null ? ItemStack.EMPTY : stack.copy();
        }

        @Override
        public boolean click(SlotAccess.Click click) {
            clickedSlots.add(click.slot());
            return true;
        }

        @Override
        public void open() {
        }

        @Override
        public void close() {
        }

        @Override
        public ItemStack carried() {
            return ItemStack.EMPTY;
        }
    }

    private static class OutputMovingInteractionAccess extends TestInteractionAccess {
        protected final SlotLayout.Reference outputSlot;
        protected final SlotLayout.Reference inventorySlot;
        protected final Map<SlotLayout.Reference, AtomicInteger> quickMoveClicks = new ConcurrentHashMap<>();

        private OutputMovingInteractionAccess(boolean open, SlotLayout.Reference outputSlot, SlotLayout.Reference inventorySlot) {
            super(open);
            this.outputSlot = outputSlot;
            this.inventorySlot = inventorySlot;
        }

        int quickMoveClicks(SlotLayout.Reference slot) {
            AtomicInteger count = quickMoveClicks.get(slot);
            return count == null ? 0 : count.get();
        }

        @Override
        public boolean click(SlotAccess.Click click) {
            clickedSlots.add(click.slot());
            if (click.slot().equals(outputSlot) && click.type() == ClickType.QUICK_MOVE) {
                quickMoveClicks.computeIfAbsent(click.slot(), ignored -> new AtomicInteger()).incrementAndGet();
                ItemStack output = stack(outputSlot);
                if (output.isEmpty()) {
                    return false;
                }
                ItemStack inventory = stack(inventorySlot);
                if (inventory.isEmpty()) {
                    put(inventorySlot, output);
                } else if (ItemStack.isSameItemSameComponents(inventory, output)) {
                    ItemStack merged = inventory.copy();
                    merged.grow(output.getCount());
                    put(inventorySlot, merged);
                } else {
                    return false;
                }
                put(outputSlot, ItemStack.EMPTY);
                return true;
            }
            return true;
        }
    }

    private static final class PartialOutputInteractionAccess extends OutputMovingInteractionAccess {
        private final int transferredAmount;

        private PartialOutputInteractionAccess(
            boolean open,
            SlotLayout.Reference outputSlot,
            SlotLayout.Reference inventorySlot,
            int transferredAmount
        ) {
            super(open, outputSlot, inventorySlot);
            this.transferredAmount = transferredAmount;
        }

        @Override
        public boolean click(SlotAccess.Click click) {
            clickedSlots.add(click.slot());
            if (click.slot().equals(outputSlot) && click.type() == ClickType.QUICK_MOVE) {
                quickMoveClicks.computeIfAbsent(click.slot(), ignored -> new AtomicInteger()).incrementAndGet();
                ItemStack output = stack(outputSlot);
                if (output.isEmpty()) {
                    return false;
                }
                int moved = Math.max(0, Math.min(transferredAmount, output.getCount()));
                if (moved <= 0) {
                    return false;
                }
                ItemStack inventory = stack(inventorySlot);
                ItemStack delta = output.copy();
                delta.setCount(moved);
                if (inventory.isEmpty()) {
                    put(inventorySlot, delta);
                } else if (ItemStack.isSameItemSameComponents(inventory, delta)) {
                    ItemStack merged = inventory.copy();
                    merged.grow(delta.getCount());
                    put(inventorySlot, merged);
                } else {
                    return false;
                }
                ItemStack remaining = output.copy();
                remaining.shrink(moved);
                put(outputSlot, remaining.getCount() > 0 ? remaining : ItemStack.EMPTY);
                return true;
            }
            return true;
        }
    }

    private static final class RetryInteractionAccess implements SlotAccess, SlotAccess.Interaction {
        private final boolean open;
        private final AtomicInteger available;
        private final SlotLayout.Reference inputSlot;
        private final SlotLayout.Reference inventorySlot;
        private final List<SlotLayout.Reference> clickedSlots = new ArrayList<>();
        private ItemStack carried = ItemStack.EMPTY;
        private ItemStack input = ItemStack.EMPTY;

        private RetryInteractionAccess(boolean open, AtomicInteger available, SlotLayout.Reference inputSlot, SlotLayout.Reference inventorySlot) {
            this.open = open;
            this.available = available;
            this.inputSlot = inputSlot;
            this.inventorySlot = inventorySlot;
        }

        @Override
        public State state() {
            return new State(open, testInteractionId, ExecutionSideRequirement.environmentAgnostic);
        }

        @Override
        public ItemStack stack(SlotLayout.Reference slot) {
            if (slot.equals(inputSlot)) {
                return input.copy();
            }
            if (slot.equals(inventorySlot) && available.get() > 0) {
                return SlotExecutionDriverTest.stack(Items.OAK_LOG, available.get());
            }
            return ItemStack.EMPTY;
        }

        @Override
        public boolean click(SlotAccess.Click click) {
            clickedSlots.add(click.slot());
            if (click.slot().equals(inventorySlot) && click.type() == ClickType.PICKUP) {
                if (available.get() <= 0) {
                    return false;
                }
                carried = SlotExecutionDriverTest.stack(Items.OAK_LOG, 1);
                available.decrementAndGet();
                return true;
            }
            if (click.slot().equals(inputSlot) && click.type() == ClickType.PICKUP && click.button() == 1) {
                if (carried.isEmpty()) {
                    return false;
                }
                if (input.isEmpty()) {
                    input = carried.copy();
                    input.setCount(1);
                } else {
                    input.grow(1);
                }
                carried.shrink(1);
                if (carried.getCount() <= 0) {
                    carried = ItemStack.EMPTY;
                }
                return true;
            }
            return true;
        }

        @Override
        public void open() {
        }

        @Override
        public void close() {
        }

        @Override
        public ItemStack carried() {
            return carried.copy();
        }
    }

    private static final class TagAwareInteractionAccess implements SlotAccess, SlotAccess.Interaction {
        private final boolean open;
        private final SlotLayout.Reference inputSlot;
        private final SlotLayout.Reference inventorySlot;
        private final List<SlotLayout.Reference> clickedSlots = new ArrayList<>();
        private ItemStack input = ItemStack.EMPTY;
        private ItemStack inventory = ItemStack.EMPTY;
        private ItemStack carried = ItemStack.EMPTY;

        private TagAwareInteractionAccess(boolean open, SlotLayout.Reference inputSlot, SlotLayout.Reference inventorySlot) {
            this.open = open;
            this.inputSlot = inputSlot;
            this.inventorySlot = inventorySlot;
        }

        void putInput(ItemStack stack) {
            input = stack.copy();
        }

        void putInventory(ItemStack stack) {
            inventory = stack.copy();
        }

        @Override
        public State state() {
            return new State(open, testInteractionId, ExecutionSideRequirement.environmentAgnostic);
        }

        @Override
        public ItemStack stack(SlotLayout.Reference slot) {
            if (slot.equals(inputSlot)) {
                return input.copy();
            }
            if (slot.equals(inventorySlot)) {
                return inventory.copy();
            }
            return ItemStack.EMPTY;
        }

        @Override
        public boolean click(SlotAccess.Click click) {
            clickedSlots.add(click.slot());
            if (click.slot().equals(inputSlot) && click.type() == ClickType.QUICK_MOVE) {
                inventory = input.copy();
                input = ItemStack.EMPTY;
                return true;
            }
            if (click.slot().equals(inventorySlot) && click.type() == ClickType.PICKUP) {
                if (inventory.isEmpty()) {
                    return false;
                }
                carried = inventory.copy();
                inventory = ItemStack.EMPTY;
                return true;
            }
            if (click.slot().equals(inputSlot) && click.type() == ClickType.PICKUP && click.button() == 1) {
                if (carried.isEmpty()) {
                    return false;
                }
                if (input.isEmpty()) {
                    input = carried.copy();
                    input.setCount(1);
                } else {
                    input.grow(1);
                }
                carried.shrink(1);
                if (carried.getCount() <= 0) {
                    carried = ItemStack.EMPTY;
                }
                return true;
            }
            if (click.slot().equals(inventorySlot) && click.type() == ClickType.PICKUP && click.button() == 0) {
                if (carried.isEmpty()) {
                    return true;
                }
                if (inventory.isEmpty()) {
                    inventory = carried.copy();
                    carried = ItemStack.EMPTY;
                    return true;
                }
                return false;
            }
            return true;
        }

        @Override
        public void open() {
        }

        @Override
        public void close() {
        }

        @Override
        public ItemStack carried() {
            return carried.copy();
        }
    }

    private static ItemStack stack(Item item, int count) {
        ItemStack stack = new ItemStack(item);
        stack.setCount(count);
        return stack;
    }

    private static final class FakeFlow implements SlotFlow {
        private final FakeAccess access;
        private boolean openCalled;
        private boolean prepareCalled;
        private boolean executeCalled;
        private boolean collectCalled;

        private FakeFlow(FakeAccess access) {
            this.access = access;
        }

        @Override
        public Validation validate(Context context) {
            return Validation.ok();
        }

        @Override
        public StepResult open(Context context, InteractionStep.OpenInteractionStep step) {
            openCalled = true;
            if (access.state().open()) {
                return StepResult.success();
            }
            return StepResult.paused(new PauseReason(PauseReasons.awaitingPlayerAction, ExecutionGuidance.simple(Component.literal("Interaction closed"))));
        }

        @Override
        public StepResult prepare(Context context, InteractionStep.PrepareInputsStep step) {
            prepareCalled = true;
            return StepResult.success();
        }

        @Override
        public InteractionPerformResult execute(Context context, InteractionStep.ExecuteRecipeStep step) {
            executeCalled = true;
            return new InteractionPerformResult.Performed();
        }

        @Override
        public StepResult collect(Context context, InteractionStep.CollectOutputsStep step) {
            collectCalled = true;
            return StepResult.success();
        }

        @Override
        public PauseReason pauseReason(Context context, Failure failure) {
            return new PauseReason(PauseReasons.awaitingPlayerAction, ExecutionGuidance.simple(Component.literal("Paused")));
        }

        @Override
        public Tracking track(Context context, ExecutionSession session) {
            return Tracking.empty();
        }
    }

    private static final class TraceTrackingFlow implements SlotFlow {
        private final FakeAccess access;

        private TraceTrackingFlow(FakeAccess access) {
            this.access = access;
        }

        @Override
        public Validation validate(Context context) {
            return Validation.ok();
        }

        @Override
        public StepResult open(Context context, InteractionStep.OpenInteractionStep step) {
            return access.state().open()
                ? StepResult.success()
                : StepResult.paused(new PauseReason(PauseReasons.awaitingPlayerAction, ExecutionGuidance.simple(Component.literal("Interaction closed"))));
        }

        @Override
        public StepResult prepare(Context context, InteractionStep.PrepareInputsStep step) {
            return StepResult.success();
        }

        @Override
        public InteractionPerformResult execute(Context context, InteractionStep.ExecuteRecipeStep step) {
            return new InteractionPerformResult.Performed();
        }

        @Override
        public StepResult collect(Context context, InteractionStep.CollectOutputsStep step) {
            return StepResult.success();
        }

        @Override
        public PauseReason pauseReason(Context context, Failure failure) {
            return new PauseReason(PauseReasons.awaitingPlayerAction, ExecutionGuidance.simple(Component.literal("Paused")));
        }

        @Override
        public Tracking track(Context context, ExecutionSession session) {
            InteractionStep step = session.currentStep();
            if (step instanceof InteractionStep.ExecuteRecipeStep) {
                return new Tracking(null, Lists.immutable.empty(), null, trace("execute"));
            }
            if (step instanceof InteractionStep.PrepareInputsStep) {
                return new Tracking(null, Lists.immutable.empty(), null, trace("prepare"));
            }
            return Tracking.empty();
        }

        private static ExecutionOverlayTraceSnapshot trace(String label) {
            return new ExecutionOverlayTraceSnapshot(
                true,
                label,
                stack(Items.CRAFTING_TABLE, 1),
                Lists.immutable.empty(),
                Lists.immutable.empty()
            );
        }
    }

    private static final TestRecipeSystem TEST_RECIPE_SYSTEM = new TestRecipeSystem();

    private static final class TestRecipeSystem implements RecipeSystem<TestRecipe> {
        private final Map<String, TestRecipe> byId = new ConcurrentHashMap<>();

        @Override
        public Namespace systemId() {
            return Namespace.of("test", "slot_driver");
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
}
