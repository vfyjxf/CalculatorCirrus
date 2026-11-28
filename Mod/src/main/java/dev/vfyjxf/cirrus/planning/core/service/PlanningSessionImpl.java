package dev.vfyjxf.cirrus.planning.core.service;

import dev.vfyjxf.cirrus.api.planning.context.AcquisitionOracle;
import dev.vfyjxf.cirrus.api.planning.context.FacilitySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.OwnedMachineGroup;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledCycleMacro;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.human.AnswerPromptFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.CostOverrideRule;
import dev.vfyjxf.cirrus.api.planning.human.CycleBreakRule;
import dev.vfyjxf.cirrus.api.planning.human.DecisionOption;
import dev.vfyjxf.cirrus.api.planning.human.DecisionPrompt;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanFeedback;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceStore;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceView;
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
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.model.MachineCapabilityId;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.api.planning.service.PlanningResult;
import dev.vfyjxf.cirrus.api.planning.service.PlanningSession;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.planning.core.cost.DefaultAcquisitionOracle;
import dev.vfyjxf.cirrus.planning.core.graph.MachineCapabilityGroups;
import dev.vfyjxf.cirrus.planning.core.human.DefaultHumanGuidanceView;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

final class PlanningSessionImpl implements PlanningSession {

    private final DefaultPlanningService service;
    private final HumanGuidanceStore guidanceStore;
    private final @Nullable Consumer<DeclareOwnedMachineFeedback> machinePersistenceListener;
    private final List<GuidanceRule> baseRules;
    private final Deque<SessionState> undoStack = new ArrayDeque<>();
    private final Deque<SessionState> redoStack = new ArrayDeque<>();

    private SessionState current;
    private boolean closed;

    PlanningSessionImpl(
        DefaultPlanningService service,
        PlanningRequest initialRequest,
        HumanGuidanceStore guidanceStore,
        @Nullable Consumer<DeclareOwnedMachineFeedback> machinePersistenceListener
    ) {
        this.service = Objects.requireNonNull(service, "service");
        this.guidanceStore = guidanceStore;
        this.machinePersistenceListener = machinePersistenceListener;
        this.baseRules = List.copyOf(initialRequest.context().guidance().allRules());
        PlanningResult initialResult = service.planOnceInternal(initialRequest, Set.of());
        this.current = new SessionState(initialRequest, List.of(), Set.of(), List.of(), initialResult);
    }

    @Override
    public synchronized PlanningResult currentResult() {
        return current.result();
    }

    @Override
    public synchronized PlanningResult refine(HumanFeedback feedback) {
        ensureOpen();
        Objects.requireNonNull(feedback, "feedback");

        MutableDelta delta = new MutableDelta(
            current.request().context().inventory(),
            current.request().context().facilities(),
            new ArrayList<>(current.sessionRules()),
            new LinkedHashSet<>(current.dismissedPromptIds())
        );

        applyFeedback(feedback, delta);

        List<GuidanceRule> combinedRules = new ArrayList<>(baseRules.size() + delta.sessionRules.size());
        combinedRules.addAll(baseRules);
        combinedRules.addAll(delta.sessionRules);
        HumanGuidanceView nextGuidance = DefaultHumanGuidanceView.of(combinedRules);

        PlanningContext baseCtx = current.request().context();
        AcquisitionOracle baseOracle = baseCtx.acquisitionOracle();
        AcquisitionOracle nextOracle = baseOracle instanceof DefaultAcquisitionOracle dao
                ? dao.withGuidance(nextGuidance)
                : baseOracle;

        PlanningContext nextCtx = baseCtx
                .withInventory(delta.inventory)
                .withFacilities(delta.facilities)
                .withOracleAndGuidance(nextOracle, nextGuidance);
        PlanningRequest nextReq = new PlanningRequest(
            current.request().targets(),
            nextCtx,
            current.request().profile(),
            current.request().budget()
        );

        Set<String> nextDismissed = Set.copyOf(delta.dismissedPromptIds);
        PlanningResult nextResult = service.planOnceInternal(nextReq, nextDismissed);

        undoStack.push(current);
        redoStack.clear();
        current = new SessionState(
            nextReq,
            List.copyOf(delta.sessionRules),
            nextDismissed,
            List.copyOf(delta.worldSaveMutations),
            nextResult
        );
        return nextResult;
    }

    @Override
    public synchronized PlanningResult undo() {
        ensureOpen();
        if (undoStack.isEmpty()) {
            return current.result();
        }
        if (guidanceStore != null && !current.worldSaveMutations().isEmpty()) {
            for (int i = current.worldSaveMutations().size() - 1; i >= 0; i--) {
                WorldSaveMutation mut = current.worldSaveMutations().get(i);
                if (mut.previousRule() != null) {
                    guidanceStore.putRule(mut.previousRule());
                } else {
                    guidanceStore.removeRule(mut.appliedRule().ruleId());
                }
            }
        }
        redoStack.push(current);
        current = undoStack.pop();
        return current.result();
    }

    @Override
    public synchronized PlanningResult redo() {
        ensureOpen();
        if (redoStack.isEmpty()) {
            return current.result();
        }
        undoStack.push(current);
        current = redoStack.pop();
        if (guidanceStore != null && !current.worldSaveMutations().isEmpty()) {
            for (WorldSaveMutation mut : current.worldSaveMutations()) {
                guidanceStore.putRule(mut.appliedRule());
            }
        }
        return current.result();
    }

    @Override
    public synchronized void close() {
        closed = true;
    }

    private void ensureOpen() {
        if (closed) {
            throw new IllegalStateException("PlanningSession is already closed");
        }
    }

    private void applyFeedback(HumanFeedback feedback, MutableDelta delta) {
        switch (feedback) {
            case AnswerPromptFeedback apf -> applyAnswerPrompt(apf, delta);
            case ApplyRulesFeedback arf -> appendRules(arf.rules(), arf.persistToWorld(), delta);
            case DeclareOwnedMachineFeedback dmf -> applyDeclareOwnedMachine(dmf, delta);
            case OverrideStockFeedback osf -> applyOverrideStock(osf, delta);
            case PinResourceRecipeFeedback prf -> applyPinRecipe(prf, delta);
            case RejectStepFeedback rsf -> applyRejectStep(rsf, delta);
        }
    }

    private void applyAnswerPrompt(AnswerPromptFeedback apf, MutableDelta delta) {
        DecisionPrompt matchedPrompt = null;
        for (DecisionPrompt p : current.result().decisionPrompts()) {
            if (p.promptId().equals(apf.promptId())) {
                matchedPrompt = p;
                break;
            }
        }
        if (matchedPrompt == null) {
            throw new IllegalArgumentException("Unknown promptId: " + apf.promptId());
        }
        DecisionOption matchedOption = null;
        for (DecisionOption o : matchedPrompt.options()) {
            if (o.optionId().equals(apf.chosenOptionId())) {
                matchedOption = o;
                break;
            }
        }
        if (matchedOption == null) {
            throw new IllegalArgumentException(
                "Unknown chosenOptionId: " + apf.chosenOptionId() + " for prompt " + apf.promptId()
            );
        }
        delta.dismissedPromptIds.add(apf.promptId());
        service.recordPromptAnswered(matchedPrompt.questionKey());
        appendRules(matchedOption.compiledRules(), apf.persistToWorld(), delta);
        if (matchedOption.followUp().isPresent()) {
            HumanFeedback follow = matchedOption.followUp().get();
            if (follow instanceof DeclareOwnedMachineFeedback dmf && apf.persistToWorld() && !dmf.persistToWorld()) {
                follow = new DeclareOwnedMachineFeedback(dmf.machine(), dmf.ownedCount(), true);
            }
            applyFeedback(follow, delta);
        }
    }

    private void applyDeclareOwnedMachine(DeclareOwnedMachineFeedback dmf, MutableDelta delta) {
        FacilitySnapshot oldFac = delta.facilities;
        List<OwnedMachineGroup> nextMachines = new ArrayList<>(oldFac.ownedMachines().size() + 1);
        OwnedMachineGroup existing = null;
        for (OwnedMachineGroup g : oldFac.ownedMachines()) {
            if (g.machine().equals(dmf.machine())) {
                existing = g;
            } else {
                nextMachines.add(g);
            }
        }
        if (dmf.ownedCount() > 0) {
            MachineCapabilityId cap = existing != null
                    ? existing.capability()
                    : inferMachineCapability(service.graph(), dmf.machine());
            double speed = existing != null ? existing.passiveSpeedMultiplier() : 1.0;
            boolean autoFeed = existing != null && existing.automatedFeeding();
            nextMachines.add(new OwnedMachineGroup(dmf.machine(), cap, dmf.ownedCount(), speed, autoFeed));
        }
        delta.facilities = new FacilitySnapshot(
            nextMachines,
            oldFac.automatedLines(),
            oldFac.automatedRecipes(),
            oldFac.unlockedSystems()
        );
        if (dmf.persistToWorld() && machinePersistenceListener != null) {
            machinePersistenceListener.accept(dmf);
        }
    }

    private void applyOverrideStock(OverrideStockFeedback osf, MutableDelta delta) {
        delta.inventory = delta.inventory.withOverriddenStock(osf.resource(), osf.newAvailableAmount());
    }

    private void applyPinRecipe(PinResourceRecipeFeedback prf, MutableDelta delta) {
        GuidanceScope scope = prf.persistToWorld() ? GuidanceScope.worldSave : GuidanceScope.session;
        RecipeRule pinRule = new RecipeRule(
            "session:pin:" + prf.resource().canonicalKey() + ":" + prf.recipeKey().toString(),
            scope,
            RuleStrength.hardPin,
            prf.recipeKey()
        );
        appendRules(List.of(pinRule), prf.persistToWorld(), delta);
    }

    private void applyRejectStep(RejectStepFeedback rsf, MutableDelta delta) {
        ProductionPlan plan = findPlanById(rsf.planId());
        if (plan == null) {
            throw new IllegalArgumentException("Unknown planId: " + rsf.planId());
        }
        PlanStep step = null;
        for (PlanStep s : plan.steps()) {
            if (s.stepId().equals(rsf.stepId())) {
                step = s;
                break;
            }
        }
        if (step == null) {
            throw new IllegalArgumentException("Unknown stepId: " + rsf.stepId() + " in plan " + rsf.planId());
        }

        GuidanceScope scope = rsf.persistToWorld() ? GuidanceScope.worldSave : GuidanceScope.session;
        List<GuidanceRule> generated = new ArrayList<>();
        switch (step) {
            case CraftStep cs -> {
                if (rsf.banSystemInstead()) {
                    generated.add(
                        new SystemRule(
                            "session:reject:sys:" + cs.recipeKey().systemId(),
                            scope,
                            RuleStrength.hardBan,
                            cs.recipeKey().systemId(),
                            false
                        )
                    );
                } else {
                    generated.add(
                        new RecipeRule(
                            "session:reject:rec:" + cs.recipeKey().toString(),
                            scope,
                            RuleStrength.hardBan,
                            cs.recipeKey()
                        )
                    );
                }
            }
            case ConvertStep cv -> {
                if (rsf.banSystemInstead()) {
                    generated.add(
                        new SystemRule(
                            "session:reject:sys:" + cv.conversionRecipeKey().systemId(),
                            scope,
                            RuleStrength.hardBan,
                            cv.conversionRecipeKey().systemId(),
                            false
                        )
                    );
                } else {
                    generated.add(
                        new RecipeRule(
                            "session:reject:rec:" + cv.conversionRecipeKey().toString(),
                            scope,
                            RuleStrength.hardBan,
                            cv.conversionRecipeKey()
                        )
                    );
                }
            }
            case CycleLoopStep cl -> {
                Optional<CompiledCycleMacro> optMacro = service.graph().cycleMacroByKey(cl.macroKey());
                if (optMacro.isPresent() && !optMacro.get().innerRecipes().isEmpty()) {
                    CompiledRecipeKey firstKey = optMacro.get().innerRecipes().get(0).key();
                    if (rsf.banSystemInstead()) {
                        generated.add(
                            new SystemRule(
                                "session:reject:sys:" + firstKey.systemId(),
                                scope,
                                RuleStrength.hardBan,
                                firstKey.systemId(),
                                false
                            )
                        );
                    } else {
                        for (CompiledRecipe inner : optMacro.get().innerRecipes()) {
                            generated.add(
                                new RecipeRule(
                                    "session:reject:rec:" + inner.key().toString(),
                                    scope,
                                    RuleStrength.hardBan,
                                    inner.key()
                                )
                            );
                        }
                    }
                }
            }
            case ProvisionMachineStep pm -> generated.add(
                new MachineRule(
                    "session:reject:machine:" + pm.machine().canonicalKey(),
                    scope,
                    RuleStrength.hardBan,
                    pm.machine(),
                    false
                )
            );
            case AcquireStep ac -> generated.add(
                new ResourceRule(
                    "session:reject:res:" + ac.resource().canonicalKey(),
                    scope,
                    RuleStrength.hardBan,
                    ac.resource(),
                    false
                )
            );
            case SetupLineStep sl -> generated.add(
                new MachineRule(
                    "session:reject:machine:" + sl.primaryMachine().canonicalKey(),
                    scope,
                    RuleStrength.hardBan,
                    sl.primaryMachine(),
                    false
                )
            );
            case LinePullStep lp -> generated.add(
                new ResourceRule(
                    "session:reject:res:" + lp.resource().canonicalKey(),
                    scope,
                    RuleStrength.hardBan,
                    lp.resource(),
                    false
                )
            );
        }
        appendRules(generated, rsf.persistToWorld(), delta);
    }

    private @Nullable ProductionPlan findPlanById(String planId) {
        for (ProductionPlan p : current.result().paretoPlans()) {
            if (p.planId().equals(planId)) {
                return p;
            }
        }
        for (ProductionPlan p : current.result().conditionalPlans()) {
            if (p.planId().equals(planId)) {
                return p;
            }
        }
        return null;
    }

    private void appendRules(List<GuidanceRule> rules, boolean persistToWorld, MutableDelta delta) {
        if (rules == null || rules.isEmpty()) {
            return;
        }
        for (GuidanceRule raw : rules) {
            GuidanceRule effective = persistToWorld ? withScope(raw, GuidanceScope.worldSave) : raw;
            delta.sessionRules.add(effective);
            if (persistToWorld && guidanceStore != null) {
                GuidanceRule previous = findExistingWorldSaveRule(effective.ruleId());
                guidanceStore.putRule(effective);
                delta.worldSaveMutations.add(new WorldSaveMutation(effective, previous));
            }
        }
    }

    private @Nullable GuidanceRule findExistingWorldSaveRule(String ruleId) {
        if (guidanceStore == null) {
            return null;
        }
        for (GuidanceRule r : guidanceStore.rulesInScope(GuidanceScope.worldSave)) {
            if (r.ruleId().equals(ruleId)) {
                return r;
            }
        }
        return null;
    }

    private static MachineCapabilityId inferMachineCapability(CompiledRecipeGraph graph, MachineEntry machine) {
        int total = graph.stats().compiledRecipes();
        for (int id = 0; id < total; id++) {
            Optional<CompiledRecipe> opt = graph.recipeByCompactId(id);
            if (opt.isPresent() && opt.get().machineRequirement().compatibleMachines().contains(machine)) {
                Optional<MachineCapabilityId> cap = opt.get().machineRequirement().capabilityId();
                if (cap.isPresent()) {
                    return cap.get();
                }
            }
        }
        return MachineCapabilityGroups.capabilityOfMachine(machine);
    }

    private static GuidanceRule withScope(GuidanceRule rule, GuidanceScope scope) {
        if (rule.scope() == scope) {
            return rule;
        }
        return switch (rule) {
            case RecipeRule r -> new RecipeRule(r.ruleId(), scope, r.strength(), r.recipeKey());
            case SystemRule r -> new SystemRule(r.ruleId(), scope, r.strength(), r.systemId(), r.optInEnabled());
            case MachineRule r ->
                new MachineRule(r.ruleId(), scope, r.strength(), r.machine(), r.willingToBuildNew());
            case ResourceRule r ->
                new ResourceRule(r.ruleId(), scope, r.strength(), r.resource(), r.treatAsFreeBaseSource());
            case TagDefaultRule r ->
                new TagDefaultRule(r.ruleId(), scope, r.strength(), r.tag(), r.preferredMember());
            case CycleBreakRule r ->
                new CycleBreakRule(r.ruleId(), scope, r.strength(), r.targetResource(), r.bannedSourceRecipe());
            case CostOverrideRule r ->
                new CostOverrideRule(r.ruleId(), scope, r.strength(), r.resource(), r.overrideUnitCost());
            case ModNamespaceRule r -> new ModNamespaceRule(r.ruleId(), scope, r.strength(), r.modNamespace());
        };
    }

    private record WorldSaveMutation(GuidanceRule appliedRule, @Nullable GuidanceRule previousRule) {}

    private record SessionState(
        PlanningRequest request,
        List<GuidanceRule> sessionRules,
        Set<String> dismissedPromptIds,
        List<WorldSaveMutation> worldSaveMutations,
        PlanningResult result
    ) {}

    private static final class MutableDelta {
        InventorySnapshot inventory;
        FacilitySnapshot facilities;
        final List<GuidanceRule> sessionRules;
        final Set<String> dismissedPromptIds;
        final List<WorldSaveMutation> worldSaveMutations = new ArrayList<>();

        MutableDelta(
            InventorySnapshot inventory,
            FacilitySnapshot facilities,
            List<GuidanceRule> sessionRules,
            Set<String> dismissedPromptIds
        ) {
            this.inventory = inventory;
            this.facilities = facilities;
            this.sessionRules = sessionRules;
            this.dismissedPromptIds = dismissedPromptIds;
        }
    }
}
