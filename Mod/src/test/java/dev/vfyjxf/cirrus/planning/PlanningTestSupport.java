package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
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
import dev.vfyjxf.cirrus.api.planning.model.PlanningTagKeys;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.planning.adapter.OfflineContentTypes;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultRecipeGraphCompiler;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultSystemAdmissionPolicy;
import dev.vfyjxf.cirrus.planning.core.graph.MachineCapabilityGroups;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

final class PlanningTestSupport {

    private PlanningTestSupport() {}

    static ComponentStackKey item(String id) {
        return OfflineContentTypes.item(id);
    }

    static ComponentStackKey fluid(String id) {
        return OfflineContentTypes.fluid(id);
    }

    static ComponentStackKey bare(Namespace contentType, String id) {
        return OfflineContentTypes.bare(contentType, id);
    }

    static Namespace ns(String id) {
        return Namespace.parse(id);
    }

    static MachineEntry machine(String id) {
        return OfflineContentTypes.machine(id);
    }

    static TagKey<?> itemTag(String id) {
        return PlanningTagKeys.itemTag(ResourceLocation.parse(id));
    }

    static TagKey<?> fluidTag(String id) {
        return PlanningTagKeys.fluidTag(ResourceLocation.parse(id));
    }

    static List<MachineEntry> machines(String... ids) {
        List<MachineEntry> out = new ArrayList<>(ids.length);
        for (String id : ids) {
            out.add(machine(id));
        }
        return List.copyOf(out);
    }

    static RawIngredientSlot slot(ComponentStackKey key, long amount) {
        return new RawIngredientSlot(Optional.empty(), List.of(key), amount, SlotMatchMode.identity);
    }

    static RawIngredientSlot exactSlot(ComponentStackKey key, long amount) {
        return new RawIngredientSlot(Optional.empty(), List.of(key), amount, SlotMatchMode.identityAndComponent);
    }

    static RawIngredientSlot tagSlot(TagKey<?> tag, List<ComponentStackKey> members, long amount) {
        return new RawIngredientSlot(Optional.of(tag), members, amount, SlotMatchMode.identity);
    }

    static RawIngredientSlot tagSlot(String tagId, List<ComponentStackKey> members, long amount) {
        return tagSlot(itemTag(tagId), members, amount);
    }

    static RawIngredientSlot durabilityCatalyst(ComponentStackKey key, int durPerBatch, int maxDur) {
        return new RawIngredientSlot(Optional.empty(), List.of(key), 0L, SlotMatchMode.identity, durPerBatch, maxDur);
    }

    static RawOutputSlot out(ComponentStackKey key, long amount) {
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
            Namespace.parse(systemId),
            source,
            id == null || id.isEmpty() ? Optional.empty() : Optional.of(ResourceLocation.parse(id)),
            machines(machines.toArray(String[]::new)),
            ins,
            outs,
            Map.of()
        );
    }

    static RawRecipeRecord rec(
        Namespace systemId,
        Optional<ResourceLocation> id,
        List<MachineEntry> machines,
        List<RawIngredientSlot> ins,
        List<RawOutputSlot> outs
    ) {
        return new RawRecipeRecord(systemId, "custom", id, machines, ins, outs, Map.of());
    }

    static CompiledRecipeGraph compile(List<RawRecipeRecord> records) {
        return compile(records, DefaultSystemAdmissionPolicy.defaults());
    }

    static CompiledRecipeGraph compile(List<RawRecipeRecord> records, SystemAdmissionPolicy policy) {
        return new DefaultRecipeGraphCompiler().compile(new RawRecipeCatalogSnapshot(1L, records), policy);
    }

    static OwnedMachineGroup owned(String machineId, MachineCapabilityId cap, int count) {
        return new OwnedMachineGroup(machine(machineId), cap, count, 1.0, false);
    }

    static OwnedMachineGroup owned(MachineEntry machine, MachineCapabilityId cap, int count) {
        return new OwnedMachineGroup(machine, cap, count, 1.0, false);
    }

    static MachineCapabilityId capability(String systemId) {
        return MachineCapabilityGroups.capabilityOf(Namespace.parse(systemId));
    }

    static OwnedMachineGroup ownedCraftingTable() {
        return owned("minecraft:crafting_table", capability("minecraft:crafting"), 1);
    }

    static OwnedMachineGroup ownedFurnace() {
        return owned("minecraft:furnace", capability("minecraft:smelting"), 1);
    }

    static OwnedMachineGroup ownedStonecutter() {
        return owned("minecraft:stonecutter", capability("minecraft:stonecutting"), 1);
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
        ComponentStackKey target,
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
        ComponentStackKey a = item("minecraft:cobblestone");
        ComponentStackKey b = item("test:warmup_out");
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
