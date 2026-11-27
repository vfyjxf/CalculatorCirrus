package dev.vfyjxf.cirrus.api.recipe.search;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseDefinition;
import dev.vfyjxf.cirrus.api.recipe.database.DatabaseKind;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseResult;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.api.search.*;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class SearchPerformanceTest {

    @BeforeAll
    static void setupRecipeApi() {
        var builder = new RecipeApiRegistryBuilder();
        var api = new RecipeApiImpl(builder);
        RecipeApiImpl.attach(api);
    }

    private static final String[] mods = {"minecraft", "thermal", "mekanism", "create", "immersiveengineering",
            "botania", "bloodmagic", "astralsorcery", "naturesaura", "ars_nouveau", "tconstruct", "pneumaticcraft",
            "rftools", "ae2", "refinedstorage", "enderio", "integrateddynamics", "industrialforegoing",
            "mysticalagriculture", "forbidden_arcanus", "occultism", "malum", "hexerei", "draconicevolution", "powah",
            "mekanismgenerators", "extreme_reactors", "nuclearcraft", "gtceu"};

    private static final String[] materials = {"iron", "gold", "diamond", "emerald", "copper", "tin", "lead", "silver",
            "nickel", "platinum", "iridium", "osmium", "uranium", "aluminum", "steel", "bronze", "brass", "electrum",
            "invar", "enderium", "lumium", "signalum", "constantan", "cobalt", "ardite", "manyullyn", "hepatizon",
            "soulsteel", "starmetal", "manasteel", "terrasteel", "elementium", "alfsteel", "gaia_spirit", "dragonstone",
            "neutronium", "infinity", "crystaltine", "dark_steel", "pulsating_iron", "vibrant_alloy", "conductive_iron",
            "energetic_alloy", "redstone_alloy", "refined_obsidian", "refined_glowstone", "certus_quartz", "fluix",
            "sky_stone", "ender_pearl", "blaze", "prismarine", "nether_quartz", "coal", "charcoal", "lapis", "redstone",
            "glowstone", "gunpowder", "slime"};

    private static final String[] forms = {"ore", "raw_ore", "crushed_ore", "purified_ore", "dust", "tiny_dust",
            "ingot", "nugget", "block", "plate", "dense_plate", "rod", "gear", "screw", "ring", "wire", "fine_wire",
            "foil", "blade", "bolt", "spring", "small_spring", "rotor", "grinding_head", "drill_head", "saw_blade",
            "casing"};

    private static final String[] fluids = {"water", "lava", "creosote_oil", "biomass", "ethanol", "rocket_fuel",
            "liquid_plastic", "molten_glass", "uu_matter", "liquid_ender", "molten_iron", "molten_gold",
            "molten_copper", "hydrogen", "oxygen", "nitrogen", "sulfuric_acid", "hydrochloric_acid",
            "naquadah_enriched", "heavy_water", "deuterium", "tritium", "bio_diesel", "seed_oil", "latex"};

    private static final String[] components = {"piston", "sticky_piston", "dispenser", "observer", "comparator",
            "repeater", "hopper", "redstone_torch", "lever", "button", "pressure_plate", "tripwire_hook",
            "daylight_detector", "dropper", "machine_frame", "machine_chassis", "advanced_machine_chassis",
            "logic_processor", "calculation_processor", "engineering_processor", "printed_silicon",
            "printed_logic_circuit", "printed_calculation_circuit", "printed_engineering_circuit",
            "basic_control_circuit", "advanced_control_circuit", "elite_control_circuit", "ultimate_control_circuit",
            "superconductor", "capacitor", "coil", "inductor", "resistor", "transistor", "diode", "vacuum_tube",
            "integrated_circuit", "cpu", "ram_chip", "hdd_platter", "optical_fiber", "carbon_plate", "carbon_mesh"};

    private static final String[] machines = {"furnace", "blast_furnace", "smoker", "crusher", "pulverizer",
            "induction_smelter", "centrifuge", "electrolyzer", "chemical_reactor", "distillation_tower", "arc_furnace",
            "assembler", "inscriber", "enrichment_chamber", "energized_smelter", "alloy_smelter", "compressor",
            "precision_sawmill", "metal_press", "mixing_basin", "fermenter", "squeezer", "carpenter",
            "thermal_centrifuge", "ore_washer", "macerator", "extractor", "compactor", "fluid_solidifier",
            "pyrolyse_oven", "coker_unit"};

    private record FakeRecipe(String id, Collection<InputContent> inputs, Collection<OutputContent> outputs) {}

    private record SyntheticDataset(List<FakeRecipeSystem> systems, List<Namespace> systemIds, int totalRecipes) {
        List<DatabaseDefinition> databases() {
            List<DatabaseDefinition> dbs = new ArrayList<>();
            for (int i = 0; i < systemIds.size(); i++)
                dbs.add(
                    new DatabaseDefinition(
                        "db_" + i,
                        mods[i] + " Recipes",
                        DatabaseKind.system,
                        true,
                        i,
                        List.of(systemIds.get(i))
                    )
                );
            return dbs;
        }
    }

    private static class FakeRecipeSystem implements RecipeSystem<FakeRecipe> {
        private final Namespace systemId;
        final RichIterable<FakeRecipe> recipes;
        private final Map<String, FakeRecipe> byId = new ConcurrentHashMap<>();
        FakeRecipeSystem(Namespace sid, Collection<FakeRecipe> rs) {
            systemId = sid;
            recipes = Lists.immutable.ofAll(rs);
            rs.forEach(r -> byId.put(r.id(), r));
        }

        @Override
        public Namespace systemId() {
            return systemId;
        }

        @Override
        public Class<?> basicType() {
            return FakeRecipe.class;
        }

        @Override
        public @Nullable ResourceLocation getId(FakeRecipe r) {
            return ResourceLocation.fromNamespaceAndPath(systemId.root(), r.id());
        }

        @Override
        public @Nullable FakeRecipe findRecipe(String id) {
            return byId.get(id);
        }

        @Override
        public @Nullable FakeRecipe findRecipe(ResourceLocation rl) {
            return byId.get(rl.getPath());
        }

        @Override
        public int hash(FakeRecipe r) {
            return r.id().hashCode();
        }

        @Override
        public boolean sameRecipe(FakeRecipe a, FakeRecipe b) {
            return Objects.equals(a.id(), b.id());
        }

        @Override
        public ResolveResult resolveFallback(ResolveContext ctx, FakeRecipe r) {
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this, r), r.inputs(), r.outputs()));
        }

        @Override
        public RichIterable<FakeRecipe> allRecipes() {
            return recipes;
        }
    }

    private record TestValueType(Namespace typeId) implements ValueType<Long> {
        TestValueType(String full) {
            this(Namespace.of(prefix(full), suffix(full)));
        }

        private static String prefix(String s) {
            return s.substring(0, s.indexOf(':'));
        }

        private static String suffix(String s) {
            return s.substring(s.indexOf(':') + 1);
        }

        @Override
        public long getValue(Long c) {
            return c;
        }

        @Override
        public boolean matches(Long a, Long b) {
            return Objects.equals(a, b);
        }

        @Override
        public Long fromValue(long v) {
            return v;
        }

        @Override
        public Long copy(Long c) {
            return c;
        }
    }

    private static Namespace ns(String mod, String path) {
        return Namespace.of(mod, path);
    }

    private static InputContent in(String id, long amt) {
        return InputContent.fromValue(new TestValueType(id), amt);
    }

    private static OutputContent out(String id, long amt, float chance) {
        return OutputContent.fromValue(new TestValueType(id), amt, chance);
    }

    private static OutputContent out(String id, long amt) {
        return out(id, amt, 1f);
    }

    private static SyntheticDataset generate(int target, long seed) {
        Random rng = new Random(seed);
        List<FakeRecipe> all = new ArrayList<>(target);

        int oreC = Math.max(1, target * 20 / 100 / 6); // 6 recipes per ore material
        for (int i = 0; i < oreC; i++) {
            String m = mods[rng.nextInt(Math.min(mods.length, 10))];
            String mat = materials[rng.nextInt(materials.length)];
            String o = m + ":ore_" + mat, cr = m + ":crushed_" + mat, pu = m + ":purified_" + mat;
            String d = m + ":dust_" + mat, in = m + ":ingot_" + mat, ng = m + ":nugget_" + mat;
            all.add(new FakeRecipe("ore_crush_" + i, List.of(in(o, 1)), List.of(out(cr, 2), out(d, 1, 0.1f))));
            all.add(
                new FakeRecipe("ore_wash_" + i, List.of(in(cr, 1)), List.of(out(pu, 1), out(d + "_tiny", 1, 0.2f)))
            );
            all.add(
                new FakeRecipe(
                    "ore_cent_" + i,
                    List.of(in(pu, 1)),
                    List.of(
                        out(d, 1),
                        out(
                            mods[rng.nextInt(mods.length)] + ":dust_" + materials[rng.nextInt(materials.length)],
                            1,
                            0.15f
                        )
                    )
                )
            );
            all.add(new FakeRecipe("ore_smelt_" + i, List.of(in(d, 1)), List.of(out(in, 1))));
            all.add(new FakeRecipe("ingot_nugget_" + i, List.of(in(in, 1)), List.of(out(ng, 9))));
            all.add(new FakeRecipe("nugget_ingot_" + i, List.of(in(ng, 9)), List.of(out(in, 1))));
        }

        int alloyC = target * 25 / 100;
        for (int i = 0; i < alloyC; i++) {
            String mod = mods[rng.nextInt(mods.length)];
            int inC = 2 + rng.nextInt(3);
            List<InputContent> ins = new ArrayList<>();
            for (int j = 0; j < inC; j++)
                ins.add(
                    in(
                        mod + ":" + forms[rng.nextInt(forms.length)] + "_" + materials[rng.nextInt(materials.length)],
                        1 + rng.nextInt(16)
                    )
                );
            List<OutputContent> outs = new ArrayList<>();
            outs.add(
                out(
                    mod + ":" + forms[12 + rng.nextInt(10)] + "_" + materials[rng.nextInt(materials.length)],
                    1 + rng.nextInt(8)
                )
            );
            if (rng.nextDouble() < 0.2) outs.add(out(mod + ":slag", 1 + rng.nextInt(3), 0.3f));
            all.add(new FakeRecipe("alloy_" + i, ins, outs));
        }

        int compC = target * 25 / 100;
        for (int i = 0; i < compC; i++) {
            String mod = mods[rng.nextInt(mods.length)];
            int inC = 2 + rng.nextInt(5);
            List<InputContent> ins = new ArrayList<>();
            for (int j = 0; j < inC; j++) {
                if (rng.nextDouble() < 0.3)
                    ins.add(in(mod + ":" + components[rng.nextInt(components.length)], 1 + rng.nextInt(4)));
                else
                    ins.add(in(mod + ":ingot_" + materials[rng.nextInt(materials.length)], 1 + rng.nextInt(8)));
            }
            List<OutputContent> outs = new ArrayList<>();
            outs.add(out(mod + ":" + components[rng.nextInt(components.length)], 1 + rng.nextInt(8)));
            if (rng.nextDouble() < 0.15) outs.add(out(mod + ":scrap", 1, 0.12f));
            all.add(new FakeRecipe("comp_" + i, ins, outs));
        }

        int machC = target * 15 / 100;
        for (int i = 0; i < machC; i++) {
            String mod = mods[rng.nextInt(mods.length)];
            int inC = 3 + rng.nextInt(6);
            List<InputContent> ins = new ArrayList<>();
            for (int j = 0; j < inC; j++) {
                if (rng.nextDouble() < 0.4) ins.add(in(mod + ":" + components[rng.nextInt(components.length)], 1));
                else ins.add(in(mod + ":plate_" + materials[rng.nextInt(materials.length)], 1 + rng.nextInt(4)));
            }
            all.add(
                new FakeRecipe(
                    "mach_" + i,
                    ins,
                    List.of(out(mod + ":machine_" + machines[rng.nextInt(machines.length)], 1))
                )
            );
        }

        int chemC = target * 8 / 100;
        for (int i = 0; i < chemC; i++) {
            String mod = mods[rng.nextInt(mods.length)];
            int inC = 2 + rng.nextInt(3);
            List<InputContent> ins = new ArrayList<>();
            for (int j = 0; j < inC; j++)
                ins.add(in(mod + ":" + fluids[rng.nextInt(fluids.length)], 100 + rng.nextInt(900)));
            List<OutputContent> outs = new ArrayList<>();
            outs.add(out(mod + ":" + fluids[rng.nextInt(fluids.length)], 50 + rng.nextInt(450)));
            if (rng.nextDouble() < 0.3) outs.add(out(mod + ":chemical_waste", 10 + rng.nextInt(40), 0.25f));
            all.add(new FakeRecipe("chem_" + i, ins, outs));
        }

        int recycleC = target * 5 / 100;
        for (int i = 0; i < recycleC; i++) {
            String mod = mods[rng.nextInt(mods.length)];
            String m = machines[rng.nextInt(machines.length)];
            List<OutputContent> outs = new ArrayList<>();
            for (int j = 0; j < 3 + rng.nextInt(3); j++)
                outs.add(
                    out(
                        mod + ":plate_" + materials[rng.nextInt(materials.length)],
                        1 + rng.nextInt(3),
                        0.5f + rng.nextFloat() * 0.3f
                    )
                );
            all.add(new FakeRecipe("recycle_" + i, List.of(in(mod + ":machine_" + m, 1)), outs));
        }

        int misc = target - all.size();
        for (int i = 0; i < misc; i++) {
            String mod = mods[rng.nextInt(mods.length)];
            all.add(
                new FakeRecipe(
                    "misc_" + i,
                    List.of(in(mod + ":" + components[rng.nextInt(components.length)], 1 + rng.nextInt(4))),
                    List.of(out(mod + ":" + components[rng.nextInt(components.length)], 1 + rng.nextInt(4)))
                )
            );
        }

        Collections.shuffle(all, rng);

        int sysC = Math.min(mods.length, target / 180 + 4);
        List<Namespace> ids = new ArrayList<>();
        for (int s = 0; s < sysC; s++) ids.add(ns(mods[s], "recipe_system"));
        List<List<FakeRecipe>> bySys = new ArrayList<>();
        for (int s = 0; s < sysC; s++) bySys.add(new ArrayList<>());
        for (int i = 0; i < all.size(); i++) bySys.get(i % sysC).add(all.get(i));
        List<FakeRecipeSystem> systems = new ArrayList<>();
        for (int s = 0; s < sysC; s++) systems.add(new FakeRecipeSystem(ids.get(s), bySys.get(s)));

        return new SyntheticDataset(systems, ids, all.size());
    }

    // -- Tests --

    @Test
    void buildIndex_1k() {
        measureBuild(1_000, 42L);
    }

    @Test
    void buildIndex_10k() {
        measureBuild(10_000, 43L);
    }

    @Test
    void buildIndex_50k() {
        measureBuild(50_000, 44L);
    }

    @Test
    void buildIndex_100k() {
        measureBuild(100_000, 45L);
    }

    private void measureBuild(int count, long seed) {
        SyntheticDataset ds = generate(count, seed);
        long start = System.nanoTime();
        RecipeDatabaseIndex idx = RecipeDatabaseIndex
                .build(ds.databases(), Lists.immutable.ofAll(ds.systems()), Map.of());
        long ms = (System.nanoTime() - start) / 1_000_000L;
        assertFalse(idx.records().isEmpty(), "the index this benchmark measures must hold records");
        System.out.printf("[build_%dk] %d ms%n", count / 1000, ms);
        System.out.printf("# %d recipes -> %d records%n", ds.totalRecipes, idx.records().size());
    }

    @Test
    void searchBatch_10k() {
        measureSearch(10_000, 46L, 50);
    }

    @Test
    void searchBatch_50k() {
        measureSearch(50_000, 47L, 20);
    }

    @Test
    void searchBatch_100k() {
        measureSearch(100_000, 48L, 10);
    }

    private void measureSearch(int count, long seed, int iters) {
        SyntheticDataset ds = generate(count, seed);
        RecipeDatabaseIndex idx = RecipeDatabaseIndex
                .build(ds.databases(), Lists.immutable.ofAll(ds.systems()), Map.of());
        assertFalse(idx.records().isEmpty(), "the index this benchmark measures must hold records");

        String[] queries = {"iron", "gold ingot", "copper plate", "diamond", "@minecraft", "@thermal", "@mekanism",
                "@create", "has:ingot", "has:dust", "has:plate", "has:gear", "in:ore_iron", "out:ingot_steel",
                "out:machine_", "iron | copper | tin", "steel -dust", "crusher", "has:ingot * >=64", "\"iron ingot\"",
                "iron*", "in:crushed_copper out:ingot_copper", "dust * >=16", "@gtceu", "wire_fine", "alloy_smelter",
                "has:plate * >=8 out:gear * >=1"};

        for (int w = 0; w < Math.max(3, iters / 5); w++) for (String q : queries) idx.search(q);

        long totalNs = 0;
        int totalRes = 0;
        for (int r = 0; r < iters; r++) for (String q : queries) {
            long t0 = System.nanoTime();
            List<RecipeDatabaseResult> res = idx.search(q);
            totalNs += System.nanoTime() - t0;
            totalRes += res.size();
        }
        System.out.printf(
            "[search_%dk] %d queries x %d, avg %.1f us/q, %d results%n",
            count / 1000,
            queries.length,
            iters,
            totalNs / 1_000.0 / (queries.length * iters),
            totalRes
        );
    }

    @Test
    void textCandidateMap_10k() {
        measureCandidateMap(10_000, 49L, 30);
    }

    @Test
    void textCandidateMap_50k() {
        measureCandidateMap(50_000, 50L, 15);
    }

    @Test
    void textCandidateMap_100k() {
        measureCandidateMap(100_000, 51L, 8);
    }

    private void measureCandidateMap(int count, long seed, int iters) {
        SyntheticDataset ds = generate(count, seed);
        RecipeDatabaseIndex idx = RecipeDatabaseIndex
                .build(ds.databases(), Lists.immutable.ofAll(ds.systems()), Map.of());
        assertFalse(idx.records().isEmpty(), "the index this benchmark measures must hold records");

        for (int w = 0; w < Math.max(3, iters / 3); w++) idx.textCandidateMap(24);

        long totalNs = 0;
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            idx.textCandidateMap(24);
            totalNs += System.nanoTime() - t0;
        }
        System.out.printf("[candidates_%dk] avg %.1f us/call%n", count / 1000, totalNs / 1_000.0 / iters);
    }

    @Test
    void searchMatcher_micro() {
        SearchDocument doc = SearchDocument.builder(CirrusSearchFields.set, CirrusSearchFields.text)
                .searchableText(CirrusSearchFields.name, "Iron Ingot")
                .searchableText(CirrusSearchFields.in, "minecraft:ore_iron")
                .searchableText(CirrusSearchFields.out, "minecraft:ingot_iron")
                .text(CirrusSearchFields.mod, "minecraft").text(CirrusSearchFields.tag, "forge:ingots/iron")
                .text(CirrusSearchFields.tag, "forge:ingots").number(CirrusSearchFields.time, 240)
                .number(CirrusSearchFields.energy, 3200).build();

        SearchParser p = CirrusSearchFields.parser();
        SearchExpression[] exprs = {p.parse("iron"), p.parse("Iron Ingot"), p.parse("iron ingot"),
                p.parse("@minecraft"), p.parse("$forge:ingots/iron"), p.parse("in:minecraft:ore_iron"),
                p.parse("out:minecraft:ingot_iron"), p.parse("has:iron"), p.parse("iron* * >=64"),
                p.parse("iron | gold | copper"), p.parse("iron -dust"), p.parse("(iron | steel) ingot"),
                p.parse("time>=200s energy<4k")};

        assertTrue(
            SearchMatcher.matches(p.parse("iron"), doc),
            "the micro benchmark must start from a matching expression"
        );

        for (int w = 0; w < 1000; w++) for (var e : exprs) SearchMatcher.matches(e, doc);

        long totalNs = 0;
        int iters = 10_000;
        for (int i = 0; i < iters; i++) for (var e : exprs) {
            long t0 = System.nanoTime();
            SearchMatcher.matches(e, doc);
            totalNs += System.nanoTime() - t0;
        }
        System.out.printf(
            "[matcher_micro] %d x %d, avg %.0f ns/match%n",
            exprs.length,
            iters,
            (double) totalNs / (exprs.length * iters)
        );
    }
}
