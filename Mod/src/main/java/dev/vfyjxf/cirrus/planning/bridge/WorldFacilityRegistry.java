package dev.vfyjxf.cirrus.planning.bridge;

import com.google.gson.JsonElement;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedLine;
import dev.vfyjxf.cirrus.api.planning.context.AutomatedRecipeCapability;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.human.CostOverrideRule;
import dev.vfyjxf.cirrus.api.planning.human.CycleBreakRule;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceStore;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.ModNamespaceRule;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.recipe.machine.ItemMachineEntry;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineMatcher;
import dev.vfyjxf.cirrus.planning.core.graph.MachineCapabilityGroups;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

public final class WorldFacilityRegistry extends SavedData {

    private static final Logger LOGGER = LoggerFactory.getLogger("CalculatorCirrus FacilityRegistry");

    public static final String DATA_NAME = "calculator_cirrus_planning_facilities";
    private static final String NBT_DATA_KEY = "planning_data";

    private static final MachineMatcher DEFAULT_MACHINE_MATCHER = new MachineMatcher() {
        private final Namespace id = CirrusNamespaces.ofMod("machine_matcher/component_equivalence");

        @Override
        public Namespace id() {
            return id;
        }

        @Override
        public boolean matches(MachineEntry entry1, MachineEntry entry2) {
            if (entry1 == entry2) {
                return true;
            }
            if (entry1 == null || entry2 == null || entry1.isEmpty() || entry2.isEmpty()) {
                return false;
            }
            if (entry1 instanceof ItemMachineEntry a && entry2 instanceof ItemMachineEntry b) {
                return a.machineStack().matches(b.machineStack(), MatchStrategy.identityAndComponent);
            }
            return entry1.equals(entry2);
        }
    };

    private static final CopyOnWriteArrayList<MachineMatcher> MACHINE_MATCHERS =
            new CopyOnWriteArrayList<>(List.of(DEFAULT_MACHINE_MATCHER));

    private static volatile @Nullable WorldFacilityRegistry installed;

    private final Map<String, OwnedMachineGroup> ownedMachines = new LinkedHashMap<>();
    private final Map<String, AutomatedLine> automatedLines = new LinkedHashMap<>();
    private final Map<CompiledRecipeKey, AutomatedRecipeCapability> automatedRecipes = new LinkedHashMap<>();
    private final TreeSet<String> unlockedSystems = new TreeSet<>();
    private final Map<String, GuidanceRule> worldSaveRules = new LinkedHashMap<>();

    public static final Codec<ResourceKey> RESOURCE_KEY_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("contentType").forGetter(ResourceKey::contentTypeId),
            Codec.STRING.fieldOf("id").forGetter(ResourceKey::resourceId),
            Codec.STRING.optionalFieldOf("variant").forGetter(ResourceKey::canonicalVariantHash)
    ).apply(inst, ResourceKey::new));

    public static final Codec<CompiledRecipeKey> RECIPE_KEY_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("system").forGetter(CompiledRecipeKey::systemId),
            Codec.STRING.optionalFieldOf("explicitId").forGetter(CompiledRecipeKey::explicitRecipeId),
            Codec.LONG.fieldOf("hash").forGetter(CompiledRecipeKey::structuralHash64)
    ).apply(inst, CompiledRecipeKey::new));

    public static final Codec<MachineCapabilityId> CAPABILITY_CODEC =
            Codec.STRING.xmap(MachineCapabilityId::new, MachineCapabilityId::id);

    public static final Codec<CostVector> COST_VECTOR_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.DOUBLE.fieldOf("h").forGetter(CostVector::humanActiveSeconds),
            Codec.DOUBLE.fieldOf("p").forGetter(CostVector::passiveWaitSeconds),
            Codec.DOUBLE.fieldOf("k").forGetter(CostVector::machineSetupCapital),
            Codec.DOUBLE.fieldOf("s").forGetter(CostVector::materialScarcity),
            Codec.DOUBLE.fieldOf("c").forGetter(CostVector::cognitiveFriction)
    ).apply(inst, CostVector::new));

    public static final Codec<OwnedMachineGroup> OWNED_MACHINE_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("machineId").forGetter(OwnedMachineGroup::machineId),
            CAPABILITY_CODEC.fieldOf("capability").forGetter(OwnedMachineGroup::capability),
            Codec.INT.fieldOf("count").forGetter(OwnedMachineGroup::count),
            Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(OwnedMachineGroup::passiveSpeedMultiplier),
            Codec.BOOL.optionalFieldOf("automatedFeeding", false).forGetter(OwnedMachineGroup::automatedFeeding)
    ).apply(inst, OwnedMachineGroup::new));

    public static final Codec<AutomatedLine> AUTOMATED_LINE_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            Codec.STRING.fieldOf("lineId").forGetter(AutomatedLine::lineId),
            RESOURCE_KEY_CODEC.fieldOf("output").forGetter(AutomatedLine::output),
            Codec.DOUBLE.fieldOf("throughputPerMinute").forGetter(AutomatedLine::throughputPerMinute),
            Codec.DOUBLE.optionalFieldOf("humanAttentionSecondsPerPull", 0.0).forGetter(AutomatedLine::humanAttentionSecondsPerPull),
            Codec.LONG.optionalFieldOf("bufferedStock", 0L).forGetter(AutomatedLine::bufferedStock),
            Codec.BOOL.optionalFieldOf("continuous", true).forGetter(AutomatedLine::continuous)
    ).apply(inst, AutomatedLine::new));

    public static final Codec<AutomatedRecipeCapability> AUTOMATED_RECIPE_CODEC = RecordCodecBuilder.create(inst -> inst.group(
            RECIPE_KEY_CODEC.fieldOf("recipeKey").forGetter(AutomatedRecipeCapability::recipeKey),
            Codec.DOUBLE.fieldOf("passiveSecondsPerBatch").forGetter(AutomatedRecipeCapability::passiveSecondsPerBatch),
            Codec.INT.optionalFieldOf("parallelCrafters", 1).forGetter(AutomatedRecipeCapability::parallelCrafters)
    ).apply(inst, AutomatedRecipeCapability::new));

    private static final Codec<RuleStrength> STRENGTH_CODEC = Codec.STRING.comapFlatMap(
            s -> {
                try {
                    return DataResult.success(RuleStrength.valueOf(s));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown RuleStrength: " + s);
                }
            },
            Enum::name
    );

    private static final Codec<GuidanceScope> SCOPE_CODEC = Codec.STRING.comapFlatMap(
            s -> {
                try {
                    return DataResult.success(GuidanceScope.valueOf(s));
                } catch (IllegalArgumentException e) {
                    return DataResult.error(() -> "Unknown GuidanceScope: " + s);
                }
            },
            Enum::name
    );

    public static final Codec<GuidanceRule> GUIDANCE_RULE_CODEC = Codec.STRING.dispatch(
            "kind",
            WorldFacilityRegistry::ruleKind,
            WorldFacilityRegistry::ruleCodecForKind
    );

    public record PersistedState(
            List<OwnedMachineGroup> ownedMachines,
            List<AutomatedLine> automatedLines,
            List<AutomatedRecipeCapability> automatedRecipes,
            List<String> unlockedSystems,
            List<GuidanceRule> worldSaveRules
    ) {
        public static final Codec<PersistedState> CODEC = RecordCodecBuilder.create(inst -> inst.group(
                OWNED_MACHINE_CODEC.listOf().optionalFieldOf("ownedMachines", List.of()).forGetter(PersistedState::ownedMachines),
                AUTOMATED_LINE_CODEC.listOf().optionalFieldOf("automatedLines", List.of()).forGetter(PersistedState::automatedLines),
                AUTOMATED_RECIPE_CODEC.listOf().optionalFieldOf("automatedRecipes", List.of()).forGetter(PersistedState::automatedRecipes),
                Codec.STRING.listOf().optionalFieldOf("unlockedSystems", List.of()).forGetter(PersistedState::unlockedSystems),
                GUIDANCE_RULE_CODEC.listOf().optionalFieldOf("worldSaveRules", List.of()).forGetter(PersistedState::worldSaveRules)
        ).apply(inst, PersistedState::new));
    }

    private static final SavedData.Factory<WorldFacilityRegistry> FACTORY = new SavedData.Factory<>(
            WorldFacilityRegistry::new,
            WorldFacilityRegistry::load,
            (DataFixTypes) null
    );

    public WorldFacilityRegistry() {
    }

    public static WorldFacilityRegistry createDetached() {
        return new WorldFacilityRegistry();
    }

    public static WorldFacilityRegistry mount(ServerLevel overworld) {
        Objects.requireNonNull(overworld, "overworld");
        WorldFacilityRegistry registry = overworld.getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
        installed = registry;
        CirrusPlanningRuntime runtime = CirrusPlanningRuntime.sharedIfCreated();
        if (runtime != null) {
            registry.applyRulesTo(runtime.guidanceStore());
        }
        return registry;
    }

    public static void unmount() {
        installed = null;
    }

    public static Optional<WorldFacilityRegistry> installed() {
        return Optional.ofNullable(installed);
    }

    public static void registerMachineMatcher(MachineMatcher matcher) {
        Objects.requireNonNull(matcher, "matcher");
        MACHINE_MATCHERS.addIfAbsent(matcher);
    }

    public static List<MachineMatcher> machineMatchers() {
        return List.copyOf(MACHINE_MATCHERS);
    }

    public static boolean matchesMachine(MachineEntry owned, MachineEntry required) {
        for (MachineMatcher matcher : MACHINE_MATCHERS) {
            try {
                if (matcher.matches(owned, required)) {
                    return true;
                }
            } catch (RuntimeException ignored) {
            }
        }
        return false;
    }

    public static boolean ownsMachineFor(MachineEntry required, Iterable<? extends MachineEntry> ownedEntries) {
        for (MachineEntry owned : ownedEntries) {
            if (matchesMachine(owned, required)) {
                return true;
            }
        }
        return false;
    }

    public synchronized FacilitySnapshot facilitySnapshot() {
        return new FacilitySnapshot(
                new ArrayList<>(ownedMachines.values()),
                new ArrayList<>(automatedLines.values()),
                new ArrayList<>(automatedRecipes.values()),
                Set.copyOf(unlockedSystems)
        );
    }

    public synchronized void declareOwnedMachine(
            String machineId,
            MachineCapabilityId capability,
            int count,
            double passiveSpeedMultiplier,
            boolean automatedFeeding
    ) {
        Objects.requireNonNull(machineId, "machineId");
        Objects.requireNonNull(capability, "capability");
        if (count <= 0) {
            if (ownedMachines.remove(machineId) != null) {
                setDirty();
            }
            return;
        }
        ownedMachines.put(machineId, new OwnedMachineGroup(
                machineId, capability, count, passiveSpeedMultiplier, automatedFeeding));
        setDirty();
    }

    public synchronized void declareOwnedMachine(DeclareOwnedMachineFeedback feedback) {
        Objects.requireNonNull(feedback, "feedback");
        String machineId = feedback.machineId();
        if (feedback.ownedCount() <= 0) {
            if (ownedMachines.remove(machineId) != null) {
                setDirty();
            }
            return;
        }
        OwnedMachineGroup existing = ownedMachines.get(machineId);
        MachineCapabilityId cap = existing != null
                ? existing.capability()
                : inferCapabilityForMachine(machineId);
        double speed = existing != null ? existing.passiveSpeedMultiplier() : 1.0;
        boolean autoFeed = existing != null && existing.automatedFeeding();
        ownedMachines.put(machineId, new OwnedMachineGroup(machineId, cap, feedback.ownedCount(), speed, autoFeed));
        setDirty();
    }

    public synchronized void putAutomatedLine(AutomatedLine line) {
        Objects.requireNonNull(line, "line");
        automatedLines.put(line.lineId(), line);
        setDirty();
    }

    public synchronized void removeAutomatedLine(String lineId) {
        if (automatedLines.remove(lineId) != null) {
            setDirty();
        }
    }

    public synchronized void putAutomatedRecipe(AutomatedRecipeCapability capability) {
        Objects.requireNonNull(capability, "capability");
        automatedRecipes.put(capability.recipeKey(), capability);
        setDirty();
    }

    public synchronized void removeAutomatedRecipe(CompiledRecipeKey recipeKey) {
        if (automatedRecipes.remove(recipeKey) != null) {
            setDirty();
        }
    }

    public synchronized void unlockSystem(String systemId) {
        Objects.requireNonNull(systemId, "systemId");
        if (unlockedSystems.add(systemId)) {
            setDirty();
        }
    }

    public synchronized void lockSystem(String systemId) {
        if (unlockedSystems.remove(systemId)) {
            setDirty();
        }
    }

    public synchronized void putWorldSaveRule(GuidanceRule rule) {
        Objects.requireNonNull(rule, "rule");
        GuidanceRule scoped = withWorldSaveScope(rule);
        worldSaveRules.put(scoped.ruleId(), scoped);
        setDirty();
    }

    public synchronized void removeWorldSaveRule(String ruleId) {
        if (worldSaveRules.remove(ruleId) != null) {
            setDirty();
        }
    }

    public synchronized List<GuidanceRule> worldSaveRules() {
        return List.copyOf(worldSaveRules.values());
    }

    public synchronized void applyRulesTo(HumanGuidanceStore store) {
        Objects.requireNonNull(store, "store");
        for (GuidanceRule rule : worldSaveRules.values()) {
            store.putRule(rule);
        }
    }

    public Consumer<GuidanceRule> worldSaveListener() {
        return rule -> {
            if (rule != null && rule.scope() == GuidanceScope.WORLD_SAVE) {
                putWorldSaveRule(rule);
            }
        };
    }

    public Consumer<DeclareOwnedMachineFeedback> machinePersistenceListener() {
        return feedback -> {
            if (feedback != null && feedback.persistToWorld()) {
                declareOwnedMachine(feedback);
            }
        };
    }

    public synchronized PersistedState toPersistedState() {
        return new PersistedState(
                List.copyOf(ownedMachines.values()),
                List.copyOf(automatedLines.values()),
                List.copyOf(automatedRecipes.values()),
                List.copyOf(unlockedSystems),
                List.copyOf(worldSaveRules.values())
        );
    }

    public synchronized void loadPersistedState(PersistedState state) {
        Objects.requireNonNull(state, "state");
        ownedMachines.clear();
        for (OwnedMachineGroup g : state.ownedMachines()) {
            ownedMachines.put(g.machineId(), g);
        }
        automatedLines.clear();
        for (AutomatedLine l : state.automatedLines()) {
            automatedLines.put(l.lineId(), l);
        }
        automatedRecipes.clear();
        for (AutomatedRecipeCapability c : state.automatedRecipes()) {
            automatedRecipes.put(c.recipeKey(), c);
        }
        unlockedSystems.clear();
        unlockedSystems.addAll(state.unlockedSystems());
        worldSaveRules.clear();
        for (GuidanceRule r : state.worldSaveRules()) {
            GuidanceRule scoped = withWorldSaveScope(r);
            worldSaveRules.put(scoped.ruleId(), scoped);
        }
    }

    public synchronized Optional<JsonElement> encodeToJson() {
        return PersistedState.CODEC.encodeStart(JsonOps.INSTANCE, toPersistedState()).result();
    }

    public static WorldFacilityRegistry loadFromJson(JsonElement element) {
        WorldFacilityRegistry reg = new WorldFacilityRegistry();
        PersistedState.CODEC.parse(JsonOps.INSTANCE, element)
                .resultOrPartial(err -> LOGGER.warn("Failed decoding facility registry JSON: {}", err))
                .ifPresent(reg::loadPersistedState);
        return reg;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        PersistedState state = toPersistedState();
        PersistedState.CODEC.encodeStart(NbtOps.INSTANCE, state)
                .resultOrPartial(err -> LOGGER.error("Failed encoding facility registry NBT: {}", err))
                .ifPresent(encoded -> tag.put(NBT_DATA_KEY, encoded));
        return tag;
    }

    public static WorldFacilityRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldFacilityRegistry reg = new WorldFacilityRegistry();
        if (tag != null && tag.contains(NBT_DATA_KEY)) {
            Tag raw = tag.get(NBT_DATA_KEY);
            if (raw != null) {
                PersistedState.CODEC.parse(NbtOps.INSTANCE, raw)
                        .resultOrPartial(err -> LOGGER.warn("Failed decoding facility registry NBT: {}", err))
                        .ifPresent(reg::loadPersistedState);
            }
        }
        return reg;
    }

    private static MachineCapabilityId inferCapabilityForMachine(String machineId) {
        return switch (machineId) {
            case "minecraft:crafting_table" -> MachineCapabilityGroups.capabilityOf("minecraft:crafting");
            case "minecraft:furnace" -> MachineCapabilityGroups.capabilityOf("minecraft:smelting");
            case "minecraft:blast_furnace" -> MachineCapabilityGroups.capabilityOf("minecraft:blasting");
            case "minecraft:smoker" -> MachineCapabilityGroups.capabilityOf("minecraft:smoking");
            case "minecraft:stonecutter" -> MachineCapabilityGroups.capabilityOf("minecraft:stonecutting");
            case "minecraft:smithing_table" -> MachineCapabilityGroups.capabilityOf("minecraft:smithing");
            case "minecraft:brewing_stand" -> MachineCapabilityGroups.capabilityOf("minecraft:brewing");
            case "minecraft:campfire", "minecraft:soul_campfire" -> MachineCapabilityGroups.capabilityOf("minecraft:campfire_cooking");
            default -> {
                if (machineId.contains("furnace")) {
                    yield MachineCapabilityGroups.capabilityOf("minecraft:smelting");
                }
                if (machineId.contains("crafting_table") || machineId.contains("workbench")) {
                    yield MachineCapabilityGroups.capabilityOf("minecraft:crafting");
                }
                yield new MachineCapabilityId("cirrus:capability/machine/" + machineId.replace(':', '/'));
            }
        };
    }

    private static GuidanceRule withWorldSaveScope(GuidanceRule rule) {
        if (rule.scope() == GuidanceScope.WORLD_SAVE) {
            return rule;
        }
        return switch (rule) {
            case RecipeRule r -> new RecipeRule(r.ruleId(), GuidanceScope.WORLD_SAVE, r.strength(), r.recipeKey());
            case SystemRule r -> new SystemRule(r.ruleId(), GuidanceScope.WORLD_SAVE, r.strength(), r.systemId(), r.optInEnabled());
            case MachineRule r -> new MachineRule(r.ruleId(), GuidanceScope.WORLD_SAVE, r.strength(), r.machineId(), r.willingToBuildNew());
            case ResourceRule r -> new ResourceRule(r.ruleId(), GuidanceScope.WORLD_SAVE, r.strength(), r.resource(), r.treatAsFreeBaseSource());
            case TagDefaultRule r -> new TagDefaultRule(r.ruleId(), GuidanceScope.WORLD_SAVE, r.strength(), r.tagId(), r.preferredMember());
            case CycleBreakRule r -> new CycleBreakRule(r.ruleId(), GuidanceScope.WORLD_SAVE, r.strength(), r.targetResource(), r.bannedSourceRecipe());
            case CostOverrideRule r -> new CostOverrideRule(r.ruleId(), GuidanceScope.WORLD_SAVE, r.strength(), r.resource(), r.overrideUnitCost());
            case ModNamespaceRule r -> new ModNamespaceRule(r.ruleId(), GuidanceScope.WORLD_SAVE, r.strength(), r.modNamespace());
        };
    }

    private static String ruleKind(GuidanceRule rule) {
        return switch (rule) {
            case RecipeRule ignored -> "recipe";
            case SystemRule ignored -> "system";
            case MachineRule ignored -> "machine";
            case ResourceRule ignored -> "resource";
            case TagDefaultRule ignored -> "tag_default";
            case CycleBreakRule ignored -> "cycle_break";
            case CostOverrideRule ignored -> "cost_override";
            case ModNamespaceRule ignored -> "mod_namespace";
        };
    }

    private static com.mojang.serialization.MapCodec<? extends GuidanceRule> ruleCodecForKind(String kind) {
        return switch (kind) {
            case "recipe" -> RecordCodecBuilder.<RecipeRule>mapCodec(inst -> inst.group(
                    Codec.STRING.fieldOf("ruleId").forGetter(RecipeRule::ruleId),
                    SCOPE_CODEC.optionalFieldOf("scope", GuidanceScope.WORLD_SAVE).forGetter(RecipeRule::scope),
                    STRENGTH_CODEC.fieldOf("strength").forGetter(RecipeRule::strength),
                    RECIPE_KEY_CODEC.fieldOf("recipeKey").forGetter(RecipeRule::recipeKey)
            ).apply(inst, RecipeRule::new));
            case "system" -> RecordCodecBuilder.<SystemRule>mapCodec(inst -> inst.group(
                    Codec.STRING.fieldOf("ruleId").forGetter(SystemRule::ruleId),
                    SCOPE_CODEC.optionalFieldOf("scope", GuidanceScope.WORLD_SAVE).forGetter(SystemRule::scope),
                    STRENGTH_CODEC.fieldOf("strength").forGetter(SystemRule::strength),
                    Codec.STRING.fieldOf("systemId").forGetter(SystemRule::systemId),
                    Codec.BOOL.optionalFieldOf("optInEnabled", false).forGetter(SystemRule::optInEnabled)
            ).apply(inst, SystemRule::new));
            case "machine" -> RecordCodecBuilder.<MachineRule>mapCodec(inst -> inst.group(
                    Codec.STRING.fieldOf("ruleId").forGetter(MachineRule::ruleId),
                    SCOPE_CODEC.optionalFieldOf("scope", GuidanceScope.WORLD_SAVE).forGetter(MachineRule::scope),
                    STRENGTH_CODEC.fieldOf("strength").forGetter(MachineRule::strength),
                    Codec.STRING.fieldOf("machineId").forGetter(MachineRule::machineId),
                    Codec.BOOL.optionalFieldOf("willingToBuildNew", true).forGetter(MachineRule::willingToBuildNew)
            ).apply(inst, MachineRule::new));
            case "resource" -> RecordCodecBuilder.<ResourceRule>mapCodec(inst -> inst.group(
                    Codec.STRING.fieldOf("ruleId").forGetter(ResourceRule::ruleId),
                    SCOPE_CODEC.optionalFieldOf("scope", GuidanceScope.WORLD_SAVE).forGetter(ResourceRule::scope),
                    STRENGTH_CODEC.fieldOf("strength").forGetter(ResourceRule::strength),
                    RESOURCE_KEY_CODEC.fieldOf("resource").forGetter(ResourceRule::resource),
                    Codec.BOOL.optionalFieldOf("treatAsFreeBaseSource", false).forGetter(ResourceRule::treatAsFreeBaseSource)
            ).apply(inst, ResourceRule::new));
            case "tag_default" -> RecordCodecBuilder.<TagDefaultRule>mapCodec(inst -> inst.group(
                    Codec.STRING.fieldOf("ruleId").forGetter(TagDefaultRule::ruleId),
                    SCOPE_CODEC.optionalFieldOf("scope", GuidanceScope.WORLD_SAVE).forGetter(TagDefaultRule::scope),
                    STRENGTH_CODEC.fieldOf("strength").forGetter(TagDefaultRule::strength),
                    Codec.STRING.fieldOf("tagId").forGetter(TagDefaultRule::tagId),
                    RESOURCE_KEY_CODEC.fieldOf("preferredMember").forGetter(TagDefaultRule::preferredMember)
            ).apply(inst, TagDefaultRule::new));
            case "cycle_break" -> RecordCodecBuilder.<CycleBreakRule>mapCodec(inst -> inst.group(
                    Codec.STRING.fieldOf("ruleId").forGetter(CycleBreakRule::ruleId),
                    SCOPE_CODEC.optionalFieldOf("scope", GuidanceScope.WORLD_SAVE).forGetter(CycleBreakRule::scope),
                    STRENGTH_CODEC.fieldOf("strength").forGetter(CycleBreakRule::strength),
                    RESOURCE_KEY_CODEC.fieldOf("targetResource").forGetter(CycleBreakRule::targetResource),
                    RECIPE_KEY_CODEC.fieldOf("bannedSourceRecipe").forGetter(CycleBreakRule::bannedSourceRecipe)
            ).apply(inst, CycleBreakRule::new));
            case "cost_override" -> RecordCodecBuilder.<CostOverrideRule>mapCodec(inst -> inst.group(
                    Codec.STRING.fieldOf("ruleId").forGetter(CostOverrideRule::ruleId),
                    SCOPE_CODEC.optionalFieldOf("scope", GuidanceScope.WORLD_SAVE).forGetter(CostOverrideRule::scope),
                    STRENGTH_CODEC.fieldOf("strength").forGetter(CostOverrideRule::strength),
                    RESOURCE_KEY_CODEC.fieldOf("resource").forGetter(CostOverrideRule::resource),
                    COST_VECTOR_CODEC.fieldOf("overrideUnitCost").forGetter(CostOverrideRule::overrideUnitCost)
            ).apply(inst, CostOverrideRule::new));
            case "mod_namespace" -> RecordCodecBuilder.<ModNamespaceRule>mapCodec(inst -> inst.group(
                    Codec.STRING.fieldOf("ruleId").forGetter(ModNamespaceRule::ruleId),
                    SCOPE_CODEC.optionalFieldOf("scope", GuidanceScope.WORLD_SAVE).forGetter(ModNamespaceRule::scope),
                    STRENGTH_CODEC.fieldOf("strength").forGetter(ModNamespaceRule::strength),
                    Codec.STRING.fieldOf("modNamespace").forGetter(ModNamespaceRule::modNamespace)
            ).apply(inst, ModNamespaceRule::new));
            default -> com.mojang.serialization.MapCodec.unit(() -> {
                throw new IllegalArgumentException("Unknown guidance rule kind: " + kind);
            });
        };
    }
}
