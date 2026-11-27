package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeCatalogSnapshot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmissionPolicy;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultRecipeGraphCompiler;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultSystemAdmissionPolicy;
import dev.vfyjxf.cirrus.planning.core.graph.MachineCapabilityGroups;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class PlanningTestSupport {

    private PlanningTestSupport() {}

    static ResourceKey item(String id) {
        return ResourceKey.item(id);
    }

    static ResourceKey fluid(String id) {
        return ResourceKey.fluid(id);
    }

    static RawIngredientSlot slot(ResourceKey key, long amount) {
        return new RawIngredientSlot(Optional.empty(), List.of(key), amount, SlotMatchMode.identity);
    }

    static RawIngredientSlot exactSlot(ResourceKey key, long amount) {
        return new RawIngredientSlot(Optional.empty(), List.of(key), amount, SlotMatchMode.identityAndComponent);
    }

    static RawIngredientSlot tagSlot(String tagId, List<ResourceKey> members, long amount) {
        return new RawIngredientSlot(Optional.of(tagId), members, amount, SlotMatchMode.identity);
    }

    static RawIngredientSlot durabilityCatalyst(ResourceKey key, int durPerBatch, int maxDur) {
        return new RawIngredientSlot(Optional.empty(), List.of(key), 0L, SlotMatchMode.identity, durPerBatch, maxDur);
    }

    static RawOutputSlot out(ResourceKey key, long amount) {
        return new RawOutputSlot(key, amount);
    }

    static RawRecipeRecord rec(
        String systemId,
        String id,
        List<String> machines,
        List<RawIngredientSlot> ins,
        List<RawOutputSlot> outs
    ) {
        String source = systemId.startsWith("jei:")
                ? "jei"
                : (systemId.startsWith("minecraft:") ? "vanilla" : "custom");
        return new RawRecipeRecord(
            systemId,
            source,
            id == null || id.isEmpty() ? Optional.empty() : Optional.of(id),
            machines,
            ins,
            outs,
            Map.of()
        );
    }

    static CompiledRecipeGraph compile(List<RawRecipeRecord> records) {
        return compile(records, DefaultSystemAdmissionPolicy.defaults());
    }

    static CompiledRecipeGraph compile(List<RawRecipeRecord> records, SystemAdmissionPolicy policy) {
        return new DefaultRecipeGraphCompiler().compile(new RawRecipeCatalogSnapshot(1L, records), policy);
    }

    static OwnedMachineGroup owned(String machineId, MachineCapabilityId cap, int count) {
        return new OwnedMachineGroup(machineId, cap, count, 1.0, false);
    }

    static OwnedMachineGroup ownedCraftingTable() {
        return owned("minecraft:crafting_table", MachineCapabilityGroups.capabilityOf("minecraft:crafting"), 1);
    }

    static OwnedMachineGroup ownedFurnace() {
        return owned("minecraft:furnace", MachineCapabilityGroups.capabilityOf("minecraft:smelting"), 1);
    }

    static OwnedMachineGroup ownedStonecutter() {
        return owned("minecraft:stonecutter", MachineCapabilityGroups.capabilityOf("minecraft:stonecutting"), 1);
    }

    static FacilitySnapshot fac(OwnedMachineGroup... groups) {
        return new FacilitySnapshot(List.of(groups), List.of(), List.of(), Set.of());
    }

    static PlanningBudget generousBudget() {
        return new PlanningBudget(Duration.ofMillis(1500), Duration.ofMillis(4000), 2500, 6000, 3, 5, true, true);
    }

    static PlanningRequest request(
        CompiledRecipeGraph graph,
        List<TargetDemand> targets,
        InventorySnapshot inv,
        FacilitySnapshot fac,
        HumanGuidanceView guidance,
        PlanningProfile profile,
        PlanningBudget budget
    ) {
        AcquisitionOracle oracle = new DefaultAcquisitionOracle(graph, guidance);
        PlanningContext ctx = new PlanningContext(inv, fac, oracle, guidance, ProgressionState.overworldDefault);
        return new PlanningRequest(targets, ctx, profile, budget);
    }

    static PlanningResult plan(
        CompiledRecipeGraph graph,
        ResourceKey target,
        long amount,
        InventorySnapshot inv,
        FacilitySnapshot fac,
        List<GuidanceRule> rules
    ) {
        HumanGuidanceView guidance = rules.isEmpty()
                ? DefaultHumanGuidanceView.empty()
                : DefaultHumanGuidanceView.of(rules);
        PlanningRequest req = request(
            graph,
            List.of(new TargetDemand(target, amount)),
            inv,
            fac,
            guidance,
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );
        return new DefaultPlanningService(graph).planOnce(req);
    }

    static void warmUpJvm() {
        ResourceKey a = item("minecraft:cobblestone");
        ResourceKey b = item("test:warmup_out");
        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "minecraft:crafting",
                    "test:warm",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(a, 1)),
                    List.of(out(b, 1))
                )
            )
        );
        plan(g, b, 1L, InventorySnapshot.empty, fac(ownedCraftingTable()), List.of());
    }
}
