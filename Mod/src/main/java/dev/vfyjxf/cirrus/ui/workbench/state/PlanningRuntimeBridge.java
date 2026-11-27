package dev.vfyjxf.cirrus.ui.workbench.state;

import dev.vfyjxf.cirrus.api.planning.context.InventorySnapshot;
import dev.vfyjxf.cirrus.api.planning.context.PlanningContext;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceRule;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.HumanGuidanceStore;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.service.PlanningRequest;
import dev.vfyjxf.cirrus.planning.bridge.CirrusPlanningRuntime;
import dev.vfyjxf.cirrus.planning.bridge.PlanningContextFactory;
import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import net.minecraft.world.entity.player.Player;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

final class PlanningRuntimeBridge {

    private PlanningRuntimeBridge() {}

    static PlannerWorkbenchController create(
        SessionTabState state,
        CirrusPlanningRuntime runtime,
        @Nullable Player player,
        Executor executor
    ) {
        Objects.requireNonNull(runtime, "runtime");
        Objects.requireNonNull(executor, "executor");
        PlanningBackend backend = request -> runtime.currentService()
                .orElseThrow(() -> new IllegalStateException("Planner graph is not published")).openSession(request);
        PlanningRequestSource source = spec -> createRequest(runtime, spec, player);
        PlannerWorkbenchController controller = new PlannerWorkbenchController(state, backend, source, executor);
        controller.bindWorldRuleGate(new RuntimeWorldRuleGate(runtime));
        controller.bindRuntimeVersionSupplier(runtime::publishedVersion);
        controller.onGraphVersionChanged(runtime.publishedVersion());
        return controller;
    }

    static PlanningRequest createRequest(
        CirrusPlanningRuntime runtime,
        PlanningRequestSpec spec,
        @Nullable Player player
    ) {
        PlanningContextFactory factory = runtime.contextFactory()
                .orElseThrow(() -> new IllegalStateException("Planner graph is not published"));
        WorldFacilityRegistry registry = WorldFacilityRegistry.installed().orElse(null);
        PlanningContext context = factory.createContext(
            player,
            registry,
            runtime.guidanceStore(),
            spec.reserved(),
            spec.progression(),
            spec.sessionRules(),
            List.of()
        );
        if (!spec.useLiveStock()) {
            context = new PlanningContext(
                InventorySnapshot.empty,
                context.facilities(),
                context.acquisitionOracle(),
                context.guidance(),
                context.progression()
            );
        } else {
            InventorySnapshot filteredInventory = PlanningContextFactory
                    .excludeTargetStock(context.inventory(), spec.targets());
            if (filteredInventory != context.inventory()) {
                context = new PlanningContext(
                    filteredInventory,
                    context.facilities(),
                    context.acquisitionOracle(),
                    context.guidance(),
                    context.progression()
                );
            }
        }
        return new PlanningRequest(spec.targets(), context, spec.profile(), spec.budget());
    }

    static InventorySnapshot excludeTargetStock(InventorySnapshot inventory, List<TargetDemand> targets) {
        return PlanningContextFactory.excludeTargetStock(inventory, targets);
    }

    private static final class RuntimeWorldRuleGate implements WorldRuleGate {

        private final CirrusPlanningRuntime runtime;

        private RuntimeWorldRuleGate(CirrusPlanningRuntime runtime) {
            this.runtime = runtime;
        }

        @Override
        public boolean available() {
            return WorldFacilityRegistry.installed().isPresent();
        }

        @Override
        public void putRule(GuidanceRule rule) {
            GuidanceRule scoped = GuidanceRuleScopes.withScope(rule, GuidanceScope.worldSave);
            HumanGuidanceStore store = runtime.guidanceStore();
            store.putRule(scoped);
            WorldFacilityRegistry.installed().ifPresent(registry -> registry.putWorldSaveRule(scoped));
        }

        @Override
        public void removeRule(String ruleId) {
            runtime.guidanceStore().removeRule(ruleId);
            WorldFacilityRegistry.installed().ifPresent(registry -> registry.removeWorldSaveRule(ruleId));
        }

        @Override
        public void declareOwnedMachine(DeclareOwnedMachineFeedback feedback) {
            WorldFacilityRegistry.installed().ifPresent(registry -> registry.declareOwnedMachine(feedback));
        }
    }
}
