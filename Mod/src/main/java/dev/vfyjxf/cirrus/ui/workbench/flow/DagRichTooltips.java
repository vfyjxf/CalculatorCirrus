package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagNodeBox;
import dev.vfyjxf.cirrus.ui.workbench.state.PlanVisualResolver;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeVisual;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeVisualRegistry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Rich OreUI tooltip builders for DAG nodes, stage locator blocks, selection preview controls, and flow capsule actions.
 */
public final class DagRichTooltips {

    private DagRichTooltips() {}

    public static OreUiRichTooltip stepNode(
        DagNodeBox node,
        StepNodeCardRenderer.StepVisuals visuals,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        boolean critical,
        boolean hasBeacon,
        int upstreamCount,
        int downstreamCount
    ) {
        PlanStep step = node.step();
        CirrusPalette.StepTint tint = palette.stepTint(step);
        Component title = stepTitle(step, visuals, resolver);
        String subtitle = stepSubtitle(node, visuals, resolver);
        OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(title).icon(primaryIconOf(step, visuals))
                .subtitle(subtitle).badge(kindTag(step), tint.accent());

        // Graphical recipe / conversion flow strip when inputs or outputs exist.
        if (!visuals.inputAmounts().isEmpty() || !visuals.inputs().isEmpty()) {
            List<OreUiRichTooltip.ItemBadge> inputs = new ArrayList<>();
            if (!visuals.inputAmounts().isEmpty()) {
                for (StepNodeCardRenderer.InputAmount entry : visuals.inputAmounts()) {
                    inputs.add(
                        OreUiRichTooltip.ItemBadge.of(
                            entry.resource(),
                            entry.amount() > 0L ? "\u00d7" + WorkbenchFormatters.formatAmount(entry.amount()) : null
                        )
                    );
                }
            } else {
                for (var resource : visuals.inputs()) {
                    inputs.add(OreUiRichTooltip.ItemBadge.of(resource, null));
                }
            }
            List<OreUiRichTooltip.ItemBadge> outputs = new ArrayList<>();
            if (visuals.primaryOutput() != null) {
                long outAmt = visuals.outputAmount() > 0L ? visuals.outputAmount() : visuals.count();
                outputs.add(
                    OreUiRichTooltip.ItemBadge.of(
                        visuals.primaryOutput(),
                        outAmt > 0L ? "\u00d7" + WorkbenchFormatters.formatAmount(outAmt) : null
                    )
                );
            }
            builder.itemFlow(Component.literal("Flow"), inputs, outputs);
        }

        // Semantic metric pills.
        List<OreUiRichTooltip.Pill> pills = new ArrayList<>();
        double work = step.stepCost().humanActiveSeconds();
        double wait = step.stepCost().passiveWaitSeconds();
        double equip = step.stepCost().machineSetupCapital();
        if (work > 0.0) {
            pills.add(
                OreUiRichTooltip.Pill.of("Work " + WorkbenchFormatters.formatSeconds(work), OreUiRichTooltip.Tone.costH)
            );
        }
        if (wait > 0.0) {
            pills.add(
                OreUiRichTooltip.Pill.of("Wait " + WorkbenchFormatters.formatSeconds(wait), OreUiRichTooltip.Tone.costP)
            );
        }
        if (equip > 0.0) {
            pills.add(OreUiRichTooltip.Pill.of("Equip " + CoPilotPaint.decimal(equip, 1), OreUiRichTooltip.Tone.costK));
        }
        if (critical) {
            pills.add(OreUiRichTooltip.Pill.colored(Component.literal("Critical"), palette.edgeCriticalPath()));
        }
        if (hasBeacon) {
            pills.add(OreUiRichTooltip.Pill.of("Decision !", OreUiRichTooltip.Tone.warning));
        }
        for (StepNodeCardRenderer.ValueTelemetry val : visuals.recipeValues()) {
            ValueTypeVisual visual = ValueTypeVisualRegistry.resolve(val.typeId());
            pills.add(
                OreUiRichTooltip.Pill.colored(
                    Component.literal(visual.formatCompact(val.totalAmount())),
                    visual.accentColor(palette)
                )
            );
        }
        if (!pills.isEmpty()) {
            builder.pills(pills);
        }

        if (visuals.machine() != null && !visuals.machine().isEmpty()) {
            builder.keyValue(
                Component.literal("Machine"),
                CoPilotPaint.machineName(visuals.machine()),
                OreUiRichTooltip.Tone.primary
            );
        }
        builder.keyValue(
            Component.literal("Topology"),
            Component.literal("\u2191 " + upstreamCount + " in \u00b7 \u2193 " + downstreamCount + " out"),
            OreUiRichTooltip.Tone.secondary
        );
        builder.footer("Click inspect \u00b7 Drag move \u00b7 Right-click actions");
        return builder.build();
    }

    public static OreUiRichTooltip stageSummary(
        int displayStageIndex,
        List<DagNodeBox> nodes,
        Map<String, StepNodeCardRenderer.StepVisuals> visuals,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        Set<String> criticalStepIds,
        Map<String, String> beacons,
        double planTotalTime
    ) {
        String stageCode = String.format("STAGE %02d", displayStageIndex + 1);
        String role = stageRoleLabel(nodes);
        boolean hasCritical = false;
        boolean hasBeacon = false;
        double totalWork = 0.0;
        double totalWait = 0.0;
        int accent = palette.focusRing();
        List<OreUiRichTooltip.ItemEntry> rows = new ArrayList<>(nodes.size());
        for (DagNodeBox node : nodes) {
            PlanStep step = node.step();
            StepNodeCardRenderer.StepVisuals data = visuals
                    .getOrDefault(node.stepId(), StepNodeCardRenderer.StepVisuals.empty());
            CirrusPalette.StepTint tint = palette.stepTint(step);
            if (rows.isEmpty()) {
                accent = tint.accent();
            }
            if (criticalStepIds.contains(node.stepId())) {
                hasCritical = true;
            }
            if (beacons.containsKey(node.stepId())) {
                hasBeacon = true;
            }
            totalWork += step.stepCost().humanActiveSeconds();
            totalWait += step.stepCost().passiveWaitSeconds();
            Component name = stepTitle(step, data, resolver);
            String trailing = kindShort(step) + " \u00b7 " + WorkbenchFormatters
                    .formatSeconds(step.stepCost().humanActiveSeconds() + step.stepCost().passiveWaitSeconds());
            rows.add(
                OreUiRichTooltip.ItemEntry.of(
                    CoPilotPaint.stackOf(primaryIconOf(step, data), 1L),
                    name,
                    Component.literal(trailing),
                    tint.accent()
                )
            );
        }

        OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(stageCode + " \u00b7 " + role)
                .subtitle(nodes.size() + (nodes.size() == 1 ? " step in stage" : " steps in stage")).badge(
                    hasCritical ? "CRITICAL" : nodes.size() + " STEPS",
                    hasCritical ? palette.edgeCriticalPath() : accent
                );

        builder.itemList(Component.literal("Steps"), rows, 5);

        double stageTime = totalWork + totalWait;
        double share = planTotalTime > 0.0 ? Math.min(1.0, stageTime / planTotalTime) : 0.0;
        builder.meter(
            Component.literal("Stage Time Share"),
            share,
            Component.literal(WorkbenchFormatters.formatSeconds(stageTime) + " (" + CoPilotPaint.percent(share) + ")"),
            hasCritical ? palette.edgeCriticalPath() : accent
        );

        List<OreUiRichTooltip.Pill> pills = new ArrayList<>();
        pills.add(
            OreUiRichTooltip.Pill
                    .of("Work " + WorkbenchFormatters.formatSeconds(totalWork), OreUiRichTooltip.Tone.costH)
        );
        pills.add(
            OreUiRichTooltip.Pill
                    .of("Wait " + WorkbenchFormatters.formatSeconds(totalWait), OreUiRichTooltip.Tone.costP)
        );
        if (hasBeacon) {
            pills.add(OreUiRichTooltip.Pill.of("Needs Choice", OreUiRichTooltip.Tone.warning));
        }
        builder.pills(pills);
        builder.footer("Click stage to frame \u00b7 Click icon to focus");
        return builder.build();
    }

    public static OreUiRichTooltip capsuleAction(SpatialDagCanvasWidget.CapsuleAction action) {
        return switch (action) {
            case viewDag ->
                OreUiRichTooltip.builder("Spatial Flow Graph").badge("VIEW [1]", OreUiRichTooltip.Tone.accent)
                        .text("Interactive node & conduit production blueprint.", OreUiRichTooltip.Tone.secondary)
                        .footer("Shortcut: Key 1").build();
            case viewLedger ->
                OreUiRichTooltip.builder("Preparation Ledger").badge("VIEW [2]", OreUiRichTooltip.Tone.costS)
                        .text(
                            "Raw materials, owned stock progress, and machine checklist.",
                            OreUiRichTooltip.Tone.secondary
                        ).footer("Shortcut: Key 2").build();
            case viewChecklist ->
                OreUiRichTooltip.builder("Execution Checklist").badge("VIEW [3]", OreUiRichTooltip.Tone.positive)
                        .text("Step-by-step build order ready to send to HUD overlay.", OreUiRichTooltip.Tone.secondary)
                        .footer("Shortcut: Key 3").build();
            case layoutLr -> OreUiRichTooltip.builder("Left \u2192 Right Layout")
                    .badge("LAYOUT", OreUiRichTooltip.Tone.accent)
                    .text("Flow progresses left to right with bottom stage locator.", OreUiRichTooltip.Tone.secondary)
                    .footer("Shortcut: R cycles layout").build();
            case layoutTb ->
                OreUiRichTooltip.builder("Top \u2193 Bottom Layout").badge("LAYOUT", OreUiRichTooltip.Tone.accent)
                        .text("Vertical tree flow with left IDE-style stage gutter.", OreUiRichTooltip.Tone.secondary)
                        .footer("Shortcut: R cycles layout").build();
            case layoutRl -> OreUiRichTooltip.builder("Right \u2190 Left Layout")
                    .badge("LAYOUT", OreUiRichTooltip.Tone.accent)
                    .text("Target anchored on the left, dependencies fanning right.", OreUiRichTooltip.Tone.secondary)
                    .footer("Shortcut: R cycles layout").build();
            case layoutBt ->
                OreUiRichTooltip.builder("Bottom \u2191 Top Layout").badge("LAYOUT", OreUiRichTooltip.Tone.accent)
                        .text("Raw materials at bottom climbing up to final targets.", OreUiRichTooltip.Tone.secondary)
                        .footer("Shortcut: R cycles layout").build();
            case layoutSwimlane ->
                OreUiRichTooltip.builder("System Swimlanes").badge("LAYOUT", OreUiRichTooltip.Tone.costC)
                        .text(
                            "Groups steps by machine system (Smelting, Crafting, Machines).",
                            OreUiRichTooltip.Tone.secondary
                        ).footer("Shortcut: R cycles layout").build();
            case edgeOrthogonal ->
                OreUiRichTooltip.builder("Orthogonal Conduits").badge("EDGES", OreUiRichTooltip.Tone.accent)
                        .text("Staggered bus lanes with crisp 45\u00b0 beveled turns.", OreUiRichTooltip.Tone.secondary)
                        .build();
            case edgeStraight ->
                OreUiRichTooltip.builder("Direct Straight Lines").badge("EDGES", OreUiRichTooltip.Tone.accent)
                        .text(
                            "Shortest direct connections with clean perpendicular port stubs.",
                            OreUiRichTooltip.Tone.secondary
                        ).build();
            case edgeCurved ->
                OreUiRichTooltip.builder("Curved Bezier Conduits").badge("EDGES", OreUiRichTooltip.Tone.accent)
                        .text("Smooth cubic S-curves fanned across distributed ports.", OreUiRichTooltip.Tone.secondary)
                        .build();
            case fit ->
                OreUiRichTooltip.builder("Fit Graph to Viewport").badge("CAMERA [F]", OreUiRichTooltip.Tone.positive)
                        .text("Smoothly frames all stages inside the active canvas.", OreUiRichTooltip.Tone.secondary)
                        .footer("Shortcut: F").build();
            case zoomReadout -> OreUiRichTooltip.builder("Canvas Zoom Level").badge("ZOOM", OreUiRichTooltip.Tone.muted)
                    .text("Scroll wheel to zoom around cursor; middle-drag to pan.", OreUiRichTooltip.Tone.secondary)
                    .build();
            case autoLayout ->
                OreUiRichTooltip.builder("Reset Manual Offsets").badge("RESET [L]", OreUiRichTooltip.Tone.warning)
                        .text(
                            "Clears manually dragged node positions back to auto layout.",
                            OreUiRichTooltip.Tone.secondary
                        ).footer("Shortcut: L").build();
            case viewMenu ->
                OreUiRichTooltip.builder("Layout & View Settings").badge("CONTROLS", OreUiRichTooltip.Tone.accent)
                        .text(
                            "Switch flow direction, conduit style, trackpad/mouse mode, minimap and reset controls.",
                            OreUiRichTooltip.Tone.secondary
                        ).footer("Click to open view menu").build();
        };
    }

    public static @Nullable ComponentStackKey primaryIconOf(PlanStep step, StepNodeCardRenderer.StepVisuals visuals) {
        if (visuals.primaryOutput() != null) {
            return visuals.primaryOutput();
        }
        if (step instanceof AcquireStep acquire) {
            return acquire.resource();
        }
        if (step instanceof LinePullStep pull) {
            return pull.resource();
        }
        if (step instanceof SetupLineStep setup) {
            return setup.targetOutput();
        }
        if (step instanceof ProvisionMachineStep provision) {
            return provision.machine().contentKey().orElse(null);
        }
        if (!visuals.outputs().isEmpty()) {
            return visuals.outputs().get(0);
        }
        if (!visuals.inputs().isEmpty()) {
            return visuals.inputs().get(0);
        }
        return null;
    }

    public static Component stepTitle(
        PlanStep step,
        StepNodeCardRenderer.StepVisuals visuals,
        @Nullable PlanVisualResolver resolver
    ) {
        if (step instanceof ProvisionMachineStep provision) {
            return resolver != null
                    ? resolver.machineNameOf(provision.machine())
                    : CoPilotPaint.machineName(provision.machine());
        }
        var icon = primaryIconOf(step, visuals);
        Component base = icon == null
                ? Component.literal(CoPilotPaint.humanizeId(step.stepId()))
                : resolver != null ? StepNodeCardRenderer.nameOf(icon, resolver) : CoPilotPaint.resourceName(icon);
        long amt = visuals.outputAmount() > 0L ? visuals.outputAmount() : visuals.count();
        if (amt > 1L) {
            return base.copy().append(Component.literal(" \u00d7" + WorkbenchFormatters.formatAmount(amt)));
        }
        return base;
    }

    private static String stepSubtitle(
        DagNodeBox node,
        StepNodeCardRenderer.StepVisuals visuals,
        @Nullable PlanVisualResolver resolver
    ) {
        String stage = String.format("Stage %02d", node.layer() + 1);
        if (visuals.machine() != null && !visuals.machine().isEmpty()) {
            Component mName = resolver != null
                    ? resolver.machineNameOf(visuals.machine())
                    : CoPilotPaint.machineName(visuals.machine());
            return stage + " \u00b7 " + mName.getString();
        }
        if (node.step() instanceof AcquireStep acquire) {
            return stage + " \u00b7 " + CoPilotPaint.humanizeId(acquire.acquisitionKind().name());
        }
        if (node.step() instanceof SetupLineStep setup) {
            return stage + " \u00b7 " + CoPilotPaint.humanizeId(setup.systemId().toString());
        }
        return stage + " \u00b7 " + node.stepId();
    }

    public static Component kindTag(PlanStep step) {
        return Component.literal(kindShort(step));
    }

    public static String kindShort(PlanStep step) {
        return switch (step) {
            case AcquireStep ignored -> "ACQUIRE";
            case CraftStep ignored -> "CRAFT";
            case ConvertStep ignored -> "CONVERT";
            case CycleLoopStep ignored -> "CYCLE";
            case ProvisionMachineStep ignored -> "BUILD";
            case SetupLineStep ignored -> "AUTO LINE";
            case LinePullStep ignored -> "PULL";
        };
    }

    private static String stageRoleLabel(List<DagNodeBox> nodes) {
        boolean hasAcquire = false;
        boolean hasBuild = false;
        boolean hasCraft = false;
        boolean hasLine = false;
        for (DagNodeBox node : nodes) {
            PlanStep step = node.step();
            if (step instanceof AcquireStep || step instanceof LinePullStep) {
                hasAcquire = true;
            } else if (step instanceof ProvisionMachineStep) {
                hasBuild = true;
            } else if (step instanceof SetupLineStep) {
                hasLine = true;
            } else {
                hasCraft = true;
            }
        }
        if (hasAcquire && hasBuild && !hasCraft) {
            return "Inputs & Machines";
        }
        if (hasAcquire && !hasCraft && !hasLine) {
            return "Raw Acquisition";
        }
        if (hasLine && !hasCraft) {
            return "Automation Setup";
        }
        if (hasCraft) {
            return "Processing";
        }
        return "Stage";
    }
}
