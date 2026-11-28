package dev.vfyjxf.cirrus.planning;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.cost.PlanningProfile;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.CycleBreakRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.RecipeRule;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.human.SystemRule;
import dev.vfyjxf.cirrus.api.planning.model.CompiledCycleMacroKey;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.StockLedger;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ByproductEntry;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.PreparationSummary;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.planning.plan.VerificationCertificate;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.planning.core.cost.PlanCosts;
import dev.vfyjxf.cirrus.planning.core.cost.StepCosts;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import dev.vfyjxf.cirrus.planning.core.verify.DefaultPlanVerifier;
import dev.vfyjxf.cirrus.planning.core.verify.ErrorCodes;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.capability;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.compile;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.fac;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.generousBudget;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.item;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.machine;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.out;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.owned;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedCraftingTable;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.ownedFurnace;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.rec;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.request;
import static dev.vfyjxf.cirrus.planning.PlanningTestSupport.slot;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPlanVerifierFaultInjectionTest {

    private static final VerificationCertificate dummyCert = new VerificationCertificate(
        false,
        false,
        false,
        false,
        false,
        false,
        List.of()
    );

    @Test
    @SuppressWarnings("NullAway")
    void tc11_allThirtyFourErrorCodesTriggeredAndRejected() throws Exception {
        ComponentStackKey cobble = item("minecraft:cobblestone");
        ComponentStackKey stone = item("minecraft:stone");
        ComponentStackKey bricks = item("minecraft:stone_bricks");
        ComponentStackKey hammer = item("mod:hammer");
        ComponentStackKey plate = item("mod:plate");
        ComponentStackKey fresh = item("mod:fresh");
        ComponentStackKey spent = item("mod:spent");
        ComponentStackKey reagent = item("mod:reagent");
        ComponentStackKey loopOut = item("mod:loop_out");
        ComponentStackKey ingot = item("minecraft:iron_ingot");
        ComponentStackKey block = item("minecraft:iron_block");

        CompiledRecipeGraph g = compile(
            List.of(
                rec(
                    "minecraft:smelting",
                    "smelt_stone",
                    List.of("minecraft:furnace"),
                    List.of(slot(cobble, 1)),
                    List.of(out(stone, 1))
                ),
                rec(
                    "minecraft:crafting",
                    "craft_bricks",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(stone, 4)),
                    List.of(out(bricks, 4))
                ),
                rec(
                    "minecraft:crafting",
                    "hammer_plate",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(hammer, 1), slot(cobble, 1)),
                    List.of(out(hammer, 1), out(plate, 1))
                ),
                rec(
                    "mod:chem",
                    "loop_a",
                    List.of("mod:reactor"),
                    List.of(slot(fresh, 1), slot(reagent, 1)),
                    List.of(out(spent, 1), out(loopOut, 1))
                ),
                rec("mod:chem", "loop_b", List.of("mod:reactor"), List.of(slot(spent, 1)), List.of(out(fresh, 1))),
                rec(
                    "minecraft:crafting",
                    "pack_block",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(ingot, 9)),
                    List.of(out(block, 1))
                ),
                rec(
                    "minecraft:crafting",
                    "unpack_block",
                    List.of("minecraft:crafting_table"),
                    List.of(slot(block, 1)),
                    List.of(out(ingot, 9))
                )
            )
        );
        DefaultPlanVerifier verifier = new DefaultPlanVerifier(g);
        CompiledRecipe smeltRec = g.producersOf(stone).get(0);
        CompiledRecipe bricksRec = g.producersOf(bricks).get(0);
        CompiledRecipe plateRec = g.producersOf(plate).get(0);
        CompiledRecipe packRec = g.producersOf(block).get(0);
        CompiledRecipe unpackRec = g.producersOf(ingot).get(0);
        CompiledCycleMacro macro = g.cycleMacrosProducing(loopOut).get(0);

        FacilitySnapshot fullFac = fac(
            ownedCraftingTable(),
            ownedFurnace(),
            owned("mod:reactor", capability("mod:chem"), 1)
        );
        Set<String> covered = new HashSet<>();

        // 1. ERR_MALFORMED_PLAN (null + empty stepId)
        assertViolation(
            verifier.verify(null, req(g, cobble, 1, InventorySnapshot.empty, fullFac, List.of())),
            ErrorCodes.errMalformedPlan,
            covered
        );
        AcquireStep emptyIdStep = new AcquireStep(
            "",
            List.of(),
            cobble,
            1,
            AcquisitionKind.manualGather,
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(emptyIdStep), PreparationSummary.empty, List.of()),
                req(g, cobble, 1, InventorySnapshot.empty, fullFac, List.of())
            ),
            ErrorCodes.errMalformedPlan,
            covered
        );

        // 2. ERR_DUPLICATE_STEP_ID
        AcquireStep dup1 = new AcquireStep(
            "s1",
            List.of(),
            cobble,
            1,
            AcquisitionKind.manualGather,
            StepCosts.freeBaseCost
        );
        AcquireStep dup2 = new AcquireStep(
            "s1",
            List.of(),
            cobble,
            1,
            AcquisitionKind.manualGather,
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(dup1, dup2), PreparationSummary.empty, List.of()),
                req(g, cobble, 2, InventorySnapshot.empty, fullFac, List.of())
            ),
            ErrorCodes.errDuplicateStepId,
            covered
        );

        // 3. ERR_MISSING_DEPENDENCY
        AcquireStep badDep = new AcquireStep(
            "s1",
            List.of("s999"),
            cobble,
            1,
            AcquisitionKind.manualGather,
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(badDep), PreparationSummary.empty, List.of()),
                req(g, cobble, 1, InventorySnapshot.empty, fullFac, List.of())
            ),
            ErrorCodes.errMissingDependency,
            covered
        );

        // 4. ERR_TOPOLOGICAL_ORDER_VIOLATION
        AcquireStep t1 = new AcquireStep(
            "s1",
            List.of("s2"),
            cobble,
            1,
            AcquisitionKind.manualGather,
            StepCosts.freeBaseCost
        );
        AcquireStep t2 = new AcquireStep(
            "s2",
            List.of(),
            cobble,
            1,
            AcquisitionKind.manualGather,
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(t1, t2), PreparationSummary.empty, List.of()),
                req(g, cobble, 2, InventorySnapshot.empty, fullFac, List.of())
            ),
            ErrorCodes.errTopologicalOrderViolation,
            covered
        );

        // 5. ERR_NON_POSITIVE_ACQUIRE
        AcquireStep zeroAcq = new AcquireStep(
            "s1",
            List.of(),
            cobble,
            0,
            AcquisitionKind.manualGather,
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(zeroAcq), PreparationSummary.empty, List.of()),
                req(g, cobble, 1, InventorySnapshot.empty, fullFac, List.of())
            ),
            ErrorCodes.errNonPositiveAcquire,
            covered
        );

        // 6. ERR_BANNED_RESOURCE
        AcquireStep banAcq = new AcquireStep(
            "s1",
            List.of(),
            cobble,
            1,
            AcquisitionKind.manualGather,
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(banAcq), PreparationSummary.empty, List.of()),
                req(
                    g,
                    cobble,
                    1,
                    InventorySnapshot.empty,
                    fullFac,
                    List.of(new ResourceRule("r_ban", GuidanceScope.session, RuleStrength.hardBan, cobble, false))
                )
            ),
            ErrorCodes.errBannedResource,
            covered
        );

        // 7. ERR_UNACQUIRABLE_RESOURCE
        AcquireStep unacq = new AcquireStep(
            "s1",
            List.of(),
            bricks,
            1,
            AcquisitionKind.manualGather,
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(unacq), PreparationSummary.empty, List.of()),
                req(g, bricks, 1, InventorySnapshot.empty, fullFac, List.of())
            ),
            ErrorCodes.errUnacquirableResource,
            covered
        );

        // 8. ERR_NON_POSITIVE_PULL & 9. ERR_UNKNOWN_LINE
        LinePullStep zeroPull = new LinePullStep("s1", List.of(), "line:a", cobble, 0, StepCosts.freeBaseCost);
        assertViolation(
            verifier.verify(
                planOf(List.of(zeroPull), PreparationSummary.empty, List.of()),
                req(g, cobble, 1, InventorySnapshot.empty, fullFac, List.of())
            ),
            ErrorCodes.errNonPositivePull,
            covered
        );
        LinePullStep badLine = new LinePullStep("s1", List.of(), "line:ghost", cobble, 1, StepCosts.freeBaseCost);
        assertViolation(
            verifier.verify(
                planOf(List.of(badLine), PreparationSummary.empty, List.of()),
                req(g, cobble, 1, InventorySnapshot.empty, fullFac, List.of())
            ),
            ErrorCodes.errUnknownLine,
            covered
        );

        // 10. ERR_NON_POSITIVE_MACHINE_COUNT, 11. ERR_BANNED_MACHINE_BUILD, 12. ERR_INSUFFICIENT_MACHINE_ITEM
        ProvisionMachineStep zeroMach = new ProvisionMachineStep(
            "s1",
            List.of(),
            machine("minecraft:furnace"),
            Optional.empty(),
            0,
            false,
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(zeroMach), PreparationSummary.empty, List.of()),
                req(g, cobble, 1, InventorySnapshot.empty, fullFac, List.of())
            ),
            ErrorCodes.errNonPositiveMachineCount,
            covered
        );
        ProvisionMachineStep banMach = new ProvisionMachineStep(
            "s1",
            List.of(),
            machine("minecraft:furnace"),
            Optional.empty(),
            1,
            false,
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(banMach), PreparationSummary.empty, List.of()),
                req(
                    g,
                    cobble,
                    1,
                    InventorySnapshot.empty,
                    FacilitySnapshot.empty,
                    List.of(
                        new MachineRule(
                            "m_ban",
                            GuidanceScope.session,
                            RuleStrength.hardBan,
                            machine("minecraft:furnace"),
                            false
                        )
                    )
                )
            ),
            ErrorCodes.errBannedMachineBuild,
            covered
        );
        ProvisionMachineStep noMachItem = new ProvisionMachineStep(
            "s1",
            List.of(),
            machine("minecraft:furnace"),
            Optional.empty(),
            1,
            true,
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(noMachItem), PreparationSummary.empty, List.of()),
                req(g, cobble, 1, InventorySnapshot.empty, FacilitySnapshot.empty, List.of())
            ),
            ErrorCodes.errInsufficientMachineItem,
            covered
        );

        // 13. ERR_NON_POSITIVE_BATCH
        CraftStep zeroBatch = new CraftStep(
            "s1",
            List.of(),
            smeltRec.key(),
            0,
            Optional.of(machine("minecraft:furnace")),
            false,
            Map.of(0, new SlotBinding(cobble, List.of())),
            Map.of(),
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(zeroBatch), PreparationSummary.empty, List.of()),
                req(g, stone, 1, invOf(cobble, 1), fullFac, List.of())
            ),
            ErrorCodes.errNonPositiveBatch,
            covered
        );

        // 14. ERR_UNKNOWN_RECIPE
        CompiledRecipeKey ghostKey = new CompiledRecipeKey(
            Namespace.parse("minecraft:crafting"), Optional.of(ResourceLocation.parse("ghost")), 42L);
        CraftStep ghostStep = new CraftStep(
            "s1",
            List.of(),
            ghostKey,
            1,
            Optional.empty(),
            false,
            Map.of(),
            Map.of(),
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(ghostStep), PreparationSummary.empty, List.of()),
                req(g, stone, 1, invOf(cobble, 1), fullFac, List.of())
            ),
            ErrorCodes.errUnknownRecipe,
            covered
        );

        // 15. ERR_BANNED_RECIPE, 16. ERR_BANNED_RECIPE_FOR_TARGET, 17. ERR_BANNED_SYSTEM
        CraftStep validSmelt = new CraftStep(
            "s1",
            List.of(),
            smeltRec.key(),
            1,
            Optional.of(machine("minecraft:furnace")),
            false,
            Map.of(0, new SlotBinding(cobble, List.of())),
            Map.of(),
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(validSmelt), PreparationSummary.empty, List.of()),
                req(
                    g,
                    stone,
                    1,
                    invOf(cobble, 1),
                    fullFac,
                    List.of(new RecipeRule("ban_smelt", GuidanceScope.session, RuleStrength.hardBan, smeltRec.key()))
                )
            ),
            ErrorCodes.errBannedRecipe,
            covered
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(validSmelt), PreparationSummary.empty, List.of()),
                req(
                    g,
                    stone,
                    1,
                    invOf(cobble, 1),
                    fullFac,
                    List.of(
                        new CycleBreakRule(
                            "cb_stone",
                            GuidanceScope.session,
                            RuleStrength.hardBan,
                            stone,
                            smeltRec.key()
                        )
                    )
                )
            ),
            ErrorCodes.errBannedRecipeForTarget,
            covered
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(validSmelt), PreparationSummary.empty, List.of()),
                req(
                    g,
                    stone,
                    1,
                    invOf(cobble, 1),
                    fullFac,
                    List.of(
                        new SystemRule(
                            "ban_sys",
                            GuidanceScope.session,
                            RuleStrength.hardBan,
                            Namespace.parse("minecraft:smelting"),
                            false
                        )
                    )
                )
            ),
            ErrorCodes.errBannedSystem,
            covered
        );

        // 18. ERR_MACHINE_NOT_AVAILABLE & 19. ERR_AUTOMATION_UNAVAILABLE
        assertViolation(
            verifier.verify(
                planOf(List.of(validSmelt), PreparationSummary.empty, List.of()),
                req(g, stone, 1, invOf(cobble, 1), FacilitySnapshot.empty, List.of())
            ),
            ErrorCodes.errMachineNotAvailable,
            covered
        );
        CraftStep autoSmelt = new CraftStep(
            "s1",
            List.of(),
            smeltRec.key(),
            1,
            Optional.of(machine("minecraft:furnace")),
            true,
            Map.of(0, new SlotBinding(cobble, List.of())),
            Map.of(),
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(autoSmelt), PreparationSummary.empty, List.of()),
                req(g, stone, 1, invOf(cobble, 1), fullFac, List.of())
            ),
            ErrorCodes.errAutomationUnavailable,
            covered
        );

        // 20. ERR_MISSING_CATALYST
        CraftStep noCat = new CraftStep(
            "s1",
            List.of(),
            plateRec.key(),
            1,
            Optional.of(machine("minecraft:crafting_table")),
            false,
            Map.of(0, new SlotBinding(cobble, List.of())),
            Map.of(),
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(noCat), PreparationSummary.empty, List.of()),
                req(g, plate, 1, invOf(cobble, 1), fullFac, List.of())
            ),
            ErrorCodes.errMissingCatalyst,
            covered
        );

        // 21. ERR_INVALID_SLOT_BINDING & 22. ERR_NEGATIVE_STOCK
        CraftStep badSlot = new CraftStep(
            "s1",
            List.of(),
            smeltRec.key(),
            1,
            Optional.of(machine("minecraft:furnace")),
            false,
            Map.of(0, new SlotBinding(stone, List.of())),
            Map.of(),
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(badSlot), PreparationSummary.empty, List.of()),
                req(g, stone, 1, invOf(cobble, 1), fullFac, List.of())
            ),
            ErrorCodes.errInvalidSlotBinding,
            covered
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(validSmelt), PreparationSummary.empty, List.of()),
                req(g, stone, 1, InventorySnapshot.empty, fullFac, List.of())
            ),
            ErrorCodes.errNegativeStock,
            covered
        );

        // 23. ERR_NOT_REVERSIBLE_CONVERSION
        ConvertStep nonRevConv = new ConvertStep(
            "s1",
            List.of(),
            smeltRec.key(),
            1,
            Map.of(0, new SlotBinding(cobble, List.of())),
            StepCosts.convertCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(nonRevConv), PreparationSummary.empty, List.of()),
                req(g, stone, 1, invOf(cobble, 1), fullFac, List.of())
            ),
            ErrorCodes.errNotReversibleConversion,
            covered
        );

        // 24-28. CycleLoopStep errors: INVALID_CYCLE_ITER, UNKNOWN_CYCLE_MACRO, MISSING_CYCLE_SEED, CYCLE_DELTA_MISMATCH, CYCLE_INSUFFICIENT_REAGENT
        List<CraftStep> validLoopTemplate = buildLoopTemplate(macro);
        CycleLoopStep badIter = new CycleLoopStep(
            "s1",
            List.of(),
            macro.macroKey(),
            0,
            macro.workingSeeds(),
            validLoopTemplate,
            macro.netDeltaPerIter(),
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(badIter), PreparationSummary.empty, List.of()),
                req(g, loopOut, 1, invOf(fresh, 1, reagent, 1), fullFac, List.of())
            ),
            ErrorCodes.errInvalidCycleIter,
            covered
        );

        CompiledCycleMacroKey unknownMacroKey = new CompiledCycleMacroKey("macro:unknown");
        CycleLoopStep badMacro = new CycleLoopStep(
            "s1",
            List.of(),
            unknownMacroKey,
            1,
            macro.workingSeeds(),
            validLoopTemplate,
            macro.netDeltaPerIter(),
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(badMacro), PreparationSummary.empty, List.of()),
                req(g, loopOut, 1, invOf(fresh, 1, reagent, 1), fullFac, List.of())
            ),
            ErrorCodes.errUnknownCycleMacro,
            covered
        );

        CycleLoopStep missingSeed = new CycleLoopStep(
            "s1",
            List.of(),
            macro.macroKey(),
            1,
            macro.workingSeeds(),
            validLoopTemplate,
            macro.netDeltaPerIter(),
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(missingSeed), PreparationSummary.empty, List.of()),
                req(g, loopOut, 1, invOf(reagent, 1), fullFac, List.of())
            ),
            ErrorCodes.errMissingCycleSeed,
            covered
        );

        CycleLoopStep badDelta = new CycleLoopStep(
            "s1",
            List.of(),
            macro.macroKey(),
            1,
            macro.workingSeeds(),
            validLoopTemplate,
            Map.of(loopOut, 99L, reagent, -1L),
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(badDelta), PreparationSummary.empty, List.of()),
                req(g, loopOut, 1, invOf(fresh, 1, reagent, 1), fullFac, List.of())
            ),
            ErrorCodes.errCycleDeltaMismatch,
            covered
        );

        CycleLoopStep shortReagent = new CycleLoopStep(
            "s1",
            List.of(),
            macro.macroKey(),
            5,
            macro.workingSeeds(),
            validLoopTemplate,
            macro.netDeltaPerIter(),
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(shortReagent), PreparationSummary.empty, List.of()),
                req(g, loopOut, 5, invOf(fresh, 1, reagent, 2), fullFac, List.of())
            ),
            ErrorCodes.errCycleInsufficientReagent,
            covered
        );

        // 29. ERR_GOAL_NOT_MET
        assertViolation(
            verifier.verify(
                planOf(List.of(), PreparationSummary.empty, List.of()),
                req(g, stone, 1, InventorySnapshot.empty, fullFac, List.of())
            ),
            ErrorCodes.errGoalNotMet,
            covered
        );

        // 30. ERR_SURPLUS_MISMATCH, 31. ERR_PREPARATION_MISMATCH, 32. ERR_COST_MISMATCH
        CraftStep makeFourBricks = new CraftStep(
            "s1",
            List.of(),
            bricksRec.key(),
            1,
            Optional.of(machine("minecraft:crafting_table")),
            false,
            Map.of(0, new SlotBinding(stone, List.of())),
            Map.of(),
            new CostVector(3.0, 0.0, 0.0, 0.0, 1.0)
        );
        PreparationSummary goodPrep = new PreparationSummary(
            new StockLedger(Map.of(stone, 4L)),
            StockLedger.empty,
            Map.of(),
            List.of(),
            List.of()
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(makeFourBricks), goodPrep, List.of()),
                req(g, bricks, 1, invOf(stone, 4), fullFac, List.of())
            ),
            ErrorCodes.errSurplusMismatch,
            covered
        );
        assertViolation(
            verifier.verify(
                planOf(
                    List.of(makeFourBricks),
                    PreparationSummary.empty,
                    List.of(new ByproductEntry(bricks, 3L, "s1"))
                ),
                req(g, bricks, 1, invOf(stone, 4), fullFac, List.of())
            ),
            ErrorCodes.errPreparationMismatch,
            covered
        );
        ProductionPlan wrongCost = new ProductionPlan(
            "p_bad_cost",
            "test",
            List.of(makeFourBricks),
            goodPrep,
            List.of(new ByproductEntry(bricks, 3L, "s1")),
            CostVector.zero,
            dummyCert
        );
        assertViolation(
            verifier.verify(wrongCost, req(g, bricks, 1, invOf(stone, 4), fullFac, List.of())),
            ErrorCodes.errCostMismatch,
            covered
        );

        // 33. ERR_ROUND_TRIP
        CraftStep pack = new CraftStep(
            "s1",
            List.of(),
            packRec.key(),
            1,
            Optional.of(machine("minecraft:crafting_table")),
            false,
            Map.of(0, new SlotBinding(ingot, List.of())),
            Map.of(),
            StepCosts.freeBaseCost
        );
        CraftStep unpack = new CraftStep(
            "s2",
            List.of("s1"),
            unpackRec.key(),
            1,
            Optional.of(machine("minecraft:crafting_table")),
            false,
            Map.of(0, new SlotBinding(block, List.of())),
            Map.of(),
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(pack, unpack), PreparationSummary.empty, List.of()),
                req(g, ingot, 9, invOf(ingot, 9), fullFac, List.of())
            ),
            ErrorCodes.errRoundTrip,
            covered
        );

        // 34. ERR_STOCK_OVERFLOW
        AcquireStep overflow = new AcquireStep(
            "s1",
            List.of(),
            cobble,
            Long.MAX_VALUE,
            AcquisitionKind.manualGather,
            StepCosts.freeBaseCost
        );
        assertViolation(
            verifier.verify(
                planOf(List.of(overflow), PreparationSummary.empty, List.of()),
                req(g, cobble, 1, invOf(cobble, 10), fullFac, List.of())
            ),
            ErrorCodes.errStockOverflow,
            covered
        );

        // Reflectively verify every constant declared in ErrorCodes was exercised.
        Set<String> allDeclared = new HashSet<>();
        for (Field f : ErrorCodes.class.getDeclaredFields()) {
            if (Modifier.isStatic(f.getModifiers()) && f.getType() == String.class) {
                allDeclared.add((String) f.get(null));
            }
        }
        assertEquals(34, allDeclared.size());
        assertEquals(allDeclared, covered, () -> "Uncovered ErrorCodes: " + diff(allDeclared, covered));
    }

    private static List<CraftStep> buildLoopTemplate(CompiledCycleMacro macro) {
        return List.of(
            new CraftStep(
                "i0",
                List.of(),
                macro.innerRecipes().get(0).key(),
                macro.innerMultipliers().get(0),
                Optional.of(machine("mod:reactor")),
                false,
                Map.of(
                    macro.innerRecipes().get(0).inputs().get(0).slotIndex(),
                    new SlotBinding(macro.innerRecipes().get(0).inputs().get(0).candidates().get(0), List.of()),
                    macro.innerRecipes().get(0).inputs().get(1).slotIndex(),
                    new SlotBinding(macro.innerRecipes().get(0).inputs().get(1).candidates().get(0), List.of())
                ),
                Map.of(),
                StepCosts.freeBaseCost
            ),
            new CraftStep(
                "i1",
                List.of(),
                macro.innerRecipes().get(1).key(),
                macro.innerMultipliers().get(1),
                Optional.of(machine("mod:reactor")),
                false,
                Map.of(
                    macro.innerRecipes().get(1).inputs().get(0).slotIndex(),
                    new SlotBinding(macro.innerRecipes().get(1).inputs().get(0).candidates().get(0), List.of())
                ),
                Map.of(),
                StepCosts.freeBaseCost
            )
        );
    }

    private static void assertViolation(VerificationCertificate cert, String expectedCode, Set<String> covered) {
        assertFalse(cert.valid(), () -> "Expected invalid certificate for " + expectedCode);
        assertTrue(
            cert.violations().stream().anyMatch(v -> v.equals(expectedCode) || v.startsWith(expectedCode + ":")),
            () -> "Expected violation " + expectedCode + " but got " + cert.violations()
        );
        covered.add(expectedCode);
    }

    private static ProductionPlan planOf(List<PlanStep> steps, PreparationSummary prep, List<ByproductEntry> surplus) {
        return new ProductionPlan("p", "test", steps, prep, surplus, PlanCosts.aggregate(steps), dummyCert);
    }

    private static PlanningRequest req(
        CompiledRecipeGraph g,
        ComponentStackKey target,
        long amount,
        InventorySnapshot inv,
        FacilitySnapshot fac,
        List<GuidanceRule> rules
    ) {
        return request(
            g,
            List.of(new TargetDemand(target, amount)),
            inv,
            fac,
            rules.isEmpty() ? DefaultHumanGuidanceView.empty() : DefaultHumanGuidanceView.of(rules),
            PlanningProfile.defaultBalanced(),
            generousBudget()
        );
    }

    private static InventorySnapshot invOf(ComponentStackKey k1, long a1) {
        return new InventorySnapshot(new StockLedger(Map.of(k1, a1)), StockLedger.empty);
    }

    private static InventorySnapshot invOf(ComponentStackKey k1, long a1, ComponentStackKey k2, long a2) {
        return new InventorySnapshot(new StockLedger(Map.of(k1, a1, k2, a2)), StockLedger.empty);
    }

    private static Set<String> diff(Set<String> expected, Set<String> actual) {
        Set<String> d = new HashSet<>(expected);
        d.removeAll(actual);
        return d;
    }
}
