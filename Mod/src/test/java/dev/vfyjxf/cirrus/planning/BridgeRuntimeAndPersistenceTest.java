package dev.vfyjxf.cirrus.planning;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.content.value.ContentVariant;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedLine;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedRecipeCapability;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.human.AnswerPromptFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.CostOverrideRule;
import dev.vfyjxf.cirrus.api.planning.human.CycleBreakRule;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.ModNamespaceRule;
import dev.vfyjxf.cirrus.api.planning.human.PinResourceRecipeFeedback;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.RejectStepFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningSession;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystemTags;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.execution.InteractionExecutionPlan;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionApi;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionContext;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionGuideResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionPerformResult;
import dev.vfyjxf.cirrus.api.recipe.execution.interaction.InteractionProbeResult;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeScreeningHints;
import dev.vfyjxf.cirrus.api.recipe.screening.RecipeUsePolicy;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSource;
import dev.vfyjxf.cirrus.api.recipe.storage.StorageSourceType;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.config.CalculatorRuleSelection;
import dev.vfyjxf.cirrus.content.ContentApiImpl;
import dev.vfyjxf.cirrus.content.ContentRegistryBuilder;
import dev.vfyjxf.cirrus.internal.InternalPlugin;
import dev.vfyjxf.cirrus.internal.performance.CalculatorSyntheticScenario;
import dev.vfyjxf.cirrus.planning.adapter.ClassifierVerdictBridge;
import dev.vfyjxf.cirrus.planning.bridge.CirrusCatalogAdapter;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.planning.bridge.ExecutionPlanBridge;
import dev.vfyjxf.cirrus.planning.bridge.StorageInventorySnapshotAdapter;
import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultCostMetadataProvider;
import dev.vfyjxf.cirrus.planning.core.cost.PlanCosts;
import dev.vfyjxf.cirrus.planning.core.cost.StepCosts;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultSystemAdmissionPolicy;
import dev.vfyjxf.cirrus.planning.core.graph.MachineCapabilityGroups;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.human.LayeredHumanGuidanceStore;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.classify.RecipeClassificationService;
import dev.vfyjxf.cirrus.recipe.execution.ExecutionTranslator;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.compile;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.generousBudget;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.rec;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.request;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BridgeRuntimeAndPersistenceTest {

    private static final StorageSourceType<StorageSource> stubSourceType = new StorageSourceType<>(
        Namespace.of("test", "stub_source"),
        null
    );

    @Test
    void worldFacilityRegistryPersistsAllEightRuleTypesAndHandlesUnknownKindGracefully() {
        WorldFacilityRegistry reg = WorldFacilityRegistry.createDetached();
        CompiledRecipeKey rk = new CompiledRecipeKey("minecraft:crafting", Optional.of("test:r1"), 12345L);
        ResourceKey iron = item("minecraft:iron_ingot");
        ResourceKey oak = item("minecraft:oak_planks");

        reg.declareOwnedMachine("minecraft:furnace", MachineCapabilityGroups.capabilityOf("minecraft:smelting"), 2, 1.5, true);
        reg.putAutomatedLine(new AutomatedLine("line:iron", iron, 60.0, 0.5, 128L, true));
        reg.putAutomatedRecipe(new AutomatedRecipeCapability(rk, 2.0, 4));
        reg.unlockSystem("jei:category/gregtech/bender");

        List<GuidanceRule> allRules = List.of(
            new RecipeRule("r1", GuidanceScope.worldSave, RuleStrength.hardBan, rk),
            new SystemRule("r2", GuidanceScope.worldSave, RuleStrength.hardPin, "jei:category/gregtech/bender", true),
            new MachineRule("r3", GuidanceScope.worldSave, RuleStrength.softPrefer, "minecraft:furnace", true),
            new ResourceRule("r4", GuidanceScope.worldSave, RuleStrength.hardPin, iron, true),
            new TagDefaultRule("r5", GuidanceScope.worldSave, RuleStrength.hardPin, "minecraft:planks", oak),
            new CycleBreakRule("r6", GuidanceScope.worldSave, RuleStrength.hardBan, iron, rk),
            new CostOverrideRule(
                "r7",
                GuidanceScope.worldSave,
                RuleStrength.hardPin,
                iron,
                new CostVector(1, 2, 3, 4, 5)
            ),
            new ModNamespaceRule("r8", GuidanceScope.worldSave, RuleStrength.softPenalize, "create")
        );
        for (GuidanceRule rule : allRules) {
            reg.putWorldSaveRule(rule);
        }

        JsonElement encoded = reg.encodeToJson().orElseThrow();
        WorldFacilityRegistry reloaded = WorldFacilityRegistry.loadFromJson(encoded);
        assertEquals(2, reloaded.facilitySnapshot().ownedCountOf("minecraft:furnace"));
        assertEquals(1, reloaded.facilitySnapshot().automatedLines().size());
        assertEquals(1, reloaded.facilitySnapshot().automatedRecipes().size());
        assertTrue(reloaded.facilitySnapshot().unlockedSystems().contains("jei:category/gregtech/bender"));
        assertEquals(8, reloaded.worldSaveRules().size());

        JsonElement bad = JsonParser.parseString(
            "{\"worldSaveRules\":[{\"kind\":\"unknown_future_rule\",\"ruleId\":\"x\",\"strength\":\"HARD_BAN\"}]}"
        );
        WorldFacilityRegistry graceful = WorldFacilityRegistry.loadFromJson(bad);
        assertNotNull(graceful);
        assertTrue(graceful.worldSaveRules().isEmpty());
    }

    @Test
    void sessionPersistToWorldWritesDirectlyToWorldFacilityRegistry() {
        ResourceKey ore = item("minecraft:cobblestone");
        ResourceKey p1 = item("mod:p1");
        CompiledRecipeGraph g = compile(
            List.of(
                rec("mod:s1", "r_a", List.of("mod:m1"), List.of(slot(ore, 1)), List.of(out(p1, 1))),
                rec("mod:s2", "r_b", List.of("mod:m2"), List.of(slot(ore, 1)), List.of(out(p1, 1)))
            )
        );
        CompiledRecipe rB = g.producersOf(p1).stream().filter(r -> r.key().systemId().equals("mod:s2")).findFirst()
                .orElseThrow();

        WorldFacilityRegistry reg = WorldFacilityRegistry.createDetached();
        LayeredHumanGuidanceStore store = new LayeredHumanGuidanceStore(reg.worldSaveListener());
        DefaultPlanningService svc = new DefaultPlanningService(
            g,
            DefaultCostMetadataProvider.instance,
            store,
            reg.machinePersistenceListener()
        );
        PlanningRequest req = request(
            g,
            List.of(new TargetDemand(p1, 1)),
            InventorySnapshot.empty,
            FacilitySnapshot.empty,
            DefaultHumanGuidanceView.empty(),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );

        try (PlanningSession session = svc.openSession(req)) {
            if (!session.currentResult().decisionPrompts().isEmpty()) {
                DecisionPrompt prompt = session.currentResult().decisionPrompts().get(0);
                session.refine(new AnswerPromptFeedback(prompt.promptId(), prompt.options().get(0).optionId(), true));
            }
            session.refine(new DeclareOwnedMachineFeedback("mod:m1", 2, true));
            session.refine(
                new ApplyRulesFeedback(
                    List.of(new ResourceRule("free_ore", GuidanceScope.worldSave, RuleStrength.hardPin, ore, true)),
                    true
                )
            );
            session.refine(new PinResourceRecipeFeedback(p1, rB.key(), true));
            ProductionPlan cur = session.currentResult().paretoPlans().get(0);
            CraftStep cs = cur.steps().stream().filter(CraftStep.class::isInstance).map(CraftStep.class::cast)
                    .findFirst().orElseThrow();
            session.refine(new RejectStepFeedback(cur.planId(), cs.stepId(), false, true));
        }

        assertEquals(2, reg.facilitySnapshot().ownedCountOf("mod:m1"));
        assertTrue(reg.worldSaveRules().size() >= 3);
    }

    @Test
    void executionPlanBridgeResolvesEmptyIdSourceRefAndTranslatesOutline() {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.create(0);
        RecipeEntry<?> targetEntry = scenario.target();

        ResourceKey a = item("minecraft:cobblestone");
        ResourceKey b = item("mod:empty_id_out");
        RawRecipeRecord emptyIdRec = new RawRecipeRecord(
            "minecraft:crafting",
            "vanilla",
            Optional.empty(),
            List.of("minecraft:crafting_table"),
            List.of(slot(a, 1)),
            List.of(out(b, 1)),
            Map.of(),
            1L
        );
        CompiledRecipeGraph g = compile(List.of(emptyIdRec));
        CompiledRecipe compiled = g.producersOf(b).get(0);
        assertTrue(compiled.key().explicitRecipeId().isEmpty());

        List<RecipeEntry<?>> entries = List.of(targetEntry);
        Optional<RecipeEntry<?>> resolved = ExecutionPlanBridge.resolveEntry(g, compiled.key(), entries);
        assertTrue(resolved.isPresent());
        assertSame(targetEntry, resolved.get());

        // Build outline containing CraftStep + all 4 task kinds -> ExecutionTranslator.translate succeeds.
        List<PlanStep> steps = List.of(
            new AcquireStep("s001", List.of(), a, 2, AcquisitionKind.manualGather, StepCosts.freeBaseCost),
            new ProvisionMachineStep("s002", List.of(), "mod:m1", Optional.empty(), 1, false, StepCosts.freeBaseCost),
            new SetupLineStep("s003", List.of(), "mod:sys", "mod:m1", b, StepCosts.freeBaseCost),
            new LinePullStep("s004", List.of(), "line:a", a, 1, StepCosts.freeBaseCost),
            new CraftStep(
                "s005",
                List.of("s001"),
                compiled.key(),
                2,
                Optional.of("minecraft:crafting_table"),
                false,
                Map.of(0, new SlotBinding(a, List.of())),
                Map.of(),
                StepCosts.freeBaseCost
            )
        );
        ProductionPlan dummyPlan = new ProductionPlan(
            "p1",
            "test",
            steps,
            PreparationSummary.empty,
            List.of(),
            PlanCosts.aggregate(steps),
            new VerificationCertificate(true, true, true, true, true, true, List.of())
        );

        ExecutionPlanBridge.ExecutionOutline outline = ExecutionPlanBridge
                .buildOutline(dummyPlan, key -> ExecutionPlanBridge.resolveEntry(g, key, entries));
        assertEquals(1, outline.batches().size());
        assertEquals(2, outline.batches().get(0).times());
        assertEquals(4, outline.tasks().size());

        InteractionApi stubInteraction = new InteractionApi() {
            @Override
            public InteractionProbeResult probe(RecipeEntry<?> recipe) {
                return new InteractionProbeResult.Supported(Namespace.of("test", "bench"), true, true);
            }

            @Override
            public InteractionGuideResult guide(RecipeEntry<?> recipe) {
                throw new UnsupportedOperationException();
            }

            @Override
            public InteractionPerformResult perform(InteractionContext context) {
                throw new UnsupportedOperationException();
            }

            @Override
            public int maxExecuteBatchSize(InteractionContext context) {
                return 16;
            }
        };
        Optional<InteractionExecutionPlan> execPlan = new ExecutionTranslator(stubInteraction)
                .translate(dummyPlan, outline, key -> ExecutionPlanBridge.resolveEntry(g, key, entries));
        assertTrue(execPlan.isPresent());
        assertFalse(execPlan.get().steps().isEmpty());
    }

    @Test
    void catalogAdapterPreSkipsExcludedDisplayAndMergesSystemRoutingHints() {
        AtomicBoolean excludedEnumerated = new AtomicBoolean(false);
        RecipeSystem<String> excludedSys = new RecipeSystem<>() {
            @Override
            public Namespace systemId() {
                return Namespace.of("jei", "category/minecraft/crafting");
            }

            @Override
            public Class<?> basicType() {
                return String.class;
            }

            @Override
            public @Nullable ResourceLocation getId(String recipe) {
                return null;
            }

            @Override
            public @Nullable String findRecipe(String uniqueId) {
                return null;
            }

            @Override
            public @Nullable String findRecipe(ResourceLocation registryName) {
                return null;
            }

            @Override
            public int hash(String recipe) {
                return recipe.hashCode();
            }

            @Override
            public boolean sameRecipe(String r1, String r2) {
                return r1.equals(r2);
            }

            @Override
            public RichIterable<String> allRecipes() {
                excludedEnumerated.set(true);
                return Lists.immutable.of("should_not_run");
            }
        };
        CirrusCatalogAdapter adapter = new CirrusCatalogAdapter();
        CirrusCatalogAdapter.SystemCapturePlan plan = new CirrusCatalogAdapter.SystemCapturePlan(
            RecipeApiImpl.attachEpoch(),
            List.of(
                new CirrusCatalogAdapter.SystemSnapshot(
                    excludedSys,
                    List.of(),
                    Set.of(RecipeSystemTags.displayOnly.toString())
                )
            )
        );
        Optional<CirrusCatalogAdapter.CapturedCatalog> captured = adapter.capture(plan, 1L, () -> false);
        assertTrue(captured.isPresent());
        assertFalse(excludedEnumerated.get(), "EXCLUDED_DISPLAY system must be skipped before allRecipes()");

        // Verify ClassifierVerdictBridge merges systemHintPolicies cleanly.
        DefaultSystemAdmissionPolicy merged = ClassifierVerdictBridge.classifyToPolicy(
            DefaultSystemAdmissionPolicy.defaults(),
            RecipeClassificationService.classifier(),
            List.of(),
            new long[0],
            Map.of(
                10L,
                RecipeUsePolicy.disabledByDefault,
                20L,
                RecipeUsePolicy.lastResort,
                30L,
                RecipeUsePolicy.discouraged
            )
        );
        RawRecipeRecord r10 = new RawRecipeRecord(
            "mod:s",
            "custom",
            Optional.of("r10"),
            List.of(),
            List.of(slot(item("minecraft:stone"), 1)),
            List.of(out(item("mod:x"), 1)),
            Map.of(),
            10L
        );
        RawRecipeRecord r20 = new RawRecipeRecord(
            "mod:s",
            "custom",
            Optional.of("r20"),
            List.of(),
            List.of(slot(item("minecraft:stone"), 1)),
            List.of(out(item("mod:x"), 1)),
            Map.of(),
            20L
        );
        RawRecipeRecord r30 = new RawRecipeRecord(
            "mod:s",
            "custom",
            Optional.of("r30"),
            List.of(),
            List.of(slot(item("minecraft:stone"), 1)),
            List.of(out(item("mod:x"), 1)),
            Map.of(),
            30L
        );
        assertEquals(SystemAdmission.excludedDisplay, merged.classifyRecipe(r10));
        assertEquals(RecipeAdvisory.lastResort, merged.advisoryOf(r20));
        assertEquals(RecipeAdvisory.discouraged, merged.advisoryOf(r30));
    }

    @Test
    void runtimeAsyncReloadPublishesSnapshotAndClosesNonBlockingly() throws Exception {
        CalculatorSyntheticScenario.Scenario scenario = CalculatorSyntheticScenario.create(2);
        RecipeApiImpl previous = CalculatorSyntheticScenario.attachRecipeApi(scenario.system());
        CirrusPlanningRuntime runtime = new CirrusPlanningRuntime();
        try {
            Optional<CirrusPlanningRuntime.PublishedSnapshot> snap1 = runtime.reloadFromRuntimeAsync()
                    .get(10, TimeUnit.SECONDS);
            assertTrue(snap1.isPresent());
            assertEquals(1L, runtime.publishedVersion());
            assertTrue(runtime.currentGraph().isPresent());
            assertTrue(runtime.currentService().isPresent());

            // Second reload increments version atomically while previous remains readable.
            Optional<CirrusPlanningRuntime.PublishedSnapshot> snap2 = runtime.reloadFromRuntimeAsync()
                    .get(10, TimeUnit.SECONDS);
            assertTrue(snap2.isPresent());
            assertEquals(2L, runtime.publishedVersion());

            long closeStart = System.nanoTime();
            runtime.close();
            long closeMs = (System.nanoTime() - closeStart) / 1_000_000L;
            assertTrue(closeMs < 200L, "close() must be non-blocking, took " + closeMs + "ms");
            assertTrue(runtime.isClosed());
            assertTrue(runtime.currentSnapshot().isEmpty());
        } finally {
            runtime.close();
            CalculatorSyntheticScenario.restoreRecipeApi(previous);
        }
    }

    @Test
    void storageAdapterRespectsReservedLedgerAndRuleSelectionMapsAllPresets() {
        StorageSource emptyNonProviding = new StorageSource() {
            @Override
            public StorageSourceType<?> type() {
                return stubSourceType;
            }

            @Override
            public Object identifier() {
                return "stub_src";
            }

            @Override
            public RichIterable<? extends ContentVariant> contents() {
                return Lists.immutable.empty();
            }

            @Override
            public boolean canProvide() {
                return false;
            }
        };
        ResourceKey stone = item("minecraft:stone");
        InventorySnapshot inv = StorageInventorySnapshotAdapter
                .fromSources(List.of(), List.of(emptyNonProviding), new StockLedger(Map.of(stone, 5L)));
        assertEquals(0L, inv.usableAmount(stone));

        for (CalculatorRuleSelection sel : CalculatorRuleSelection.values()) {
            PlanningProfile profile = sel.toPlanningProfile();
            assertEquals(sel.toObjectivePreset(), profile.preset());
        }

        CalculatorSyntheticScenario.Scenario synth = CalculatorSyntheticScenario.create(6);
        CompiledRecipeGraph synthGraph = synth.compileGraph();
        assertTrue(synthGraph.stats().compiledRecipes() > 0);
        assertTrue(synthGraph.allProducibleResources().contains(synth.targetResourceKey()));
    }

    @Test
    void executionPlanBridgeConcretizesSlotBindingsAndHandlesConvertAndCycleLoops() {
        ContentApiImpl previousContent = ContentApiImpl.instance();
        ContentRegistryBuilder contentBuilder = new ContentRegistryBuilder();
        InternalPlugin internal = new InternalPlugin();
        internal.registerContentType(contentBuilder);
        internal.registerContentVariant(contentBuilder);
        internal.registerContentResolver(contentBuilder);
        ContentApiImpl.attach(new ContentApiImpl(contentBuilder));
        try {
            ContentStack<?, ?> oakStack = ContentStack.fromStack(new ItemStack(Items.OAK_PLANKS), 2L);
            ContentStack<?, ?> birchStack = ContentStack.fromStack(new ItemStack(Items.BIRCH_PLANKS), 2L);
            ContentStack<?, ?> stickStack = ContentStack.fromStack(new ItemStack(Items.STICK), 4L);
            InputContent multiPlankInput = InputContent.fromStackList(List.of(oakStack, birchStack), 2L);
            OutputContent stickOutput = OutputContent.fromStack(stickStack);

            RecipeSystem<String> sys = new RecipeSystem<>() {
                @Override
                public Namespace systemId() {
                    return Namespace.of("test", "bridge");
                }

                @Override
                public Class<?> basicType() {
                    return String.class;
                }

                @Override
                public @Nullable ResourceLocation getId(String recipe) {
                    return ResourceLocation.fromNamespaceAndPath("test", recipe);
                }

                @Override
                public @Nullable String findRecipe(String uniqueId) {
                    return uniqueId;
                }

                @Override
                public @Nullable String findRecipe(ResourceLocation registryName) {
                    return registryName.getPath();
                }

                @Override
                public int hash(String recipe) {
                    return recipe.hashCode();
                }

                @Override
                public boolean sameRecipe(String r1, String r2) {
                    return r1.equals(r2);
                }

                @Override
                public RichIterable<String> allRecipes() {
                    return Lists.immutable.of("planks_to_sticks");
                }

                @Override
                public RoutingRecipeView routingView(ResolveContext context, String recipe) {
                    return new RoutingRecipeView(List.of(multiPlankInput), List.of(stickOutput));
                }

                @Override
                public RecipeScreeningHints routingHints(String recipe) {
                    return new RecipeScreeningHints(RecipeUsePolicy.lastResort, 0, Set.of(), null);
                }
            };

            CirrusCatalogAdapter adapter = new CirrusCatalogAdapter();
            long epoch = RecipeApiImpl.attachEpoch();
            CirrusCatalogAdapter.SystemCapturePlan capPlan = new CirrusCatalogAdapter.SystemCapturePlan(
                epoch,
                List.of(new CirrusCatalogAdapter.SystemSnapshot(sys, List.of("minecraft:crafting_table")))
            );
            CirrusCatalogAdapter.CapturedCatalog captured = adapter.capture(capPlan, 1L, () -> false).orElseThrow();
            assertEquals(1, captured.snapshot().recipes().size());
            assertEquals(RecipeAdvisory.lastResort, captured.policy().advisoryOf(captured.snapshot().recipes().get(0)));
            CirrusCatalogAdapter.SystemCapturePlan stalePlan = new CirrusCatalogAdapter.SystemCapturePlan(
                epoch - 1L,
                capPlan.systems()
            );
            assertTrue(adapter.capture(stalePlan, 2L, () -> false).isEmpty(), "Stale attachEpoch must be discarded");

            RecipeEntry<String> entry = RecipeEntry.of(sys, "planks_to_sticks");
            CompiledRecipeKey rk = new CompiledRecipeKey("test:bridge", Optional.of("test:planks_to_sticks"), 1L);
            ResourceKey birchKey = item("minecraft:birch_planks");
            ResourceKey stickKey = item("minecraft:stick");

            CraftStep craftStep = new CraftStep(
                "s001",
                List.of(),
                rk,
                1,
                Optional.empty(),
                false,
                Map.of(0, new SlotBinding(birchKey, List.of())),
                Map.of(),
                StepCosts.freeBaseCost
            );
            ConvertStep convertStep = new ConvertStep(
                "s002",
                List.of(),
                rk,
                2,
                Map.of(0, new SlotBinding(birchKey, List.of())),
                StepCosts.freeBaseCost
            );
            CraftStep loopA = new CraftStep(
                "t0",
                List.of(),
                rk,
                1,
                Optional.empty(),
                false,
                Map.of(0, new SlotBinding(birchKey, List.of())),
                Map.of(),
                StepCosts.freeBaseCost
            );
            CraftStep loopB = new CraftStep(
                "t1",
                List.of(),
                rk,
                1,
                Optional.empty(),
                false,
                Map.of(0, new SlotBinding(birchKey, List.of())),
                Map.of(),
                StepCosts.freeBaseCost
            );
            CycleLoopStep smallLoop = new CycleLoopStep(
                "s003",
                List.of(),
                new CompiledCycleMacroKey("m_small"),
                3L,
                Map.of(),
                List.of(loopA, loopB),
                Map.of(stickKey, 4L),
                StepCosts.freeBaseCost
            );
            CycleLoopStep largeLoop = new CycleLoopStep(
                "s004",
                List.of(),
                new CompiledCycleMacroKey("m_large"),
                70L,
                Map.of(),
                List.of(loopA, loopB),
                Map.of(stickKey, 4L),
                StepCosts.freeBaseCost
            );

            List<PlanStep> allSteps = List.of(craftStep, convertStep, smallLoop, largeLoop);
            ProductionPlan dummyPlan = new ProductionPlan(
                "p_bridge",
                "bridge",
                allSteps,
                PreparationSummary.empty,
                List.of(),
                PlanCosts.aggregate(allSteps),
                new VerificationCertificate(true, true, true, true, true, true, List.of())
            );

            ExecutionPlanBridge.ExecutionOutline outline = ExecutionPlanBridge
                    .buildOutline(dummyPlan, k -> Optional.of(entry));
            // 1 craft + 1 convert + (3 * 2 unrolled smallLoop) + (2 collapsed largeLoop) = 10 batches.
            assertEquals(10, outline.batches().size());
            assertEquals(70, outline.batches().get(8).times());
            assertEquals(70, outline.batches().get(9).times());

            InputContent boundInput = outline.batches().get(0).inputs().get(0).input();
            assertTrue(
                boundInput instanceof StackContent<?, ?>,
                "Expected concretized StackContent, got " + boundInput.getClass()
            );
            assertEquals(2L, boundInput.amount());
            assertEquals(
                Items.BIRCH_PLANKS,
                ((ItemStack) ((StackContent<?, ?>) boundInput).content().value()).getItem()
            );

            StorageSource accessibleSource = new StorageSource() {
                @Override
                public StorageSourceType<?> type() {
                    return stubSourceType;
                }

                @Override
                public Object identifier() {
                    return "chest_1";
                }

                @Override
                public RichIterable<? extends ContentVariant> contents() {
                    return Lists.immutable.of(ContentStack.fromStack(new ItemStack(Items.COBBLESTONE), 10L));
                }

                @Override
                public boolean canProvide() {
                    return true;
                }
            };
            ResourceKey cobbleKey = item("minecraft:cobblestone");
            InventorySnapshot inv = StorageInventorySnapshotAdapter.fromSources(
                List.of(new ItemStack(Items.COBBLESTONE, 5)),
                List.of(accessibleSource),
                new StockLedger(Map.of(cobbleKey, 3L))
            );
            assertEquals(15L, inv.available().amountOf(cobbleKey));
            assertEquals(12L, inv.usableAmount(cobbleKey));
        } finally {
            if (previousContent != null) {
                ContentApiImpl.attach(previousContent);
            }
        }
    }
}
