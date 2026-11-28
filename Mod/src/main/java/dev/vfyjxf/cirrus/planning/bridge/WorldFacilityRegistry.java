package dev.vfyjxf.cirrus.planning.bridge;

import com.google.gson.JsonElement;
import com.mojang.datafixers.util.Pair;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.MapLike;
import com.mojang.serialization.RecordBuilder;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.vfyjxf.cirrus.api.CirrusTypes;
import dev.vfyjxf.cirrus.api.content.ContentApi;
import dev.vfyjxf.cirrus.api.content.MatchStrategy;
import dev.vfyjxf.cirrus.api.content.type.StackType;
import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
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
import dev.vfyjxf.cirrus.api.planning.model.PlanningTagKeys;
import dev.vfyjxf.cirrus.api.recipe.machine.ItemMachineEntry;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineMatcher;
import dev.vfyjxf.cirrus.planning.core.graph.MachineCapabilityGroups;
import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Games;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.saveddata.SavedData;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.stream.Stream;

public final class WorldFacilityRegistry extends SavedData {

    private static final Logger logger = LoggerFactory.getLogger("CalculatorCirrus FacilityRegistry");

    public static final String dataName = "calculator_cirrus_planning_facilities";
    private static final String nbtDataKey = "planning_data";

    private static final MachineMatcher defaultMachineMatcher = new MachineMatcher() {
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

    private static final CopyOnWriteArrayList<MachineMatcher> machineMatchers = new CopyOnWriteArrayList<>(
        List.of(defaultMachineMatcher)
    );

    private static volatile @Nullable WorldFacilityRegistry installed;

    private final Map<MachineEntry, OwnedMachineGroup> ownedMachines = new LinkedHashMap<>();
    private final Map<String, AutomatedLine> automatedLines = new LinkedHashMap<>();
    private final Map<CompiledRecipeKey, AutomatedRecipeCapability> automatedRecipes = new LinkedHashMap<>();
    private final TreeSet<Namespace> unlockedSystems = new TreeSet<>(Comparator.comparing(Namespace::toString));
    private final Map<String, GuidanceRule> worldSaveRules = new LinkedHashMap<>();

    public static final Codec<Namespace> namespaceCodec = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(Namespace.parse(s));
        } catch (RuntimeException e) {
            return DataResult.error(() -> "Invalid Namespace: " + s);
        }
    }, Namespace::toString);

    public static final Codec<ComponentStackKey> resourceKeyCodec = new Codec<>() {
        @Override
        public <T> DataResult<T> encode(ComponentStackKey input, DynamicOps<T> ops, T prefix) {
            return encodeComponentStackKey(input, ops);
        }

        @Override
        public <T> DataResult<Pair<ComponentStackKey, T>> decode(DynamicOps<T> ops, T input) {
            return decodeComponentStackKey(ops, input).map(key -> Pair.of(key, ops.empty()));
        }
    };

    public static final Codec<MachineEntry> machineEntryCodec = new Codec<>() {
        @Override
        public <T> DataResult<T> encode(MachineEntry input, DynamicOps<T> ops, T prefix) {
            Optional<ComponentStackKey> keyOpt = input.contentKey();
            if (keyOpt.isEmpty()) {
                return DataResult.error(() -> "Cannot encode empty or non-content MachineEntry: " + input);
            }
            return encodeComponentStackKey(keyOpt.get(), ops);
        }

        @Override
        public <T> DataResult<Pair<MachineEntry, T>> decode(DynamicOps<T> ops, T input) {
            DataResult<String> asString = ops.getStringValue(input);
            if (asString.result().isPresent()) {
                ResourceLocation loc = ResourceLocation.tryParse(asString.result().get());
                if (loc != null) {
                    Optional<Item> item = BuiltInRegistries.ITEM.getOptional(loc);
                    if (item.isPresent()) {
                        return DataResult.success(
                            Pair.of(MachineEntry.fromItemStack(new ItemStack(item.get())), ops.empty())
                        );
                    }
                }
            }
            return decodeComponentStackKey(ops, input).map(key -> Pair.of(MachineEntry.fromKey(key), ops.empty()));
        }
    };

    public static final Codec<CompiledRecipeKey> recipeKeyCodec = RecordCodecBuilder.create(
        inst -> inst.group(
            namespaceCodec.fieldOf("system").forGetter(CompiledRecipeKey::systemId),
            ResourceLocation.CODEC.optionalFieldOf("explicitId").forGetter(CompiledRecipeKey::explicitRecipeId),
            Codec.LONG.fieldOf("hash").forGetter(CompiledRecipeKey::structuralHash64)
        ).apply(inst, CompiledRecipeKey::new)
    );

    public static final Codec<MachineCapabilityId> capabilityCodec = namespaceCodec
            .xmap(MachineCapabilityId::new, MachineCapabilityId::id);

    public static final Codec<CostVector> costVectorCodec = RecordCodecBuilder.create(
        inst -> inst.group(
            Codec.DOUBLE.fieldOf("h").forGetter(CostVector::humanActiveSeconds),
            Codec.DOUBLE.optionalFieldOf("t", 0.0).forGetter(CostVector::tripActiveSeconds),
            Codec.DOUBLE.fieldOf("p").forGetter(CostVector::passiveWaitSeconds),
            Codec.DOUBLE.fieldOf("k").forGetter(CostVector::machineSetupCapital),
            Codec.DOUBLE.fieldOf("s").forGetter(CostVector::materialScarcity),
            Codec.DOUBLE.fieldOf("c").forGetter(CostVector::cognitiveFriction)
        ).apply(inst, CostVector::new)
    );

    public static final Codec<OwnedMachineGroup> ownedMachineCodec = RecordCodecBuilder.create(
        inst -> inst.group(
            machineEntryCodec.fieldOf("machine").forGetter(OwnedMachineGroup::machine),
            capabilityCodec.fieldOf("capability").forGetter(OwnedMachineGroup::capability),
            Codec.INT.fieldOf("count").forGetter(OwnedMachineGroup::count),
            Codec.DOUBLE.optionalFieldOf("speed", 1.0).forGetter(OwnedMachineGroup::passiveSpeedMultiplier),
            Codec.BOOL.optionalFieldOf("automatedFeeding", false).forGetter(OwnedMachineGroup::automatedFeeding)
        ).apply(inst, OwnedMachineGroup::new)
    );

    public static final Codec<AutomatedLine> automatedLineCodec = RecordCodecBuilder.create(
        inst -> inst.group(
            Codec.STRING.fieldOf("lineId").forGetter(AutomatedLine::lineId),
            resourceKeyCodec.fieldOf("output").forGetter(AutomatedLine::output),
            Codec.DOUBLE.fieldOf("throughputPerMinute").forGetter(AutomatedLine::throughputPerMinute),
            Codec.DOUBLE.optionalFieldOf("humanAttentionSecondsPerPull", 0.0)
                    .forGetter(AutomatedLine::humanAttentionSecondsPerPull),
            Codec.LONG.optionalFieldOf("bufferedStock", 0L).forGetter(AutomatedLine::bufferedStock),
            Codec.BOOL.optionalFieldOf("continuous", true).forGetter(AutomatedLine::continuous)
        ).apply(inst, AutomatedLine::new)
    );

    public static final Codec<AutomatedRecipeCapability> automatedRecipeCodec = RecordCodecBuilder.create(
        inst -> inst.group(
            recipeKeyCodec.fieldOf("recipeKey").forGetter(AutomatedRecipeCapability::recipeKey),
            Codec.DOUBLE.fieldOf("passiveSecondsPerBatch").forGetter(AutomatedRecipeCapability::passiveSecondsPerBatch),
            Codec.INT.optionalFieldOf("parallelCrafters", 1).forGetter(AutomatedRecipeCapability::parallelCrafters)
        ).apply(inst, AutomatedRecipeCapability::new)
    );

    private static final Codec<RuleStrength> strengthCodec = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(RuleStrength.valueOf(s));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Unknown RuleStrength: " + s);
        }
    }, Enum::name);

    private static final Codec<GuidanceScope> scopeCodec = Codec.STRING.comapFlatMap(s -> {
        try {
            return DataResult.success(GuidanceScope.valueOf(s));
        } catch (IllegalArgumentException e) {
            return DataResult.error(() -> "Unknown GuidanceScope: " + s);
        }
    }, Enum::name);

    public static final Codec<GuidanceRule> guidanceRuleCodec = Codec.STRING
            .dispatch("kind", WorldFacilityRegistry::ruleKind, WorldFacilityRegistry::ruleCodecForKind);

    public record PersistedState(
        List<OwnedMachineGroup> ownedMachines,
        List<AutomatedLine> automatedLines,
        List<AutomatedRecipeCapability> automatedRecipes,
        List<Namespace> unlockedSystems,
        List<GuidanceRule> worldSaveRules
    ) {
        public static final Codec<PersistedState> codec = RecordCodecBuilder.create(
            inst -> inst.group(
                ownedMachineCodec.listOf().optionalFieldOf("ownedMachines", List.of())
                        .forGetter(PersistedState::ownedMachines),
                automatedLineCodec.listOf().optionalFieldOf("automatedLines", List.of())
                        .forGetter(PersistedState::automatedLines),
                automatedRecipeCodec.listOf().optionalFieldOf("automatedRecipes", List.of())
                        .forGetter(PersistedState::automatedRecipes),
                namespaceCodec.listOf().optionalFieldOf("unlockedSystems", List.of())
                        .forGetter(PersistedState::unlockedSystems),
                guidanceRuleCodec.listOf().optionalFieldOf("worldSaveRules", List.of())
                        .forGetter(PersistedState::worldSaveRules)
            ).apply(inst, PersistedState::new)
        );
    }

    private static final SavedData.Factory<WorldFacilityRegistry> factory = new SavedData.Factory<>(
        WorldFacilityRegistry::new,
        WorldFacilityRegistry::load,
        (DataFixTypes) null
    );

    public WorldFacilityRegistry() {}

    public static WorldFacilityRegistry createDetached() {
        return new WorldFacilityRegistry();
    }

    public static WorldFacilityRegistry mount(ServerLevel overworld) {
        Objects.requireNonNull(overworld, "overworld");
        WorldFacilityRegistry registry = overworld.getDataStorage().computeIfAbsent(factory, dataName);
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
        machineMatchers.addIfAbsent(matcher);
    }

    public static List<MachineMatcher> machineMatchers() {
        return List.copyOf(machineMatchers);
    }

    public static boolean matchesMachine(MachineEntry owned, MachineEntry required) {
        for (MachineMatcher matcher : machineMatchers) {
            try {
                if (matcher.matches(owned, required)) {
                    return true;
                }
            } catch (RuntimeException ignored) {}
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
        MachineEntry machine,
        MachineCapabilityId capability,
        int count,
        double passiveSpeedMultiplier,
        boolean automatedFeeding
    ) {
        Objects.requireNonNull(machine, "machine");
        Objects.requireNonNull(capability, "capability");
        if (count <= 0) {
            if (ownedMachines.remove(machine) != null) {
                setDirty();
            }
            return;
        }
        ownedMachines.put(
            machine,
            new OwnedMachineGroup(machine, capability, count, passiveSpeedMultiplier, automatedFeeding)
        );
        setDirty();
    }

    public synchronized void declareOwnedMachine(DeclareOwnedMachineFeedback feedback) {
        Objects.requireNonNull(feedback, "feedback");
        MachineEntry machine = feedback.machine();
        if (feedback.ownedCount() <= 0) {
            if (ownedMachines.remove(machine) != null) {
                setDirty();
            }
            return;
        }
        OwnedMachineGroup existing = ownedMachines.get(machine);
        MachineCapabilityId cap = existing != null ? existing.capability() : inferCapabilityForMachine(machine);
        double speed = existing != null ? existing.passiveSpeedMultiplier() : 1.0;
        boolean autoFeed = existing != null && existing.automatedFeeding();
        ownedMachines.put(machine, new OwnedMachineGroup(machine, cap, feedback.ownedCount(), speed, autoFeed));
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

    public synchronized void unlockSystem(Namespace systemId) {
        Objects.requireNonNull(systemId, "systemId");
        if (unlockedSystems.add(systemId)) {
            setDirty();
        }
    }

    public synchronized void lockSystem(Namespace systemId) {
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
            if (rule != null && rule.scope() == GuidanceScope.worldSave) {
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
            ownedMachines.put(g.machine(), g);
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
        return PersistedState.codec.encodeStart(currentJsonOps(), toPersistedState()).result();
    }

    public static WorldFacilityRegistry loadFromJson(JsonElement element) {
        WorldFacilityRegistry reg = new WorldFacilityRegistry();
        PersistedState.codec.parse(currentJsonOps(), element)
                .resultOrPartial(err -> logger.warn("Failed decoding facility registry JSON: {}", err))
                .ifPresent(reg::loadPersistedState);
        return reg;
    }

    @Override
    public synchronized CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        PersistedState state = toPersistedState();
        DynamicOps<Tag> ops = registries != null ? registries.createSerializationContext(NbtOps.INSTANCE) : NbtOps.INSTANCE;
        PersistedState.codec.encodeStart(ops, state)
                .resultOrPartial(err -> logger.error("Failed encoding facility registry NBT: {}", err))
                .ifPresent(encoded -> tag.put(nbtDataKey, encoded));
        return tag;
    }

    public static WorldFacilityRegistry load(CompoundTag tag, HolderLookup.Provider registries) {
        WorldFacilityRegistry reg = new WorldFacilityRegistry();
        if (tag != null && tag.contains(nbtDataKey)) {
            Tag raw = tag.get(nbtDataKey);
            if (raw != null) {
                DynamicOps<Tag> ops = registries != null
                        ? registries.createSerializationContext(NbtOps.INSTANCE)
                        : NbtOps.INSTANCE;
                PersistedState.codec.parse(ops, raw)
                        .resultOrPartial(err -> logger.warn("Failed decoding facility registry NBT: {}", err))
                        .ifPresent(reg::loadPersistedState);
            }
        }
        return reg;
    }

    private static DynamicOps<JsonElement> currentJsonOps() {
        try {
            RegistryAccess access = Games.registryAccess();
            if (access != null) {
                return RegistryOps.create(JsonOps.INSTANCE, access);
            }
        } catch (RuntimeException ignored) {}
        return JsonOps.INSTANCE;
    }

    @SuppressWarnings("unchecked")
    private static <T, S> DataResult<T> encodeComponentStackKey(ComponentStackKey key, DynamicOps<T> ops) {
        StackType<?, S> st = (StackType<?, S>) key.stackType();
        Codec<S> stackCodec = st.stackCodec();
        if (stackCodec != null) {
            DataResult<T> encodedStack = stackCodec.encodeStart(ops, (S) key.stack().value());
            if (encodedStack.result().isPresent()) {
                RecordBuilder<T> builder = ops.mapBuilder();
                builder.add("stackType", ops.createString(key.stackTypeId().toString()));
                builder.add("stack", encodedStack.result().get());
                return builder.build(ops.empty());
            }
        }
        RecordBuilder<T> builder = ops.mapBuilder();
        builder.add("contentType", ops.createString(key.contentTypeId().toString()));
        builder.add("id", ops.createString(key.displayId()));
        return builder.build(ops.empty());
    }

    @SuppressWarnings("unchecked")
    private static <T, C, S> DataResult<ComponentStackKey> decodeComponentStackKey(DynamicOps<T> ops, T input) {
        DataResult<MapLike<T>> mapRes = ops.getMap(input);
        if (mapRes.error().isPresent()) {
            return DataResult.error(() -> "Expected map for ComponentStackKey: " + mapRes.error().get().message());
        }
        MapLike<T> map = mapRes.result().orElseThrow();
        T stElem = map.get("stackType");
        T stackElem = map.get("stack");
        if (stElem != null && stackElem != null) {
            DataResult<String> stStrRes = ops.getStringValue(stElem);
            if (stStrRes.result().isEmpty()) {
                return DataResult.error(() -> "Invalid stackType string in ComponentStackKey");
            }
            Namespace stId;
            try {
                stId = Namespace.parse(stStrRes.result().get());
            } catch (RuntimeException e) {
                return DataResult.error(() -> "Invalid stackType Namespace: " + stStrRes.result().get());
            }
            StackType<C, S> stackType = (StackType<C, S>) resolveStackType(stId);
            if (stackType == null || stackType.stackCodec() == null) {
                return DataResult.error(() -> "Unknown or non-serializable StackType: " + stId);
            }
            DataResult<S> rawStackRes = stackType.stackCodec().parse(ops, stackElem);
            return rawStackRes.flatMap(rawStack -> {
                ContentStack<C, S> cs = ContentStack.fromStack(stackType, rawStack);
                if (cs.isEmpty()) {
                    return DataResult.error(() -> "Decoded empty stack for StackType: " + stId);
                }
                return DataResult.success(ComponentStackKey.ofNormalized(cs));
            });
        }

        // Backward-compatible legacy {contentType, id} format.
        T ctElem = map.get("contentType");
        T idElem = map.get("id");
        if (ctElem != null && idElem != null) {
            String ctStr = ops.getStringValue(ctElem).result().orElse("");
            String idStr = ops.getStringValue(idElem).result().orElse("");
            ResourceLocation loc = ResourceLocation.tryParse(idStr);
            if (loc != null) {
                if ("minecraft:item".equals(ctStr)) {
                    Optional<Item> item = BuiltInRegistries.ITEM.getOptional(loc);
                    if (item.isPresent()) {
                        return DataResult.success(ComponentStackKey.ofItem(item.get()));
                    }
                } else if ("minecraft:fluid".equals(ctStr)) {
                    Optional<Fluid> fluid = BuiltInRegistries.FLUID.getOptional(loc);
                    if (fluid.isPresent()) {
                        return DataResult.success(ComponentStackKey.ofFluid(fluid.get()));
                    }
                }
            }
            return DataResult.error(() -> "Cannot resolve legacy resource key: " + ctStr + " / " + idStr);
        }
        return DataResult.error(() -> "ComponentStackKey requires (stackType, stack) or legacy (contentType, id)");
    }

    private static @Nullable StackType<?, ?> resolveStackType(Namespace typeId) {
        if (CirrusTypes.Contents.itemStack.typeId().equals(typeId)) {
            return CirrusTypes.Contents.itemStack;
        }
        if (CirrusTypes.Contents.fluidStack.typeId().equals(typeId)) {
            return CirrusTypes.Contents.fluidStack;
        }
        try {
            return ContentApi.instance().findStackType(typeId);
        } catch (RuntimeException | LinkageError ignored) {
            return null;
        }
    }

    private static MachineCapabilityId inferCapabilityForMachine(MachineEntry machine) {
        ItemStack stack = machine.toItemStack();
        if (!stack.isEmpty()) {
            Item item = stack.getItem();
            if (item == Items.CRAFTING_TABLE) return MachineCapabilityGroups.capabilityOf(Namespace.ofMc("crafting"));
            if (item == Items.FURNACE) return MachineCapabilityGroups.capabilityOf(Namespace.ofMc("smelting"));
            if (item == Items.BLAST_FURNACE) return MachineCapabilityGroups.capabilityOf(Namespace.ofMc("blasting"));
            if (item == Items.SMOKER) return MachineCapabilityGroups.capabilityOf(Namespace.ofMc("smoking"));
            if (item == Items.STONECUTTER) return MachineCapabilityGroups.capabilityOf(Namespace.ofMc("stonecutting"));
            if (item == Items.SMITHING_TABLE) return MachineCapabilityGroups.capabilityOf(Namespace.ofMc("smithing"));
            if (item == Items.BREWING_STAND) return MachineCapabilityGroups.capabilityOf(Namespace.ofMc("brewing"));
            if (item == Items.CAMPFIRE || item == Items.SOUL_CAMPFIRE) {
                return MachineCapabilityGroups.capabilityOf(Namespace.ofMc("campfire_cooking"));
            }
        }
        return MachineCapabilityGroups.capabilityOfMachine(machine);
    }

    private static GuidanceRule withWorldSaveScope(GuidanceRule rule) {
        if (rule.scope() == GuidanceScope.worldSave) {
            return rule;
        }
        return switch (rule) {
            case RecipeRule r -> new RecipeRule(r.ruleId(), GuidanceScope.worldSave, r.strength(), r.recipeKey());
            case SystemRule r ->
                new SystemRule(r.ruleId(), GuidanceScope.worldSave, r.strength(), r.systemId(), r.optInEnabled());
            case MachineRule r -> new MachineRule(
                r.ruleId(),
                GuidanceScope.worldSave,
                r.strength(),
                r.machine(),
                r.willingToBuildNew()
            );
            case ResourceRule r -> new ResourceRule(
                r.ruleId(),
                GuidanceScope.worldSave,
                r.strength(),
                r.resource(),
                r.treatAsFreeBaseSource()
            );
            case TagDefaultRule r ->
                new TagDefaultRule(r.ruleId(), GuidanceScope.worldSave, r.strength(), r.tag(), r.preferredMember());
            case CycleBreakRule r -> new CycleBreakRule(
                r.ruleId(),
                GuidanceScope.worldSave,
                r.strength(),
                r.targetResource(),
                r.bannedSourceRecipe()
            );
            case CostOverrideRule r -> new CostOverrideRule(
                r.ruleId(),
                GuidanceScope.worldSave,
                r.strength(),
                r.resource(),
                r.overrideUnitCost()
            );
            case ModNamespaceRule r ->
                new ModNamespaceRule(r.ruleId(), GuidanceScope.worldSave, r.strength(), r.modNamespace());
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

    private static MapCodec<? extends GuidanceRule> ruleCodecForKind(String kind) {
        return switch (kind) {
            case "recipe" ->
                RecordCodecBuilder
                        .<RecipeRule>mapCodec(
                            inst -> inst.group(
                                Codec.STRING.fieldOf("ruleId").forGetter(RecipeRule::ruleId),
                                scopeCodec.optionalFieldOf("scope", GuidanceScope.worldSave)
                                        .forGetter(RecipeRule::scope),
                                strengthCodec.fieldOf("strength").forGetter(RecipeRule::strength),
                                recipeKeyCodec.fieldOf("recipeKey").forGetter(RecipeRule::recipeKey)
                            ).apply(inst, RecipeRule::new)
                        );
            case "system" ->
                RecordCodecBuilder
                        .<SystemRule>mapCodec(
                            inst -> inst.group(
                                Codec.STRING.fieldOf("ruleId").forGetter(SystemRule::ruleId),
                                scopeCodec.optionalFieldOf("scope", GuidanceScope.worldSave)
                                        .forGetter(SystemRule::scope),
                                strengthCodec.fieldOf("strength").forGetter(SystemRule::strength),
                                namespaceCodec.fieldOf("systemId").forGetter(SystemRule::systemId),
                                Codec.BOOL.optionalFieldOf("optInEnabled", false).forGetter(SystemRule::optInEnabled)
                            ).apply(inst, SystemRule::new)
                        );
            case "machine" -> RecordCodecBuilder
                    .<MachineRule>mapCodec(
                        inst -> inst.group(
                            Codec.STRING.fieldOf("ruleId").forGetter(MachineRule::ruleId),
                            scopeCodec.optionalFieldOf("scope", GuidanceScope.worldSave).forGetter(MachineRule::scope),
                            strengthCodec.fieldOf("strength").forGetter(MachineRule::strength),
                            machineEntryCodec.fieldOf("machine").forGetter(MachineRule::machine),
                            Codec.BOOL.optionalFieldOf("willingToBuildNew", true)
                                    .forGetter(MachineRule::willingToBuildNew)
                        ).apply(inst, MachineRule::new)
                    );
            case "resource" -> RecordCodecBuilder.<ResourceRule>mapCodec(
                inst -> inst.group(
                    Codec.STRING.fieldOf("ruleId").forGetter(ResourceRule::ruleId),
                    scopeCodec.optionalFieldOf("scope", GuidanceScope.worldSave).forGetter(ResourceRule::scope),
                    strengthCodec.fieldOf("strength").forGetter(ResourceRule::strength),
                    resourceKeyCodec.fieldOf("resource").forGetter(ResourceRule::resource),
                    Codec.BOOL.optionalFieldOf("treatAsFreeBaseSource", false)
                            .forGetter(ResourceRule::treatAsFreeBaseSource)
                ).apply(inst, ResourceRule::new)
            );
            case "tag_default" -> RecordCodecBuilder
                    .<TagDefaultRule>mapCodec(
                        inst -> inst.group(
                            Codec.STRING.fieldOf("ruleId").forGetter(TagDefaultRule::ruleId),
                            scopeCodec.optionalFieldOf("scope", GuidanceScope.worldSave)
                                    .forGetter(TagDefaultRule::scope),
                            strengthCodec.fieldOf("strength").forGetter(TagDefaultRule::strength),
                            PlanningTagKeys.codec.fieldOf("tag").forGetter(TagDefaultRule::tag),
                            resourceKeyCodec.fieldOf("preferredMember").forGetter(TagDefaultRule::preferredMember)
                        ).apply(inst, TagDefaultRule::new)
                    );
            case "cycle_break" -> RecordCodecBuilder
                    .<CycleBreakRule>mapCodec(
                        inst -> inst.group(
                            Codec.STRING.fieldOf("ruleId").forGetter(CycleBreakRule::ruleId),
                            scopeCodec.optionalFieldOf("scope", GuidanceScope.worldSave)
                                    .forGetter(CycleBreakRule::scope),
                            strengthCodec.fieldOf("strength").forGetter(CycleBreakRule::strength),
                            resourceKeyCodec.fieldOf("targetResource").forGetter(CycleBreakRule::targetResource),
                            recipeKeyCodec.fieldOf("bannedSourceRecipe").forGetter(CycleBreakRule::bannedSourceRecipe)
                        ).apply(inst, CycleBreakRule::new)
                    );
            case "cost_override" -> RecordCodecBuilder
                    .<CostOverrideRule>mapCodec(
                        inst -> inst.group(
                            Codec.STRING.fieldOf("ruleId").forGetter(CostOverrideRule::ruleId),
                            scopeCodec.optionalFieldOf("scope", GuidanceScope.worldSave)
                                    .forGetter(CostOverrideRule::scope),
                            strengthCodec.fieldOf("strength").forGetter(CostOverrideRule::strength),
                            resourceKeyCodec.fieldOf("resource").forGetter(CostOverrideRule::resource),
                            costVectorCodec.fieldOf("overrideUnitCost").forGetter(CostOverrideRule::overrideUnitCost)
                        ).apply(inst, CostOverrideRule::new)
                    );
            case "mod_namespace" -> RecordCodecBuilder
                    .<ModNamespaceRule>mapCodec(
                        inst -> inst.group(
                            Codec.STRING.fieldOf("ruleId").forGetter(ModNamespaceRule::ruleId),
                            scopeCodec.optionalFieldOf("scope", GuidanceScope.worldSave)
                                    .forGetter(ModNamespaceRule::scope),
                            strengthCodec.fieldOf("strength").forGetter(ModNamespaceRule::strength),
                            Codec.STRING.fieldOf("modNamespace").forGetter(ModNamespaceRule::modNamespace)
                        ).apply(inst, ModNamespaceRule::new)
                    );
            default -> new MapCodec<GuidanceRule>() {
                @Override
                public <T> Stream<T> keys(DynamicOps<T> ops) {
                    return Stream.empty();
                }

                @Override
                public <T> DataResult<GuidanceRule> decode(DynamicOps<T> ops, MapLike<T> input) {
                    return DataResult.error(() -> "Unknown guidance rule kind: " + kind);
                }

                @Override
                public <T> RecordBuilder<T> encode(GuidanceRule input, DynamicOps<T> ops, RecordBuilder<T> prefix) {
                    return prefix.withErrorsFrom(DataResult.error(() -> "Unknown guidance rule kind: " + kind));
                }
            };
        };
    }
}
