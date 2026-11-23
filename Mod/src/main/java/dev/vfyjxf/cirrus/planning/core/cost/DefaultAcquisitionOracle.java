package dev.vfyjxf.cirrus.planning.core.cost;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class DefaultAcquisitionOracle implements AcquisitionOracle {

    public static final String KEY_FREE = "cirrus.acquire.player_free";
    public static final String KEY_OVERRIDE = "cirrus.acquire.cost_override";
    public static final String KEY_BASE = "cirrus.acquire.base";
    public static final String KEY_NATURAL_LEAF = "cirrus.acquire.natural";
    public static final String KEY_UNKNOWN_GAP = "cirrus.acquire.unknown";

    private static final AcquisitionOption PLAYER_FREE_OPT = new AcquisitionOption(
            AcquisitionKind.PLAYER_DECLARED_FREE, 0.0, 0.0, 0.0, 0.0, KEY_FREE);
    private static final AcquisitionOption NATURAL_LEAF_OPT = new AcquisitionOption(
            AcquisitionKind.WORLD_GATHER, 0.8, 0.0, 4.0, 0.8, KEY_NATURAL_LEAF);
    private static final AcquisitionOption UNKNOWN_GAP_OPT = new AcquisitionOption(
            AcquisitionKind.WORLD_GATHER, 5.0, 0.0, 15.0, 8.0, KEY_UNKNOWN_GAP);

    private static final Map<String, AcquisitionOption> NATURAL_BASE_TABLE = buildNaturalTable();
    private static final Set<String> PROCESSED_SUFFIXES = Set.of(
            "_ingot", "_nugget", "_plate", "_gear", "_dust", "_rod", "_wire", "_sheet",
            "_brick", "_bricks", "_pane", "_casing", "_circuit", "_processor", "_alloy",
            "_wafer", "_boule", "_pcb", "_capacitor", "_resistor", "_transistor", "_diode",
            "_coil", "_motor", "_piston", "_conveyor", "_module", "_upgrade", "_core",
            "_machine", "_factory", "_furnace", "_crusher", "_compressor", "_extractor",
            "_assembler", "_centrifuge", "_electrolyzer", "_mixer", "_press", "_smelter"
    );
    private static final Set<String> PROCESSED_PREFIXES = Set.of(
            "plate_", "gear_", "ingot_", "nugget_", "dust_", "rod_", "wire_", "sheet_",
            "circuit_", "processor_", "alloy_", "casing_", "printed_"
    );

    private final Set<ResourceKey> producibleResources;
    private final Set<ResourceKey> unresolvedOutputs;
    private final Set<ResourceKey> dormantResources;
    private final HumanGuidanceView guidance;
    private final ConcurrentHashMap<ResourceKey, Optional<AcquisitionOption>> cache = new ConcurrentHashMap<>();

    public DefaultAcquisitionOracle(CompiledRecipeGraph graph) {
        this(Objects.requireNonNull(graph, "graph").allProducibleResources(), graph.unresolvedOutputs(), Set.of(), null);
    }

    public DefaultAcquisitionOracle(CompiledRecipeGraph graph, HumanGuidanceView guidance) {
        this(Objects.requireNonNull(graph, "graph").allProducibleResources(), graph.unresolvedOutputs(), Set.of(), guidance);
    }

    public DefaultAcquisitionOracle(Set<ResourceKey> producibleResources) {
        this(Objects.requireNonNull(producibleResources, "producibleResources"), Set.of(), Set.of(), null);
    }

    private DefaultAcquisitionOracle(
            Set<ResourceKey> producibleResources,
            Set<ResourceKey> unresolvedOutputs,
            Set<ResourceKey> dormantResources,
            HumanGuidanceView guidance
    ) {
        this.producibleResources = producibleResources;
        this.unresolvedOutputs = unresolvedOutputs;
        this.dormantResources = dormantResources;
        this.guidance = guidance;
    }

    public DefaultAcquisitionOracle withGuidance(HumanGuidanceView newGuidance) {
        return new DefaultAcquisitionOracle(producibleResources, unresolvedOutputs, dormantResources, newGuidance);
    }

    public DefaultAcquisitionOracle withDormantResources(Set<ResourceKey> dormant) {
        return new DefaultAcquisitionOracle(producibleResources, unresolvedOutputs,
                dormant == null ? Set.of() : dormant, guidance);
    }

    public static AcquisitionOption fromUnitCost(CostVector unitCost, String translationKey) {
        Objects.requireNonNull(unitCost, "unitCost");
        return new AcquisitionOption(
                AcquisitionKind.WORLD_GATHER,
                unitCost.humanActiveSeconds(),
                unitCost.passiveWaitSeconds(),
                0.0,
                unitCost.materialScarcity(),
                translationKey == null ? KEY_OVERRIDE : translationKey
        );
    }

    public static boolean isKnownBaseNatural(ResourceKey key) {
        return lookupNatural(key) != null;
    }

    public static boolean looksLikeProcessedProduct(ResourceKey key) {
        if (lookupNatural(key) != null) {
            return false;
        }
        String path = pathOf(key.resourceId());
        if (path.equals("stone") || path.equals("smooth_stone") || path.equals("glass")
                || path.equals("charcoal") || path.equals("fluix_crystal")
                || path.equals("obsidian") || path.equals("silicon")
                || path.equals("raw_iron_block") || path.equals("raw_copper_block") || path.equals("raw_gold_block")) {
            return true;
        }
        for (String suffix : PROCESSED_SUFFIXES) {
            if (path.endsWith(suffix)) {
                return true;
            }
        }
        for (String prefix : PROCESSED_PREFIXES) {
            if (path.startsWith(prefix)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public Optional<AcquisitionOption> assess(ResourceKey key, long missingAmount, ProgressionState progression) {
        Objects.requireNonNull(key, "key");
        if (guidance != null) {
            if (guidance.isResourceBanned(key)) {
                return Optional.empty();
            }
            Optional<CostVector> override = guidance.overriddenUnitCost(key);
            if (override.isPresent()) {
                return Optional.of(fromUnitCost(override.get(), KEY_OVERRIDE));
            }
            if (guidance.isResourceTreatedAsBase(key)) {
                return Optional.of(PLAYER_FREE_OPT);
            }
        }
        return cache.computeIfAbsent(key, this::assessStatic);
    }

    private Optional<AcquisitionOption> assessStatic(ResourceKey key) {
        AcquisitionOption natural = lookupNatural(key);
        if (natural != null) {
            return Optional.of(natural);
        }
        boolean hasActiveProducer = producibleResources.contains(key) && !dormantResources.contains(key);
        if (hasActiveProducer) {
            return Optional.empty();
        }
        if (unresolvedOutputs.contains(key) || looksLikeProcessedProduct(key)) {
            return Optional.of(UNKNOWN_GAP_OPT);
        }
        return Optional.of(NATURAL_LEAF_OPT);
    }

    private static AcquisitionOption lookupNatural(ResourceKey key) {
        if (key.canonicalVariantHash().isPresent()) {
            return null;
        }
        String id = key.resourceId();
        AcquisitionOption direct = NATURAL_BASE_TABLE.get(id);
        if (direct != null) {
            return direct;
        }
        if ("minecraft:fluid".equals(key.contentTypeId())) {
            if ("minecraft:water".equals(id)) {
                return NATURAL_BASE_TABLE.get("fluid:minecraft:water");
            }
            if ("minecraft:lava".equals(id)) {
                return NATURAL_BASE_TABLE.get("fluid:minecraft:lava");
            }
        }
        String path = pathOf(id);
        if (id.startsWith("minecraft:") && (path.endsWith("_log") || path.endsWith("_stem") || path.endsWith("_wood"))) {
            if (!path.startsWith("stripped_")) {
                return NATURAL_BASE_TABLE.get("minecraft:oak_log");
            }
        }
        return null;
    }

    private static String pathOf(String id) {
        int colon = id.indexOf(':');
        return colon >= 0 ? id.substring(colon + 1) : id;
    }

    private static Map<String, AcquisitionOption> buildNaturalTable() {
        Map<String, AcquisitionOption> m = new HashMap<>();
        put(m, "minecraft:cobblestone", 0.2, 0.0, 2.0, 0.2);
        put(m, "minecraft:cobbled_deepslate", 0.3, 0.0, 3.0, 0.3);
        put(m, "minecraft:dirt", 0.2, 0.0, 1.0, 0.1);
        put(m, "minecraft:sand", 0.3, 0.0, 2.0, 0.2);
        put(m, "minecraft:red_sand", 0.4, 0.0, 4.0, 0.4);
        put(m, "minecraft:gravel", 0.3, 0.0, 2.0, 0.2);
        put(m, "minecraft:clay_ball", 0.5, 0.0, 4.0, 0.5);
        put(m, "minecraft:netherrack", 0.2, 0.0, 4.0, 0.2);
        put(m, "minecraft:soul_sand", 0.5, 0.0, 6.0, 0.5);
        put(m, "minecraft:end_stone", 0.4, 0.0, 10.0, 0.5);
        put(m, "minecraft:oak_log", 0.5, 0.0, 3.0, 0.3);
        put(m, "minecraft:spruce_log", 0.5, 0.0, 3.0, 0.3);
        put(m, "minecraft:birch_log", 0.5, 0.0, 3.0, 0.3);
        put(m, "minecraft:jungle_log", 0.6, 0.0, 4.0, 0.4);
        put(m, "minecraft:acacia_log", 0.6, 0.0, 4.0, 0.4);
        put(m, "minecraft:dark_oak_log", 0.5, 0.0, 4.0, 0.3);
        put(m, "minecraft:mangrove_log", 0.6, 0.0, 4.0, 0.4);
        put(m, "minecraft:cherry_log", 0.6, 0.0, 4.0, 0.4);
        put(m, "minecraft:bamboo", 0.2, 0.0, 3.0, 0.2);
        put(m, "minecraft:sugar_cane", 0.3, 0.0, 3.0, 0.2);
        put(m, "minecraft:wheat", 0.4, 0.0, 3.0, 0.3);
        put(m, "minecraft:coal", 1.2, 0.0, 6.0, 1.0);
        put(m, "minecraft:raw_copper", 1.8, 0.0, 8.0, 1.5);
        put(m, "minecraft:raw_iron", 2.5, 0.0, 10.0, 2.5);
        put(m, "minecraft:raw_gold", 5.0, 0.0, 15.0, 5.0);
        put(m, "minecraft:redstone", 1.5, 0.0, 10.0, 1.5);
        put(m, "minecraft:lapis_lazuli", 2.0, 0.0, 12.0, 2.0);
        put(m, "minecraft:quartz", 3.0, 0.0, 15.0, 3.0);
        put(m, "minecraft:amethyst_shard", 3.5, 0.0, 15.0, 3.5);
        put(m, "minecraft:diamond", 25.0, 0.0, 30.0, 25.0);
        put(m, "minecraft:emerald", 20.0, 0.0, 25.0, 20.0);
        put(m, "minecraft:ancient_debris", 90.0, 0.0, 60.0, 80.0);
        put(m, "minecraft:string", 1.5, 0.0, 8.0, 1.0);
        put(m, "minecraft:leather", 2.0, 0.0, 10.0, 1.5);
        put(m, "minecraft:feather", 1.0, 0.0, 6.0, 0.8);
        put(m, "minecraft:flint", 1.0, 0.0, 5.0, 0.8);
        put(m, "minecraft:bone", 1.5, 0.0, 8.0, 1.0);
        put(m, "minecraft:gunpowder", 2.0, 0.0, 10.0, 1.5);
        put(m, "minecraft:slime_ball", 3.0, 0.0, 15.0, 2.5);
        put(m, "minecraft:ender_pearl", 6.0, 0.0, 20.0, 5.0);
        put(m, "minecraft:blaze_rod", 8.0, 0.0, 25.0, 7.0);
        put(m, "minecraft:glowstone_dust", 2.0, 0.0, 12.0, 2.0);
        put(m, "minecraft:water_bucket", 0.5, 0.0, 2.0, 0.1);
        put(m, "minecraft:lava_bucket", 2.0, 0.0, 10.0, 1.0);
        put(m, "fluid:minecraft:water", 0.001, 0.0, 1.0, 0.0001);
        put(m, "fluid:minecraft:lava", 0.004, 0.0, 8.0, 0.001);
        put(m, "ae2:sky_stone_block", 4.0, 0.0, 20.0, 4.0);
        put(m, "ae2:certus_quartz_crystal", 3.5, 0.0, 18.0, 3.5);
        put(m, "create:raw_zinc", 2.5, 0.0, 10.0, 2.5);
        put(m, "mekanism:raw_osmium", 3.0, 0.0, 12.0, 3.0);
        put(m, "mekanism:raw_tin", 2.2, 0.0, 10.0, 2.0);
        put(m, "mekanism:raw_lead", 2.5, 0.0, 10.0, 2.2);
        put(m, "mekanism:raw_uranium", 6.0, 0.0, 18.0, 6.0);
        return Map.copyOf(m);
    }

    private static void put(Map<String, AcquisitionOption> m, String id, double h, double p, double trip, double s) {
        m.put(id, new AcquisitionOption(AcquisitionKind.WORLD_GATHER, h, p, trip, s, KEY_BASE));
    }
}
