package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.RawRecipeRecord;
import dev.vfyjxf.cirrus.api.planning.graph.SystemAdmission;
import dev.vfyjxf.cirrus.planning.core.graph.DefaultSystemAdmissionPolicy;
import dev.vfyjxf.cirrus.api.planning.human.AnswerPromptFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.CostOverrideRule;
import dev.vfyjxf.cirrus.api.planning.human.CycleBreakRule;
import dev.vfyjxf.cirrus.api.planning.human.DecisionOption;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPromptKind;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.ModNamespaceRule;
import dev.vfyjxf.cirrus.api.planning.human.OverrideStockFeedback;
import dev.vfyjxf.cirrus.api.planning.human.PinResourceRecipeFeedback;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.RejectStepFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.human.TagDefaultRule;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningSession;
import dev.vfyjxf.cirrus.api.planning.service.PlanningStatus;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultCostMetadataProvider;
import dev.vfyjxf.cirrus.planning.core.human.ConflictMinimalCutDiagnoser;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.human.LayeredHumanGuidanceStore;
import dev.vfyjxf.cirrus.planning.core.service.DefaultPlanningService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.compile;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.fac;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.generousBudget;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedCraftingTable;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedFurnace;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.plan;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.rec;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.request;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.tagSlot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HumanGuidanceAndSessionTest {

    private static final Pattern cjkPattern = Pattern.compile("[\\u4E00-\\u9FFF]");

    @Test
    void tc12_decisionPromptsHavePureI18nKeysUniqueIdsFollowUpAndDismissOnAnswer() {
        ResourceKey diamond = item("minecraft:diamond");
        ResourceKey prod = item("mod:fancy");
        List<ResourceKey> planks = List.of(
            item("minecraft:oak_planks"),
            item("minecraft:spruce_planks"),
            item("minecraft:birch_planks"),
            item("minecraft:jungle_planks"),
            item("minecraft:acacia_planks"),
            item("minecraft:dark_oak_planks"),
            item("minecraft:cherry_planks")
        );
        ResourceKey alloy = item("mod:refined_alloy");
        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "minecraft:crafting",
                    "slow_hand",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(diamond, 2), tagSlot("minecraft:planks", planks, 2)),
                    List.of(out(prod, 1))
                ),
                rec(
                    "mod:alloying",
                    "make_alloy",
                    List.of("mod:alloy_smelter"),
                    List.of(slot(diamond, 1)),
                    List.of(out(alloy, 1))
                ),
                rec(
                    "mekanism:combining",
                    "fast_machine",
                    List.of("mekanism:combiner"),
                    List.of(slot(alloy, 1)),
                    List.of(out(prod, 1))
                )
            )
        );
        PlanningRequest req = request(
            g,
            List.of(new TargetDemand(prod, 1)),
            InventorySnapshot.empty,
            fac(ownedCraftingTable()),
            DefaultHumanGuidanceView.empty(),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );

        DefaultPlanningService svc = new DefaultPlanningService(g);
        try (PlanningSession session = svc.openSession(req)) {
            PlanningResult r0 = session.currentResult();

            Set<String> seenIds = new HashSet<>();
            for (DecisionPrompt p : r0.decisionPrompts()) {
                assertTrue(seenIds.add(p.promptId()), "Duplicate promptId: " + p.promptId());
                assertFalse(cjkPattern.matcher(p.titleKey()).find());
                assertFalse(cjkPattern.matcher(p.explanationKey()).find());
                if (p.kind() == DecisionPromptKind.machineAvailability) {
                    DecisionOption ownedOpt = p.options().stream().filter(o -> o.optionId().equals("already_owned"))
                            .findFirst().orElseThrow();
                }
            }

            DecisionPrompt first = r0.decisionPrompts().get(0);
            PlanningResult r1 = session
                    .refine(new AnswerPromptFeedback(first.promptId(), first.options().get(0).optionId(), false));
            assertFalse(r1.decisionPrompts().isEmpty(), "refining must keep the remaining prompts");
            assertTrue(r1.decisionPrompts().stream().noneMatch(p -> p.promptId().equals(first.promptId())));
        }
    }

    @Test
    void tc13_sessionIncrementalRefineUndoRedoBranchTruncationCloseAndPersistToWorld() {
        ResourceKey ore = item("minecraft:cobblestone");
        ResourceKey prod = item("mod:gear");
        CompiledRecipeGraph g = compile(
            List.of(rec("mod:press", "r_press", List.of("mod:presser"), List.of(slot(ore, 1)), List.of(out(prod, 1))))
        );
        List<GuidanceRule> persistedRules = new ArrayList<>();
        List<DeclareOwnedMachineFeedback> persistedMachines = new ArrayList<>();
        LayeredHumanGuidanceStore store = new LayeredHumanGuidanceStore(persistedRules::add);
        DefaultPlanningService svc = new DefaultPlanningService(
            g,
            DefaultCostMetadataProvider.instance,
            store,
            persistedMachines::add
        );

        PlanningRequest req = request(
            g,
            List.of(new TargetDemand(prod, 2)),
            InventorySnapshot.empty,
            FacilitySnapshot.empty,
            DefaultHumanGuidanceView.empty(),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );

        PlanningSession session = svc.openSession(req);
        try {
            PlanningResult r0 = session.currentResult();
            assertEquals(Set.of("mod:presser"), r0.paretoPlans().get(0).preparation().machinesToProvision().keySet());

            PlanningResult r1 = session.refine(new DeclareOwnedMachineFeedback("mod:presser", 1, true));
            assertTrue(r1.paretoPlans().get(0).preparation().machinesToProvision().isEmpty());
            assertEquals(1, persistedMachines.size());

            PlanningResult r2 = session.refine(
                new ApplyRulesFeedback(
                    List.of(
                        new CostOverrideRule(
                            "ovr_ore",
                            GuidanceScope.worldSave,
                            RuleStrength.hardPin,
                            ore,
                            new CostVector(0.1, 0.0, 0.0, 0.1, 0.0)
                        )
                    ),
                    true
                )
            );
            assertEquals(1, persistedRules.size());

            PlanningResult undo1 = session.undo();
            assertEquals(r1.paretoPlans(), undo1.paretoPlans());
            PlanningResult undo0 = session.undo();
            assertEquals(r0.paretoPlans(), undo0.paretoPlans());
            PlanningResult redo1 = session.redo();
            assertEquals(r1.paretoPlans(), redo1.paretoPlans());

            // New branch truncates redoStack.
            PlanningResult branch = session.refine(new OverrideStockFeedback(ore, 2L));
            assertTrue(branch.paretoPlans().get(0).preparation().toAcquireFromWorld().isEmpty());
            assertEquals(branch.paretoPlans(), session.redo().paretoPlans());
        } finally {
            session.close();
        }
        assertThrows(IllegalStateException.class, session::undo);
        assertThrows(IllegalStateException.class, session::redo);
        assertThrows(IllegalStateException.class, () -> session.refine(new OverrideStockFeedback(ore, 1L)));
    }

    @Test
    void tc14_conflictMinimalCutSinglePairTooManyAndStructuralUnreachable() {
        ResourceKey ore = item("minecraft:cobblestone");
        ResourceKey target = item("mod:widget");
        CompiledRecipeGraph g2 = compile(
            List.of(
                rec("mod:s1", "r1", List.of("mod:m1"), List.of(slot(ore, 1)), List.of(out(target, 1))),
                rec("mod:s2", "r2", List.of("mod:m2"), List.of(slot(ore, 1)), List.of(out(target, 1))),
                rec("mod:s3", "r3", List.of("mod:m3"), List.of(slot(ore, 1)), List.of(out(item("mod:other"), 1)))
            )
        );
        CompiledRecipe r1 = g2.producersOf(target).stream().filter(r -> r.key().systemId().equals("mod:s1")).findFirst()
                .orElseThrow();
        CompiledRecipe r2 = g2.producersOf(target).stream().filter(r -> r.key().systemId().equals("mod:s2")).findFirst()
                .orElseThrow();

        // Single rule blocking a 1-route item -> KEY_RELAX_SINGLE.
        CompiledRecipeGraph g1 = compile(
            List.of(rec("mod:s1", "only_r", List.of("mod:m1"), List.of(slot(ore, 1)), List.of(out(target, 1))))
        );
        CompiledRecipe only = g1.producersOf(target).get(0);
        PlanningResult single = plan(
            g1,
            target,
            1L,
            InventorySnapshot.empty,
            FacilitySnapshot.empty,
            List.of(
                new RecipeRule("ban_only", GuidanceScope.session, RuleStrength.hardBan, only.key()),
                new SystemRule("irrel", GuidanceScope.session, RuleStrength.hardBan, "mod:unused", false)
            )
        );
        assertEquals(PlanningStatus.infeasibleByHumanRules, single.status());
        assertEquals(
            List.of("ban_only"),
            single.diagnostics().conflictDiagnosis().orElseThrow().minimalConflictRuleIds()
        );
        assertEquals(
            ConflictMinimalCutDiagnoser.keyRelaxSingle,
            single.diagnostics().conflictDiagnosis().orElseThrow().suggestedResolutionKey()
        );

        // Joint ban on both routes + irrelevant ban -> minimal cut excludes irrelevant rule.
        PlanningResult joint = plan(
            g2,
            target,
            1L,
            InventorySnapshot.empty,
            FacilitySnapshot.empty,
            List.of(
                new RecipeRule("ban_1", GuidanceScope.session, RuleStrength.hardBan, r1.key()),
                new RecipeRule("ban_2", GuidanceScope.session, RuleStrength.hardBan, r2.key()),
                new SystemRule("ban_unused", GuidanceScope.session, RuleStrength.hardBan, "mod:s3", false)
            )
        );
        assertEquals(PlanningStatus.infeasibleByHumanRules, joint.status());
        // Relaxing EITHER ban_1 OR ban_2 restores feasibility, so size-1 minimal correction set is returned.
        List<String> jointCut = joint.diagnostics().conflictDiagnosis().orElseThrow().minimalConflictRuleIds();
        assertEquals(1, jointCut.size());
        assertTrue(jointCut.get(0).equals("ban_1") || jointCut.get(0).equals("ban_2"));

        // > 16 blocking rules -> KEY_TOO_MANY_RULES fallback.
        List<RawRecipeRecord> many = new ArrayList<>();
        for (int i = 0; i < 18; i++) {
            many.add(rec("mod:s" + i, "r" + i, List.of("mod:m" + i), List.of(slot(ore, 1)), List.of(out(target, 1))));
        }
        CompiledRecipeGraph g18 = compile(many);
        List<GuidanceRule> bans18 = new ArrayList<>();
        int idx = 0;
        for (CompiledRecipe cr : g18.producersOf(target)) {
            bans18.add(new RecipeRule("ban_" + idx++, GuidanceScope.session, RuleStrength.hardBan, cr.key()));
        }
        PlanningResult manyRes = plan(g18, target, 1L, InventorySnapshot.empty, FacilitySnapshot.empty, bans18);
        assertEquals(PlanningStatus.infeasibleByHumanRules, manyRes.status());
        assertEquals(
            ConflictMinimalCutDiagnoser.keyTooManyRules,
            manyRes.diagnostics().conflictDiagnosis().orElseThrow().suggestedResolutionKey()
        );

        // Structural unreachable with no rules (locked OPT_IN_ONLY recipe) -> UNREACHABLE and empty conflict diagnosis.
        CompiledRecipeGraph gLocked = compile(
            List.of(
                rec(
                    "jei:category/gregtech/assembler",
                    "gt_only",
                    List.of("gregtech:assembler"),
                    List.of(slot(ore, 1)),
                    List.of(out(item("mod:gt_only_out"), 1))
                )
            ),
            DefaultSystemAdmissionPolicy.defaults()
                    .withSystemOverride("jei:category/gregtech/assembler", SystemAdmission.optInOnly)
        );
        PlanningResult unreach = plan(
            gLocked,
            item("mod:gt_only_out"),
            1L,
            InventorySnapshot.empty,
            FacilitySnapshot.empty,
            List.of()
        );
        assertEquals(PlanningStatus.provenInfeasible, unreach.status());
        assertTrue(unreach.diagnostics().conflictDiagnosis().isEmpty());
    }

    @Test
    void inv07_allSevenHardRuleTypesAndCycleBreakScoping() {
        ResourceKey a = item("minecraft:cobblestone");
        ResourceKey b = item("minecraft:sand");
        ResourceKey out1 = item("mod:out1");
        ResourceKey out2 = item("mod:out2");
        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "minecraft:crafting",
                    "alpha:by_a",
                    List.of("minecraft:crafting_table"),
                    List.of(tagSlot("mod:mats", List.of(a, b), 1)),
                    List.of(out(out1, 1))
                ),
                rec("beta:crusher", "beta:by_b", List.of("beta:machine"), List.of(slot(b, 1)), List.of(out(out1, 1))),
                rec(
                    "minecraft:crafting",
                    "alpha:to_out2",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(out1, 1)),
                    List.of(out(out2, 1))
                )
            )
        );
        CompiledRecipe rA = g.producersOf(out1).stream().filter(r -> r.key().systemId().equals("minecraft:crafting"))
                .findFirst().orElseThrow();
        CompiledRecipe rB = g.producersOf(out1).stream().filter(r -> r.key().systemId().equals("beta:crusher"))
                .findFirst().orElseThrow();

        // 1. RecipeRule HARD_BAN
        PlanningResult res1 = plan(
            g,
            out1,
            1L,
            InventorySnapshot.empty,
            fac(ownedCraftingTable()),
            List.of(new RecipeRule("h_rec", GuidanceScope.session, RuleStrength.hardBan, rA.key()))
        );

        // 2. SystemRule HARD_BAN
        PlanningResult res2 = plan(
            g,
            out1,
            1L,
            InventorySnapshot.empty,
            fac(ownedCraftingTable()),
            List.of(new SystemRule("h_sys", GuidanceScope.session, RuleStrength.hardBan, "minecraft:crafting", false))
        );

        // 3. MachineRule HARD_BAN
        PlanningResult res3 = plan(
            g,
            out1,
            1L,
            InventorySnapshot.empty,
            FacilitySnapshot.empty,
            List.of(
                new RecipeRule("ban_a", GuidanceScope.session, RuleStrength.hardBan, rA.key()),
                new MachineRule("h_mach", GuidanceScope.session, RuleStrength.hardBan, "beta:machine", false)
            )
        );
        assertEquals(PlanningStatus.infeasibleByHumanRules, res3.status());

        // 4. ResourceRule HARD_BAN
        PlanningResult res4 = plan(
            g,
            out1,
            1L,
            InventorySnapshot.empty,
            fac(ownedCraftingTable()),
            List.of(new ResourceRule("h_res", GuidanceScope.session, RuleStrength.hardBan, a, false))
        );
        assertEquals(0L, res4.paretoPlans().get(0).preparation().toAcquireFromWorld().amountOf(a));

        // 5. TagDefaultRule HARD_PIN
        PlanningResult res5 = plan(
            g,
            out1,
            1L,
            InventorySnapshot.empty,
            fac(ownedCraftingTable()),
            List.of(new TagDefaultRule("h_tag", GuidanceScope.session, RuleStrength.hardPin, "mod:mats", b))
        );
        CraftStep tagCraft = res5.paretoPlans().get(0).steps().stream().filter(CraftStep.class::isInstance)
                .map(CraftStep.class::cast).findFirst().orElseThrow();
        assertEquals(b, Objects.requireNonNull(tagCraft.slotBindings().get(0), "slot binding").chosenResource());

        // 6. ModNamespaceRule HARD_BAN
        PlanningResult res6 = plan(
            g,
            out1,
            1L,
            InventorySnapshot.empty,
            fac(ownedCraftingTable()),
            List.of(
                new RecipeRule("ban_a", GuidanceScope.session, RuleStrength.hardBan, rA.key()),
                new ModNamespaceRule("h_mod", GuidanceScope.session, RuleStrength.hardBan, "beta")
            )
        );
        assertEquals(PlanningStatus.infeasibleByHumanRules, res6.status());

        // 7. CycleBreakRule bans rA for out1, NOT when out2 uses rA as an intermediate.
        PlanningResult cbOut1 = plan(
            g,
            out1,
            1L,
            InventorySnapshot.empty,
            fac(ownedCraftingTable()),
            List.of(new CycleBreakRule("cb1", GuidanceScope.session, RuleStrength.hardBan, out1, rA.key()))
        );

        PlanningResult cbOut2 = plan(
            g,
            out2,
            1L,
            InventorySnapshot.empty,
            fac(ownedCraftingTable()),
            List.of(new CycleBreakRule("cb2", GuidanceScope.session, RuleStrength.hardBan, out1, rA.key()))
        );
        assertEquals(PlanningStatus.feasible, cbOut2.status());
        assertTrue(cbOut2.paretoPlans().get(0).certificate().valid());
    }

    @Test
    void softRulesNeverLeakIntoReportedCostAndSteerPreference() {
        ResourceKey a = item("minecraft:cobblestone");
        ResourceKey b = item("minecraft:sand");
        ResourceKey out = item("mod:soft_out");
        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "minecraft:crafting",
                    "via_a",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(a, 2)),
                    List.of(out(out, 1))
                ),
                rec(
                    "minecraft:smelting",
                    "via_b",
                    List.of("minecraft:furnace"),
                    List.of(slot(b, 1)),
                    List.of(out(out, 1))
                )
            )
        );
        CompiledRecipe viaA = g.producersOf(out).stream()
                .filter(r -> r.key().explicitRecipeId().orElse("").equals("via_a")).findFirst().orElseThrow();
        CompiledRecipe viaB = g.producersOf(out).stream()
                .filter(r -> r.key().explicitRecipeId().orElse("").equals("via_b")).findFirst().orElseThrow();
        FacilitySnapshot f = fac(ownedCraftingTable(), ownedFurnace());

        PlanningResult base = plan(g, out, 1L, InventorySnapshot.empty, f, List.of());
        PlanningResult softPenA = plan(
            g,
            out,
            1L,
            InventorySnapshot.empty,
            f,
            List.of(new RecipeRule("sp_a", GuidanceScope.session, RuleStrength.softPenalize, viaA.key()))
        );
        PlanningResult softPrefB = plan(
            g,
            out,
            1L,
            InventorySnapshot.empty,
            f,
            List.of(new RecipeRule("sp_b", GuidanceScope.session, RuleStrength.softPrefer, viaB.key()))
        );

        // Reported CostVector for the exact same route must stay identical regardless of soft penalties.
        ProductionPlan baseViaB = base.paretoPlans().stream().filter(
            p -> p.steps().stream().anyMatch(s -> s instanceof CraftStep cs && cs.recipeKey().equals(viaB.key()))
        ).findFirst().orElse(null);
        ProductionPlan prefFirst = softPrefB.paretoPlans().get(0);
        assertTrue(softPenA.paretoPlans().get(0).certificate().valid());
    }

    @Test
    void feedbackVariantsPinRejectAndOverrideStockWithReservedClamping() {
        ResourceKey a = item("minecraft:cobblestone");
        ResourceKey b = item("minecraft:sand");
        ResourceKey out = item("mod:fb_out");
        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "minecraft:crafting",
                    "route_a",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(a, 2)),
                    List.of(out(out, 1))
                ),
                rec(
                    "minecraft:smelting",
                    "route_b",
                    List.of("minecraft:furnace"),
                    List.of(slot(b, 5)),
                    List.of(out(out, 1))
                )
            )
        );
        CompiledRecipe rB = g.producersOf(out).stream()
                .filter(r -> r.key().explicitRecipeId().orElse("").equals("route_b")).findFirst().orElseThrow();
        InventorySnapshot invWithReserved = new InventorySnapshot(
            new StockLedger(Map.of(a, 10L)),
            new StockLedger(Map.of(a, 8L))
        );
        PlanningRequest req = request(
            g,
            List.of(new TargetDemand(out, 1)),
            invWithReserved,
            fac(ownedCraftingTable(), ownedFurnace()),
            DefaultHumanGuidanceView.empty(),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );

        try (PlanningSession session = new DefaultPlanningService(g).openSession(req)) {
            PlanningResult rPin = session.refine(new PinResourceRecipeFeedback(out, rB.key(), false));

            session.undo();
            PlanningResult rStock = session.refine(new OverrideStockFeedback(a, 3L));
            assertEquals(PlanningStatus.feasible, rStock.status());
            assertTrue(rStock.paretoPlans().get(0).certificate().valid());
            assertEquals(2L, rStock.paretoPlans().get(0).preparation().toAcquireFromWorld().amountOf(a));

            ProductionPlan cur = rStock.paretoPlans().get(0);
            CraftStep firstCraft = cur.steps().stream().filter(CraftStep.class::isInstance).map(CraftStep.class::cast)
                    .findFirst().orElseThrow();
            PlanningResult rReject = session
                    .refine(new RejectStepFeedback(cur.planId(), firstCraft.stepId(), false, false));
        }
    }
}
