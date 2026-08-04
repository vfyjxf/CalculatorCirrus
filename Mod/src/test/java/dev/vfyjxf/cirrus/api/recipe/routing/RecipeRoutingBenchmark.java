package dev.vfyjxf.cirrus.api.recipe.routing;

import dev.vfyjxf.cirrus.api.content.type.ValueType;
import dev.vfyjxf.cirrus.api.recipe.RecipeEntry;
import dev.vfyjxf.cirrus.api.recipe.content.InputContent;
import dev.vfyjxf.cirrus.api.recipe.content.OutputContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.routing.service.*;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystem;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveContext;
import dev.vfyjxf.cirrus.api.recipe.system.ResolveResult;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cirrus.recipe.RecipeApiImpl;
import dev.vfyjxf.cirrus.recipe.RecipeApiRegistryBuilder;
import net.minecraft.resources.ResourceLocation;
import org.eclipse.collections.api.RichIterable;
import org.eclipse.collections.api.factory.Lists;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

class RecipeRoutingBenchmark {

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
        "forbidden_arcanus","occultism","malum","hexerei","draconicevolution"};

    private static final String[] MATERIALS = {"iron","gold","diamond","emerald",
        "copper","tin","lead","silver","nickel","platinum","iridium","osmium",
        "uranium","aluminum","steel","bronze","brass","electrum","invar","enderium",
        "lumium","signalum","constantan","cobalt","ardite","manyullyn","hepatizon"};

    private static final String[] FORMS = {"ore","raw_ore","crushed_ore","purified_ore",
        "dust","tiny_dust","ingot","nugget","block","plate","dense_plate","rod",
        "gear","screw","ring","wire","fine_wire","foil","blade","bolt","spring",
        "small_spring","rotor","grinding_head","drill_head","saw_blade","casing"};

    private static final String[] COMPONENTS = {"piston","sticky_piston","dispenser",
        "observer","comparator","repeater","hopper","machine_frame","machine_chassis",
        "advanced_machine_chassis","logic_processor","calculation_processor",
        "engineering_processor","basic_control_circuit","advanced_control_circuit",
        "elite_control_circuit","ultimate_control_circuit","superconductor",
        "capacitor","coil","inductor","resistor","transistor","diode",
        "vacuum_tube","integrated_circuit","cpu","ram_chip","carbon_plate"};

    private static final String[] MACHINES = {"crusher","pulverizer","induction_smelter",
        "centrifuge","electrolyzer","chemical_reactor","distillation_tower",
        "arc_furnace","assembler","inscriber","enrichment_chamber","alloy_smelter",
        "compressor","precision_sawmill","metal_press","mixing_basin",
        "fermenter","squeezer","carpenter","thermal_centrifuge","ore_washer",
        "macerator","extractor","compactor","fluid_solidifier","pyrolyse_oven"};

    private static Namespace ns(String p) { return Namespace.of("test",p); }
    private static InputContent in(String id, long amt) {
        return InputContent.fromValue(new TestValueType(id), amt);
    }
    private static OutputContent out(String id, long amt) {
        return OutputContent.fromValue(new TestValueType(id), amt);
    }

    private record FakeRecipe(String id, Collection<InputContent> inputs,
        Collection<OutputContent> outputs) {}

    private static RoutingService service(FakeRecipe... rs) {
        return new RoutingService(graph(rs));
    }

    private static RecipeGraph graph(FakeRecipe... rs) {
        return RecipeGraph.buildGraph(Lists.immutable.ofAll(entries(rs)));
    }

    private static List<RecipeEntry<FakeRecipe>> entries(FakeRecipe... rs) {
        FakeRecipeSystem sys = new FakeRecipeSystem(Lists.immutable.of(rs));
        List<RecipeEntry<FakeRecipe>> entries = new ArrayList<>();
        for (FakeRecipe r : rs) entries.add(RecipeEntry.of(sys, r));
        return entries;
    }

    private static @Nullable RecipeEntry<?> findById(RecipeGraph g, String id) {
        for (RecipeGraph.Node n : g.nodes())
            if (n.recipe().id() != null && Objects.equals(n.recipe().id().getPath(), id))
                return n.recipe();
        return null;
    }

    static final class FakeRecipeSystem implements RecipeSystem<FakeRecipe> {
        private final RichIterable<FakeRecipe> recipes;
        private final Map<String,FakeRecipe> byId = new ConcurrentHashMap<>();
        FakeRecipeSystem(RichIterable<FakeRecipe> rs) { recipes=rs; rs.forEach(r->byId.put(r.id(),r)); }
        @Override public Namespace systemId() { return ns("routing"); }
        @Override public Class<?> basicType() { return FakeRecipe.class; }
        @Override public @Nullable ResourceLocation getId(FakeRecipe r) {
            return ResourceLocation.fromNamespaceAndPath("test",r.id());
        }
        @Override public @Nullable FakeRecipe findRecipe(String s) { return byId.get(s); }
        @Override public @Nullable FakeRecipe findRecipe(ResourceLocation rl) { return byId.get(rl.getPath()); }
        @Override public int hash(FakeRecipe r) { return r.id().hashCode(); }
        @Override public boolean sameRecipe(FakeRecipe a,FakeRecipe b) { return Objects.equals(a.id(),b.id()); }
        @Override public ResolveResult resolveFallback(ResolveContext ctx, FakeRecipe r) {
            return ResolveResult.resolved(RecipeProjection.create(RecipeEntry.of(this,r), r.inputs(), r.outputs()));
        }

        @Override
        public MachineEntry conventionMachine() {
            return new MachineEntry() {
                @Override public boolean isEmpty() { return false; }
            };
        }

        @Override public RichIterable<FakeRecipe> allRecipes() { return recipes; }
    }

    static final class TestValueType implements ValueType<Long> {
        private final Namespace id;
        TestValueType(String raw) {
            int c = raw.indexOf(':');
            if (c >= 0) {
                id = Namespace.of(raw.substring(0, c), raw.substring(c + 1));
            } else {
                id = Namespace.of("test", raw);
            }
        }
        @Override public Namespace typeId() { return id; }
        @Override public long getValue(Long v) { return v; }
        @Override public boolean matches(Long a,Long b) { return Objects.equals(a,b); }
        @Override public Long fromValue(long v) { return v; }
        @Override public Long copy(Long v) { return v; }
    }

    // -- Generators --

    private static FakeRecipe[] oreChains(int materialCount, long seed) {
        Random rng = new Random(seed);
        List<FakeRecipe> all = new ArrayList<>();
        for (int i = 0; i < materialCount; i++) {
            String mod = MODS[rng.nextInt(Math.min(MODS.length,8))];
            String mat = MATERIALS[i % MATERIALS.length];
            all.add(new FakeRecipe("c"+i+"_crush", List.of(in(mod+":ore_"+mat,1)),
                List.of(out(mod+":crushed_"+mat,2), out(mod+":dust_"+mat,1))));
            all.add(new FakeRecipe("c"+i+"_wash", List.of(in(mod+":crushed_"+mat,1)),
                List.of(out(mod+":purified_"+mat,1), out(mod+":tiny_dust_"+MATERIALS[rng.nextInt(MATERIALS.length)],1))));
            all.add(new FakeRecipe("c"+i+"_centrifuge", List.of(in(mod+":purified_"+mat,1)),
                List.of(out(mod+":dust_"+mat,1), out(mod+":dust_"+MATERIALS[rng.nextInt(MATERIALS.length)],1))));
            all.add(new FakeRecipe("c"+i+"_smelt", List.of(in(mod+":dust_"+mat,1)),
                List.of(out(mod+":ingot_"+mat,1))));
            all.add(new FakeRecipe("c"+i+"_plate", List.of(in(mod+":ingot_"+mat,1)),
                List.of(out(mod+":plate_"+mat,1))));
            all.add(new FakeRecipe("c"+i+"_block_ingot", List.of(in(mod+":block_"+mat,1)),
                List.of(out(mod+":ingot_"+mat,9))));
            all.add(new FakeRecipe("c"+i+"_ingot_block", List.of(in(mod+":ingot_"+mat,9)),
                List.of(out(mod+":block_"+mat,1))));
        }
        return all.toArray(new FakeRecipe[0]);
    }

    private static FakeRecipe[] alternateGraph(int productCount, int altPaths, long seed) {
        Random rng = new Random(seed);
        List<FakeRecipe> all = new ArrayList<>();
        for (int p = 0; p < productCount; p++) {
            String mod = MODS[rng.nextInt(MODS.length)];
            String product = mod+":ingot_"+MATERIALS[p % MATERIALS.length];
            all.add(new FakeRecipe("a"+p+"_from_ore",
                List.of(in(mod+":ore_"+MATERIALS[p%MATERIALS.length],1)), List.of(out(product,1))));
            all.add(new FakeRecipe("a"+p+"_from_dust",
                List.of(in(mod+":dust_"+MATERIALS[p%MATERIALS.length],1), in(MODS[rng.nextInt(MODS.length)]+":coal_dust",1)),
                List.of(out(product,1), out(MODS[rng.nextInt(MODS.length)]+":slag",1))));
            for (int a = 2; a < altPaths; a++) {
                String inType = FORMS[12+rng.nextInt(8)];
                String inMat = MATERIALS[rng.nextInt(MATERIALS.length)];
                List<InputContent> ins = new ArrayList<>();
                ins.add(in(mod+":"+inType+"_"+inMat, 1+rng.nextInt(4)));
                if (rng.nextDouble() < 0.4) ins.add(in(MODS[rng.nextInt(MODS.length)]+":catalyst_"+rng.nextInt(3), 1));
                List<OutputContent> outs = new ArrayList<>();
                outs.add(out(product, 1+rng.nextInt(3)));
                if (rng.nextDouble() < 0.2) outs.add(out(mod+":byproduct_"+rng.nextInt(3), 1));
                all.add(new FakeRecipe("a"+p+"_alt"+a, ins, outs));
            }
        }
        return all.toArray(new FakeRecipe[0]);
    }

    private static FakeRecipe[] diamondGraph(int intermediateCount, int consumerFactor, long seed) {
        Random rng = new Random(seed);
        List<FakeRecipe> all = new ArrayList<>();
        for (int i = 0; i < intermediateCount; i++) {
            String mod = MODS[rng.nextInt(MODS.length)];
            String comp = COMPONENTS[rng.nextInt(COMPONENTS.length)];
            int inC = 2 + rng.nextInt(3);
            List<InputContent> ins = new ArrayList<>();
            for (int j = 0; j < inC; j++)
                ins.add(in(mod+":"+FORMS[4+rng.nextInt(6)]+"_"+MATERIALS[rng.nextInt(MATERIALS.length)], 1+rng.nextInt(8)));
            all.add(new FakeRecipe("d"+i+"_intermediate", ins, List.of(out(mod+":"+comp, 1+rng.nextInt(4)))));
            for (int c = 0; c < consumerFactor; c++) {
                int inC2 = 2 + rng.nextInt(4);
                List<InputContent> ins2 = new ArrayList<>();
                ins2.add(in(mod+":"+comp, 1));
                for (int j = 1; j < inC2; j++)
                    ins2.add(in(mod+":plate_"+MATERIALS[rng.nextInt(MATERIALS.length)], 1+rng.nextInt(4)));
                all.add(new FakeRecipe("d"+i+"_consumer"+c, ins2, List.of(out(mod+":"+MACHINES[rng.nextInt(MACHINES.length)], 1))));
            }
        }
        return all.toArray(new FakeRecipe[0]);
    }

    private static FakeRecipe[] cyclicGraph(int cycleCount, int stepsPerCycle, long seed) {
        Random rng = new Random(seed);
        List<FakeRecipe> all = new ArrayList<>();
        for (int c = 0; c < cycleCount; c++) {
            all.add(new FakeRecipe("cy"+c+"_entry",
                List.of(in(MODS[rng.nextInt(MODS.length)]+":raw_cycle_"+c, 1)),
                List.of(out("test:cycle_"+c+"_0", 10))));
            for (int s = 0; s < stepsPerCycle - 1; s++) {
                List<InputContent> ins = new ArrayList<>();
                ins.add(in("test:cycle_"+c+"_"+s, 10));
                if (s >= 2 && rng.nextDouble() < 0.5) ins.add(in("test:cycle_"+c+"_"+(s-2), 2));
                if (c > 0 && rng.nextDouble() < 0.3) ins.add(in("test:cycle_"+(c-1)+"_"+rng.nextInt(stepsPerCycle-1), 1));
                all.add(new FakeRecipe("cy"+c+"_step"+s, ins,
                    List.of(out("test:cycle_"+c+"_"+(s+1), 8+rng.nextInt(5)))));
            }
            int last = stepsPerCycle - 1;
            all.add(new FakeRecipe("cy"+c+"_exit",
                List.of(in("test:cycle_"+c+"_"+last, 10)), List.of(out("test:product_cycle_"+c, 1))));
        }
        return all.toArray(new FakeRecipe[0]);
    }

    private static FakeRecipe[] hybridGraph(int total, long seed) {
        Random rng = new Random(seed);
        List<FakeRecipe> all = new ArrayList<>(total);
        int oreC = Math.max(1, total * 30 / 100 / 7);
        for (int i = 0; i < oreC; i++) {
            String mod = MODS[rng.nextInt(Math.min(MODS.length,10))];
            String mat = MATERIALS[rng.nextInt(MATERIALS.length)];
            all.add(new FakeRecipe("h_ore_crush_"+i, List.of(in(mod+":ore_"+mat,1)),
                List.of(out(mod+":crushed_"+mat,2),out(mod+":dust_"+mat,1))));
            all.add(new FakeRecipe("h_ore_wash_"+i, List.of(in(mod+":crushed_"+mat,1)),
                List.of(out(mod+":purified_"+mat,1))));
            all.add(new FakeRecipe("h_ore_cent_"+i, List.of(in(mod+":purified_"+mat,1)),
                List.of(out(mod+":dust_"+mat,2))));
            all.add(new FakeRecipe("h_ore_smelt_"+i, List.of(in(mod+":dust_"+mat,1)),
                List.of(out(mod+":ingot_"+mat,1))));
            all.add(new FakeRecipe("h_plate_"+i, List.of(in(mod+":ingot_"+mat,1)),
                List.of(out(mod+":plate_"+mat,1))));
            all.add(new FakeRecipe("h_block_ingot_"+i, List.of(in(mod+":block_"+mat,1)),
                List.of(out(mod+":ingot_"+mat,9))));
            all.add(new FakeRecipe("h_ingot_block_"+i, List.of(in(mod+":ingot_"+mat,9)),
                List.of(out(mod+":block_"+mat,1))));
        }

        int compC = total * 30 / 100;
        for (int i = 0; i < compC; i++) {
            String mod = MODS[rng.nextInt(MODS.length)];
            int inC = 3 + rng.nextInt(4);
            List<InputContent> ins = new ArrayList<>();
            for (int j = 0; j < inC; j++) {
                if (rng.nextDouble() < 0.35) ins.add(in(mod+":"+COMPONENTS[rng.nextInt(COMPONENTS.length)], 1+rng.nextInt(4)));
                else ins.add(in(mod+":"+FORMS[rng.nextInt(FORMS.length)]+"_"+MATERIALS[rng.nextInt(MATERIALS.length)], 1+rng.nextInt(8)));
            }
            all.add(new FakeRecipe("h_comp_"+i, ins, List.of(out(mod+":"+COMPONENTS[rng.nextInt(COMPONENTS.length)], 1+rng.nextInt(6)))));
        }

        int machC = total * 20 / 100;
        for (int i = 0; i < machC; i++) {
            String mod = MODS[rng.nextInt(MODS.length)];
            int inC = 4 + rng.nextInt(6);
            List<InputContent> ins = new ArrayList<>();
            for (int j = 0; j < inC; j++) {
                if (rng.nextDouble() < 0.3) ins.add(in(mod+":"+COMPONENTS[rng.nextInt(COMPONENTS.length)], 1));
                else ins.add(in(mod+":plate_"+MATERIALS[rng.nextInt(MATERIALS.length)], 1+rng.nextInt(8)));
            }
            all.add(new FakeRecipe("h_mach_"+i, ins, List.of(out(mod+":machine_"+MACHINES[rng.nextInt(MACHINES.length)], 1))));
        }

        int finalC = total * 15 / 100;
        for (int i = 0; i < finalC; i++) {
            String mod = MODS[rng.nextInt(MODS.length)];
            int inC = 5 + rng.nextInt(8);
            List<InputContent> ins = new ArrayList<>();
            for (int j = 0; j < inC; j++) {
                int kind = rng.nextInt(4);
                ins.add(switch (kind) {
                    case 0 -> in(mod+":"+COMPONENTS[rng.nextInt(COMPONENTS.length)], 1);
                    case 1 -> in(mod+":machine_"+MACHINES[rng.nextInt(MACHINES.length)], 1);
                    case 2 -> in(mod+":plate_"+MATERIALS[rng.nextInt(MATERIALS.length)], 1+rng.nextInt(4));
                    default -> in(mod+":ingot_"+MATERIALS[rng.nextInt(MATERIALS.length)], 1+rng.nextInt(16));
                });
            }
            all.add(new FakeRecipe("h_final_"+i, ins, List.of(out(mod+":ultimate_gadget_tier_"+(i%5), 1))));
        }

        int misc = total - all.size();
        for (int i = 0; i < misc; i++) {
            String mod = MODS[rng.nextInt(MODS.length)];
            all.add(new FakeRecipe("h_misc_"+i,
                List.of(in(mod+":"+COMPONENTS[rng.nextInt(COMPONENTS.length)], 1+rng.nextInt(4))),
                List.of(out(mod+":"+COMPONENTS[rng.nextInt(COMPONENTS.length)], 1+rng.nextInt(4)))));
        }

        java.util.Collections.shuffle(all, rng);
        return all.toArray(new FakeRecipe[0]);
    }

    // -- Build timing tests --

    @Test void buildOreChains_50() { measureBuild(oreChains(50,1001L)); }
    @Test void buildOreChains_200() { measureBuild(oreChains(200,1002L)); }
    @Test void buildAlternate_50_4paths() { measureBuild(alternateGraph(50,4,2001L)); }
    @Test void buildDiamond_40_6consumers() { measureBuild(diamondGraph(40,6,3001L)); }
    @Test void buildCyclic_30_8steps() { measureBuild(cyclicGraph(30,8,4001L)); }
    @Test void buildHybrid_1k() { measureBuild(hybridGraph(1_000,5001L)); }
    @Test void buildHybrid_5k() { measureBuild(hybridGraph(5_000,5002L)); }
    @Test void buildHybrid_15k() { measureBuild(hybridGraph(15_000,5003L)); }

    private void measureBuild(FakeRecipe[] recipes) {
        long t0 = System.nanoTime();
        RecipeGraph g = RecipeGraph.buildGraph(Lists.immutable.ofAll(entries(recipes)));
        long ms = (System.nanoTime() - t0) / 1_000_000L;
        System.out.printf("[buildGraph] %d ms :: %d recipes, %d nodes%n",
            ms, recipes.length, g.nodes().size());
    }

    // -- Routing timing tests --

    @Test void route_oreChain_50() {
        FakeRecipe[] rs = oreChains(50, 1001L);
        RoutingService svc = service(rs);
        RecipeEntry<?> target = findById(svc.graph(), "c49_plate");
        if (target == null) target = svc.graph().stableNodes().getLast().recipe();
        assertNotNull(target);
        for (int w = 0; w < 8; w++) svc.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.standard).targetRecipe(target,1)
            .budget(RoutingBudget.standardDefault()).build());
        long totalNs = 0;
        int iters = 20;
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            var o = svc.route(RoutingRequest.builder()
                .plannerMode(PlannerMode.standard).targetRecipe(target,1)
                .budget(RoutingBudget.standardDefault()).build());
            totalNs += System.nanoTime() - t0;
            assertNotNull(o); // routing may fail without full mod init
        }
        double avgUs = totalNs / 1_000.0 / iters;
        System.out.printf("[route_oreChain_50] %.1f us :: %d nodes%n",
            avgUs, svc.graph().nodes().size());
    }

    @Test void route_alternate_50() {
        FakeRecipe[] rs = alternateGraph(50, 4, 2001L);
        RoutingService svc = service(rs);
        RecipeEntry<?> target = findById(svc.graph(), "a0_from_ore");
        assertNotNull(target);
        for (int w = 0; w < 5; w++) svc.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.standard).targetRecipe(target,1)
            .budget(RoutingBudget.standardDefault()).build());
        long totalNs = 0;
        int iters = 15;
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            var o = svc.route(RoutingRequest.builder()
                .plannerMode(PlannerMode.standard).targetRecipe(target,1)
                .budget(RoutingBudget.standardDefault()).build());
            totalNs += System.nanoTime() - t0;
            assertNotNull(o); // routing may fail without full mod init
        }
        double avgUs = totalNs / 1_000.0 / iters;
        System.out.printf("[route_alternate_50] %.1f us :: %d nodes%n",
            avgUs, svc.graph().nodes().size());
    }

    @Test void route_diamond_40_assured() {
        FakeRecipe[] rs = diamondGraph(40, 6, 3001L);
        RoutingService svc = service(rs);
        RecipeEntry<?> target = svc.graph().stableNodes().getFirst().recipe();
        for (int w = 0; w < 5; w++) svc.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.assuredBounded).targetRecipe(target,1)
            .budget(new RoutingBudget(5_000,60_000,120,24_000,0.08,LoopBudget.assuredDefault())).build());
        long totalNs = 0;
        int iters = 10;
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            var o = svc.route(RoutingRequest.builder()
                .plannerMode(PlannerMode.assuredBounded).targetRecipe(target,1)
                .budget(new RoutingBudget(5_000,60_000,120,24_000,0.08,LoopBudget.assuredDefault())).build());
            totalNs += System.nanoTime() - t0;
            assertNotNull(o); // routing may fail without full mod init
        }
        double avgUs = totalNs / 1_000.0 / iters;
        System.out.printf("[route_diamond_40_assured] %.1f us :: %d nodes%n",
            avgUs, svc.graph().nodes().size());
    }

    @Test void route_cyclic_30() {
        FakeRecipe[] rs = cyclicGraph(30, 8, 4001L);
        RoutingService svc = service(rs);
        RecipeEntry<?> target = findById(svc.graph(), "cy0_exit");
        if (target == null) for (var n : svc.graph().nodes())
            if (n.recipe().id() != null && n.recipe().id().getPath().contains("exit")) { target=n.recipe(); break; }
        assertNotNull(target);
        for (int w = 0; w < 5; w++) svc.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.assuredBounded).targetRecipe(target,1)
            .budget(new RoutingBudget(3_000,40_000,80,16_000,0.08,LoopBudget.assuredDefault())).build());
        long totalNs = 0;
        int iters = 12;
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            var o = svc.route(RoutingRequest.builder()
                .plannerMode(PlannerMode.assuredBounded).targetRecipe(target,1)
                .budget(new RoutingBudget(3_000,40_000,80,16_000,0.08,LoopBudget.assuredDefault())).build());
            totalNs += System.nanoTime() - t0;
            assertNotNull(o); // routing may fail without full mod init
        }
        double avgUs = totalNs / 1_000.0 / iters;
        System.out.printf("[route_cyclic_30] %.1f us :: %d nodes%n",
            avgUs, svc.graph().nodes().size());
    }

    @Test void route_hybrid_1k() {
        FakeRecipe[] rs = hybridGraph(1_000, 5001L);
        RoutingService svc = service(rs);
        RecipeEntry<?> target = null;
        for (var n : svc.graph().stableNodes())
            if (n.recipe().id() != null && n.recipe().id().getPath().startsWith("h_final_")) { target=n.recipe(); break; }
        if (target == null) target = svc.graph().stableNodes().getLast().recipe();
        assertNotNull(target);
        for (int w = 0; w < 5; w++) svc.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.standard).targetRecipe(target,1)
            .budget(RoutingBudget.standardDefault()).build());
        long totalNs = 0;
        int iters = 10;
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            var o = svc.route(RoutingRequest.builder()
                .plannerMode(PlannerMode.standard).targetRecipe(target,1)
                .budget(RoutingBudget.standardDefault()).build());
            totalNs += System.nanoTime() - t0;
            assertNotNull(o); // routing may fail without full mod init
        }
        double avgUs = totalNs / 1_000.0 / iters;
        System.out.printf("[route_hybrid_1k] %.1f us :: %d nodes%n",
            avgUs, svc.graph().nodes().size());
    }

    @Test void route_hybrid_5k() {
        FakeRecipe[] rs = hybridGraph(5_000, 5002L);
        RoutingService svc = service(rs);
        RecipeEntry<?> target = null;
        for (var n : svc.graph().stableNodes())
            if (n.recipe().id() != null && n.recipe().id().getPath().startsWith("h_final_")) { target=n.recipe(); break; }
        if (target == null) target = svc.graph().stableNodes().getLast().recipe();
        assertNotNull(target);
        for (int w = 0; w < 3; w++) svc.route(RoutingRequest.builder()
            .plannerMode(PlannerMode.standard).targetRecipe(target,1)
            .budget(RoutingBudget.standardDefault()).build());
        long totalNs = 0;
        int iters = 5;
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            var o = svc.route(RoutingRequest.builder()
                .plannerMode(PlannerMode.standard).targetRecipe(target,1)
                .budget(RoutingBudget.standardDefault()).build());
            totalNs += System.nanoTime() - t0;
            assertNotNull(o); // routing may fail without full mod init
        }
        double avgUs = totalNs / 1_000.0 / iters;
        System.out.printf("[route_hybrid_5k] %.1f us :: %d nodes%n",
            avgUs, svc.graph().nodes().size());
    }

    @Test void serviceCreation_hybrid_5k() {
        FakeRecipe[] rs = hybridGraph(5_000, 5002L);
        RecipeGraph g = RecipeGraph.buildGraph(Lists.immutable.ofAll(entries(rs)));
        for (int w = 0; w < 5; w++) new RoutingService(g);
        long totalNs = 0;
        int iters = 10;
        for (int i = 0; i < iters; i++) {
            long t0 = System.nanoTime();
            new RoutingService(g);
            totalNs += System.nanoTime() - t0;
        }
        double avgMs = totalNs / 1_000_000.0 / iters;
        System.out.printf("[serviceCreate_5k] %.3f ms :: %d nodes%n",
            avgMs, g.nodes().size());
    }
}
