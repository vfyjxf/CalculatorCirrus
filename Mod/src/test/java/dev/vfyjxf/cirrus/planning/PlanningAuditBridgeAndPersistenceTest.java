package dev.vfyjxf.cirrus.planning;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedLine;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.human.CostOverrideRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.execution.InputBinding;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.RoutingRecipeView;
import dev.vfyjxf.cirrus.planning.bridge.ExecutionPlanBridge;
import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import dev.vfyjxf.cirrus.planning.core.cost.PlanCosts;
import dev.vfyjxf.cirrus.planning.core.cost.StepCosts;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlanningAuditBridgeAndPersistenceTest {

    private static final int unrolledCycleLimit = 64;
    private static final int componentMaxStackSize = 16;

    @Test
    void slotBindingKeepsDataComponentsOnConcretizedExecutionInput() {
        ComponentStackKey componentKey = componentKey(Items.IRON_INGOT, componentMaxStackSize);
        ContentStack<?, ?> componentStack = componentKey.toStack(2L);
        ComponentStackKey plainKey = ComponentStackKey.fromItemStack(new ItemStack(Items.IRON_INGOT)).orElseThrow();
        ContentStack<?, ?> plainStack = plainKey.toStack(2L);
        InputContent candidateList = InputContent.fromStackList(List.of(componentStack, plainStack), 2L);
        RecipeEntry<String> entry = recipeEntry(List.of(candidateList), List.of(stickOutput()));

        CraftStep craft = new CraftStep(
            "s_component",
            List.of(),
            recipeKey(),
            1,
            Optional.empty(),
            false,
            Map.of(0, new SlotBinding(componentKey, List.of())),
            Map.of(),
            StepCosts.freeBaseCost
        );
        ExecutionPlanBridge.ExecutionOutline outline = ExecutionPlanBridge
                .buildOutline(plan(craft), key -> Optional.of(entry));

        assertEquals(1, outline.batches().size());
        List<InputBinding> inputs = outline.batches().get(0).inputs();
        assertEquals(1, inputs.size());
        StackContent<?, ?> bound = stackContent(inputs.get(0).input());
        ComponentStackKey boundKey = ComponentStackKey.ofNormalized(bound.content());
        assertTrue(boundKey.hasComponents(), "concretized binding must keep the chosen data components");
        assertEquals(componentKey, boundKey);
        assertEquals(componentKey.canonicalKey(), boundKey.canonicalKey());
        assertEquals(
            Integer.valueOf(componentMaxStackSize),
            ((ItemStack) bound.content().value()).get(DataComponents.MAX_STACK_SIZE)
        );
    }

    @Test
    void valueContentInputDoesNotShiftLaterSlotIndices() {
        ComponentStackKey oak = item("minecraft:oak_planks");
        ComponentStackKey birch = item("minecraft:birch_planks");
        ComponentStackKey blaze = item("minecraft:blaze_rod");
        ComponentStackKey stick = item("minecraft:stick");
        ComponentStackKey sugar = item("minecraft:sugar");

        InputContent first = InputContent.fromStackList(List.of(oak.toStack(1L), birch.toStack(1L)), 1L);
        InputContent time = InputContent.fromTimeTick(200);
        InputContent second = InputContent.fromStackList(List.of(blaze.toStack(1L), stick.toStack(1L)), 1L);
        InputContent third = InputContent.fromStackList(List.of(stick.toStack(1L), sugar.toStack(1L)), 1L);
        RecipeEntry<String> entry = recipeEntry(List.of(first, time, second, third), List.of(stickOutput()));

        CraftStep craft = new CraftStep(
            "s_slots",
            List.of(),
            recipeKey(),
            1,
            Optional.empty(),
            false,
            Map.of(
                0, new SlotBinding(birch, List.of()),
                1, new SlotBinding(blaze, List.of()),
                2, new SlotBinding(stick, List.of())
            ),
            Map.of(),
            StepCosts.freeBaseCost
        );
        ExecutionPlanBridge.ExecutionOutline outline = ExecutionPlanBridge
                .buildOutline(plan(craft), key -> Optional.of(entry));

        List<InputBinding> inputs = outline.batches().get(0).inputs();
        assertEquals(3, inputs.size(), "ValueContent(time) must not become an execution input binding");
        assertBoundTo(inputs.get(0).input(), birch);
        assertBoundTo(inputs.get(1).input(), blaze);
        assertBoundTo(inputs.get(2).input(), stick);
    }

    @Test
    void oversizedCycleLoopUnrollsIntoOrderedBoundedWaves() {
        RecipeEntry<String> entry = recipeEntry(List.of(), List.of(stickOutput()));
        CompiledRecipeKey key = recipeKey();
        CraftStep firstTemplateStep = new CraftStep(
            "tA", List.of(), key, 1L, Optional.empty(), false, Map.of(), Map.of(), StepCosts.freeBaseCost
        );
        CraftStep secondTemplateStep = new CraftStep(
            "tB", List.of(), key, 2L, Optional.empty(), false, Map.of(), Map.of(), StepCosts.freeBaseCost
        );
        CycleLoopStep loop = new CycleLoopStep(
            "cl_audit",
            List.of(),
            new CompiledCycleMacroKey("m_audit"),
            130L,
            Map.of(),
            List.of(firstTemplateStep, secondTemplateStep),
            Map.of(),
            StepCosts.freeBaseCost
        );
        ExecutionPlanBridge.ExecutionOutline outline = ExecutionPlanBridge
                .buildOutline(plan(loop), k -> Optional.of(entry));

        List<ExecutionPlanBridge.RecipeBatch> batches = outline.batches();
        assertEquals(unrolledCycleLimit * 2, batches.size());
        assertEquals("cl_audit#w0:tA", batches.get(0).stepId());
        assertEquals(3, batches.get(0).times());
        assertEquals("cl_audit#w0:tB", batches.get(1).stepId());
        assertEquals(6, batches.get(1).times());
        assertEquals("cl_audit#w2:tA", batches.get(4).stepId());
        assertEquals(2, batches.get(4).times());
        assertEquals("cl_audit#w2:tB", batches.get(5).stepId());
        assertEquals(4, batches.get(5).times());
        assertEquals("cl_audit#w63:tA", batches.get(126).stepId());
        assertEquals(2, batches.get(126).times());
        assertEquals("cl_audit#w63:tB", batches.get(127).stepId());
        assertEquals(4, batches.get(127).times());

        long firstStepIterations = 0L;
        long secondStepIterations = 0L;
        for (int wave = 0; wave < unrolledCycleLimit; wave++) {
            ExecutionPlanBridge.RecipeBatch waveFirst = batches.get(wave * 2);
            ExecutionPlanBridge.RecipeBatch waveSecond = batches.get(wave * 2 + 1);
            assertEquals("cl_audit#w" + wave + ":tA", waveFirst.stepId());
            assertEquals("cl_audit#w" + wave + ":tB", waveSecond.stepId());
            assertTrue(
                waveFirst.times() <= unrolledCycleLimit,
                "one wave must stay within " + unrolledCycleLimit + " iterations, got " + waveFirst.times()
            );
            assertEquals(waveFirst.times() * 2, waveSecond.times());
            firstStepIterations += waveFirst.times();
            secondStepIterations += waveSecond.times();
        }
        assertEquals(130L, firstStepIterations);
        assertEquals(260L, secondStepIterations);
    }

    @Test
    void costVectorCodecRoundTripsTripActiveSecondsThroughJsonAndNbt() {
        CostVector cost = new CostVector(1.5, 7.25, 2.5, 3.5, 4.5, 5.5);

        JsonElement json = WorldFacilityRegistry.costVectorCodec.encodeStart(JsonOps.INSTANCE, cost)
                .result().orElseThrow();
        CostVector jsonDecoded = WorldFacilityRegistry.costVectorCodec.parse(JsonOps.INSTANCE, json)
                .result().orElseThrow();
        assertEquals(cost, jsonDecoded);
        assertEquals(7.25, jsonDecoded.tripActiveSeconds(), 0.0);

        Tag nbt = WorldFacilityRegistry.costVectorCodec.encodeStart(NbtOps.INSTANCE, cost).result().orElseThrow();
        CostVector nbtDecoded = WorldFacilityRegistry.costVectorCodec.parse(NbtOps.INSTANCE, nbt)
                .result().orElseThrow();
        assertEquals(cost, nbtDecoded);
        assertEquals(7.25, nbtDecoded.tripActiveSeconds(), 0.0);
    }

    @Test
    void resourceKeyCodecRoundTripsDataComponentsThroughJsonAndNbt() {
        ComponentStackKey key = componentKey(Items.IRON_INGOT, componentMaxStackSize);

        JsonElement json = WorldFacilityRegistry.resourceKeyCodec.encodeStart(JsonOps.INSTANCE, key)
                .result().orElseThrow();
        ComponentStackKey jsonDecoded = WorldFacilityRegistry.resourceKeyCodec.parse(JsonOps.INSTANCE, json)
                .result().orElseThrow();
        assertComponentKey(key, jsonDecoded);

        Tag nbt = WorldFacilityRegistry.resourceKeyCodec.encodeStart(NbtOps.INSTANCE, key).result().orElseThrow();
        ComponentStackKey nbtDecoded = WorldFacilityRegistry.resourceKeyCodec.parse(NbtOps.INSTANCE, nbt)
                .result().orElseThrow();
        assertComponentKey(key, nbtDecoded);
    }

    @Test
    void facilityRegistryKeepsTripCostAndComponentKeysThroughJsonAndNbt() {
        ComponentStackKey componentKey = componentKey(Items.IRON_INGOT, componentMaxStackSize);
        CostVector overrideUnitCost = new CostVector(1.5, 7.25, 2.5, 3.5, 4.5, 5.5);
        WorldFacilityRegistry registry = WorldFacilityRegistry.createDetached();
        registry.declareOwnedMachine(
            MachineEntry.fromKey(componentKey),
            new MachineCapabilityId(Namespace.parse("test:audit")),
            2,
            1.0,
            false
        );
        registry.putAutomatedLine(new AutomatedLine("line:audit", componentKey, 12.0, 0.25, 32L, true));
        registry.putWorldSaveRule(
            new CostOverrideRule(
                "cost:audit", GuidanceScope.worldSave, RuleStrength.hardPin, componentKey, overrideUnitCost
            )
        );
        registry.putWorldSaveRule(
            new ResourceRule("resource:audit", GuidanceScope.worldSave, RuleStrength.hardPin, componentKey, false)
        );

        JsonElement json = registry.encodeToJson().orElseThrow();
        assertFacilityState(WorldFacilityRegistry.loadFromJson(json), componentKey, overrideUnitCost);

        CompoundTag tag = registry.save(new CompoundTag(), RegistryAccess.EMPTY);
        assertFacilityState(WorldFacilityRegistry.load(tag, RegistryAccess.EMPTY), componentKey, overrideUnitCost);
    }

    private static void assertFacilityState(
        WorldFacilityRegistry reloaded,
        ComponentStackKey expectedKey,
        CostVector expectedUnitCost
    ) {
        List<OwnedMachineGroup> ownedMachines = reloaded.facilitySnapshot().ownedMachines();
        assertEquals(1, ownedMachines.size());
        assertComponentKey(expectedKey, ownedMachines.get(0).machine().contentKey().orElseThrow());

        List<AutomatedLine> automatedLines = reloaded.facilitySnapshot().automatedLines();
        assertEquals(1, automatedLines.size());
        assertComponentKey(expectedKey, automatedLines.get(0).output());

        List<GuidanceRule> rules = reloaded.worldSaveRules();
        assertEquals(2, rules.size());
        CostOverrideRule costRule = rules.stream()
                .filter(CostOverrideRule.class::isInstance)
                .map(CostOverrideRule.class::cast)
                .findFirst()
                .orElseThrow();
        ResourceRule resourceRule = rules.stream()
                .filter(ResourceRule.class::isInstance)
                .map(ResourceRule.class::cast)
                .findFirst()
                .orElseThrow();
        assertEquals(expectedUnitCost, costRule.overrideUnitCost());
        assertEquals(7.25, costRule.overrideUnitCost().tripActiveSeconds(), 0.0);
        assertComponentKey(expectedKey, costRule.resource());
        assertComponentKey(expectedKey, resourceRule.resource());
    }

    private static void assertComponentKey(ComponentStackKey expected, ComponentStackKey actual) {
        assertTrue(actual.hasComponents(), "persisted key must still carry its data components");
        assertEquals(expected, actual);
        assertEquals(expected.canonicalKey(), actual.canonicalKey());
        assertEquals(
            Integer.valueOf(componentMaxStackSize),
            ((ItemStack) actual.stack().value()).get(DataComponents.MAX_STACK_SIZE)
        );
    }

    private static void assertBoundTo(InputContent input, ComponentStackKey expected) {
        StackContent<?, ?> bound = stackContent(input);
        ComponentStackKey actual = ComponentStackKey.ofNormalized(bound.content());
        assertEquals(expected, actual);
        assertEquals(expected.canonicalKey(), actual.canonicalKey());
    }

    private static StackContent<?, ?> stackContent(InputContent input) {
        assertTrue(
            input instanceof StackContent<?, ?>,
            "slot binding must concretize to a StackContent, got " + input.getClass().getName()
        );
        return (StackContent<?, ?>) input;
    }

    private static ComponentStackKey componentKey(Item item, int maxStackSize) {
        ItemStack stack = new ItemStack(item);
        stack.set(DataComponents.MAX_STACK_SIZE, maxStackSize);
        return ComponentStackKey.fromItemStack(stack).orElseThrow();
    }

    private static CompiledRecipeKey recipeKey() {
        return new CompiledRecipeKey(
            Namespace.parse("test:audit"),
            Optional.of(ResourceLocation.parse("test:audit_recipe")),
            7L
        );
    }

    private static OutputContent stickOutput() {
        return OutputContent.fromStack(item("minecraft:stick").toStack(1L));
    }

    private static ProductionPlan plan(PlanStep... steps) {
        List<PlanStep> stepList = List.of(steps);
        return new ProductionPlan(
            "p_audit",
            "test",
            stepList,
            PreparationSummary.empty,
            List.of(),
            PlanCosts.aggregate(stepList),
            new VerificationCertificate(true, true, true, true, true, true, List.of())
        );
    }

    private static RecipeEntry<String> recipeEntry(List<InputContent> inputs, List<OutputContent> outputs) {
        RecipeSystem<String> system = new RecipeSystem<>() {
            @Override
            public Namespace systemId() {
                return Namespace.of("test", "audit_bridge");
            }

            @Override
            public Class<?> basicType() {
                return String.class;
            }

            @Override
            public ResourceLocation getId(String recipe) {
                return ResourceLocation.fromNamespaceAndPath("test", recipe);
            }

            @Override
            public String findRecipe(String uniqueId) {
                return uniqueId;
            }

            @Override
            public String findRecipe(ResourceLocation registryName) {
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
                return Lists.immutable.of("audit_recipe");
            }

            @Override
            public RoutingRecipeView routingView(ResolveContext context, String recipe) {
                return new RoutingRecipeView(inputs, outputs);
            }
        };
        return RecipeEntry.of(system, "audit_recipe");
    }
}
