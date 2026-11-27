package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.planning.human.ApplyRulesFeedback;
import dev.vfyjxf.cirrus.api.planning.human.DeclareOwnedMachineFeedback;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.api.planning.human.MachineRule;
import dev.vfyjxf.cirrus.api.planning.human.OverrideStockFeedback;
import dev.vfyjxf.cirrus.api.planning.human.PinResourceRecipeFeedback;
import dev.vfyjxf.cirrus.api.planning.human.ResourceRule;
import dev.vfyjxf.cirrus.api.planning.human.RuleStrength;
import dev.vfyjxf.cirrus.api.planning.model.ResourceKey;
import dev.vfyjxf.cirrus.api.planning.model.TargetDemand;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.workbench.state.DragPayload;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.performer.PerformerContainer;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHandle;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.drag.DragConsumer;
import dev.vfyjxf.cloudlib.api.ui.drag.DragContext;
import dev.vfyjxf.cloudlib.api.ui.drag.DragProvider;
import dev.vfyjxf.cloudlib.api.ui.drag.DraggableElement;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.client.gui.GuiGraphics;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The single drop router for DND-04/05/06/07: turns a drag payload and drop target into a host dispatch.
 */
public final class CoPilotDnd {

    private CoPilotDnd() {}

    public enum DropTarget {
        canvasBlank, mainTargetSlot, acquireStep, freeSourceSlot, banSlot, craftStep, provisionStep, compareSlot, invalid
    }

    public record DropOutcome(boolean handled, @Nullable LangEntry message) {
        public static DropOutcome rejected() {
            return new DropOutcome(false, CirrusLangs.Workbench.Dnd.dropInvalid);
        }

        public static DropOutcome accepted(@Nullable LangEntry message) {
            return new DropOutcome(true, message);
        }
    }

    /** Host capability of the top dock target slot and project basket; implemented by the workbench shell. */
    public interface TargetSink {

        TargetSink NOOP = new TargetSink() {
            @Override
            public void addTarget(TargetDemand demand) {}

            @Override
            public void replaceTarget(TargetDemand demand) {}
        };

        void addTarget(TargetDemand demand);

        void replaceTarget(TargetDemand demand);
    }

    public record DropContext(@Nullable String targetStepId, boolean persistToWorld, @Nullable TargetSink sink) {
        public DropContext {
            sink = sink == null ? TargetSink.NOOP : sink;
        }

        @Override
        public TargetSink sink() {
            return sink == null ? TargetSink.NOOP : sink;
        }

        public static DropContext of(boolean persistToWorld) {
            return new DropContext(null, persistToWorld, TargetSink.NOOP);
        }

        public static DropContext onStep(@Nullable String targetStepId, boolean persistToWorld) {
            return new DropContext(targetStepId, persistToWorld, TargetSink.NOOP);
        }

        public DropContext withSink(TargetSink sink) {
            return new DropContext(targetStepId, persistToWorld, sink);
        }
    }

    public static DropOutcome accept(
        CoPilotHost host,
        DropTarget target,
        @Nullable DragPayload payload,
        DropContext context
    ) {
        if (host == null || target == null || target == DropTarget.invalid || payload == null) {
            return DropOutcome.rejected();
        }
        DropContext settled = context == null ? DropContext.of(false) : context;
        return switch (payload) {
            case DragPayload.ResourceDrag resource -> acceptResource(host, target, resource, settled);
            case DragPayload.MachineDrag machine -> acceptMachine(host, target, machine, settled);
            case DragPayload.RecipeDrag recipe -> acceptRecipe(host, target, recipe, settled);
            case DragPayload.PlanCompareDrag compare -> acceptCompare(host, target, compare, settled);
        };
    }

    public static DropOutcome acceptResource(
        CoPilotHost host,
        DropTarget target,
        DragPayload payload,
        DropContext context
    ) {
        if (!(payload instanceof DragPayload.ResourceDrag drag)) {
            return DropOutcome.rejected();
        }
        return acceptResource(host, target, drag, context);
    }

    private static DropOutcome acceptResource(
        CoPilotHost host,
        DropTarget target,
        DragPayload.ResourceDrag drag,
        DropContext context
    ) {
        ResourceKey resource = drag.resource();
        long amount = Math.max(1L, drag.suggestedAmount());
        return switch (target) {
            case canvasBlank -> {
                context.sink().addTarget(new TargetDemand(resource, amount));
                host.toast(CirrusLangs.Workbench.Dnd.dropAddTarget);
                yield DropOutcome.accepted(CirrusLangs.Workbench.Dnd.dropAddTarget);
            }
            case mainTargetSlot -> {
                context.sink().replaceTarget(new TargetDemand(resource, amount));
                host.toast(CirrusLangs.Workbench.Dnd.dropReplaceTarget);
                yield DropOutcome.accepted(CirrusLangs.Workbench.Dnd.dropReplaceTarget);
            }
            case acquireStep -> {
                AcquireStep step = acquireStep(host, context.targetStepId());
                if (step == null || !step.resource().sameBaseIdentity(resource)) {
                    yield DropOutcome.rejected();
                }
                host.refine(new OverrideStockFeedback(resource, amount), null);
                yield DropOutcome.accepted(CirrusLangs.Workbench.Dnd.dropOverrideStock);
            }
            case freeSourceSlot -> {
                host.refine(
                    new ApplyRulesFeedback(
                        List.of(freeSourceRule(resource, context.persistToWorld())),
                        context.persistToWorld()
                    ),
                    null
                );
                host.toast(CirrusLangs.Workbench.Dnd.dropFreeSource);
                yield DropOutcome.accepted(CirrusLangs.Workbench.Dnd.dropFreeSource);
            }
            case banSlot -> {
                host.refine(
                    new ApplyRulesFeedback(
                        List.of(banRule(resource, context.persistToWorld())),
                        context.persistToWorld()
                    ),
                    null
                );
                host.toast(CirrusLangs.Workbench.Dnd.dropBanResource);
                yield DropOutcome.accepted(CirrusLangs.Workbench.Dnd.dropBanResource);
            }
            default -> DropOutcome.rejected();
        };
    }

    public static DropOutcome acceptMachine(
        CoPilotHost host,
        DropTarget target,
        DragPayload payload,
        DropContext context
    ) {
        if (!(payload instanceof DragPayload.MachineDrag drag)) {
            return DropOutcome.rejected();
        }
        int count = Math.max(1, drag.suggestedCount());
        if (target == DropTarget.freeSourceSlot) {
            host.refine(new DeclareOwnedMachineFeedback(drag.machineId(), count, context.persistToWorld()), null);
            host.toast(CirrusLangs.Workbench.Dnd.dropOwnMachine);
            return DropOutcome.accepted(CirrusLangs.Workbench.Dnd.dropOwnMachine);
        }
        if (target == DropTarget.banSlot) {
            host.refine(
                new ApplyRulesFeedback(
                    List.of(
                        new MachineRule(
                            ruleId("ban_machine", drag.machineId()),
                            scopeFor(context.persistToWorld()),
                            RuleStrength.hardBan,
                            drag.machineId(),
                            false
                        )
                    ),
                    context.persistToWorld()
                ),
                null
            );
            host.toast(CirrusLangs.Workbench.Dnd.dropBanResource);
            return DropOutcome.accepted(CirrusLangs.Workbench.Dnd.dropBanResource);
        }
        boolean matches = switch (target) {
            case provisionStep -> {
                ProvisionMachineStep step = provisionStep(host, context.targetStepId());
                yield step != null && step.machineId().equals(drag.machineId());
            }
            case craftStep -> {
                CraftStep step = craftStep(host, context.targetStepId());
                yield step != null && step.chosenMachineId().map(drag.machineId()::equals).orElse(false);
            }
            default -> false;
        };
        if (!matches) {
            return DropOutcome.rejected();
        }
        host.refine(new DeclareOwnedMachineFeedback(drag.machineId(), count, context.persistToWorld()), null);
        return DropOutcome.accepted(CirrusLangs.Workbench.Dnd.dropOwnMachine);
    }

    public static DropOutcome acceptRecipe(
        CoPilotHost host,
        DropTarget target,
        DragPayload payload,
        DropContext context
    ) {
        if (!(payload instanceof DragPayload.RecipeDrag drag)) {
            return DropOutcome.rejected();
        }
        boolean matches = switch (target) {
            case craftStep -> {
                CraftStep step = craftStep(host, context.targetStepId());
                yield step != null && step.recipeKey().equals(drag.recipeKey());
            }
            case acquireStep -> {
                AcquireStep step = acquireStep(host, context.targetStepId());
                yield step != null && step.resource().sameBaseIdentity(drag.outputResource());
            }
            default -> false;
        };
        if (!matches) {
            return DropOutcome.rejected();
        }
        host.refine(
            new PinResourceRecipeFeedback(drag.outputResource(), drag.recipeKey(), context.persistToWorld()),
            null
        );
        return DropOutcome.accepted(CirrusLangs.Workbench.Dnd.dropPinRecipe);
    }

    public static DropOutcome acceptCompare(
        CoPilotHost host,
        DropTarget target,
        DragPayload payload,
        DropContext context
    ) {
        if (!(payload instanceof DragPayload.PlanCompareDrag drag)) {
            return DropOutcome.rejected();
        }
        if (target != DropTarget.compareSlot || drag.planId().isBlank()) {
            return DropOutcome.rejected();
        }
        host.setComparePlan(drag.planId());
        return DropOutcome.accepted(CirrusLangs.Workbench.Dnd.dropComparePlan);
    }

    public static boolean consumable(
        @Nullable DragPayload payload,
        boolean cancelled,
        @Nullable FloatPos current,
        @Nullable Rect zone
    ) {
        if (cancelled || payload == null || current == null || zone == null) {
            return false;
        }
        return zone.contains(current.x(), current.y());
    }

    public static GuidanceScope scopeFor(boolean persistToWorld) {
        return persistToWorld ? GuidanceScope.worldSave : GuidanceScope.session;
    }

    public static ResourceRule freeSourceRule(ResourceKey resource, boolean persistToWorld) {
        return new ResourceRule(
            ruleId("free", resource),
            scopeFor(persistToWorld),
            RuleStrength.hardPin,
            resource,
            true
        );
    }

    public static ResourceRule banRule(ResourceKey resource, boolean persistToWorld) {
        return new ResourceRule(
            ruleId("ban", resource),
            scopeFor(persistToWorld),
            RuleStrength.hardBan,
            resource,
            false
        );
    }

    public static String ruleId(String kind, ResourceKey resource) {
        Objects.requireNonNull(resource, "resource");
        String subject = (resource.contentTypeId() + "_" + resource.resourceId());
        if (resource.canonicalVariantHash().isPresent()) {
            subject = subject + "_" + resource.canonicalVariantHash().get();
        }
        return ruleId(kind, subject);
    }

    public static String ruleId(String kind, String subject) {
        String normalized = subject == null || subject.isBlank() ? "none" : subject;
        return "cirrus:dnd/" + kind + "/"
                + normalized.toLowerCase(Locale.ROOT).replace(':', '_').replace(' ', '_').replace('/', '_');
    }

    public static @Nullable PlanStep findStep(CoPilotHost host, @Nullable String stepId) {
        if (host == null || stepId == null || stepId.isEmpty()) {
            return null;
        }
        Optional<ProductionPlan> plan = host.snapshot().selectedPlan();
        if (plan.isEmpty()) {
            return null;
        }
        for (PlanStep step : plan.get().steps()) {
            if (step.stepId().equals(stepId)) {
                return step;
            }
        }
        return null;
    }

    private static @Nullable AcquireStep acquireStep(CoPilotHost host, @Nullable String stepId) {
        return findStep(host, stepId) instanceof AcquireStep acquire ? acquire : null;
    }

    private static @Nullable CraftStep craftStep(CoPilotHost host, @Nullable String stepId) {
        return findStep(host, stepId) instanceof CraftStep craft ? craft : null;
    }

    private static @Nullable ProvisionMachineStep provisionStep(CoPilotHost host, @Nullable String stepId) {
        return findStep(host, stepId) instanceof ProvisionMachineStep provision ? provision : null;
    }

    /** Drag payload carrier; outside DragProvider/DragConsumer it only carries the payload and the cancel flag. */
    public static final class Carried implements DraggableElement<Widget> {

        private final Widget widget;
        private final Rect bounds;
        private final DragPayload payload;
        private boolean cancelled;

        private Carried(Widget widget, Rect bounds, DragPayload payload) {
            this.widget = widget;
            this.bounds = bounds;
            this.payload = payload;
        }

        public static Carried of(Widget widget, DragPayload payload) {
            Objects.requireNonNull(widget, "widget");
            Objects.requireNonNull(payload, "payload");
            return new Carried(widget, widget.absoluteBounds().copy(), payload);
        }

        public DragPayload payload() {
            return payload;
        }

        public void cancel() {
            cancelled = true;
        }

        public boolean cancelled() {
            return cancelled;
        }

        @Override
        public Widget value() {
            return widget;
        }

        @Override
        public Rect originalBounds() {
            return bounds;
        }

        @Override
        public void dragStart(InputContext input, DragContext context) {
            widget.setDragging(true);
        }

        @Override
        public void dragEnd(InputContext input, DragContext context, double deltaX, double deltaY, boolean consumed) {
            widget.setDragging(false);
        }

        @Override
        public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
            widget.render(graphics, mouseX, mouseY, partialTicks);
        }
    }

    public static DragProvider provider(Widget source, Supplier<@Nullable DragPayload> payload) {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(payload, "payload");
        return new DragProvider() {
            @Override
            public boolean draggable(@Nullable Scene scene, InputContext input, DragContext dragContext) {
                return payload.get() != null && source.parent() != null && source.isMouseOver(input);
            }

            @Override
            public @Nullable DraggableElement<?> getDraggableElement(
                @Nullable Scene scene,
                InputContext input,
                DragContext dragContext
            ) {
                DragPayload settled = payload.get();
                if (settled == null || source.parent() == null || !source.isMouseOver(input)) {
                    return null;
                }
                return Carried.of(source, settled);
            }
        };
    }

    public static DragConsumer consumer(Widget zone, Consumer<Carried> onDrop) {
        Objects.requireNonNull(zone, "zone");
        Objects.requireNonNull(onDrop, "onDrop");
        return new DragConsumer() {
            @Override
            public boolean consume(DraggableElement<?> element, DragContext context) {
                if (!(element instanceof Carried carried)) {
                    return false;
                }
                if (carried.cancelled()) {
                    return true;
                }
                if (!consumable(carried.payload(), false, context.getCurrent(), zone.absoluteBounds())) {
                    return false;
                }
                onDrop.accept(carried);
                return true;
            }
        };
    }

    /**
     * Attaches a drag provider from an {@code onMount} handler, where the widget itself is not yet mounted.
     *
     * <p>{@link Widget#performers()} refuses to answer before the mount callback returns, so the container is taken from the
     * scene root, which the breadth-first mount walk always reaches first.
     */
    public static void attachProvider(Scene scene, Widget widget, SceneHandle handle, DragProvider provider) {
        PerformerContainer container = scene.root().performers();
        container.addWeak(DragProvider.scenario, provider, widget);
        handle.onCleanup(() -> container.remove(DragProvider.scenario, provider));
    }

    /** @see #attachProvider(Scene, Widget, SceneHandle, DragProvider) */
    public static void attachConsumer(Scene scene, Widget widget, SceneHandle handle, DragConsumer consumer) {
        PerformerContainer container = scene.root().performers();
        container.addWeak(DragConsumer.scenario, consumer, widget);
        handle.onCleanup(() -> container.remove(DragConsumer.scenario, consumer));
    }

    /** Attaches a drag provider from anywhere the widget is already mounted. */
    public static void attachProvider(Widget widget, SceneHandle handle, DragProvider provider) {
        attachProvider(widget.scene(), widget, handle, provider);
    }

    /** Attaches a drag consumer from anywhere the widget is already mounted. */
    public static void attachConsumer(Widget widget, SceneHandle handle, DragConsumer consumer) {
        attachConsumer(widget.scene(), widget, handle, consumer);
    }
}
