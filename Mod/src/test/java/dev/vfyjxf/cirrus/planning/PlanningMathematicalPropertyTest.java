package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionEstimate;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.BaseBoundarySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.BaseContract;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventoryTier;
import dev.vfyjxf.cirrus.api.planning.context.PlanningBudget;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.context.ProgressionState;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.CostWeights;
import dev.vfyjxf.cirrus.api.planning.cost.ObjectivePreset;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.cost.PreferenceModifiers;
import dev.vfyjxf.cirrus.api.planning.cost.ScoreBreakdown;
import dev.vfyjxf.cirrus.api.planning.cost.StageDistribution;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
import dev.vfyjxf.cirrus.api.planning.human.PromptSuppressionLedger;
import dev.vfyjxf.cirrus.api.planning.human.QuestionKey;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.VoIEstimate;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.RegimeSignature;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.api.planning.tag.CanonicalRecipeTag;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeEvidenceTier;
import dev.vfyjxf.cirrus.api.planning.tag.RecipeSemanticKey;
import dev.vfyjxf.cirrus.api.planning.telemetry.ExecutionFailureCategory;
import dev.vfyjxf.cirrus.api.recipe.system.RecipeSystemTags;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.cost.PassBInventoryModel;
import dev.vfyjxf.cirrus.planning.core.cost.StageAndCapitalModel;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.human.VoICounterfactualEngine;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;
import dev.vfyjxf.cirrus.planning.core.learn.ExecutionFailureTracker;
import dev.vfyjxf.cirrus.planning.core.learn.OnlinePreferenceLearner;
import dev.vfyjxf.cirrus.planning.core.select.ConditionedFrontierSelector;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;
import dev.vfyjxf.cirrus.planning.core.trust.FidelityScorer;
import dev.vfyjxf.cirrus.planning.core.trust.RecipeSemanticKeyBuilder;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.compile;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.fac;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.generousBudget;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.machine;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ns;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedCraftingTable;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.rec;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Mathematical invariant and counterexample checks for the surviving planning core models:
 * Pass B inventory economics, stage/capital amortization, conditioned frontier selection,
 * end-to-end planning service, trust/fidelity scoring, VoI, bounded online learning,
 * semantic keys, and saturating arithmetic.
 */
class PlanningMathematicalPropertyTest {

    private static InventorySnapshot invTiers(Map<InventoryTier, StockLedger> avail, Map<InventoryTier, StockLedger> res, Set<ComponentStackKey> prot) {
        return InventorySnapshot.fromTiers(avail, res, Set.of(), prot);
    }

    private static BaseContract contract(ComponentStackKey rk, double unitSec) {
        return new BaseContract(rk, unitSec, 0.1, 0.0, 0.0, 1_000_000L, false, "test");
    }

    @Test
    void sharedMachineBuiltOnce() {
        ComponentStackKey ore = item("test:ore");
        ComponentStackKey plate = item("test:plate");
        ComponentStackKey rod = item("test:rod");
        ComponentStackKey frame = item("test:frame");
        CompiledRecipeGraph g = compile(
            List.of(
                rec("mod:press", "r_plate", List.of("mod:press_machine"), List.of(slot(ore, 1)), List.of(out(plate, 1))),
                rec("mod:press", "r_rod", List.of("mod:press_machine"), List.of(slot(ore, 1)), List.of(out(rod, 1))),
                rec(
                    "minecraft:crafting",
                    "r_frame",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(plate, 1), slot(rod, 1)),
                    List.of(out(frame, 1))
                )
            )
        );
        PlanningRequest req = new PlanningRequest(
            List.of(new TargetDemand(frame, 1L)),
            new PlanningContext(
                InventorySnapshot.empty,
                fac(ownedCraftingTable()),
                new DefaultAcquisitionOracle(g),
                DefaultHumanGuidanceView.empty(),
                ProgressionState.overworldDefault
            ),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );
        PlanningResult res = new DefaultPlanningService(g).planOnce(req);
        assertEquals(PlanningStatus.feasible, res.status());
        ProductionPlan best = res.paretoPlans().get(0);
        long pressBuilds = best.steps().stream()
                .filter(s -> s instanceof ProvisionMachineStep pm && pm.machine().matches(machine("mod:press_machine")))
                .count();
        assertEquals(1L, pressBuilds, "Shared machine across two sub-branches must be provisioned exactly once");
    }

    @Test
    void layersAndReservesAreFinite() {
        ComponentStackKey iron = item("test:iron");
        InventorySnapshot inv = invTiers(
            Map.of(InventoryTier.hand, new StockLedger(Map.of(iron, 10L))),
            Map.of(InventoryTier.hand, new StockLedger(Map.of(iron, 7L))),
            Set.of()
        );
        assertEquals(3L, inv.usableAmount(iron));
        assertEquals(3L, inv.usableAmountInTier(iron, InventoryTier.hand));
        assertEquals(0L, inv.usableAmountInTier(iron, InventoryTier.remote));

        InventorySnapshot overridden = inv.withOverriddenStock(iron, 2L);
        assertEquals(2L, overridden.usableAmount(iron));
        assertEquals(0L, overridden.withoutResources(Set.of(iron)).usableAmount(iron));
    }

    @Test
    void noSourceFromRawCount() {
        ComponentStackKey uncertified = item("mod:mysterious_crystal");
        ComponentStackKey bannedNat = item("mod:banned_herb");
        CompiledRecipeGraph g = compile(List.of());
        BaseBoundarySnapshot boundary = new BaseBoundarySnapshot(
            Map.of(),
            Set.of(),
            Set.of(),
            Set.of(bannedNat),
            List.of()
        );
        HumanGuidanceView guidance = DefaultHumanGuidanceView.of(
            List.of(new ResourceRule("ban_herb", GuidanceScope.worldSave, RuleStrength.hardBan, bannedNat, false))
        );
        DefaultAcquisitionOracle oracle = new DefaultAcquisitionOracle(g, boundary, guidance, false);
        assertEquals("no_base_contract_or_natural_source", oracle.diagnose(uncertified, 1L, ProgressionState.overworldDefault));
        assertTrue(oracle.assess(uncertified, 1L, ProgressionState.overworldDefault).isEmpty());
        assertEquals("banned_by_guidance", oracle.diagnose(bannedNat, 1L, ProgressionState.overworldDefault));
        assertTrue(oracle.assess(bannedNat, 1L, ProgressionState.overworldDefault).isEmpty());
    }

    @Test
    void baseOrderFromEvidence() {
        ComponentStackKey cheap = item("opaque:alpha_001");
        ComponentStackKey expensive = item("opaque:beta_002");
        BaseBoundarySnapshot boundary = new BaseBoundarySnapshot(
            Map.of(cheap, contract(cheap, 10.0), expensive, contract(expensive, 3.0)),
            Set.of(),
            Set.of(),
            Set.of(),
            List.of(new BaseBoundarySnapshot.PartialOrderConstraint(cheap, expensive, 5.0))
        );
        Map<ComponentStackKey, Double> enforced = boundary.enforcePartialOrder(Map.of(cheap, 10.0, expensive, 3.0));
        double pExp = Objects.requireNonNull(enforced.get(expensive));
        double pCheap = Objects.requireNonNull(enforced.get(cheap));
        assertTrue(pExp >= pCheap + 5.0 - 1e-9);

        ComponentStackKey cheap2 = item("opaque:zeta_999");
        ComponentStackKey expensive2 = item("opaque:aaa_000");
        BaseBoundarySnapshot swapped = new BaseBoundarySnapshot(
            Map.of(cheap2, contract(cheap2, 10.0), expensive2, contract(expensive2, 3.0)),
            Set.of(),
            Set.of(),
            Set.of(),
            List.of(new BaseBoundarySnapshot.PartialOrderConstraint(cheap2, expensive2, 5.0))
        );
        Map<ComponentStackKey, Double> enforced2 = swapped.enforcePartialOrder(Map.of(cheap2, 10.0, expensive2, 3.0));
        assertEquals(pExp, Objects.requireNonNull(enforced2.get(expensive2)), 1e-9);
    }

    @Test
    void inventoryAnalyticMatchesEnumeration() {
        Random rng = new Random(20260401L);
        for (int trial = 0; trial < 40; trial++) {
            long d = 1 + rng.nextInt(40);
            long i = rng.nextInt(50);
            double p = 0.5 + rng.nextDouble() * 8.0;
            double a = rng.nextDouble() * 4.0;
            double beta = rng.nextDouble() * 6.0;
            double cl = rng.nextDouble() * 1.5;
            double gamma = rng.nextDouble() * 4.0;
            double h = 1.0 + rng.nextDouble() * 6.0;
            double s0 = rng.nextDouble() * 3.0;
            double capB = 5.0 + rng.nextDouble() * 15.0;

            var analytic = PassBInventoryModel.solveSingleChannelLinear(d, i, p, a, beta, cl, gamma, h, s0, capB);
            long m = Math.min(d, i);
            double bestBrute = p * d;
            for (long q = 1; q <= m; q++) {
                double t = a * q + beta + gamma * PassBInventoryModel.smoothSplitRamp(q, d, h);
                double g = PassBInventoryModel.marginalCappedSoft(t, s0, capB);
                double f = p * (d - q) + cl * q + g;
                if (f < bestBrute) {
                    bestBrute = f;
                }
            }
            assertEquals(bestBrute, analytic.totalComparableCost(), 1e-9, "Analytic endpoint must match enumeration on trial " + trial);
        }
    }

    @Test
    void stockCoverageHasNoFakeCliff() {
        long d = 32L;
        double h = 6.0;
        double atD = PassBInventoryModel.smoothSplitRamp(d, d, h);
        double atDMinus1 = PassBInventoryModel.smoothSplitRamp(d - 1, d, h);
        assertEquals(0.0, atD, 1e-12);
        assertTrue(atDMinus1 < 0.20, "Smooth ramp near D must be small, got " + atDMinus1);

        double prev = PassBInventoryModel.solveSingleChannelLinear(d, 0, 2.0, 0.5, 2.0, 0.2, 3.0, h, 0.0, 20.0).totalComparableCost();
        for (long i = 1; i <= d + 4; i++) {
            double cur = PassBInventoryModel.solveSingleChannelLinear(d, i, 2.0, 0.5, 2.0, 0.2, 3.0, h, 0.0, 20.0).totalComparableCost();
            double diff = Math.abs(cur - prev);
            assertTrue(diff <= 2.5 + 3.0 / h + 1e-6, "Adjacent integer step exceeded Lipschitz bound at I=" + i + ": diff=" + diff);
            prev = cur;
        }
    }

    @Test
    void ignoreScrapsAndBlendWhenWorthwhile() {
        long d = 32L;
        double p = 1.0;
        double a = 0.25;
        double beta = 3.0;
        double cl = 0.05;
        double gamma = 3.5;
        double h = 4.0;
        double capB = 100.0;

        var dScrap = PassBInventoryModel.solveSingleChannelLinear(d, 2L, p, a, beta, cl, gamma, h, 0.0, capB);
        var dBlend = PassBInventoryModel.solveSingleChannelLinear(d, 20L, p, a, beta, cl, gamma, h, 0.0, capB);
        var dFull = PassBInventoryModel.solveSingleChannelLinear(d, 32L, p, a, beta, cl, gamma, h, 0.0, capB);
        assertEquals(0L, dScrap.chosenQuantity(), "I=2 scrap must be ignored");
        assertEquals(20L, dBlend.chosenQuantity(), "I=20 worthwhile stock must blend");
        assertEquals(32L, dFull.chosenQuantity(), "I=32 full stock must cover demand");

        var closedContainer = PassBInventoryModel.solveFourTierExact(
            d, p, List.of(new PassBInventoryModel.ChannelSpec(InventoryTier.nearby, 2L, a, beta, cl, false)),
            gamma, h, 0.0, capB
        );
        var openContainer = PassBInventoryModel.solveFourTierExact(
            d, p, List.of(new PassBInventoryModel.ChannelSpec(InventoryTier.nearby, 2L, a, beta, cl, true)),
            0.0, h, 0.0, capB
        );
        assertEquals(0L, closedContainer.totalQuantity());
        assertEquals(2L, openContainer.totalQuantity());
    }

    @Test
    void batchBreakpointsBeatLinearEndpoint() {
        var dec = PassBInventoryModel.solveSingleChannelBatch(10L, 5L, 8L, 10.0, 2.0, 0.5, 0.1, 0.2, 4.0, 0.0, 100.0);
        assertEquals(2L, dec.chosenQuantity(), "Batch breakpoint q=2 must strictly beat q=0 and q=5");
    }

    @Test
    void fourLayersMatchBruteForce() {
        long d = 10L;
        double p = 3.0;
        List<PassBInventoryModel.ChannelSpec> channels = List.of(
            new PassBInventoryModel.ChannelSpec(InventoryTier.hand, 3L, 0.5, 0.0, 0.1, true),
            new PassBInventoryModel.ChannelSpec(InventoryTier.network, 4L, 0.8, 0.5, 0.2, false),
            new PassBInventoryModel.ChannelSpec(InventoryTier.nearby, 3L, 1.0, 1.5, 0.4, false),
            new PassBInventoryModel.ChannelSpec(InventoryTier.remote, 4L, 1.2, 3.0, 0.8, false)
        );
        double gamma = 2.0;
        double h = 3.0;
        double s0 = 1.0;
        double capB = 25.0;

        var exact = PassBInventoryModel.solveFourTierExact(d, p, channels, gamma, h, s0, capB);
        double bruteMin = p * d;
        for (long q0 = 0; q0 <= 3; q0++) {
            for (long q1 = 0; q1 <= 4; q1++) {
                for (long q2 = 0; q2 <= 3; q2++) {
                    for (long q3 = 0; q3 <= 4; q3++) {
                        long sum = q0 + q1 + q2 + q3;
                        if (sum > d || sum == 0) continue;
                        double lin = q0 * 0.1 + q1 * 0.2 + q2 * 0.4 + q3 * 0.8;
                        double raw = q0 * 0.5
                                + q1 * 0.8
                                + q2 * 1.0
                                + q3 * 1.2
                                + (q1 > 0 ? 0.5 : 0.0)
                                + (q2 > 0 ? 1.5 : 0.0)
                                + (q3 > 0 ? 3.0 : 0.0)
                                + gamma * PassBInventoryModel.smoothSplitRamp(sum, d, h);
                        double total = p * (d - sum) + lin + PassBInventoryModel.marginalCappedSoft(raw, s0, capB);
                        if (total < bruteMin) {
                            bruteMin = total;
                        }
                    }
                }
            }
        }
        assertEquals(bruteMin, exact.totalComparableCost(), 1e-9);
    }

    @Test
    void rareStockProtectedUnderItsInequality() {
        assertTrue(PassBInventoryModel.isRareSubstitutionStrictlyInferior(18.0, 2.0, 5.0, 0.0, 100.0));
        assertFalse(PassBInventoryModel.isRareSubstitutionStrictlyInferior(8.0, 2.0, 10.0, 0.0, 100.0));

        ComponentStackKey rare = item("test:rare_gem");
        ComponentStackKey common = item("test:common_stone");
        InventorySnapshot inv = invTiers(
            Map.of(InventoryTier.hand, new StockLedger(Map.of(rare, 10L, common, 10L))),
            Map.of(),
            Set.of(rare)
        );
        assertTrue(PassBInventoryModel.isForbiddenForGenericTag(rare, inv, PreferenceModifiers.StockReferenceMode.protectedStrict));
        assertFalse(PassBInventoryModel.isForbiddenForGenericTag(common, inv, PreferenceModifiers.StockReferenceMode.protectedStrict));
        assertTrue(PassBInventoryModel.isForbiddenForGenericTag(common, inv, PreferenceModifiers.StockReferenceMode.ignore));
    }

    @Test
    void familyBreaksAtSavingsBoundary() {
        double a = 40.0;
        double switchRaw = 3.0;
        double cap = ScoreBreakdown.defaultBCons * a;
        double marginalPenalty = ScoreBreakdown.cappedSoft(switchRaw, cap);
        assertTrue(marginalPenalty - 0.5 < marginalPenalty);
        assertTrue(marginalPenalty + 0.5 > marginalPenalty);
        assertEquals(ScoreBreakdown.cappedSoft(2.0 + 1.0, cap), ScoreBreakdown.cappedSoft(1.0 + 2.0, cap), 1e-12);
    }

    @Test
    void riskZeroCVReducesExactly() {
        AcquisitionEstimate det = new AcquisitionEstimate(
            AcquisitionKind.manualGather, 12.0, 25.0, 60.0, 0.0, 0.2, 0.0, 4L, "ep:mine", "v1", "100%", "test"
        );
        assertEquals(37.0, det.riskAdjustedSeconds(0.65, 0.50), 1e-12);
        assertEquals(0.0, det.riskPremiumSeconds(0.65, 0.50), 1e-12);

        AcquisitionEstimate noisy = new AcquisitionEstimate(
            AcquisitionKind.manualGather, 12.0, 25.0, 60.0, 0.0, 0.2, 0.5, 4L, "ep:mine", "v1", "100%", "test"
        );
        double rLow = noisy.riskAdjustedSeconds(0.2, 0.5);
        double rHigh = noisy.riskAdjustedSeconds(0.8, 0.5);
        assertTrue(rHigh > rLow && rLow > 37.0);
        assertEquals(4L, noisy.guaranteedQuantity());
    }

    @Test
    void stageAndCapitalAreMonotone() {
        for (int i = 0; i <= 20; i++) {
            double s = i / 20.0;
            StageDistribution st = StageDistribution.fromMaturity(s);
            assertEquals(1.0, st.early() + st.mid() + st.late(), 1e-9);
            assertTrue(st.early() >= 0.0 && st.mid() >= 0.0 && st.late() >= 0.0);
        }
        StageDistribution st = StageDistribution.fromMaturity(0.5);
        assertEquals(100.0, StageAndCapitalModel.amortizedCapital(100.0, 1L, st, 0L), 1e-9);
        double[] ks = new double[12];
        for (int q = 1; q <= 12; q++) {
            ks[q - 1] = StageAndCapitalModel.amortizedCapital(100.0, q, st, 0L);
        }
        for (int q = 0; q < 11; q++) {
            assertTrue(ks[q + 1] - ks[q] <= 1e-9);
        }
        for (int q = 0; q < 10; q++) {
            double d2 = (ks[q + 2] - ks[q + 1]) - (ks[q + 1] - ks[q]);
            assertTrue(d2 >= -1e-9);
        }
    }

    @Test
    void tripSliderMonotoneOnFrozenSet() {
        List<CostVector> frozen = List.of(
            new CostVector(5.0, 40.0, 0.0, 0.0, 1.0, 0.5),
            new CostVector(20.0, 15.0, 0.0, 0.0, 1.0, 0.5),
            new CostVector(45.0, 0.0, 0.0, 0.0, 1.0, 0.5)
        );
        CostWeights base = CostWeights.forPreset(ObjectivePreset.autoSmart);
        double prevTrip = Double.POSITIVE_INFINITY;
        for (double aT : new double[]{0.5, 1.0, 1.5, 2.5, 4.0, 8.0}) {
            CostWeights w = new CostWeights(
                base.humanActiveWeight(), aT, base.passiveWaitWeight(),
                base.machineSetupWeight(), base.materialScarcityWeight(), base.cognitiveFrictionWeight()
            );
            CostVector best = frozen.stream().min((x, y) -> Double.compare(w.scalarize(x), w.scalarize(y))).orElseThrow();
            assertTrue(best.tripActiveSeconds() <= prevTrip + 1e-12);
            prevTrip = best.tripActiveSeconds();
        }
    }

    @Test
    void conditionedFrontierIsAcyclic() {
        ProductionPlan p1 = synthPlan("p1", new CostVector(10.0, 0.0, 10.4, 0.0, 10.8, 0.0), RegimeSignature.baseline, 1.0);
        ProductionPlan p2 = synthPlan("p2", new CostVector(10.8, 0.0, 10.0, 0.0, 10.4, 0.0), RegimeSignature.baseline, 1.01);
        ProductionPlan p3 = synthPlan("p3", new CostVector(10.4, 0.0, 10.8, 0.0, 10.0, 0.0), RegimeSignature.baseline, 1.02);
        double[] scales = {10.0, 10.0, 10.0, 10.0, 10.0, 10.0};
        var ref = ConditionedFrontierSelector.select(List.of(p1, p2, p3), scales, 0.05, 4);
        assertFalse(ref.selectedTopK().isEmpty());
        List<String> refIds = ref.selectedTopK().stream().map(ProductionPlan::planId).toList();

        List<List<ProductionPlan>> perms = List.of(
            List.of(p1, p2, p3), List.of(p1, p3, p2), List.of(p2, p1, p3),
            List.of(p2, p3, p1), List.of(p3, p1, p2), List.of(p3, p2, p1)
        );
        for (var perm : perms) {
            var out = ConditionedFrontierSelector.select(perm, scales, 0.05, 4);
            assertEquals(refIds, out.selectedTopK().stream().map(ProductionPlan::planId).toList());
        }
    }

    @Test
    void incompatibleTechSurvives() {
        RegimeSignature regX = new RegimeSignature(Set.of(machine("mod:mach_x")), Set.of("clus_x"), false, Set.of("build:x"));
        RegimeSignature regY = new RegimeSignature(Set.of(machine("mod:mach_y")), Set.of("clus_y"), false, Set.of("build:y"));
        assertFalse(regX.entails(regY));
        assertFalse(regY.entails(regX));
        ProductionPlan planA = synthPlan("plan_x", new CostVector(10.0, 0.0, 5.0, 20.0, 1.0, 1.0), regX, 1.0);
        ProductionPlan planB = synthPlan("plan_y", new CostVector(18.0, 0.0, 8.0, 20.0, 2.0, 1.0), regY, 1.4);
        var out = ConditionedFrontierSelector.select(List.of(planA, planB), new double[]{10, 10, 10, 10, 10, 10}, 0.05, 4);
        assertEquals(2, out.selectedTopK().size());
    }

    @Test
    void tabuCannotTurnUnknownIntoExecutable() {
        ComponentStackKey ore = item("test:ore");
        ComponentStackKey widget = item("test:widget");
        CompiledRecipeGraph g = compile(
            List.of(rec("minecraft:crafting", "only_route", List.of("minecraft:crafting_table"), List.of(slot(ore, 1)), List.of(out(widget, 1))))
        );
        PlanningRequest req = new PlanningRequest(
            List.of(new TargetDemand(widget, 1L)),
            new PlanningContext(
                InventorySnapshot.empty,
                fac(ownedCraftingTable()),
                new DefaultAcquisitionOracle(g),
                DefaultHumanGuidanceView.empty(),
                ProgressionState.overworldDefault
            ),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );
        PlanningResult res = new DefaultPlanningService(g).planOnce(req);
        assertEquals(PlanningStatus.feasible, res.status());
        assertEquals(1, res.paretoPlans().size());
    }

    @Test
    void oneNativeOutvotesTwoHundredDisplays() {
        List<FidelityScorer.SiblingSample> base = List.of(
            new FidelityScorer.SiblingSample("native:smelting", RecipeEvidenceTier.tierA, 20.0, true)
        );
        var anchorBase = FidelityScorer.computeSiblingAnchor(base);

        List<FidelityScorer.SiblingSample> flooded = new ArrayList<>(base);
        for (int i = 0; i < 200; i++) {
            flooded.add(new FidelityScorer.SiblingSample("jei:fake_" + i, RecipeEvidenceTier.tierC, 0.5, false));
        }
        var anchorFlooded = FidelityScorer.computeSiblingAnchor(flooded);
        assertEquals(anchorBase.medianUnitCost(), anchorFlooded.medianUnitCost(), 1e-12);
        assertEquals(anchorBase.status(), anchorFlooded.status());

        var assess = FidelityScorer.assess(
            new FidelityScorer.StructuralFeatures(0.3, 0.5, 1.0, 0.2, 0.0),
            RecipeEvidenceTier.tierC,
            0.5,
            false,
            anchorFlooded
        );
        assertTrue(assess.suspect());
        assertTrue(assess.liftedGuidanceUnitCost() >= 20.0 * 0.99);
    }

    @Test
    void uniqueSuspiciousRecipeRemainsInspectable() {
        ComponentStackKey dirt = item("test:dirt");
        ComponentStackKey unicorn = item("test:unicorn");
        RawRecipeRecord suspicious = new RawRecipeRecord(
            ns("jei:category/mystery/altar"),
            "jei_fallback",
            Optional.empty(),
            List.of(),
            List.of(slot(dirt, 1)),
            List.of(out(unicorn, 1)),
            Map.of(),
            0L,
            Set.of(RecipeSystemTags.jeiFallback),
            Set.of(CanonicalRecipeTag.suspiciousJei),
            RecipeEvidenceTier.tierC,
            0.35,
            Optional.empty(),
            true
        );
        CompiledRecipeGraph g = compile(List.of(suspicious));
        PlanningRequest req = new PlanningRequest(
            List.of(new TargetDemand(unicorn, 1L)),
            new PlanningContext(
                InventorySnapshot.empty,
                FacilitySnapshot.empty,
                new DefaultAcquisitionOracle(g),
                DefaultHumanGuidanceView.empty(),
                ProgressionState.overworldDefault
            ),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );
        PlanningResult res = new DefaultPlanningService(g).planOnce(req);
        assertEquals(PlanningStatus.conditional, res.status());
        assertTrue(res.paretoPlans().isEmpty());
        assertFalse(res.conditionalPlans().isEmpty());
        assertTrue(res.conditionalPlans().get(0).conditionalDraft());
    }

    @Test
    void softCostsDoNotLeak() {
        CostVector phys = new CostVector(12.0, 8.0, 5.0, 30.0, 2.0, 1.5);
        CostWeights w = CostWeights.forPreset(ObjectivePreset.autoSmart);
        ScoreBreakdown clean = ScoreBreakdown.compute(phys, w, 30.0, 50.0, 0.0, 0.0, 0.0, 0.0, 0.0);
        ScoreBreakdown penalized = ScoreBreakdown.compute(phys, w, 30.0, 50.0, 10.0, 5.0, 8.0, 3.0, 0.1);
        assertEquals(clean.physicalCost(), penalized.physicalCost());
        assertEquals(clean.physicalDecisionSecondsU(), penalized.physicalDecisionSecondsU(), 1e-12);
        assertTrue(penalized.totalObjectiveJ() > clean.totalObjectiveJ());
        assertTrue(penalized.stockSoftCapped() <= ScoreBreakdown.defaultBStock * 50.0 + 1e-9);
        assertTrue(penalized.habitSoftCapped() <= ScoreBreakdown.defaultBHabit * 50.0 + 1e-9);
    }

    @Test
    void voiZeroWhenActionNeverChanges() {
        VoIEstimate zero = VoICounterfactualEngine.evaluateExact(
            new double[]{0.5, 0.5}, new double[][]{{10.0, 12.0}, {20.0, 25.0}}, 3.0, 50.0
        );
        assertEquals(0.0, zero.grossEvsiSeconds(), 1e-12);
        assertEquals(0.0, zero.netEvsiSeconds(), 1e-12);

        VoIEstimate m9 = VoICounterfactualEngine.evaluateExact(
            new double[]{0.5, 0.5}, new double[][]{{0.0, 40.0}, {20.0, 20.0}}, 3.0, 50.0
        );
        assertEquals(10.0, m9.grossEvsiSeconds(), 1e-9);
        assertEquals(7.0, m9.netEvsiSeconds(), 1e-9);
        assertTrue(m9.netEvsiSeconds() > 0.0);
    }

    @Test
    void idempotentQuestionsAndFeedback() {
        PromptSuppressionLedger ledger = new PromptSuppressionLedger();
        QuestionKey qk = new QuestionKey(DecisionPromptKind.machineAvailability, "mod:smelter", GuidanceScope.worldSave, 1L);
        assertFalse(ledger.isSuppressed(qk, 10L));
        ledger.markAnsweredOrDismissed(qk);
        assertTrue(ledger.isSuppressed(qk, 10L));
        assertTrue(ledger.isSuppressed(qk, 999_999L));

        OnlinePreferenceLearner learner = new OnlinePreferenceLearner();
        boolean first = learner.recordChoice("ev-1", Map.of("sys:a", 1.0), 1.0, List.of(Map.of("sys:b", 1.0)), List.of(1.0), 1L);
        double wAfterFirst = learner.weightOf("sys:a", 1L);
        boolean replay = learner.recordChoice("ev-1", Map.of("sys:a", 1.0), 1.0, List.of(Map.of("sys:b", 1.0)), List.of(1.0), 2L);
        assertTrue(first);
        assertFalse(replay);
        assertEquals(wAfterFirst, learner.weightOf("sys:a", 1L), 1e-12);

        ExecutionFailureTracker tracker = new ExecutionFailureTracker();
        var key = new ExecutionFailureTracker.Key("sem:1", "cond:1", "v1");
        assertTrue(tracker.recordExecutionOutcome("exec-1", key, false, ExecutionFailureCategory.wrongBinding, 1L));
        double b1 = tracker.currentFailureBurden(key, 1L);
        assertFalse(tracker.recordExecutionOutcome("exec-1", key, false, ExecutionFailureCategory.wrongBinding, 1L));
        assertEquals(b1, tracker.currentFailureBurden(key, 1L), 1e-12);
    }

    @Test
    void boundedLearningAndRecovery() {
        OnlinePreferenceLearner learner = new OnlinePreferenceLearner();
        double prev = 0.0;
        for (int i = 1; i <= 100; i++) {
            learner.recordChoice("ev-" + i, Map.of("sys:fav", 1.0), 1.0, List.of(Map.of("sys:other", 1.0)), List.of(1.0), i);
            double cur = learner.weightOf("sys:fav", i);
            assertTrue(Math.abs(cur) <= OnlinePreferenceLearner.defaultHMax + 1e-9);
            assertTrue(Math.abs(cur - prev) <= OnlinePreferenceLearner.defaultMaxSingleStepDh + 1e-6);
            prev = cur;
        }
        ExecutionFailureTracker tracker = new ExecutionFailureTracker();
        var key = new ExecutionFailureTracker.Key("sem:2", "cond:1", "v1");
        tracker.recordExecutionOutcome("cancel-1", key, false, ExecutionFailureCategory.userCancel, 1L);
        assertEquals(0.0, tracker.currentFailureBurden(key, 1L), 1e-12);
        assertEquals(1.0, tracker.runtimeFidelity(1.0, key, 1L), 1e-12);

        tracker.recordExecutionOutcome("fail-1", key, false, ExecutionFailureCategory.hiddenRequirement, 2L);
        double failedBurden = tracker.currentFailureBurden(key, 2L);
        assertTrue(failedBurden > 0.0);
        assertTrue(tracker.runtimeFidelity(1.0, key, 2L) < 1.0);

        tracker.recordExecutionOutcome("ok-1", key, true, ExecutionFailureCategory.other, 10L);
        assertTrue(tracker.currentFailureBurden(key, 10L) < failedBurden);
    }

    @Test
    void coldWarmParallelDeterminism() throws Exception {
        ComponentStackKey ore = item("test:ore");
        ComponentStackKey dust = item("test:dust");
        ComponentStackKey ingot = item("test:ingot");
        List<RawRecipeRecord> recs = List.of(
            rec("minecraft:smelting", "s1", List.of("minecraft:furnace"), List.of(slot(ore, 1)), List.of(out(ingot, 1))),
            rec("mod:crush", "c1", List.of("mod:crusher"), List.of(slot(ore, 1)), List.of(out(dust, 2))),
            rec("minecraft:smelting", "s2", List.of("minecraft:furnace"), List.of(slot(dust, 1)), List.of(out(ingot, 1)))
        );
        List<RawRecipeRecord> reversed = new ArrayList<>(recs);
        Collections.reverse(reversed);
        CompiledRecipeGraph g1 = compile(recs);
        CompiledRecipeGraph g2 = compile(reversed);

        DefaultPlanningService svc1 = new DefaultPlanningService(g1);
        DefaultPlanningService svc2 = new DefaultPlanningService(g2);
        PlanningRequest req1 = new PlanningRequest(
            List.of(new TargetDemand(ingot, 8L)),
            new PlanningContext(InventorySnapshot.empty, FacilitySnapshot.empty, new DefaultAcquisitionOracle(g1), DefaultHumanGuidanceView.empty(), ProgressionState.overworldDefault),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );
        PlanningRequest req2 = new PlanningRequest(
            List.of(new TargetDemand(ingot, 8L)),
            new PlanningContext(InventorySnapshot.empty, FacilitySnapshot.empty, new DefaultAcquisitionOracle(g2), DefaultHumanGuidanceView.empty(), ProgressionState.overworldDefault),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );
        CostVector ref = svc1.planOnce(req1).paretoPlans().get(0).totalCost();
        CostVector warm = svc1.planOnce(req1).paretoPlans().get(0).totalCost();
        assertEquals(ref, warm);
        assertEquals(ref, svc2.planOnce(req2).paretoPlans().get(0).totalCost());

        ExecutorService pool = Executors.newFixedThreadPool(4);
        try {
            List<Future<CostVector>> futures = new ArrayList<>();
            for (int t = 0; t < 8; t++) {
                Callable<CostVector> task = () -> new DefaultPlanningService(g1).planOnce(req1).paretoPlans().get(0).totalCost();
                futures.add(pool.submit(task));
            }
            for (Future<CostVector> f : futures) {
                assertEquals(ref, f.get());
            }
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void deadlineNeverPublishesSpeedDependentWinner() {
        ComponentStackKey ore = item("test:ore");
        ComponentStackKey ingot = item("test:ingot");
        CompiledRecipeGraph g = compile(
            List.of(rec("minecraft:smelting", "s1", List.of("minecraft:furnace"), List.of(slot(ore, 1)), List.of(out(ingot, 1))))
        );
        PlanningBudget fixedWork = new PlanningBudget(
            Duration.ofSeconds(5), Duration.ofSeconds(10), 1000, 2000, 4, 4, true, true, 16, 50_000L
        );
        PlanningRequest req = new PlanningRequest(
            List.of(new TargetDemand(ingot, 4L)),
            new PlanningContext(InventorySnapshot.empty, FacilitySnapshot.empty, new DefaultAcquisitionOracle(g), DefaultHumanGuidanceView.empty(), ProgressionState.overworldDefault),
            PlanningProfile.defaultBalanced(),
            fixedWork
        );
        PlanningResult r1 = new DefaultPlanningService(g).planOnce(req);
        PlanningResult r2 = new DefaultPlanningService(g).planOnce(req);
        assertEquals(r1.status(), r2.status());
        assertEquals(r1.paretoPlans().size(), r2.paretoPlans().size());
        assertEquals(r1.paretoPlans().get(0).planId(), r2.paretoPlans().get(0).planId());
    }

    @Test
    void stableIdentityWithMissingId() {
        ComponentStackKey a = item("test:a");
        ComponentStackKey b = item("test:b");
        ComponentStackKey outItem = item("test:out");
        RawRecipeRecord r1 = new RawRecipeRecord(
            ns("jei:category/mod/mixer"), "jei_fallback", Optional.empty(), List.of(machine("mod:mixer")),
            List.of(slot(a, 1), slot(b, 2)), List.of(out(outItem, 1)), Map.of()
        );
        RawRecipeRecord r2 = new RawRecipeRecord(
            ns("jei:category/mod/mixer"), "jei_fallback", Optional.empty(), List.of(machine("mod:mixer")),
            List.of(slot(b, 2), slot(a, 1)), List.of(out(outItem, 1)), Map.of()
        );
        RecipeSemanticKey k1 = RecipeSemanticKeyBuilder.build(r1, true);
        RecipeSemanticKey k2 = RecipeSemanticKeyBuilder.build(r2, true);
        assertEquals(k1.sha256Hex(), k2.sha256Hex(), "Commutative slot permutation must yield identical semantic key");

        RawRecipeRecord rOpaque = new RawRecipeRecord(
            ns("jei:category/mod/mixer"), "jei_fallback", Optional.empty(), List.of(machine("mod:mixer")),
            List.of(slot(a, 1), slot(b, 2)), List.of(out(outItem, 1)), Map.of(),
            0L, Set.of(), Set.of(CanonicalRecipeTag.normalProduction), RecipeEvidenceTier.tierC, 1.0, Optional.empty(), true
        );
        assertNotEquals(k1.sha256Hex(), RecipeSemanticKeyBuilder.build(rOpaque, true).sha256Hex());
        assertNotEquals(
            RecipeSemanticKeyBuilder.build(r1, false).sha256Hex(),
            RecipeSemanticKeyBuilder.build(r2, false).sha256Hex()
        );
    }

    @Test
    void overflowCannotCreateFreeMaterials() {
        long huge = SafeMath.saturationLimit - 5L;
        assertEquals(SafeMath.saturationLimit, SafeMath.addSaturating(huge, 100L));
        assertEquals(SafeMath.saturationLimit, SafeMath.mulSaturating(huge, 3L));
        assertTrue(SafeMath.addSaturating(huge, 100L) > 0L);
        assertTrue(SafeMath.willOverflowAdd(huge, 100L));
        assertTrue(SafeMath.willOverflowMul(huge, 3L));
        assertEquals(0L, SafeMath.mulSaturating(0L, Long.MAX_VALUE));
    }

    @Test
    void diversityTieIsCanonical() {
        RegimeSignature r1 = new RegimeSignature(Set.of(machine("m1")), Set.of("c1"), false, Set.of());
        RegimeSignature r2 = new RegimeSignature(Set.of(machine("m2")), Set.of("c2"), false, Set.of());
        RegimeSignature r3 = new RegimeSignature(Set.of(machine("m3")), Set.of("c3"), true, Set.of());
        RegimeSignature r4 = new RegimeSignature(Set.of(machine("m4")), Set.of("c4"), false, Set.of());

        List<ProductionPlan> pool = List.of(
            synthPlan("p1", new CostVector(10.0, 0.0, 5.0, 10.0, 1.0, 1.0), r1, 1.00),
            synthPlan("p2", new CostVector(10.0, 0.0, 5.0, 10.0, 1.0, 1.0), r2, 1.01),
            synthPlan("p3", new CostVector(10.0, 5.0, 5.0, 10.0, 1.0, 1.0), r3, 1.02),
            synthPlan("p4", new CostVector(10.0, 0.0, 5.0, 10.0, 1.0, 1.0), r4, 1.03)
        );
        double[] scales = {10, 10, 10, 10, 10, 10};
        var ref = ConditionedFrontierSelector.select(pool, scales, 0.05, 3);
        List<String> refIds = ref.selectedTopK().stream().map(ProductionPlan::planId).toList();
        assertEquals(3, refIds.size());

        List<ProductionPlan> rev = new ArrayList<>(pool);
        Collections.reverse(rev);
        var revOut = ConditionedFrontierSelector.select(rev, scales, 0.05, 3);
        assertEquals(refIds, revOut.selectedTopK().stream().map(ProductionPlan::planId).toList());
    }

    private static ProductionPlan synthPlan(String id, CostVector cost, RegimeSignature reg, double jScore) {
        ScoreBreakdown sb = new ScoreBreakdown(
            cost, 10.0, jScore * 10.0, cost.machineSetupCapital(),
            0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0,
            jScore, jScore, OptionalDouble.empty(), "NO_NONTRIVIAL_BOUND"
        );
        return new ProductionPlan(
            id,
            "cirrus.strategy.test",
            List.of(),
            PreparationSummary.empty,
            List.of(),
            cost,
            new VerificationCertificate(true, true, true, true, true, true, List.of()),
            sb,
            reg,
            false,
            List.of()
        );
    }
}
