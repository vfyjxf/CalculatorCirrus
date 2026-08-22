package dev.vfyjxf.cirrus.api.recipe.database;

import dev.vfyjxf.cirrus.api.recipe.search.CirrusSearchFields;
import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cloudlib.api.search.SearchField;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class DatabasePagePerformanceTest {

    @BeforeAll
    static void setupRecipeApi() {
        var builder = new RecipeApiRegistryBuilder();
        var api = new RecipeApiImpl(builder);
        RecipeApiImpl.attach(api);
    }

    private static final String[] MODS = {"minecraft","thermal","mekanism","create",
        "immersiveengineering","botania","bloodmagic","astralsorcery","naturesaura",
        "ars_nouveau","tconstruct","pneumaticcraft","rftools","ae2","refinedstorage",
        "enderio","integrateddynamics","industrialforegoing","mysticalagriculture",
        "forbidden_arcanus","occultism","malum","hexerei","draconicevolution",
        "powah","mekanismgenerators","extreme_reactors","nuclearcraft","gtceu"};

    private static final String[] MATERIALS = {"iron","gold","diamond","emerald",
        "copper","tin","lead","silver","nickel","platinum","iridium","osmium",
        "uranium","aluminum","steel","bronze","brass","electrum","invar","enderium",
        "lumium","signalum","constantan","cobalt","ardite","manyullyn","hepatizon",
        "soulsteel","starmetal","manasteel","terrasteel","elementium","alfsteel"};

    private static final String[] FORMS = {"ore","raw_ore","crushed_ore","purified_ore",
        "dust","tiny_dust","ingot","nugget","block","plate","dense_plate","rod",
        "gear","screw","ring","wire","fine_wire","foil","blade","bolt","spring",
        "small_spring","rotor","grinding_head","drill_head","saw_blade","casing"};

    private static final String[] FLUIDS = {"water","lava","creosote_oil","biomass",
        "ethanol","rocket_fuel","liquid_plastic","molten_glass","uu_matter",
        "liquid_ender","molten_iron","molten_gold","molten_copper","hydrogen",
        "oxygen","nitrogen","sulfuric_acid","hydrochloric_acid","naquadah_enriched"};

    private static final String[] COMPONENTS = {"piston","sticky_piston","dispenser",
        "observer","comparator","repeater","hopper","machine_frame","machine_chassis",
        "advanced_machine_chassis","logic_processor","calculation_processor",
        "engineering_processor","basic_control_circuit","advanced_control_circuit",
        "elite_control_circuit","ultimate_control_circuit","superconductor",
        "capacitor","coil","inductor","resistor","transistor","diode",
        "vacuum_tube","integrated_circuit","cpu","ram_chip","carbon_plate"};

    private static final String[] MACHINES = {"furnace","blast_furnace","smoker",
        "crusher","pulverizer","induction_smelter","centrifuge","electrolyzer",
        "chemical_reactor","distillation_tower","arc_furnace","assembler",
        "inscriber","enrichment_chamber","energized_smelter","alloy_smelter",
        "compressor","precision_sawmill","metal_press","mixing_basin"};

    private record FakeRecipe(String id, Collection<InputContent> inputs,
        Collection<OutputContent> outputs) {}

    private static Namespace ns(String m, String p) { return Namespace.of(m,p); }
    private static InputContent in(String id, long amt) {
        return InputContent.fromValue(new TestValueType(id), amt);
    }
    private static OutputContent out(String id, long amt, float chance) {
        return OutputContent.fromValue(new TestValueType(id), amt, chance);
    }
    private static OutputContent out(String id, long amt) { return out(id,amt,1f); }

    private static final class TestValueType implements ValueType<Long> {
        private final Namespace id;
        TestValueType(String raw) {
            int c = raw.indexOf(':');
            id = Namespace.of(raw.substring(0,c), raw.substring(c+1));
        }
        @Override public Namespace typeId() { return id; }
        @Override public long getValue(Long v) { return v; }
        @Override public boolean matches(Long a,Long b) { return Objects.equals(a,b); }
        @Override public Long fromValue(long v) { return v; }
        @Override public Long copy(Long v) { return v; }
    }

    private record Dataset(List<FakeRecipeSystem> systems, List<Namespace> systemIds) {
        int size() { return systems.stream().mapToInt(s->s.recipes.size()).sum(); }
    }

    private static class FakeRecipeSystem implements RecipeSystem<FakeRecipe> {
        private final Namespace id; final RichIterable<FakeRecipe> recipes;
        private final Map<String,FakeRecipe> byId = new ConcurrentHashMap<>();
        FakeRecipeSystem(Namespace id, Collection<FakeRecipe> rs) {
            this.id=id; recipes=Lists.immutable.ofAll(rs);
            rs.forEach(r->byId.put(r.id(),r));
        }
        @Override public Namespace systemId() { return id; }
        @Override public Class<?> basicType() { return FakeRecipe.class; }
        @Override public @Nullable ResourceLocation getId(FakeRecipe r) {
            return ResourceLocation.fromNamespaceAndPath(id.root(), r.id());
        }
        @Override public @Nullable FakeRecipe findRecipe(String s) { return byId.get(s); }
        @Override public @Nullable FakeRecipe findRecipe(ResourceLocation rl) { return byId.get(rl.getPath()); }
        @Override public int hash(FakeRecipe r) { return r.id().hashCode(); }
        @Override public boolean sameRecipe(FakeRecipe a, FakeRecipe b) { return Objects.equals(a.id(),b.id()); }
        @Override public ResolveResult resolveFallback(ResolveContext ctx, FakeRecipe r) {
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this,r), r.inputs(), r.outputs()));
        }
        @Override public RichIterable<FakeRecipe> allRecipes() { return recipes; }
    }

    private static Dataset generate(int target, long seed) {
        Random rng = new Random(seed);
        List<FakeRecipe> all = new ArrayList<>(target);

        int oreC = Math.max(1, target * 20 / 100 / 6);
        for (int i = 0; i < oreC; i++) {
            String m = MODS[rng.nextInt(Math.min(MODS.length,10))];
            String mat = MATERIALS[rng.nextInt(MATERIALS.length)];
            String o = m+":ore_"+mat, cr = m+":crushed_"+mat, pu = m+":purified_"+mat;
            String d = m+":dust_"+mat, in = m+":ingot_"+mat, ng = m+":nugget_"+mat;
            all.add(new FakeRecipe("ore_crush_"+i, List.of(in(o,1)), List.of(out(cr,2),out(d,1,0.1f))));
            all.add(new FakeRecipe("ore_wash_"+i, List.of(in(cr,1)), List.of(out(pu,1),out(d+"_tiny",1,0.2f))));
            all.add(new FakeRecipe("ore_cent_"+i, List.of(in(pu,1)),
                List.of(out(d,1), out(MODS[rng.nextInt(MODS.length)]+":dust_"+MATERIALS[rng.nextInt(MATERIALS.length)],1,0.15f))));
            all.add(new FakeRecipe("ore_smelt_"+i, List.of(in(d,1)), List.of(out(in,1))));
            all.add(new FakeRecipe("ingot_nugget_"+i, List.of(in(in,1)), List.of(out(ng,9))));
            all.add(new FakeRecipe("nugget_ingot_"+i, List.of(in(ng,9)), List.of(out(in,1))));
        }

        int alloyC = target * 25 / 100;
        for (int i = 0; i < alloyC; i++) {
            String mod = MODS[rng.nextInt(MODS.length)];
            int inC = 2 + rng.nextInt(3);
            List<InputContent> ins = new ArrayList<>();
            for (int j = 0; j < inC; j++)
                ins.add(in(mod+":"+FORMS[rng.nextInt(FORMS.length)]+"_"+MATERIALS[rng.nextInt(MATERIALS.length)], 1+rng.nextInt(16)));
            List<OutputContent> outs = new ArrayList<>();
            outs.add(out(mod+":"+FORMS[12+rng.nextInt(10)]+"_"+MATERIALS[rng.nextInt(MATERIALS.length)], 1+rng.nextInt(8)));
            if (rng.nextDouble() < 0.2) outs.add(out(mod+":slag", 1+rng.nextInt(3), 0.3f));
            all.add(new FakeRecipe("alloy_"+i, ins, outs));
        }

        int compC = target * 25 / 100;
        for (int i = 0; i < compC; i++) {
            String mod = MODS[rng.nextInt(MODS.length)];
            int inC = 2 + rng.nextInt(5);
            List<InputContent> ins = new ArrayList<>();
            for (int j = 0; j < inC; j++) {
                if (rng.nextDouble() < 0.3) ins.add(in(mod+":"+COMPONENTS[rng.nextInt(COMPONENTS.length)], 1+rng.nextInt(4)));
                else ins.add(in(mod+":ingot_"+MATERIALS[rng.nextInt(MATERIALS.length)], 1+rng.nextInt(8)));
            }
            List<OutputContent> outs = new ArrayList<>();
            outs.add(out(mod+":"+COMPONENTS[rng.nextInt(COMPONENTS.length)], 1+rng.nextInt(8)));
            if (rng.nextDouble() < 0.15) outs.add(out(mod+":scrap", 1, 0.12f));
            all.add(new FakeRecipe("comp_"+i, ins, outs));
        }

        int machC = target * 15 / 100;
        for (int i = 0; i < machC; i++) {
            String mod = MODS[rng.nextInt(MODS.length)];
            int inC = 3 + rng.nextInt(6);
            List<InputContent> ins = new ArrayList<>();
            for (int j = 0; j < inC; j++) {
                if (rng.nextDouble() < 0.4) ins.add(in(mod+":"+COMPONENTS[rng.nextInt(COMPONENTS.length)], 1));
                else ins.add(in(mod+":plate_"+MATERIALS[rng.nextInt(MATERIALS.length)], 1+rng.nextInt(4)));
            }
            all.add(new FakeRecipe("mach_"+i, ins, List.of(out(mod+":machine_"+MACHINES[rng.nextInt(MACHINES.length)], 1))));
        }

        int chemC = target * 8 / 100;
        for (int i = 0; i < chemC; i++) {
            String mod = MODS[rng.nextInt(MODS.length)];
            int inC = 2 + rng.nextInt(3);
            List<InputContent> ins = new ArrayList<>();
            for (int j = 0; j < inC; j++)
                ins.add(in(mod+":"+FLUIDS[rng.nextInt(FLUIDS.length)], 100+rng.nextInt(900)));
            List<OutputContent> outs = new ArrayList<>();
            outs.add(out(mod+":"+FLUIDS[rng.nextInt(FLUIDS.length)], 50+rng.nextInt(450)));
            if (rng.nextDouble() < 0.3) outs.add(out(mod+":chemical_waste", 10+rng.nextInt(40), 0.25f));
            all.add(new FakeRecipe("chem_"+i, ins, outs));
        }

        int recycleC = target * 5 / 100;
        for (int i = 0; i < recycleC; i++) {
            String mod = MODS[rng.nextInt(MODS.length)];
            String m = MACHINES[rng.nextInt(MACHINES.length)];
            List<OutputContent> outs = new ArrayList<>();
            for (int j = 0; j < 3+rng.nextInt(3); j++)
                outs.add(out(mod+":plate_"+MATERIALS[rng.nextInt(MATERIALS.length)], 1+rng.nextInt(3), 0.5f+rng.nextFloat()*0.3f));
            all.add(new FakeRecipe("recycle_"+i, List.of(in(mod+":machine_"+m, 1)), outs));
        }

        int misc = target - all.size();
        for (int i = 0; i < misc; i++) {
            String mod = MODS[rng.nextInt(MODS.length)];
            all.add(new FakeRecipe("misc_"+i,
                List.of(in(mod+":"+COMPONENTS[rng.nextInt(COMPONENTS.length)], 1+rng.nextInt(4))),
                List.of(out(mod+":"+COMPONENTS[rng.nextInt(COMPONENTS.length)], 1+rng.nextInt(4)))));
        }

        java.util.Collections.shuffle(all, rng);

        int sysC = Math.min(MODS.length, target / 180 + 4);
        List<Namespace> ids = new ArrayList<>();
        for (int s = 0; s < sysC; s++) ids.add(ns(MODS[s], "recipe_system"));
        List<List<FakeRecipe>> bySys = new ArrayList<>();
        for (int s = 0; s < sysC; s++) bySys.add(new ArrayList<>());
        for (int i = 0; i < all.size(); i++) bySys.get(i % sysC).add(all.get(i));
        List<FakeRecipeSystem> systems = new ArrayList<>();
        for (int s = 0; s < sysC; s++) systems.add(new FakeRecipeSystem(ids.get(s), bySys.get(s)));
        return new Dataset(systems, ids);
    }

    private static List<DatabaseDefinition> dbsFor(Dataset ds) {
        List<DatabaseDefinition> dbs = new ArrayList<>();
        int count = Math.min(ds.systemIds.size(), 16);
        for (int i = 0; i < count; i++)
            dbs.add(new DatabaseDefinition("db_"+i, MODS[i]+" Recipes", DatabaseKind.system, true, i, List.of(ds.systemIds.get(i))));
        return dbs;
    }

    @Test void buildIndex_10k() { measureBuild(10_000, 100L); }
    @Test void buildIndex_50k() { measureBuild(50_000, 101L); }
    @Test void buildIndex_100k() { measureBuild(100_000, 102L); }

    private void measureBuild(int count, long seed) {
        Dataset ds = generate(count, seed);
        long t0 = System.nanoTime();
        RecipeDatabaseIndex idx = RecipeDatabaseIndex.build(dbsFor(ds),
            Lists.immutable.ofAll(ds.systems()), Map.of());
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        assertTrue(idx.records().size() > 0);
        System.out.printf("[buildIndex_%dk] %d ms%n", count/1000, ms);
        System.out.printf("# %d recipes -> %d records%n", ds.size(), idx.records().size());
    }

    @Test void keystrokePipeline_10k() { measureKeystrokes(10_000, 200L, 50); }
    @Test void keystrokePipeline_50k() { measureKeystrokes(50_000, 201L, 20); }
    @Test void keystrokePipeline_100k() { measureKeystrokes(100_000, 202L, 10); }

    private void measureKeystrokes(int count, long seed, int iters) {
        Dataset ds = generate(count, seed);
        RecipeDatabaseIndex idx = RecipeDatabaseIndex.build(dbsFor(ds),
            Lists.immutable.ofAll(ds.systems()), Map.of());

        String[] strokes = {"i","ir","iro","iron","iron ","iron i",
            "iron in","iron ing","iron ingo","iron ingot"};

        for (int w = 0; w < Math.max(2, iters/10); w++)
            for (String s : strokes) idx.search(s);

        for (String stroke : strokes) {
            long totalNs = 0; int totalRes = 0;
            for (int i = 0; i < iters; i++) {
                long t0 = System.nanoTime();
                List<RecipeDatabaseResult> res = idx.search(stroke);
                // results already sorted by RecipeDatabaseIndex.search()
                totalNs += System.nanoTime() - t0;
                totalRes += res.size();
            }
            System.out.printf("[keystroke_%dk] query='%s' -> %d res, %.1f us%n",
                count/1000, stroke, totalRes/iters, totalNs/1_000.0/iters);
        }
    }

    @Test void suggestionRefresh_10k() { measureSuggestions(10_000, 300L, 30); }
    @Test void suggestionRefresh_50k() { measureSuggestions(50_000, 301L, 15); }
    @Test void suggestionRefresh_100k() { measureSuggestions(100_000, 302L, 8); }

    private void measureSuggestions(int count, long seed, int iters) {
        Dataset ds = generate(count, seed);
        RecipeDatabaseIndex idx = RecipeDatabaseIndex.build(dbsFor(ds),
            Lists.immutable.ofAll(ds.systems()), Map.of());

        for (int w = 0; w < Math.max(3, iters/4); w++) {
            idx.textCandidateMap(24);
            for (var f : CirrusSearchFields.SET.fields()) {
                if (f == CirrusSearchFields.text || f == CirrusSearchFields.time || f == CirrusSearchFields.energy
                    || f == CirrusSearchFields.chance || f == CirrusSearchFields.tier || f == CirrusSearchFields.level) continue;
                idx.textCandidates(f, 16);
            }
        }

        long mapNs = 0;
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            idx.textCandidateMap(24);
            mapNs += System.nanoTime() - t0;
        }
        System.out.printf("[suggest_%dk] textCandidateMap: %.1f us%n", count/1000, mapNs/1_000.0/iters);

        for (var f : CirrusSearchFields.SET.fields()) {
            if (f == CirrusSearchFields.text || f == CirrusSearchFields.time || f == CirrusSearchFields.energy
                || f == CirrusSearchFields.chance || f == CirrusSearchFields.tier || f == CirrusSearchFields.level) continue;
            long fNs = 0;
            int fi = Math.max(5, iters/2);
            for (int i = 0; i < fi; i++) {
                long t0 = System.nanoTime();
                idx.textCandidates(f, 16);
                fNs += System.nanoTime() - t0;
            }
            System.out.printf("[suggest_%dk] textCandidates(%s): %.1f us%n",
                count/1000, f, fNs/1_000.0/fi);
        }
    }

    @Test void uncappedQueryPipeline_10k() { measureUncappedPipeline(10_000, 400L); }
    @Test void uncappedQueryPipeline_50k() { measureUncappedPipeline(50_000, 401L); }
    @Test void uncappedQueryPipeline_100k() { measureUncappedPipeline(100_000, 402L); }

    /**
     * Replicates the full DatabasePage query pipeline with no result cap:
     * search("") (matches everything) -> relevance sort -> category bucketing.
     * Guards the uncapped full-list rendering path from regressing past interactive budgets.
     */
    private void measureUncappedPipeline(int count, long seed) {
        Dataset ds = generate(count, seed);
        RecipeDatabaseIndex idx = RecipeDatabaseIndex.build(dbsFor(ds),
            Lists.immutable.ofAll(ds.systems()), Map.of());
        int recordCount = idx.records().size();

        int iters = 10;
        for (int w = 0; w < 3; w++) {
            idx.search("");
        }

        long searchNs = 0, sortNs = 0, bucketNs = 0;
        int matched = 0, bucketCount = 0;
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            List<RecipeDatabaseResult> matches = idx.search("");
            long t1 = System.nanoTime();
            List<RecipeDatabaseResult> sorted = matches.stream()
                .sorted(Comparator
                    .comparingInt(RecipeDatabaseResult::matchScore).reversed()
                    .thenComparing(r -> r.record().recipe().systemId().toString().toLowerCase(Locale.ROOT)))
                .toList();
            long t2 = System.nanoTime();
            LinkedHashMap<String, List<RecipeDatabaseResult>> groups = new LinkedHashMap<>();
            for (RecipeDatabaseResult result : sorted) {
                RecipeDatabaseRecord record = result.record();
                String key = record.systemTags().isEmpty()
                    ? record.recipe().systemId().root()
                    : record.systemTags().getFirst().toString();
                groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(result);
            }
            long t3 = System.nanoTime();
            searchNs += t1 - t0;
            sortNs += t2 - t1;
            bucketNs += t3 - t2;
            matched = sorted.size();
            bucketCount = groups.size();
        }

        assertEquals(recordCount, matched, "empty query must return every record uncapped");
        assertTrue(bucketCount > 0);
        System.out.printf("[uncapped_%dk] records=%d buckets=%d search=%.2f ms sort=%.2f ms bucket=%.2f ms total=%.2f ms%n",
            count / 1000, matched, bucketCount,
            searchNs / 1_000_000.0 / iters,
            sortNs / 1_000_000.0 / iters,
            bucketNs / 1_000_000.0 / iters,
            (searchNs + sortNs + bucketNs) / 1_000_000.0 / iters);
    }
}

