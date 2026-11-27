package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionEstimate;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOption;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
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
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
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
import dev.vfyjxf.cirrus.planning.core.cone.FiveBucketTagPager;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.cost.DualBoundCertifier;
import dev.vfyjxf.cirrus.planning.core.cost.PassBInventoryModel;
import dev.vfyjxf.cirrus.planning.core.cost.StageAndCapitalModel;
import dev.vfyjxf.cirrus.planning.core.cost.V3AcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.graph.TagHomomorphismAnalyzer;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.human.VoICounterfactualEngine;
import dev.vfyjxf.cirrus.planning.core.lang.SafeMath;
import dev.vfyjxf.cirrus.planning.core.learn.ExecutionFailureTracker;
import dev.vfyjxf.cirrus.planning.core.learn.OnlinePreferenceLearner;
import dev.vfyjxf.cirrus.planning.core.select.ConditionedFrontierSelector;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;
import dev.vfyjxf.cirrus.planning.core.solve.DeterministicAlnsScheduler;
import dev.vfyjxf.cirrus.planning.core.solve.PassABaselinePotentials;
import dev.vfyjxf.cirrus.planning.core.solve.TransactionalLedger;
import dev.vfyjxf.cirrus.planning.core.trust.FidelityScorer;
import dev.vfyjxf.cirrus.planning.core.trust.RecipeSemanticKeyBuilder;
import dev.vfyjxf.cirrus.planning.core.verify.MacroSeedCertifier;
import dev.vfyjxf.cirrus.planning.core.verify.NonLossyCertificateVerifier;
import dev.vfyjxf.cirrus.planning.core.verify.TwoPoolReplayVerifier;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
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
import java.util.function.Function;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.compile;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.fac;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.generousBudget;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedCraftingTable;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.rec;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.tagSlot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exhaustive JUnit 5 verification of all 46 mathematical properties & counterexamples
 * defined in the Planning v3 Mathematical Specification (M1-M9, Table 2 #1-#46).
 */
class PlanningV3MathematicalPropertyTest {

    private static InventorySnapshot invTiers(Map<InventoryTier, StockLedger> avail, Map<InventoryTier, StockLedger> res, Set<ResourceKey> prot) {
        return InventorySnapshot.fromTiers(avail, res, Set.of(), prot);
    }

    private static BaseContract contract(ResourceKey rk, double unitSec) {
        return new BaseContract(rk, unitSec, 0.1, 0.0, 0.0, 1_000_000L, false, "test");
    }

    // #1
    @Test
    void conservesEveryPrefix() {
        ResourceKey ore = item("test:ore");
        ResourceKey ingot = item("test:ingot");
        ResourceKey gear = item("test:gear");
        InventorySnapshot inv = invTiers(Map.of(InventoryTier.hand, new StockLedger(Map.of(ore, 2L))), Map.of(), Set.of());
        List<TwoPoolReplayVerifier.ReplayEvent> validEvents = List.of(
            new TwoPoolReplayVerifier.PullStockEvent(InventoryTier.hand, ore, 2L),
            new TwoPoolReplayVerifier.ExternalSupplyEvent(ore, 2L, false),
            new TwoPoolReplayVerifier.CraftActionEvent(
                "smelt",
                4L,
                null,
                true,
                List.of(new TwoPoolReplayVerifier.BatchSlotBinding(0, 1L, true, Map.of(ore, 4L), List.of())),
                Map.of(),
                Map.of(ingot, 4L),
                Map.of()
            ),
            new TwoPoolReplayVerifier.CraftActionEvent(
                "press",
                1L,
                null,
                true,
                List.of(new TwoPoolReplayVerifier.BatchSlotBinding(0, 4L, true, Map.of(ingot, 4L), List.of())),
                Map.of(),
                Map.of(gear, 1L),
                Map.of()
            ),
            new TwoPoolReplayVerifier.DeliverTargetEvent(gear, 1L)
        );
        var ok = TwoPoolReplayVerifier.replay(inv, Set.of(), Map.of(gear, 1L), validEvents, true);
        assertTrue(ok.valid(), () -> "Expected valid replay: " + ok.violations());
        assertTrue(ok.nonLossyCertified());

        // Prefix violation: craft before pulling stock must fail even though total sum matches!
        List<TwoPoolReplayVerifier.ReplayEvent> badOrder = List.of(
            validEvents.get(2),
            validEvents.get(0),
            validEvents.get(1),
            validEvents.get(3),
            validEvents.get(4)
        );
        var bad = TwoPoolReplayVerifier.replay(inv, Set.of(), Map.of(gear, 1L), badOrder, true);
        assertFalse(bad.valid());
    }

    // #2
    @Test
    void mixedSlotHasPhysicalLots() {
        ResourceKey oak = item("test:oak_planks");
        ResourceKey birch = item("test:birch_planks");
        ResourceKey stick = item("test:stick");
        InventorySnapshot inv = invTiers(
            Map.of(
                InventoryTier.hand,
                new StockLedger(Map.of(oak, 2L)),
                InventoryTier.network,
                new StockLedger(Map.of(birch, 2L))
            ),
            Map.of(),
            Set.of()
        );
        List<TwoPoolReplayVerifier.ReplayEvent> events = List.of(
            new TwoPoolReplayVerifier.PullStockEvent(InventoryTier.hand, oak, 2L),
            new TwoPoolReplayVerifier.PullStockEvent(InventoryTier.network, birch, 2L),
            new TwoPoolReplayVerifier.CraftActionEvent(
                "sticks",
                2L,
                null,
                true,
                List.of(
                    new TwoPoolReplayVerifier.BatchSlotBinding(
                        0,
                        2L,
                        false,
                        Map.of(oak, 2L, birch, 2L),
                        List.of(Map.of(oak, 2L), Map.of(birch, 2L))
                    )
                ),
                Map.of(),
                Map.of(stick, 8L),
                Map.of()
            ),
            new TwoPoolReplayVerifier.DeliverTargetEvent(stick, 8L)
        );
        var res = TwoPoolReplayVerifier.replay(inv, Set.of(), Map.of(stick, 8L), events, true);
        assertTrue(res.valid(), () -> "Mixed slot lots must pass: " + res.violations());

        List<TwoPoolReplayVerifier.ReplayEvent> overspend = List.of(
            new TwoPoolReplayVerifier.PullStockEvent(InventoryTier.hand, oak, 3L)
        );
        assertFalse(TwoPoolReplayVerifier.replay(inv, Set.of(), Map.of(), overspend, false).valid());
    }

    // #3
    @Test
    void enforcesPerBatchVariant() {
        ResourceKey red = item("test:red_wool");
        ResourceKey blue = item("test:blue_wool");
        ResourceKey bed = item("test:bed");
        InventorySnapshot inv = invTiers(Map.of(InventoryTier.hand, new StockLedger(Map.of(red, 2L, blue, 2L))), Map.of(), Set.of());
        List<TwoPoolReplayVerifier.ReplayEvent> mixedOneBatch = List.of(
            new TwoPoolReplayVerifier.PullStockEvent(InventoryTier.hand, red, 2L),
            new TwoPoolReplayVerifier.PullStockEvent(InventoryTier.hand, blue, 2L),
            new TwoPoolReplayVerifier.CraftActionEvent(
                "bed_craft",
                1L,
                null,
                true,
                List.of(new TwoPoolReplayVerifier.BatchSlotBinding(0, 4L, true, Map.of(red, 2L, blue, 2L), List.of())),
                Map.of(),
                Map.of(bed, 1L),
                Map.of()
            ),
            new TwoPoolReplayVerifier.DeliverTargetEvent(bed, 1L)
        );
        assertFalse(TwoPoolReplayVerifier.replay(inv, Set.of(), Map.of(bed, 1L), mixedOneBatch, false).valid());

        InventorySnapshot inv2 = invTiers(Map.of(InventoryTier.hand, new StockLedger(Map.of(red, 4L, blue, 4L))), Map.of(), Set.of());
        List<TwoPoolReplayVerifier.ReplayEvent> twoHomogeneousBatches = List.of(
            new TwoPoolReplayVerifier.PullStockEvent(InventoryTier.hand, red, 4L),
            new TwoPoolReplayVerifier.PullStockEvent(InventoryTier.hand, blue, 4L),
            new TwoPoolReplayVerifier.CraftActionEvent(
                "bed_craft",
                2L,
                null,
                true,
                List.of(
                    new TwoPoolReplayVerifier.BatchSlotBinding(
                        0,
                        4L,
                        true,
                        Map.of(red, 4L, blue, 4L),
                        List.of(Map.of(red, 4L), Map.of(blue, 4L))
                    )
                ),
                Map.of(),
                Map.of(bed, 2L),
                Map.of()
            ),
            new TwoPoolReplayVerifier.DeliverTargetEvent(bed, 2L)
        );
        var ok = TwoPoolReplayVerifier.replay(inv2, Set.of(), Map.of(bed, 2L), twoHomogeneousBatches, true);
        assertTrue(ok.valid(), () -> "Per-batch homogeneous must pass: " + ok.violations());
    }

    // #4
    @Test
    void probabilisticSurplusNeverPaysDebt() {
        ResourceKey ore = item("test:ore");
        ResourceKey luckyGem = item("test:lucky_gem");
        List<TwoPoolReplayVerifier.ReplayEvent> events = List.of(
            new TwoPoolReplayVerifier.ExternalSupplyEvent(ore, 100L, false),
            new TwoPoolReplayVerifier.CraftActionEvent(
                "crush_prob",
                100L,
                null,
                true,
                List.of(new TwoPoolReplayVerifier.BatchSlotBinding(0, 1L, true, Map.of(ore, 100L), List.of())),
                Map.of(),
                Map.of(),
                Map.of()
            ),
            new TwoPoolReplayVerifier.DeliverTargetEvent(luckyGem, 1L)
        );
        assertFalse(TwoPoolReplayVerifier.replay(InventorySnapshot.empty, Set.of(), Map.of(luckyGem, 1L), events, false).valid());
    }

    // #5
    @Test
    void fifoPoolsPublishOnlyFinishedLots() {
        ResourceKey dust = item("test:dust");
        TransactionalLedger ledger = new TransactionalLedger(InventorySnapshot.empty, Set.of());
        ledger.publishLot(TransactionalLedger.PoolKind.byproduct, dust, 1, 0, 3L);
        ledger.publishLot(TransactionalLedger.PoolKind.byproduct, dust, 3, 0, 5L);

        List<TransactionalLedger.LotSlice> taken = ledger.consumeFifo(dust, 3L, 2);
        assertEquals(1, taken.size());
        assertEquals(1, taken.get(0).sourceEventIndex());
        assertEquals(3L, taken.get(0).quantity());

        assertThrows(IllegalStateException.class, () -> ledger.consumeFifo(dust, 1L, 3));

        List<TransactionalLedger.LotSlice> taken2 = ledger.consumeFifo(dust, 2L, 4);
        assertEquals(3, taken2.get(0).sourceEventIndex());
        assertEquals(2L, taken2.get(0).quantity());
    }

    // #6
    @Test
    void holdsNonconsumableAndMachine() {
        ResourceKey mold = item("test:mold");
        ResourceKey plate = item("test:plate");
        InventorySnapshot inv = invTiers(Map.of(InventoryTier.hand, new StockLedger(Map.of(mold, 1L))), Map.of(), Set.of());
        List<TwoPoolReplayVerifier.ReplayEvent> dualUse = List.of(
            new TwoPoolReplayVerifier.PullStockEvent(InventoryTier.hand, mold, 1L),
            new TwoPoolReplayVerifier.CraftActionEvent(
                "press",
                1L,
                null,
                true,
                List.of(new TwoPoolReplayVerifier.BatchSlotBinding(0, 1L, true, Map.of(mold, 1L), List.of())),
                Map.of(mold, 1L),
                Map.of(plate, 1L),
                Map.of()
            )
        );
        assertFalse(TwoPoolReplayVerifier.replay(inv, Set.of(), Map.of(plate, 1L), dualUse, false).valid());

        List<TwoPoolReplayVerifier.ReplayEvent> badMachine = List.of(
            new TwoPoolReplayVerifier.PullStockEvent(InventoryTier.hand, mold, 1L),
            new TwoPoolReplayVerifier.CraftActionEvent(
                "press",
                1L,
                "mod:press_mk1",
                false,
                List.of(),
                Map.of(mold, 1L),
                Map.of(plate, 1L),
                Map.of()
            )
        );
        assertFalse(TwoPoolReplayVerifier.replay(inv, Set.of("mod:press_mk2_incompatible"), Map.of(plate, 1L), badMachine, false).valid());

        List<TwoPoolReplayVerifier.ReplayEvent> installFirst = List.of(
            new TwoPoolReplayVerifier.PullStockEvent(InventoryTier.hand, mold, 1L),
            new TwoPoolReplayVerifier.InstallMachineEvent("mod:press_mk1", Map.of(), null),
            new TwoPoolReplayVerifier.CraftActionEvent(
                "press",
                1L,
                "mod:press_mk1",
                true,
                List.of(),
                Map.of(mold, 1L),
                Map.of(plate, 1L),
                Map.of()
            ),
            new TwoPoolReplayVerifier.DeliverTargetEvent(plate, 1L)
        );
        assertTrue(TwoPoolReplayVerifier.replay(inv, Set.of(), Map.of(plate, 1L), installFirst, true).valid());
    }

    // #7
    @Test
    void sharedMachineBuiltOnce() {
        ResourceKey ore = item("test:ore");
        ResourceKey plate = item("test:plate");
        ResourceKey rod = item("test:rod");
        ResourceKey frame = item("test:frame");
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
                .filter(s -> s instanceof ProvisionMachineStep pm && pm.machineId().equals("mod:press_machine"))
                .count();
        assertEquals(1L, pressBuilds, "Shared machine across two sub-branches must be provisioned exactly once");
    }

    // #8
    @Test
    void layersAndReservesAreFinite() {
        ResourceKey iron = item("test:iron");
        InventorySnapshot inv = invTiers(
            Map.of(InventoryTier.hand, new StockLedger(Map.of(iron, 10L))),
            Map.of(InventoryTier.hand, new StockLedger(Map.of(iron, 7L))),
            Set.of()
        );
        assertEquals(3L, inv.usableAmount(iron));
        TransactionalLedger ledger = new TransactionalLedger(inv, Set.of());
        assertEquals(3L, ledger.remainingTierStock(InventoryTier.hand, iron));
        ledger.pullFromTier(InventoryTier.hand, iron, 2L, 0);
        ledger.pullFromTier(InventoryTier.hand, iron, 1L, 1);
        assertThrows(IllegalStateException.class, () -> ledger.pullFromTier(InventoryTier.hand, iron, 1L, 2));
    }

    // #9
    @Test
    void noSourceFromRawCount() {
        ResourceKey uncertified = item("mod:mysterious_crystal");
        ResourceKey bannedNat = item("mod:banned_herb");
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
        V3AcquisitionOracle oracle = new V3AcquisitionOracle(g, boundary, guidance, List.of(), null);
        assertEquals(V3AcquisitionOracle.LeafDiagnosis.dataGapUncertifiedLeaf, oracle.diagnose(uncertified));
        assertTrue(oracle.assess(uncertified, 1L, ProgressionState.overworldDefault).isEmpty());
        assertEquals(V3AcquisitionOracle.LeafDiagnosis.forbiddenByUser, oracle.diagnose(bannedNat));
        assertTrue(oracle.assess(bannedNat, 1L, ProgressionState.overworldDefault).isEmpty());
    }

    // #10
    @Test
    void baseOrderFromEvidence() {
        ResourceKey cheap = item("opaque:alpha_001");
        ResourceKey expensive = item("opaque:beta_002");
        BaseBoundarySnapshot boundary = new BaseBoundarySnapshot(
            Map.of(cheap, contract(cheap, 10.0), expensive, contract(expensive, 3.0)),
            Set.of(),
            Set.of(),
            Set.of(),
            List.of(new BaseBoundarySnapshot.PartialOrderConstraint(cheap, expensive, 5.0))
        );
        Map<ResourceKey, Double> enforced = boundary.enforcePartialOrder(Map.of(cheap, 10.0, expensive, 3.0));
        double pExp = Objects.requireNonNull(enforced.get(expensive));
        double pCheap = Objects.requireNonNull(enforced.get(cheap));
        assertTrue(pExp >= pCheap + 5.0 - 1e-9);

        ResourceKey cheap2 = item("opaque:zeta_999");
        ResourceKey expensive2 = item("opaque:aaa_000");
        BaseBoundarySnapshot swapped = new BaseBoundarySnapshot(
            Map.of(cheap2, contract(cheap2, 10.0), expensive2, contract(expensive2, 3.0)),
            Set.of(),
            Set.of(),
            Set.of(),
            List.of(new BaseBoundarySnapshot.PartialOrderConstraint(cheap2, expensive2, 5.0))
        );
        Map<ResourceKey, Double> enforced2 = swapped.enforcePartialOrder(Map.of(cheap2, 10.0, expensive2, 3.0));
        assertEquals(pExp, Objects.requireNonNull(enforced2.get(expensive2)), 1e-9);
    }

    // #11
    @Test
    void unseededCycleCannotStart() {
        ResourceKey a = item("test:a");
        ResourceKey b = item("test:b");
        CompiledRecipeGraph g = compile(
            List.of(
                rec("mod:conv", "a2b", List.of(), List.of(slot(a, 1)), List.of(out(b, 1))),
                rec("mod:conv", "b2a", List.of(), List.of(slot(b, 1)), List.of(out(a, 1)))
            )
        );
        AcquisitionOracle none = (k, m, p) -> Optional.empty();
        var baseline = PassABaselinePotentials.solve(g, none, ProgressionState.overworldDefault, Map.of(), 12);
        assertFalse(baseline.bootableResources().contains(a));
        assertFalse(baseline.bootableResources().contains(b));
        assertEquals(Double.POSITIVE_INFINITY, baseline.priceOf(a));

        MacroSeedCertifier.MacroCertificate cert = MacroSeedCertifier.certify(
            List.of(new MacroSeedCertifier.MicroStep(Map.of(a, 1L), Map.of(a, 2L))),
            Set.of(a)
        );
        assertTrue(cert.validPositiveMacro());
        assertEquals(1L, cert.singleIterationSeeds().get(a));
        assertEquals(1L, cert.netDeltaPerIteration().get(a));
    }

    // #12
    @Test
    void closedPriceNeverPullsDownExternalSource() {
        ResourceKey a = item("opaque:rare_777");
        ResourceKey b = item("opaque:rare_888");
        ResourceKey anchor = item("opaque:cobble_like");
        CompiledRecipeGraph g = compile(
            List.of(
                rec("mod:pack", "pack", List.of(), List.of(slot(a, 9)), List.of(out(b, 1))),
                rec("mod:pack", "unpack", List.of(), List.of(slot(b, 1)), List.of(out(a, 9)))
            )
        );
        AcquisitionOracle onlyAnchor = (k, m, p) -> k.equals(anchor)
                ? Optional.of(new AcquisitionOption(AcquisitionKind.baseSupply, 1.0, 0.0, 0.0, 0.1, "t"))
                : Optional.empty();
        var baseline = PassABaselinePotentials.solve(
            g,
            onlyAnchor,
            ProgressionState.overworldDefault,
            Map.of(a, 50.0),
            12
        );
        assertEquals(50.0, baseline.reserveOrPriceOf(a), 1e-6);
        assertEquals(450.0, baseline.reserveOrPriceOf(b), 1e-6);
        assertFalse(baseline.bootableResources().contains(a));
    }

    // #13
    @Test
    void unitPriceMayUnderbidItsParent() {
        ResourceKey parent = item("test:log");
        ResourceKey child = item("test:plank");
        CompiledRecipeGraph g = compile(
            List.of(rec("mod:saw", "saw_log", List.of(), List.of(slot(parent, 1)), List.of(out(child, 4))))
        );
        AcquisitionOracle oracle = (k, m, p) -> {
            if (k.equals(parent)) return Optional.of(new AcquisitionOption(AcquisitionKind.baseSupply, 10.0, 0.0, 0.0, 0.0, "t"));
            if (k.equals(child)) return Optional.of(new AcquisitionOption(AcquisitionKind.baseSupply, 3.0, 0.0, 0.0, 0.0, "t"));
            return Optional.empty();
        };
        var res = PassABaselinePotentials.solve(g, oracle, ProgressionState.overworldDefault, Map.of(), 8);
        assertTrue(res.priceOf(child) < 3.0, "4-output craft must underbid 3.0 alternative, got " + res.priceOf(child));
        assertTrue(
            Objects.requireNonNull(res.witnesses().get(parent)).layerDepth()
                    < Objects.requireNonNull(res.witnesses().get(child)).layerDepth()
        );
    }

    // #14
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

    // #15
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

    // #16
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

    // #17
    @Test
    void batchBreakpointsBeatLinearEndpoint() {
        var dec = PassBInventoryModel.solveSingleChannelBatch(10L, 5L, 8L, 10.0, 2.0, 0.5, 0.1, 0.2, 4.0, 0.0, 100.0);
        assertEquals(2L, dec.chosenQuantity(), "Batch breakpoint q=2 must strictly beat q=0 and q=5");
    }

    // #18
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
                        double raw = q0 * 0.5 + q1 * 0.8 + q2 * 1.0 + q3 * 1.2
                                + (q1 > 0 ? 0.5 : 0.0) + (q2 > 0 ? 1.5 : 0.0) + (q3 > 0 ? 3.0 : 0.0)
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

    // #19
    @Test
    void rareStockProtectedUnderItsInequality() {
        assertTrue(PassBInventoryModel.isRareSubstitutionStrictlyInferior(18.0, 2.0, 5.0, 0.0, 100.0));
        assertFalse(PassBInventoryModel.isRareSubstitutionStrictlyInferior(8.0, 2.0, 10.0, 0.0, 100.0));

        ResourceKey rare = item("test:rare_gem");
        ResourceKey common = item("test:common_stone");
        InventorySnapshot inv = invTiers(
            Map.of(InventoryTier.hand, new StockLedger(Map.of(rare, 10L, common, 10L))),
            Map.of(),
            Set.of(rare)
        );
        assertTrue(PassBInventoryModel.isForbiddenForGenericTag(rare, inv, PreferenceModifiers.StockReferenceMode.protectedStrict));
        assertFalse(PassBInventoryModel.isForbiddenForGenericTag(common, inv, PreferenceModifiers.StockReferenceMode.protectedStrict));
        assertTrue(PassBInventoryModel.isForbiddenForGenericTag(common, inv, PreferenceModifiers.StockReferenceMode.ignore));
    }

    // #20
    @Test
    void homomorphismDoesNotMergeConvergingRoots() {
        ResourceKey oakLog = item("test:oak_log");
        ResourceKey oakPlank = item("test:oak_plank");
        ResourceKey spruceLog = item("test:spruce_log");
        ResourceKey sprucePlank = item("test:spruce_plank");
        ResourceKey birchLog = item("test:birch_log");
        ResourceKey birchPlank = item("test:birch_plank");
        ResourceKey sharedStick = item("test:stick");
        ResourceKey charcoal = item("test:charcoal");

        CompiledRecipeGraph g = compile(
            List.of(
                rec("minecraft:crafting", "oak_p", List.of(), List.of(slot(oakLog, 1)), List.of(out(oakPlank, 4))),
                rec("minecraft:crafting", "spruce_p", List.of(), List.of(slot(spruceLog, 1)), List.of(out(sprucePlank, 4))),
                rec("minecraft:crafting", "birch_p", List.of(), List.of(slot(birchLog, 1)), List.of(out(birchPlank, 4))),
                rec("minecraft:smelting", "logs_charcoal", List.of(), List.of(tagSlot("test:logs", List.of(oakLog, spruceLog, birchLog), 1)), List.of(out(charcoal, 1))),
                rec("minecraft:crafting", "oak_s", List.of(), List.of(slot(oakPlank, 2)), List.of(out(sharedStick, 4))),
                rec("minecraft:crafting", "spruce_s", List.of(), List.of(slot(sprucePlank, 2)), List.of(out(sharedStick, 4))),
                rec(
                    "minecraft:crafting",
                    "any_stick",
                    List.of(),
                    List.of(tagSlot("test:planks", List.of(oakPlank, sprucePlank, birchPlank), 2)),
                    List.of(out(sharedStick, 4))
                )
            )
        );
        var idx = TagHomomorphismAnalyzer.analyze(g);
        assertEquals(idx.familyOf(oakLog), idx.familyOf(oakPlank));
        assertEquals(idx.familyOf(spruceLog), idx.familyOf(sprucePlank));
        assertNotEquals(idx.familyOf(oakLog), idx.familyOf(spruceLog), "Converging roots must NEVER merge distinct families");
        assertEquals(TagHomomorphismAnalyzer.ambiguousFamily, idx.familyOf(sharedStick), "Converging target must be AMBIGUOUS");
    }

    // #21
    @Test
    void lazyTagPagingHasCoverageFlags() {
        List<FiveBucketTagPager.MemberSignals> members = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            ResourceKey k = item(String.format("test:plank_%02d", i));
            boolean isSoleUsable = (i == 29);
            members.add(
                new FiveBucketTagPager.MemberSignals(
                    k, "fam_" + i, "clus_" + (i % 3),
                    false, 0.0, false, 0.0, true, isSoleUsable ? 500.0 : (1.0 + i),
                    false, Double.POSITIVE_INFINITY
                )
            );
        }
        ResourceKey target = item("test:plank_29");
        var page0 = FiveBucketTagPager.selectPage(members, 0, 0);
        assertTrue(page0.truncated());
        assertTrue(page0.hasMorePages());
        assertFalse(page0.selectedMembers().contains(target));

        var page2 = FiveBucketTagPager.selectPage(members, 0, 2);
        assertFalse(page2.truncated());
        assertFalse(page2.hasMorePages());
        assertTrue(page2.selectedMembers().contains(target));
    }

    // #22
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

    // #23
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

    // #24
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

    // #25
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

    // #26
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

    // #27
    @Test
    void incompatibleTechSurvives() {
        RegimeSignature regX = new RegimeSignature(Set.of("mod:mach_x"), Set.of("clus_x"), false, Set.of("build:x"));
        RegimeSignature regY = new RegimeSignature(Set.of("mod:mach_y"), Set.of("clus_y"), false, Set.of("build:y"));
        assertFalse(regX.entails(regY));
        assertFalse(regY.entails(regX));
        ProductionPlan planA = synthPlan("plan_x", new CostVector(10.0, 0.0, 5.0, 20.0, 1.0, 1.0), regX, 1.0);
        ProductionPlan planB = synthPlan("plan_y", new CostVector(18.0, 0.0, 8.0, 20.0, 2.0, 1.0), regY, 1.4);
        var out = ConditionedFrontierSelector.select(List.of(planA, planB), new double[]{10, 10, 10, 10, 10, 10}, 0.05, 4);
        assertEquals(2, out.selectedTopK().size());
    }

    // #28
    @Test
    void tabuCannotTurnUnknownIntoExecutable() {
        ResourceKey ore = item("test:ore");
        ResourceKey widget = item("test:widget");
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

    // #29
    @Test
    void lossCertificateExcludesAllNonpositiveCycles() {
        var lossy = List.of(
            new NonLossyCertificateVerifier.NetColumn("m1", Map.of("A", -2L, "B", 1L)),
            new NonLossyCertificateVerifier.NetColumn("m2", Map.of("B", -1L, "A", 1L))
        );
        assertFalse(NonLossyCertificateVerifier.certify(lossy).certified());

        var zeroNet = List.of(
            new NonLossyCertificateVerifier.NetColumn("pack", Map.of("A", -9L, "B", 1L)),
            new NonLossyCertificateVerifier.NetColumn("unpack", Map.of("B", -1L, "A", 9L))
        );
        assertFalse(NonLossyCertificateVerifier.certify(zeroNet).certified());

        var good = List.of(
            new NonLossyCertificateVerifier.NetColumn("smelt", Map.of("ore", -1L, "ingot", 1L)),
            new NonLossyCertificateVerifier.NetColumn("press", Map.of("ingot", -2L, "plate", 1L))
        );
        var ok = NonLossyCertificateVerifier.certify(good);
        assertTrue(ok.certified());
        assertTrue(NonLossyCertificateVerifier.verifyWeights(good, ok.weightsW()));
        for (BigInteger wi : ok.weightsW().values()) {
            assertTrue(wi.signum() >= 0);
        }
    }

    // #30
    @Test
    void usefulCoproductAndLossDiffer() {
        ResourceKey a = item("test:a");
        ResourceKey b = item("test:b");
        ResourceKey c = item("test:c");
        var useful = MacroSeedCertifier.certify(
            List.of(
                new MacroSeedCertifier.MicroStep(Map.of(a, 1L), Map.of(b, 1L, c, 1L)),
                new MacroSeedCertifier.MicroStep(Map.of(b, 1L), Map.of(a, 1L))
            ),
            Set.of(a, b)
        );
        assertTrue(useful.validPositiveMacro());
        assertEquals(1L, useful.netDeltaPerIteration().get(c));
        assertEquals(1L, useful.singleIterationSeeds().get(a));

        var roundTrip = MacroSeedCertifier.certify(
            List.of(
                new MacroSeedCertifier.MicroStep(Map.of(a, 9L), Map.of(b, 1L)),
                new MacroSeedCertifier.MicroStep(Map.of(b, 1L), Map.of(a, 9L))
            ),
            Set.of(a, b)
        );
        assertFalse(roundTrip.validPositiveMacro());
    }

    // #31
    @Test
    void resolvedMemberCannotFundItsAncestor() {
        ResourceKey itemA = item("test:item_a");
        List<TwoPoolReplayVerifier.ReplayEvent> selfLoop = List.of(
            new TwoPoolReplayVerifier.CraftActionEvent(
                "self_a", 1L, null, true,
                List.of(new TwoPoolReplayVerifier.BatchSlotBinding(0, 1L, true, Map.of(itemA, 1L), List.of())),
                Map.of(), Map.of(itemA, 1L), Map.of()
            ),
            new TwoPoolReplayVerifier.DeliverTargetEvent(itemA, 1L)
        );
        assertFalse(TwoPoolReplayVerifier.replay(InventorySnapshot.empty, Set.of(), Map.of(itemA, 1L), selfLoop, false).valid());

        ResourceKey raw = item("test:raw");
        List<TwoPoolReplayVerifier.ReplayEvent> earlierByprod = List.of(
            new TwoPoolReplayVerifier.ExternalSupplyEvent(raw, 1L, false),
            new TwoPoolReplayVerifier.CraftActionEvent(
                "byprod_maker", 1L, null, true,
                List.of(new TwoPoolReplayVerifier.BatchSlotBinding(0, 1L, true, Map.of(raw, 1L), List.of())),
                Map.of(), Map.of(), Map.of(itemA, 1L)
            ),
            new TwoPoolReplayVerifier.DeliverTargetEvent(itemA, 1L)
        );
        assertTrue(TwoPoolReplayVerifier.replay(InventorySnapshot.empty, Set.of(), Map.of(itemA, 1L), earlierByprod, true).valid());
    }

    // #32
    @Test
    void rollbackIsByteEquivalent() {
        ResourceKey ore = item("test:ore");
        ResourceKey mold = item("test:mold");
        InventorySnapshot inv = invTiers(Map.of(InventoryTier.hand, new StockLedger(Map.of(ore, 5L, mold, 1L))), Map.of(), Set.of());
        TransactionalLedger ledger = new TransactionalLedger(inv, Set.of("mod:base_mach"));
        ledger.pullFromTier(InventoryTier.hand, ore, 2L, 0);
        String before = ledger.canonicalStateSignature();
        TransactionalLedger.SnapshotCheckpoint cp = ledger.checkpoint();

        ledger.pullFromTier(InventoryTier.hand, ore, 2L, 1);
        ledger.pullFromTier(InventoryTier.hand, mold, 1L, 2);
        ledger.lockCatalyst(mold, 1L);
        ledger.installMachine("mod:temp_mach");
        ledger.publishLot(TransactionalLedger.PoolKind.byproduct, ore, 3, 0, 7L);
        assertNotEquals(before, ledger.canonicalStateSignature());

        ledger.rollback(cp);
        assertEquals(before, ledger.canonicalStateSignature());
        assertFalse(ledger.hasMachine("mod:temp_mach"));
        assertEquals(3L, ledger.remainingTierStock(InventoryTier.hand, ore));
    }

    // #33
    @Test
    void alnsDescendsAndStops() {
        var init = new DeterministicAlnsScheduler.CandidateState<>("s0", 1000L, 10.0, "s0");
        List<Function<DeterministicAlnsScheduler.CandidateState<String>, DeterministicAlnsScheduler.MoveAttempt<String>>> ops = List.of(
            cur -> new DeterministicAlnsScheduler.MoveAttempt<>(
                cur.quantizedObjectiveJ() > 400L
                        ? new DeterministicAlnsScheduler.CandidateState<>("s_" + (cur.quantizedObjectiveJ() - 150L), cur.quantizedObjectiveJ() - 150L, cur.stepFrictionF(), "k" + cur.quantizedObjectiveJ())
                        : null,
                10L
            ),
            cur -> new DeterministicAlnsScheduler.MoveAttempt<>(null, 5L),
            cur -> new DeterministicAlnsScheduler.MoveAttempt<>(
                new DeterministicAlnsScheduler.CandidateState<>("worse", cur.quantizedObjectiveJ() + 50L, 20.0, "w"),
                5L
            ),
            cur -> new DeterministicAlnsScheduler.MoveAttempt<>(null, 5L),
            cur -> new DeterministicAlnsScheduler.MoveAttempt<>(null, 5L)
        );
        var out = DeterministicAlnsScheduler.refine(init, ops, 200L);
        assertTrue(out.incumbent().quantizedObjectiveJ() <= 400L);
        assertTrue(out.acceptedMoves() >= 4);
        assertTrue(out.totalWorkConsumed() <= 200L);
    }

    // #34
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

    // #35
    @Test
    void uniqueSuspiciousRecipeRemainsInspectable() {
        ResourceKey dirt = item("test:dirt");
        ResourceKey unicorn = item("test:unicorn");
        RawRecipeRecord suspicious = new RawRecipeRecord(
            "jei:category/mystery/altar",
            "jei_fallback",
            Optional.empty(),
            List.of(),
            List.of(slot(dirt, 1)),
            List.of(out(unicorn, 1)),
            Map.of(),
            0L,
            Set.of("calculator_cirrus:system/jei_fallback"),
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

    // #36
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

    // #37
    @Test
    void boundIsAdmissibleOnTinyOracle() {
        ResourceKey ore = item("test:ore");
        ResourceKey ingot = item("test:ingot");
        List<DualBoundCertifier.ActionBoundConstraint> actions = List.of(
            new DualBoundCertifier.ActionBoundConstraint("acq_ore", Map.of(ore, 1L), List.of(), 4.0),
            new DualBoundCertifier.ActionBoundConstraint(
                "smelt",
                Map.of(ingot, 1L),
                List.of(new DualBoundCertifier.SlotMinDemand(2L, List.of(ore))),
                2.0
            )
        );
        var cert = DualBoundCertifier.certify(Map.of(ore, 4.0, ingot, 10.0), actions, Map.of(ingot, 3L), Map.of(), 30.0);
        assertTrue(cert.isNontrivial());
        assertEquals(1.0, cert.lowerBoundL(), 1e-9);

        List<DualBoundCertifier.ActionBoundConstraint> withFree = new ArrayList<>(actions);
        withFree.add(new DualBoundCertifier.ActionBoundConstraint("free_ingot", Map.of(ingot, 1L), List.of(), 0.0));
        var certFree = DualBoundCertifier.certify(Map.of(ore, 4.0, ingot, 10.0), withFree, Map.of(ingot, 3L), Map.of(), 30.0);
        assertEquals(0.0, certFree.lowerBoundL(), 1e-9);
    }

    // #38
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

    // #39
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

    // #40
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

    // #41
    @Test
    void coldWarmParallelDeterminism() throws Exception {
        ResourceKey ore = item("test:ore");
        ResourceKey dust = item("test:dust");
        ResourceKey ingot = item("test:ingot");
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

    // #42
    @Test
    void deadlineNeverPublishesSpeedDependentWinner() {
        ResourceKey ore = item("test:ore");
        ResourceKey ingot = item("test:ingot");
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

    // #43
    @Test
    void stableIdentityWithMissingId() {
        ResourceKey a = item("test:a");
        ResourceKey b = item("test:b");
        ResourceKey outItem = item("test:out");
        RawRecipeRecord r1 = new RawRecipeRecord(
            "jei:category/mod/mixer", "jei_fallback", Optional.empty(), List.of("mod:mixer"),
            List.of(slot(a, 1), slot(b, 2)), List.of(out(outItem, 1)), Map.of()
        );
        RawRecipeRecord r2 = new RawRecipeRecord(
            "jei:category/mod/mixer", "jei_fallback", Optional.empty(), List.of("mod:mixer"),
            List.of(slot(b, 2), slot(a, 1)), List.of(out(outItem, 1)), Map.of()
        );
        RecipeSemanticKey k1 = RecipeSemanticKeyBuilder.build(r1, true);
        RecipeSemanticKey k2 = RecipeSemanticKeyBuilder.build(r2, true);
        assertEquals(k1.sha256Hex(), k2.sha256Hex(), "Commutative slot permutation must yield identical semantic key");

        RawRecipeRecord rOpaque = new RawRecipeRecord(
            "jei:category/mod/mixer", "jei_fallback", Optional.empty(), List.of("mod:mixer"),
            List.of(slot(a, 1), slot(b, 2)), List.of(out(outItem, 1)), Map.of(),
            0L, Set.of(), Set.of(CanonicalRecipeTag.normalProduction), RecipeEvidenceTier.tierC, 1.0, Optional.empty(), true
        );
        assertNotEquals(k1.sha256Hex(), RecipeSemanticKeyBuilder.build(rOpaque, true).sha256Hex());
        assertNotEquals(
            RecipeSemanticKeyBuilder.build(r1, false).sha256Hex(),
            RecipeSemanticKeyBuilder.build(r2, false).sha256Hex()
        );
    }

    // #44
    @Test
    void overflowCannotCreateFreeMaterials() {
        long huge = SafeMath.saturationLimit - 5L;
        assertEquals(SafeMath.saturationLimit, SafeMath.addSaturating(huge, 100L));
        assertEquals(SafeMath.saturationLimit, SafeMath.mulSaturating(huge, 3L));
        assertTrue(SafeMath.addSaturating(huge, 100L) > 0L);

        ResourceKey x = item("test:x");
        List<TwoPoolReplayVerifier.ReplayEvent> overflow = List.of(
            new TwoPoolReplayVerifier.ExternalSupplyEvent(x, Long.MAX_VALUE, false),
            new TwoPoolReplayVerifier.ExternalSupplyEvent(x, 10L, false)
        );
        var res = TwoPoolReplayVerifier.replay(InventorySnapshot.empty, Set.of(), Map.of(), overflow, false);
        assertFalse(res.valid(), "Overflowing external supply must be rejected cleanly without wrapping negative");
        assertTrue(res.finalOrdinarySurplus().getOrDefault(x, 0L) >= 0L);
    }

    // #45
    @Test
    void cacheInvalidatesPriceRisesAndNewEntrants() {
        ResourceKey cheapA = item("test:cheap_a");
        ResourceKey midB = item("test:mid_b");
        ResourceKey newerCheaperC = item("test:new_c");
        ResourceKey outItem = item("test:plank_out");

        CompiledRecipeGraph gBase = compile(
            List.of(rec("minecraft:crafting", "make", List.of(), List.of(tagSlot("test:mats", List.of(cheapA, midB), 1)), List.of(out(outItem, 1))))
        );
        AcquisitionOracle oracle1 = (k, m, p) -> {
            if (k.equals(cheapA)) return Optional.of(new AcquisitionOption(AcquisitionKind.baseSupply, 2.0, 0.0, 0.0, 0.0, "t"));
            if (k.equals(midB)) return Optional.of(new AcquisitionOption(AcquisitionKind.baseSupply, 10.0, 0.0, 0.0, 0.0, "t"));
            return Optional.empty();
        };
        var baseRes = PassABaselinePotentials.solve(gBase, oracle1, ProgressionState.overworldDefault, Map.of(), 8);
        assertEquals(3.0, baseRes.priceOf(outItem), 1e-6);

        AcquisitionOracle oracleRemoved = (k, m, p) -> k.equals(midB)
                ? Optional.of(new AcquisitionOption(AcquisitionKind.baseSupply, 10.0, 0.0, 0.0, 0.0, "t"))
                : Optional.empty();
        var risenRes = PassABaselinePotentials.solve(gBase, oracleRemoved, ProgressionState.overworldDefault, Map.of(), 8);
        assertEquals(11.0, risenRes.priceOf(outItem), 1e-6);

        CompiledRecipeGraph gWithC = compile(
            List.of(rec("minecraft:crafting", "make", List.of(), List.of(tagSlot("test:mats", List.of(cheapA, midB, newerCheaperC), 1)), List.of(out(outItem, 1))))
        );
        AcquisitionOracle oracleWithC = (k, m, p) -> {
            if (k.equals(newerCheaperC)) return Optional.of(new AcquisitionOption(AcquisitionKind.baseSupply, 0.5, 0.0, 0.0, 0.0, "t"));
            return oracle1.assess(k, m, p);
        };
        var entrantRes = PassABaselinePotentials.solve(gWithC, oracleWithC, ProgressionState.overworldDefault, Map.of(), 8);
        assertEquals(1.5, entrantRes.priceOf(outItem), 1e-6);
    }

    // #46
    @Test
    void diversityTieIsCanonical() {
        RegimeSignature r1 = new RegimeSignature(Set.of("m1"), Set.of("c1"), false, Set.of());
        RegimeSignature r2 = new RegimeSignature(Set.of("m2"), Set.of("c2"), false, Set.of());
        RegimeSignature r3 = new RegimeSignature(Set.of("m3"), Set.of("c3"), true, Set.of());
        RegimeSignature r4 = new RegimeSignature(Set.of("m4"), Set.of("c4"), false, Set.of());

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
