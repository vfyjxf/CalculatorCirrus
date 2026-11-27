package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RawIngredientSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawOutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultRecipeGraphCompiler;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;
import dev.vfyjxf.cirrus.planning.core.verify.DefaultPlanVerifier;
import dev.vfyjxf.cirrus.planning.offline.ExportJsonCatalogLoader;
import dev.vfyjxf.cirrus.planning.offline.LoadedCatalog;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.compile;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.fac;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.generousBudget;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedCraftingTable;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedFurnace;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedStonecutter;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.rec;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.request;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SystemInvariantsAndPropertyTest {

    private static final Pattern forbiddenImports = Pattern.compile(
        "import\\s+(net\\.minecraft|net\\.neoforged|com\\.google\\.gson|com\\.mojang|dev\\.vfyjxf\\.cloudlib|org\\.eclipse\\.collections|mezz\\.jei)\\."
    );
    private static final Pattern forbiddenProbability = Pattern
            .compile("chance|probability|probabilistic", Pattern.CASE_INSENSITIVE);

    /** Entry point for INV-02 cross-JVM determinism child process. */
    public static void main(String[] args) throws Exception {
        Path fixtureDir = Path.of(args[0]);
        LoadedCatalog loaded = new ExportJsonCatalogLoader().loadPackDirectory(fixtureDir, true);
        CompiledRecipeGraph graph = new DefaultRecipeGraphCompiler().compile(loaded.snapshot(), loaded.policy());
        StringBuilder sb = new StringBuilder();
        sb.append(graph.stats()).append('\n');
        for (int cid = 0; cid < graph.stats().compiledRecipes(); cid++) {
            CompiledRecipe r = graph.recipeByCompactId(cid).orElseThrow();
            sb.append(cid).append('|').append(r.key()).append('|').append(r.role()).append('|').append(r.advisory())
                    .append('\n');
        }
        System.out.print(sb);
    }

    @Test
    void inv01_pureCoreSourceScanHasZeroExternalImportsAndZeroProbability() throws IOException {
        Path root = locateModRoot();
        List<Path> dirs = List.of(
            root.resolve("src/main/java/dev/vfyjxf/cirrus/api/planning"),
            root.resolve("src/main/java/dev/vfyjxf/cirrus/planning/core")
        );
        int filesScanned = 0;
        for (Path dir : dirs) {
            assertTrue(Files.isDirectory(dir), "Missing core directory: " + dir);
            try (Stream<Path> walk = Files.walk(dir)) {
                for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                    filesScanned++;
                    String text = Files.readString(file, StandardCharsets.UTF_8);
                    assertFalse(forbiddenImports.matcher(text).find(), "Forbidden import in " + file);
                    assertFalse(forbiddenProbability.matcher(text).find(), "Probability leak in " + file);
                }
            }
        }
    }

    @Test
    @Timeout(45)
    void inv02_crossIndependentJvmCompileDeterminism() throws Exception {
        Path modRoot = locateModRoot();
        Path fixtureDir = modRoot.resolve("src/test/resources/planning-exports/fixture-pack");
        assertTrue(Files.isDirectory(fixtureDir));

        String javaBin = ProcessHandle.current().info().command()
                .orElse(Path.of(System.getProperty("java.home"), "bin", "java").toString());
        String cp = resolveChildClasspath(modRoot);

        String out1 = runChildDigest(javaBin, cp, fixtureDir);
        String out2 = runChildDigest(javaBin, cp, fixtureDir);
        assertFalse(out1.isBlank());
        assertEquals(out1, out2);
    }

    @Test
    @Timeout(30)
    void inv03_inv04_inv05_hundredRunDeterminismCertifiedAndParetoNonDominated() {
        ResourceKey stone = item("minecraft:stone");
        ResourceKey bricks = item("minecraft:stone_bricks");
        List<RawRecipeRecord> recs = List.of(
            rec(
                "minecraft:crafting",
                "craft_bricks",
                List.of("minecraft:crafting_table"),
                List.of(slot(stone, 4)),
                List.of(out(bricks, 4))
            ),
            rec(
                "minecraft:stonecutting",
                "cut_bricks",
                List.of("minecraft:stonecutter"),
                List.of(slot(stone, 1)),
                List.of(out(bricks, 1))
            ),
            rec(
                "minecraft:smelting",
                "smelt_stone",
                List.of("minecraft:furnace"),
                List.of(slot(item("minecraft:cobblestone"), 1)),
                List.of(out(stone, 1))
            )
        );

        CompiledRecipeGraph baseGraph = compile(recs);
        List<String> baseKeys = graphDigest(baseGraph);
        for (int i = 0; i < 100; i++) {
            assertEquals(baseKeys, graphDigest(compile(recs)));
        }

        DefaultPlanningService svc = new DefaultPlanningService(baseGraph);
        DefaultPlanVerifier verifier = new DefaultPlanVerifier(baseGraph);
        PlanningRequest req = request(
            baseGraph,
            List.of(new TargetDemand(bricks, 5)),
            InventorySnapshot.empty,
            fac(ownedCraftingTable(), ownedFurnace()),
            DefaultHumanGuidanceView.empty(),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );

        PlanningResult first = svc.planOnce(req);
        assertEquals(PlanningStatus.feasible, first.status());
        assertCertifiedAndNonDominated(first, req, verifier);
        List<ProductionPlan> expected = first.paretoPlans();

        for (int i = 0; i < 100; i++) {
            PlanningResult again = svc.planOnce(req);
            assertEquals(expected, again.paretoPlans());
        }
    }

    @Test
    @Timeout(30)
    void inv06_tightTimeAndNodeBudgetsTerminateCleanly() {
        List<RawRecipeRecord> recs = new ArrayList<>();
        for (int i = 0; i < 80; i++) {
            ResourceKey in = item("mod:node_" + i);
            ResourceKey out = item("mod:node_" + (i + 1));
            recs.add(
                rec(
                    "minecraft:crafting",
                    "step_" + i,
                    List.of("minecraft:crafting_table"),
                    List.of(slot(in, 1)),
                    List.of(out(out, 1))
                )
            );
            recs.add(
                rec(
                    "minecraft:crafting",
                    "alt_" + i,
                    List.of("minecraft:crafting_table"),
                    List.of(slot(item("mod:branch_" + i), 1)),
                    List.of(out(out, 1))
                )
            );
        }
        CompiledRecipeGraph g = compile(recs);

        PlanningBudget tightNodes = new PlanningBudget(
            Duration.ofMillis(100),
            Duration.ofMillis(200),
            10,
            10,
            3,
            3,
            true,
            true
        );
        PlanningRequest nodeReq = request(
            g,
            List.of(new TargetDemand(item("mod:node_80"), 1)),
            InventorySnapshot.empty,
            fac(ownedCraftingTable()),
            DefaultHumanGuidanceView.empty(),
            PlanningProfile.defaultBalanced(),
            tightNodes
        );
        PlanningResult nodeRes = new DefaultPlanningService(g).planOnce(nodeReq);
        assertTrue(nodeRes.diagnostics().truncatedByNodeBudget());

        PlanningBudget zeroSoft = new PlanningBudget(
            Duration.ZERO,
            Duration.ofMillis(5),
            2500,
            6000,
            3,
            3,
            true,
            true,
            16,
            1L
        );
        PlanningRequest timeReq = request(
            g,
            List.of(new TargetDemand(item("mod:node_80"), 1)),
            InventorySnapshot.empty,
            fac(ownedCraftingTable()),
            DefaultHumanGuidanceView.empty(),
            PlanningProfile.defaultBalanced(),
            zeroSoft
        );
        PlanningResult timeRes = new DefaultPlanningService(g).planOnce(timeReq);
        assertTrue(
            timeRes.status() == PlanningStatus.noPlanWithinBudget
                    || timeRes.status() == PlanningStatus.feasible
                    || timeRes.status() == PlanningStatus.provenInfeasible
        );
    }

    @Test
    @Timeout(60)
    void propertyBased_220RandomGraphsAlwaysCertifiedAndNonDominated() {
        SplittableRandom rng = new SplittableRandom(0xC1AA55_2026L);
        ResourceKey[] bases = {item("minecraft:cobblestone"), item("minecraft:oak_log"), item("minecraft:raw_iron"),
                item("minecraft:sand"), item("minecraft:coal")};
        String[] systems = {"minecraft:crafting", "minecraft:smelting", "mod:press", "mod:mixer"};
        String[] machines = {"minecraft:crafting_table", "minecraft:furnace", "mod:presser", "mod:mixer_m"};

        int plansVerified = 0;
        for (int trial = 0; trial < 220; trial++) {
            int intermediates = 4 + rng.nextInt(7);
            ResourceKey[] nodes = new ResourceKey[bases.length + intermediates];
            System.arraycopy(bases, 0, nodes, 0, bases.length);
            for (int i = 0; i < intermediates; i++) {
                nodes[bases.length + i] = item("pbt:item_" + i);
            }
            List<RawRecipeRecord> recs = new ArrayList<>();
            int recCount = 8 + rng.nextInt(14);
            for (int r = 0; r < recCount; r++) {
                int outIdx = bases.length + rng.nextInt(intermediates);
                int sysIdx = rng.nextInt(systems.length);
                int inSlots = 1 + rng.nextInt(2);
                List<RawIngredientSlot> ins = new ArrayList<>();
                for (int s = 0; s < inSlots; s++) {
                    // Bias mostly DAG forward with occasional back-edges to stress SCC/cycle handling.
                    int maxIn = rng.nextInt(5) == 0 ? nodes.length : outIdx;
                    int inIdx = rng.nextInt(Math.max(1, maxIn));
                    long amt = 1L + rng.nextInt(4);
                    ins.add(
                        new RawIngredientSlot(Optional.empty(), List.of(nodes[inIdx]), amt, SlotMatchMode.identity)
                    );
                }
                List<RawOutputSlot> outs = new ArrayList<>();
                outs.add(new RawOutputSlot(nodes[outIdx], 1L + rng.nextInt(4)));
                if (rng.nextInt(4) == 0) {
                    int sideIdx = bases.length + rng.nextInt(intermediates);
                    if (sideIdx != outIdx) {
                        outs.add(new RawOutputSlot(nodes[sideIdx], 1L));
                    }
                }
                recs.add(
                    new RawRecipeRecord(
                        systems[sysIdx],
                        "custom",
                        Optional.of("pbt:r_" + trial + "_" + r),
                        List.of(machines[sysIdx]),
                        ins,
                        outs,
                        Map.of()
                    )
                );
            }
            CompiledRecipeGraph g = compile(recs);
            Map<ResourceKey, Long> stockMap = new HashMap<>();
            if (rng.nextBoolean()) {
                stockMap.put(nodes[rng.nextInt(nodes.length)], 1L + rng.nextInt(10));
            }
            InventorySnapshot inv = stockMap.isEmpty()
                    ? InventorySnapshot.empty
                    : new InventorySnapshot(new StockLedger(stockMap), StockLedger.empty);
            FacilitySnapshot f = fac(ownedCraftingTable(), ownedFurnace(), ownedStonecutter());
            ResourceKey target = nodes[bases.length + rng.nextInt(intermediates)];
            long qty = 1L + rng.nextInt(6);

            PlanningRequest req = request(
                g,
                List.of(new TargetDemand(target, qty)),
                inv,
                f,
                DefaultHumanGuidanceView.empty(),
                PlanningProfile.defaultBalanced(),
                generousBudget()
            );
            PlanningResult res = new DefaultPlanningService(g).planOnce(req);
            DefaultPlanVerifier verifier = new DefaultPlanVerifier(g);
            assertCertifiedAndNonDominated(res, req, verifier);
            plansVerified += res.paretoPlans().size();
        }
    }

    private static void assertCertifiedAndNonDominated(
        PlanningResult res,
        PlanningRequest req,
        DefaultPlanVerifier verifier
    ) {
        List<ProductionPlan> plans = res.paretoPlans();
        for (ProductionPlan p : plans) {
            assertTrue(p.certificate().valid(), () -> "Invalid embedded cert: " + p.certificate().violations());
            VerificationCertificate recheck = verifier.verify(p, req);
            assertTrue(recheck.valid(), () -> "Independent verifier rejected: " + recheck.violations());
            assertTrue(recheck.dagAcyclic());
            assertTrue(recheck.massBalanced());
            assertTrue(recheck.machinesAndCatalystsSatisfied());
            assertTrue(recheck.integerBatchesValid());
            assertTrue(recheck.surplusConsistent());
        }
        for (int i = 0; i < plans.size(); i++) {
            for (int j = 0; j < plans.size(); j++) {
                if (i == j) continue;
                // M6 Conditioned Frontier: strict 6D Pareto holds within the same RegimeSignature and soft-risk tier;
                // incompatible tech-tree regimes intentionally survive side-by-side even across raw cost dominance.
                if (plans.get(i).regimeSignature().equals(plans.get(j).regimeSignature())
                        && Math.abs(
                            plans.get(i).scoreBreakdown().totalCappedSoftSeconds()
                                    - plans.get(j).scoreBreakdown().totalCappedSoftSeconds()
                        ) <= 1e-9) {
                    assertFalse(
                        plans.get(i).totalCost().dominates(plans.get(j).totalCost()),
                        "INV-04 violation within same regime: plan " + i + " dominates plan " + j
                    );
                }
            }
        }
    }

    private static List<String> graphDigest(CompiledRecipeGraph g) {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < g.stats().compiledRecipes(); i++) {
            out.add(i + ":" + g.recipeByCompactId(i).orElseThrow().key());
        }
        return out;
    }

    private static String resolveChildClasspath(Path modRoot) throws IOException {
        Path testClasses = modRoot.resolve("build/classes/java/test").toAbsolutePath().normalize();
        Path cpFile = modRoot.resolve("build/chainexport/cp.txt").toAbsolutePath().normalize();
        if (Files.isRegularFile(cpFile)) {
            String runtimeCp = Files.readString(cpFile, StandardCharsets.UTF_8).trim();
            if (!runtimeCp.isEmpty()) {
                return testClasses + File.pathSeparator + runtimeCp;
            }
        }
        return testClasses + File.pathSeparator + System.getProperty("java.class.path");
    }

    private static String runChildDigest(String javaBin, String cp, Path fixtureDir) throws Exception {
        ProcessBuilder pb = new ProcessBuilder(
            javaBin,
            "-Xmx512m",
            "-cp",
            cp,
            SystemInvariantsAndPropertyTest.class.getName(),
            fixtureDir.toAbsolutePath().toString()
        );
        pb.redirectErrorStream(false);
        Process p = pb.start();
        StringBuilder errBuf = new StringBuilder();
        Thread errDrain = new Thread(() -> {
            try {
                errBuf.append(new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8));
            } catch (IOException ignored) {}
        }, "inv02-stderr");
        errDrain.start();
        String stdout = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = p.waitFor(30, TimeUnit.SECONDS);
        errDrain.join(2000);
        if (!finished) {
            p.destroyForcibly();
            throw new AssertionError("Child JVM timed out; stderr=" + errBuf);
        }
        assertEquals(0, p.exitValue(), "Child JVM failed: " + errBuf);
        return stdout;
    }

    static Path locateModRoot() {
        String prop = System.getProperty("moddevmcp.project.root");
        if (prop != null && !prop.isBlank()) {
            Path root = Path.of(prop).toAbsolutePath().normalize();
            if (Files.isDirectory(root.resolve("Mod/src/main/java/dev/vfyjxf/cirrus"))) {
                return root.resolve("Mod");
            }
            if (Files.isDirectory(root.resolve("src/main/java/dev/vfyjxf/cirrus"))) {
                return root;
            }
        }
        Path cur = Path.of("").toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("src/main/java/dev/vfyjxf/cirrus"))) {
                return p;
            }
            if (Files.isDirectory(p.resolve("Mod/src/main/java/dev/vfyjxf/cirrus"))) {
                return p.resolve("Mod");
            }
        }
        throw new IllegalStateException("Cannot locate Mod root from " + cur);
    }
}
