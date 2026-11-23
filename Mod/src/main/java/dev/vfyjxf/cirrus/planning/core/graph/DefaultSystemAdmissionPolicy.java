package dev.vfyjxf.cirrus.planning.core.graph;

import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmissionPolicy;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

public final class DefaultSystemAdmissionPolicy implements SystemAdmissionPolicy {

    private static final DefaultSystemAdmissionPolicy DEFAULTS = new DefaultSystemAdmissionPolicy();

    private static final Set<String> EXCLUDED_EXACT_SYSTEMS = Set.of(
            "minecraft:anvil",
            "jei:category/minecraft/anvil",
            "jei:category/minecraft/fuel",
            "jei:category/minecraft/compostable",
            "jei:category/minecraft/information",
            "jei:category/minecraft/crafting",
            "jei:category/minecraft/smelting",
            "jei:category/minecraft/blasting",
            "jei:category/minecraft/smoking",
            "jei:category/minecraft/campfire_cooking",
            "jei:category/minecraft/stonecutting",
            "jei:category/minecraft/smithing",
            "jei:category/minecraft/brewing"
    );

    private static final Set<String> OPT_IN_EXACT_SYSTEMS = Set.of(
            "jei:category/irons_spellbooks/arcane_anvil",
            "jei:category/create/automatic_shaped",
            "jei:category/create/automatic_shapeless"
    );

    private static final Pattern VILLAGER_TRADE_DISPLAY =
            Pattern.compile("category/[^/]+/(potential_)?villager(_trade)?s?$");
    private static final Pattern JER_DISPLAY =
            Pattern.compile("category/[^/]+/(plant|mob|fluid|dungeon|ore)$");
    private static final Pattern MARKET_NPC_TRADE =
            Pattern.compile("category/[^/]+/(amadron(/spirit_trade)?|spirit_trade)$");

    private static final Pattern EXCLUDED_RECIPE_ID =
            Pattern.compile("^minecraft:anvil\\.(self_repair|materials_repair)|^minecraft:enchantment\\.|enchantment\\.lvl_\\d+");
    private static final Pattern DISCOURAGED_GIANT =
            Pattern.compile("(^|/)giant_\\w+_to_");
    private static final Pattern LAST_RESORT_RECIPE =
            Pattern.compile("(^|/|_)(recycling|recycle|salvage|salvaging|disassembly|disassembling|uncrafting|uncraft)(/|_|:$|$)|nugget_from_smelting|clay_oven_bottom_to_clay");
    private static final Pattern LAST_RESORT_PREDICTION =
            Pattern.compile("(^|[:/_])living_?matter([/_]|$)|_prediction$|/predictions?/");

    private final Map<String, SystemAdmission> systemOverrides;
    private final Map<Long, SystemAdmission> externalRecipeAdmissions;
    private final Map<Long, RecipeAdvisory> externalRecipeAdvisories;

    public DefaultSystemAdmissionPolicy() {
        this(Map.of(), Map.of(), Map.of());
    }

    private DefaultSystemAdmissionPolicy(
            Map<String, SystemAdmission> systemOverrides,
            Map<Long, SystemAdmission> externalRecipeAdmissions,
            Map<Long, RecipeAdvisory> externalRecipeAdvisories
    ) {
        this.systemOverrides = systemOverrides;
        this.externalRecipeAdmissions = externalRecipeAdmissions;
        this.externalRecipeAdvisories = externalRecipeAdvisories;
    }

    public static DefaultSystemAdmissionPolicy defaults() {
        return DEFAULTS;
    }

    public DefaultSystemAdmissionPolicy withSystemOverride(String systemId, SystemAdmission admission) {
        Objects.requireNonNull(systemId, "systemId");
        Objects.requireNonNull(admission, "admission");
        Map<String, SystemAdmission> copy = new HashMap<>(systemOverrides);
        copy.put(normalizeSystemId(systemId), admission);
        return new DefaultSystemAdmissionPolicy(Map.copyOf(copy), externalRecipeAdmissions, externalRecipeAdvisories);
    }

    public DefaultSystemAdmissionPolicy withExternalVerdicts(
            Map<Long, SystemAdmission> recipeAdmissions,
            Map<Long, RecipeAdvisory> recipeAdvisories
    ) {
        return new DefaultSystemAdmissionPolicy(
                systemOverrides,
                recipeAdmissions == null ? Map.of() : Map.copyOf(recipeAdmissions),
                recipeAdvisories == null ? Map.of() : Map.copyOf(recipeAdvisories)
        );
    }

    @Override
    public SystemAdmission classify(String systemId, String sourceKind) {
        String norm = normalizeSystemId(systemId);
        SystemAdmission override = systemOverrides.get(norm);
        if (override != null) {
            return override;
        }
        if (isExcludedDisplaySystem(norm)) {
            return SystemAdmission.EXCLUDED_DISPLAY;
        }
        if (isOptInSystem(norm)) {
            return SystemAdmission.OPT_IN_ONLY;
        }
        return SystemAdmission.ENABLED;
    }

    @Override
    public SystemAdmission classifyRecipe(RawRecipeRecord record) {
        SystemAdmission sysAdm = classify(record.systemId(), record.sourceKind());
        if (sysAdm == SystemAdmission.EXCLUDED_DISPLAY) {
            return SystemAdmission.EXCLUDED_DISPLAY;
        }
        if (record.explicitId().isPresent() && EXCLUDED_RECIPE_ID.matcher(record.explicitId().get()).find()) {
            return SystemAdmission.EXCLUDED_DISPLAY;
        }
        if (!externalRecipeAdmissions.isEmpty() && record.sourceRef() != 0L) {
            SystemAdmission ext = externalRecipeAdmissions.get(record.sourceRef());
            if (ext == SystemAdmission.EXCLUDED_DISPLAY) {
                return SystemAdmission.EXCLUDED_DISPLAY;
            }
            if (ext == SystemAdmission.OPT_IN_ONLY && sysAdm == SystemAdmission.ENABLED) {
                return SystemAdmission.OPT_IN_ONLY;
            }
        }
        return sysAdm;
    }

    @Override
    public RecipeAdvisory advisoryOf(RawRecipeRecord record) {
        RecipeAdvisory adv = lexicalAdvisory(record.systemId(), record.explicitId().orElse(""));
        if (!externalRecipeAdvisories.isEmpty() && record.sourceRef() != 0L) {
            RecipeAdvisory ext = externalRecipeAdvisories.get(record.sourceRef());
            if (ext != null) {
                adv = RecipeAdvisory.max(adv, ext);
            }
        }
        return adv;
    }

    @Override
    public MachineCapabilityId capabilityOf(String systemId) {
        return MachineCapabilityGroups.capabilityOf(normalizeSystemId(systemId));
    }

    @Override
    public List<String> orderMachinesForCapability(String systemId, List<String> rawMachines) {
        return MachineCapabilityGroups.orderMachines(normalizeSystemId(systemId), rawMachines);
    }

    static String normalizeSystemId(String systemId) {
        if (systemId == null) {
            return "";
        }
        if (systemId.startsWith("cirrus_test:jei__category/")) {
            return "jei:category/" + systemId.substring("cirrus_test:jei__category/".length());
        }
        return systemId;
    }

    private static boolean isExcludedDisplaySystem(String norm) {
        if (EXCLUDED_EXACT_SYSTEMS.contains(norm)) {
            return true;
        }
        if (norm.contains(":jei_info")
                || norm.contains("category/ftbquests/")
                || norm.contains("category/quest")
                || norm.contains("archeolog")
                || norm.contains("jearchaeology")
                || norm.contains("/archaeology")
                || norm.contains("attachment_query")
                || norm.endsWith("/query")
                || norm.contains("queen_trades")
                || norm.contains("queen_color_randomizer_trades")
                || norm.contains("amadron_trade")
                || norm.contains("spirit_trade")
                || norm.contains("villager_trades")
                || norm.contains("farmingforblockheads/market")
                || norm.endsWith("/trades")
                || norm.contains("industrialforegoing/machine_produce")
                || norm.startsWith("jei:category/easy_villagers/")
                || norm.startsWith("jei:category/justenoughbreeding/")
                || norm.contains("hostilenetworks/loot_fabricator")
                || norm.contains("hostilenetworks/sim_chamber")
                || norm.contains("/loot_table")
                || norm.contains("/loot_fabricator")
                || norm.contains("locate_structure")) {
            return true;
        }
        return VILLAGER_TRADE_DISPLAY.matcher(norm).find()
                || JER_DISPLAY.matcher(norm).find()
                || MARKET_NPC_TRADE.matcher(norm).find();
    }

    private static boolean isOptInSystem(String norm) {
        return OPT_IN_EXACT_SYSTEMS.contains(norm)
                || norm.startsWith("jei:category/minecolonies/")
                || norm.startsWith("jei:category/modern_industrialization/")
                || norm.startsWith("jei:category/gregtech/");
    }

    private static RecipeAdvisory lexicalAdvisory(String rawSystemId, String recipeId) {
        String sys = normalizeSystemId(rawSystemId);
        if (sys.endsWith("/salvaging")
                || sys.endsWith("/scrapping")
                || sys.contains("/disassembly")
                || sys.contains("composter")
                || sys.contains("composting")
                || sys.contains("nutritional_liquifier")
                || sys.contains("canning_machine")) {
            return RecipeAdvisory.LAST_RESORT;
        }
        if (!recipeId.isEmpty()
                && (LAST_RESORT_RECIPE.matcher(recipeId).find() || LAST_RESORT_PREDICTION.matcher(recipeId).find())) {
            return RecipeAdvisory.LAST_RESORT;
        }
        if (sys.endsWith("/coloring")
                || sys.endsWith("/painting")
                || sys.endsWith("/dyer")
                || sys.contains("/chiseling")
                || sys.endsWith("/mystery_conversion")
                || sys.startsWith("jei:category/chipped/")
                || sys.contains("securitycraft/vanilla_to_securitycraft")
                || sys.contains("securitycraft/securitycraft_to_vanilla")) {
            return RecipeAdvisory.DISCOURAGED;
        }
        if (!recipeId.isEmpty()) {
            if (recipeId.contains("redying")
                    || recipeId.contains("recolor")
                    || recipeId.contains("redye")
                    || recipeId.endsWith("_smithing_trim")
                    || recipeId.startsWith("quark:tweaks/crafting/slab_to_block")
                    || recipeId.contains("stonecutting/chipped_")
                    || recipeId.contains("stonecutting/chisel_")
                    || recipeId.endsWith("_from_stairs")
                    || recipeId.endsWith("_from_stair")
                    || recipeId.endsWith("_from_walls")
                    || recipeId.endsWith("_from_wall")
                    || recipeId.endsWith("_from_slabs")
                    || recipeId.endsWith("_from_slab")
                    || recipeId.startsWith("allthecompressed:compress/")
                    || recipeId.startsWith("allthecompressed:decompress/")
                    || recipeId.startsWith("create_compressed:")
                    || recipeId.endsWith("_from_crate")
                    || recipeId.endsWith("_from_bag")
                    || recipeId.endsWith("_from_conversion")
                    || recipeId.endsWith("_block_craft")
                    || recipeId.endsWith("_block_decraft")
                    || recipeId.contains("storage_blocks/from_")
                    || recipeId.contains("deconstruction")
                    || recipeId.contains("9x9")
                    || DISCOURAGED_GIANT.matcher(recipeId).find()) {
                return RecipeAdvisory.DISCOURAGED;
            }
        }
        return RecipeAdvisory.NONE;
    }
}
