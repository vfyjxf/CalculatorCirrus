package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.planning.context.AcquisitionKind;
import dev.vfyjxf.cirrus.api.planning.graph.CatalystSlot;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipe;
import dev.vfyjxf.cirrus.api.planning.graph.CompiledRecipeGraph;
import dev.vfyjxf.cirrus.api.planning.graph.InputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.OutputSlot;
import dev.vfyjxf.cirrus.api.planning.graph.RecipeAdvisory;
import dev.vfyjxf.cirrus.api.planning.model.CompiledRecipeKey;
import dev.vfyjxf.cirrus.api.planning.plan.AcquireStep;
import dev.vfyjxf.cirrus.api.planning.plan.ConvertStep;
import dev.vfyjxf.cirrus.api.planning.plan.CraftStep;
import dev.vfyjxf.cirrus.api.planning.plan.CycleLoopStep;
import dev.vfyjxf.cirrus.api.planning.plan.LinePullStep;
import dev.vfyjxf.cirrus.api.planning.plan.PlanStep;
import dev.vfyjxf.cirrus.api.planning.plan.ProvisionMachineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SetupLineStep;
import dev.vfyjxf.cirrus.api.planning.plan.SlotBinding;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusThemeIcons;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchFormatters;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeButtons;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.layout.DagNodeBox;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cirrus.ui.workbench.paint.WorkbenchCountBadge;
import dev.vfyjxf.cirrus.ui.workbench.state.DagLodLevel;
import dev.vfyjxf.cirrus.ui.workbench.state.PlanVisualResolver;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeGaugeRenderer;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeVisualRegistry;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Draws the seven plan-step card anatomies at the three semantic zoom levels.
 */
public final class StepNodeCardRenderer {

    public static final int lod0Icon = 24;
    public static final int headerHeight = 14;
    public static final int slotLarge = 20;
    public static final int slotSmall = 18;
    public static final int machineIcon = 10;
    public static final int textInset = 7;

    private static final String keyHandCraft = "cirrus.ui.workbench.flow.node.hand_craft";
    private static final String keyAuto = "cirrus.ui.workbench.flow.node.auto";
    private static final String keyByproducts = "cirrus.ui.workbench.flow.node.byproducts";
    private static final String keyCraftedFirst = "cirrus.ui.workbench.flow.node.crafted_first";
    private static final String keyPlayerPrepared = "cirrus.ui.workbench.flow.node.player_prepared";
    private static final String keySetupLine = "cirrus.ui.workbench.flow.node.setup_line";
    private static final String keyLinePull = "cirrus.ui.workbench.flow.node.line_pull";
    private static final String keyReversible = "cirrus.ui.workbench.flow.node.reversible_pack";
    private static final String keyIterations = "cirrus.ui.workbench.flow.node.iterations";
    private static final String keyWorkingSeed = "cirrus.ui.workbench.flow.node.working_seed";
    private static final String keyNetDelta = "cirrus.ui.workbench.flow.node.net_delta";
    private static final String keyDeclareOwned = "cirrus.ui.workbench.action.declare_owned";

    private StepNodeCardRenderer() {}

    public record InputAmount(ComponentStackKey resource, long amount) {}

    public record ValueTelemetry(Namespace typeId, long totalAmount, long perBatchAmount) {}

    public record StepVisuals(
        @Nullable ComponentStackKey primaryOutput,
        List<ComponentStackKey> outputs,
        List<ComponentStackKey> inputs,
        List<InputAmount> inputAmounts,
        List<ComponentStackKey> catalysts,
        List<ValueTelemetry> recipeValues,
        @Nullable RecipeAdvisory advisory,
        @Nullable MachineEntry machine,
        boolean auto,
        boolean hardPinned,
        long batchCount,
        long count,
        long outputAmount,
        @Nullable AcquisitionKind acquisitionKind,
        @Nullable Namespace systemId,
        @Nullable String lineId,
        long iterations,
        Map<ComponentStackKey, Long> workingSeed,
        Map<ComponentStackKey, Long> netDelta
    ) {

        public StepVisuals {
            outputs = outputs == null ? List.of() : List.copyOf(outputs);
            inputs = inputs == null ? List.of() : List.copyOf(inputs);
            inputAmounts = inputAmounts == null ? List.of() : List.copyOf(inputAmounts);
            catalysts = catalysts == null ? List.of() : List.copyOf(catalysts);
            recipeValues = recipeValues == null ? List.of() : List.copyOf(recipeValues);
            workingSeed = workingSeed == null ? Map.of() : Map.copyOf(workingSeed);
            netDelta = netDelta == null ? Map.of() : Map.copyOf(netDelta);
        }

        public static StepVisuals empty() {
            return new StepVisuals(
                null,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                null,
                false,
                false,
                0L,
                0L,
                0L,
                null,
                null,
                null,
                0L,
                Map.of(),
                Map.of()
            );
        }

        public int secondaryOutputs() {
            return Math.max(0, outputs.size() - 1);
        }
    }

    public record Hotspots(DagHitTester.@Nullable RectF expandButton, DagHitTester.@Nullable RectF declareOwnedButton) {

        public static final Hotspots none = new Hotspots(null, null);
    }

    public record CardState(
        boolean hovered,
        boolean selected,
        boolean dimmed,
        boolean tracedUpstream,
        boolean tracedDownstream
    ) {

        public static final CardState idle = new CardState(false, false, false, false, false);
    }

    public static StepVisuals resolve(
        PlanStep step,
        @Nullable CompiledRecipeGraph graph,
        @Nullable ComponentStackKey inferredOutput,
        boolean hardPinned
    ) {
        if (step == null) {
            return StepVisuals.empty();
        }
        RecipeAdvisory advisory = RecipeAdvisory.none;
        List<ComponentStackKey> inputs = new ArrayList<>();
        List<InputAmount> inputAmounts = new ArrayList<>();
        List<ComponentStackKey> outputs = new ArrayList<>();
        List<ComponentStackKey> catalysts = new ArrayList<>();
        List<ValueTelemetry> recipeValues = new ArrayList<>();
        MachineEntry machine = null;
        long batches = 0L;
        long outputAmount = countOf(step);
        switch (step) {
            case AcquireStep acquire -> outputs.add(acquire.resource());
            case LinePullStep pull -> outputs.add(pull.resource());
            case SetupLineStep setup -> {
                machine = setup.primaryMachine().isEmpty() ? null : setup.primaryMachine();
                outputs.add(setup.targetOutput());
            }
            case ProvisionMachineStep provision -> {
                machine = provision.machine().isEmpty() ? null : provision.machine();
                provision.machine().contentKey().ifPresent(outputs::add);
            }
            case CraftStep craft -> {
                batches = craft.batches();
                machine = craft.chosenMachine().filter(m -> !m.isEmpty()).orElse(null);
                for (ComponentStackKey catalyst : craft.catalystBindings().values()) {
                    if (catalyst != null && !catalysts.contains(catalyst)) {
                        catalysts.add(catalyst);
                    }
                }
                CompiledRecipe recipe = recipeOf(graph, craft.recipeKey());
                if (recipe != null) {
                    advisory = recipe.advisory();
                    if (machine == null && !recipe.machineRequirement().compatibleMachines().isEmpty()) {
                        MachineEntry first = recipe.machineRequirement().compatibleMachines().get(0);
                        if (!first.isEmpty()) {
                            machine = first;
                        }
                    }
                    collectInputs(craft.slotBindings(), recipe.inputs(), batches, inputs, inputAmounts);
                    collectRecipeValues(recipe.recipeValues(), batches, recipeValues);
                    for (OutputSlot slot : recipe.outputs()) {
                        if (!outputs.contains(slot.resource())) {
                            outputs.add(slot.resource());
                        }
                    }
                    if (!recipe.outputs().isEmpty()) {
                        outputAmount = safeMul(recipe.outputs().get(0).amountPerBatch(), Math.max(1L, batches));
                    }
                    for (CatalystSlot slot : recipe.catalysts()) {
                        if (!slot.candidates().isEmpty() && !catalysts.contains(slot.candidates().get(0))) {
                            catalysts.add(slot.candidates().get(0));
                        }
                    }
                } else {
                    outputAmount = batches;
                }
            }
            case ConvertStep convert -> {
                batches = convert.batches();
                CompiledRecipe recipe = recipeOf(graph, convert.conversionRecipeKey());
                if (recipe != null) {
                    advisory = recipe.advisory();
                    collectInputs(convert.slotBindings(), recipe.inputs(), batches, inputs, inputAmounts);
                    collectRecipeValues(recipe.recipeValues(), batches, recipeValues);
                    for (OutputSlot slot : recipe.outputs()) {
                        if (!outputs.contains(slot.resource())) {
                            outputs.add(slot.resource());
                        }
                    }
                    if (!recipe.outputs().isEmpty()) {
                        outputAmount = safeMul(recipe.outputs().get(0).amountPerBatch(), Math.max(1L, batches));
                    }
                } else {
                    outputAmount = batches;
                }
            }
            case CycleLoopStep loop -> {
                batches = loop.iterations();
                for (Map.Entry<ComponentStackKey, Long> seed : loop.workingSeedAmounts().entrySet()) {
                    inputs.add(seed.getKey());
                    inputAmounts.add(new InputAmount(seed.getKey(), seed.getValue()));
                }
                for (Map.Entry<ComponentStackKey, Long> delta : loop.netDeltaPerIteration().entrySet()) {
                    if (!outputs.contains(delta.getKey())) {
                        outputs.add(delta.getKey());
                    }
                    if (outputAmount <= loop.iterations()) {
                        outputAmount = safeMul(delta.getValue(), Math.max(1L, loop.iterations()));
                    }
                }
            }
        }
        ComponentStackKey primary = outputs.isEmpty() ? inferredOutput : outputs.get(0);
        return new StepVisuals(
            primary,
            outputs,
            inputs,
            inputAmounts,
            catalysts,
            recipeValues,
            advisory,
            machine,
            step instanceof CraftStep craft && craft.executedByAutomation(),
            hardPinned,
            batches,
            countOf(step),
            outputAmount,
            step instanceof AcquireStep acquire ? acquire.acquisitionKind() : null,
            step instanceof SetupLineStep setup ? setup.systemId() : systemOf(step),
            step instanceof LinePullStep pull ? pull.lineId() : null,
            step instanceof CycleLoopStep loop ? loop.iterations() : 0L,
            step instanceof CycleLoopStep loop ? loop.workingSeedAmounts() : Map.of(),
            step instanceof CycleLoopStep loop ? loop.netDeltaPerIteration() : Map.of()
        );
    }

    private static void collectRecipeValues(Map<Namespace, Double> rawValues, long batches, List<ValueTelemetry> out) {
        if (rawValues == null || rawValues.isEmpty()) {
            return;
        }
        long safeBatches = Math.max(1L, batches);
        for (Map.Entry<Namespace, Double> entry : rawValues.entrySet()) {
            Double raw = entry.getValue();
            if (raw == null || !Double.isFinite(raw) || raw <= 0.0) {
                continue;
            }
            long perBatch = Math.max(1L, Math.round(raw));
            out.add(new ValueTelemetry(entry.getKey(), safeMul(perBatch, safeBatches), perBatch));
        }
    }

    private static void collectInputs(
        Map<Integer, SlotBinding> bindings,
        List<InputSlot> slots,
        long batches,
        List<ComponentStackKey> inputs,
        List<InputAmount> inputAmounts
    ) {
        long safeBatches = Math.max(1L, batches);
        for (InputSlot slot : slots) {
            ComponentStackKey chosen = resolveInput(bindings, slot);
            if (chosen == null) {
                continue;
            }
            long total = safeMul(slot.amountPerBatch(), safeBatches);
            int existing = inputs.indexOf(chosen);
            if (existing < 0) {
                inputs.add(chosen);
                inputAmounts.add(new InputAmount(chosen, total));
            } else {
                InputAmount prev = inputAmounts.get(existing);
                inputAmounts.set(existing, new InputAmount(chosen, safeAdd(prev.amount(), total)));
            }
        }
    }

    private static long safeMul(long a, long b) {
        try {
            return Math.multiplyExact(a, b);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    private static long safeAdd(long a, long b) {
        try {
            return Math.addExact(a, b);
        } catch (ArithmeticException overflow) {
            return Long.MAX_VALUE;
        }
    }

    public static Hotspots draw(
        SceneCanvas canvas,
        DagNodeBox node,
        DagLodLevel lod,
        CardState state,
        StepVisuals visuals,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        WorkbenchThemeMode theme,
        float alpha
    ) {
        if (canvas == null || node == null || palette == null) {
            return Hotspots.none;
        }
        DagLodLevel level = lod == null ? DagLodLevel.blueprint : lod;
        StepVisuals data = visuals == null ? StepVisuals.empty() : visuals;
        CardState visual = state == null ? CardState.idle : state;
        float dim = visual.dimmed() ? 0.30f : 1.0f;
        float fade = clampAlpha(alpha) * dim;
        CirrusPalette.StepTint tint = palette.stepTint(node.step());
        WorkbenchThemeMode themeMode = theme == null ? WorkbenchThemeMode.defaultMode() : theme;
        if (level == DagLodLevel.macro) {
            drawMacro(canvas, node, visual, data, resolver, palette, tint, fade);
        } else {
            drawCard(canvas, node, visual, data, resolver, palette, tint, themeMode, fade, level);
            if (level == DagLodLevel.microRecipe) {
                drawInlineRecipe(canvas, node, data, resolver, palette, tint, themeMode, fade);
            }
        }
        return new Hotspots(
            node.step() instanceof CycleLoopStep ? expandButton(node) : null,
            node.step() instanceof ProvisionMachineStep ? declareOwnedButton(node) : null
        );
    }

    public static DagHitTester.RectF expandButton(DagNodeBox node) {
        return new DagHitTester.RectF(node.maxX() - 16.0f, node.y() + 2.0f, 12.0f, 11.0f);
    }

    public static DagHitTester.RectF declareOwnedButton(DagNodeBox node) {
        return new DagHitTester.RectF(
            node.x() + 7.0f,
            node.maxY() - 18.0f,
            Math.max(48.0f, node.width() - 14.0f),
            14.0f
        );
    }

    private static void drawMacro(
        SceneCanvas canvas,
        DagNodeBox node,
        CardState state,
        StepVisuals visuals,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        CirrusPalette.StepTint tint,
        float fade
    ) {
        int x = Math.round(node.x());
        int y = Math.round(node.y());
        int width = Math.max(1, Math.round(node.width()));
        int height = Math.max(1, Math.round(node.height()));
        OreUiPaint.raisedCardWithAlpha(canvas, x, y, width, height, palette, state.hovered(), state.selected(), fade);
        drawAdvisoryBorder(canvas, visuals, palette, x, y, width, height, fade);
        if (state.tracedUpstream() || state.tracedDownstream()) {
            int color = state.tracedUpstream() ? palette.edgeUpstreamTrace() : palette.edgeDownstreamTrace();
            canvas.strokeRect(x - 1, y - 1, width + 2, height + 2, withAlpha(color, fade), 1);
        }

        int pad = 6;
        int slotDim = Math.min(34, Math.max(22, height - pad * 2));
        int slotX = x + pad;
        int slotY = y + Math.max(pad, (height - slotDim) / 2);
        ComponentStackKey iconRes = DagRichTooltips.primaryIconOf(node.step(), visuals);
        long amount = visuals.outputAmount() > 0L ? visuals.outputAmount() : visuals.count();
        if (amount <= 0L) {
            amount = visuals.batchCount();
        }
        OreUiPaint.slot(canvas, slotX, slotY, slotDim, palette);
        int iconDim = Math.max(16, slotDim - 4);
        drawResource(
            canvas,
            iconRes,
            resolver,
            palette,
            slotX + (slotDim - iconDim) / 2,
            slotY + (slotDim - iconDim) / 2,
            iconDim
        );

        int infoX = slotX + slotDim + 7;
        int infoW = Math.max(16, x + width - infoX - pad);
        if (infoW < 22) {
            if (amount > 1L) {
                drawBadge(canvas, amount, x + width - 3, y + height - 12, palette, fade, true);
            }
            return;
        }

        int topRowY = y + pad + 1;
        DagLocatorRulerWidget
                .drawStepKindGlyph(canvas, node.step(), infoX, topRowY + 1, withAlpha(tint.accent(), fade));
        double work = node.step().stepCost().humanActiveSeconds();
        double wait = node.step().stepCost().passiveWaitSeconds();
        double totalSec = work + wait;
        String durText = totalSec > 0.05 ? WorkbenchFormatters.formatSeconds(totalSec) : "";
        int durW = durText.isEmpty() ? 0 : ChromeText.width(canvas, Component.literal(durText)) + 4;
        if (!durText.isEmpty() && infoW >= 46) {
            ChromeText.drawRight(
                canvas,
                Component.literal(durText),
                x + width - pad,
                topRowY + 1,
                withAlpha(work >= wait ? palette.costH() : palette.costP(), fade)
            );
        }
        String countText = amount > 0L
                ? "\u00d7" + WorkbenchFormatters.formatAmount(amount)
                : DagRichTooltips.kindTag(node.step()).getString();
        Component countComp = Component.literal(countText);
        int countMaxW = Math.max(12, infoW - 12 - durW);
        ChromeText.draw(
            canvas,
            ChromeText.fit(canvas, countComp, countMaxW),
            infoX + 12,
            topRowY + 1,
            withAlpha(palette.textPrimary(), fade)
        );

        Component title = iconRes != null
                ? nameOf(iconRes, resolver)
                : node.step() instanceof ProvisionMachineStep prov
                        ? machineNameOf(prov.machine(), resolver)
                        : Component.literal(CoPilotPaint.humanizeId(node.stepId()));
        int midY = y + Math.max(topRowY - y + 13, height / 2 - 2);
        ChromeText.draw(
            canvas,
            Component.literal(ellipsize(canvas.font(), title.getString(), infoW)),
            infoX,
            midY,
            withAlpha(palette.textPrimary(), fade)
        );

        if (height >= 48) {
            int botY = y + height - pad - 9;
            String sub = visuals.machine() != null && !visuals.machine().isEmpty()
                    ? machineNameOf(visuals.machine(), resolver).getString()
                    : visuals.inputs().isEmpty()
                            ? DagRichTooltips.kindTag(node.step()).getString()
                            : visuals.inputs().size() + " in";
            ChromeText.draw(
                canvas,
                Component.literal(ellipsize(canvas.font(), sub, infoW)),
                infoX,
                botY,
                withAlpha(palette.textSecondary(), fade)
            );
        }
    }

    private static void drawCard(
        SceneCanvas canvas,
        DagNodeBox node,
        CardState state,
        StepVisuals visuals,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        CirrusPalette.StepTint tint,
        WorkbenchThemeMode theme,
        float fade,
        DagLodLevel level
    ) {
        int x = Math.round(node.x());
        int y = Math.round(node.y());
        int width = Math.max(1, Math.round(node.width()));
        int height = Math.max(1, Math.round(node.height()));
        OreUiPaint.raisedCardWithAlpha(canvas, x, y, width, height, palette, state.hovered(), state.selected(), fade);
        drawAdvisoryBorder(canvas, visuals, palette, x, y, width, height, fade);
        if (state.tracedUpstream() || state.tracedDownstream()) {
            int color = state.tracedUpstream() ? palette.edgeUpstreamTrace() : palette.edgeDownstreamTrace();
            canvas.strokeRect(x - 1, y - 1, width + 2, height + 2, withAlpha(color, fade), 1);
        }

        int inset = (state.selected() && OreUiPaint.isDark(palette)) ? 2 : 1;
        int innerW = Math.max(0, width - inset * 2);
        int headerY = y + inset;
        drawBand(canvas, x + inset, headerY, innerW, headerHeight, palette, fade, true);

        StepKind kind = StepKind.of(node.step());
        drawHeaderContent(canvas, node, kind, visuals, resolver, palette, tint, x + inset, headerY, innerW, fade);

        int bodyTop = headerY + headerHeight + 2;
        int capBottom = y + height - 3;
        switch (kind) {
            case acquire -> drawAcquireCard(
                canvas,
                node,
                visuals,
                resolver,
                palette,
                tint,
                x,
                bodyTop,
                width,
                capBottom - bodyTop,
                fade
            );
            case craft -> drawCraftCard(
                canvas,
                node,
                visuals,
                resolver,
                palette,
                tint,
                theme,
                x,
                bodyTop,
                width,
                capBottom,
                inset,
                fade
            );
            case convert -> drawConvertCard(
                canvas,
                node,
                visuals,
                resolver,
                palette,
                tint,
                x,
                bodyTop,
                width,
                capBottom,
                inset,
                fade
            );
            case cycleLoop -> drawCycleCard(
                canvas,
                node,
                visuals,
                resolver,
                palette,
                tint,
                x,
                bodyTop,
                width,
                capBottom,
                inset,
                fade
            );
            case provision ->
                drawProvisionCard(canvas, node, visuals, resolver, palette, tint, x, bodyTop, width, capBottom, fade);
            case setup -> drawSetupCard(
                canvas,
                node,
                visuals,
                resolver,
                palette,
                tint,
                x,
                bodyTop,
                width,
                capBottom,
                inset,
                fade
            );
            case linePull -> drawLinePullCard(
                canvas,
                node,
                visuals,
                resolver,
                palette,
                tint,
                x,
                bodyTop,
                width,
                capBottom - bodyTop,
                fade
            );
        }
        if (level == DagLodLevel.microRecipe) {
            canvas.strokeRect(x - 1, y - 1, width + 2, height + 2, withAlpha(tint.borderSelected(), fade), 1);
        }
    }

    private static void drawBand(
        SceneCanvas canvas,
        int x,
        int y,
        int width,
        int height,
        CirrusPalette palette,
        float fade,
        boolean header
    ) {
        if (width <= 0 || height <= 0) {
            return;
        }
        if (header) {
            canvas.fill(x, y, width, height, withAlpha(OreUiPaint.cardFooter(palette), fade));
            canvas.fill(x, y + height, width, 1, withAlpha(OreUiPaint.cardDivider(palette), fade));
        } else {
            canvas.fill(x, y, width, 1, withAlpha(OreUiPaint.cardDivider(palette), fade));
            if (height > 1) {
                canvas.fill(x, y + 1, width, height - 1, withAlpha(OreUiPaint.cardFooter(palette), fade));
            }
        }
    }

    private static void drawHeaderContent(
        SceneCanvas canvas,
        DagNodeBox node,
        StepKind kind,
        StepVisuals visuals,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        CirrusPalette.StepTint tint,
        int x,
        int y,
        int width,
        float fade
    ) {
        int textY = ChromeText.centeredY(y, headerHeight, canvas);
        Component kindLabel = kindLabel(kind);
        int leftX = x + 5;
        canvas.drawString(kindLabel, leftX, textY, withAlpha(tint.accent(), fade), false);
        int afterKind = leftX + canvas.font().width(kindLabel) + 4;
        if (visuals.hardPinned()) {
            ChromeButtons.lock(canvas, afterKind, y + 3, 7, withAlpha(palette.ruleHardPin(), fade));
            afterKind += 10;
        }
        int rightEdge = x + width - 5;
        int rightMaxW = Math.max(8, rightEdge - afterKind);
        switch (kind) {
            case acquire -> {
                Component source = acquireLabel(visuals.acquisitionKind());
                String fitted = ellipsize(canvas.font(), source.getString(), rightMaxW);
                canvas.drawString(
                    Component.literal(fitted),
                    rightEdge - canvas.font().width(fitted),
                    textY,
                    withAlpha(acquireColor(visuals.acquisitionKind(), palette), fade),
                    false
                );
            }
            case craft -> {
                int cursorRight = rightEdge;
                if (visuals.auto()) {
                    Component autoLabel = Component.translatable(keyAuto);
                    int pillW = canvas.font().width(autoLabel) + 6;
                    int pillX = cursorRight - pillW;
                    canvas.fill(pillX, y + 2, pillW, headerHeight - 4, withAlpha(palette.costS(), fade * 0.24f));
                    canvas.strokeRect(
                        pillX,
                        y + 2,
                        pillW,
                        headerHeight - 4,
                        withAlpha(palette.costS(), fade * 0.70f),
                        1
                    );
                    canvas.drawString(
                        autoLabel,
                        pillX + 3,
                        ChromeText.centeredY(y + 2, headerHeight - 4, canvas),
                        withAlpha(palette.costS(), fade),
                        false
                    );
                    cursorRight = pillX - 4;
                }
                int machineMaxW = Math.max(8, cursorRight - afterKind - 13);
                Component machine = visuals.machine() == null || visuals.machine().isEmpty()
                        ? Component.translatable(keyHandCraft)
                        : machineNameOf(visuals.machine(), resolver);
                String fitted = ellipsize(canvas.font(), machine.getString(), machineMaxW);
                int textStartX = cursorRight - canvas.font().width(fitted);
                if (visuals.machine() != null && !visuals.machine().isEmpty() && textStartX - 13 >= afterKind) {
                    drawResource(
                        canvas,
                        visuals.machine().contentKey().orElse(null),
                        resolver,
                        palette,
                        textStartX - 13,
                        y + Math.max(1, (headerHeight - 10) / 2),
                        10
                    );
                }
                canvas.drawString(
                    Component.literal(fitted),
                    textStartX,
                    textY,
                    withAlpha(palette.textSecondary(), fade),
                    false
                );
            }
            case convert -> {
                Component label = Component.translatable(keyReversible);
                String fitted = ellipsize(canvas.font(), label.getString(), rightMaxW);
                canvas.drawString(
                    Component.literal(fitted),
                    rightEdge - canvas.font().width(fitted),
                    textY,
                    withAlpha(palette.textSecondary(), fade),
                    false
                );
            }
            case cycleLoop -> {
                DagHitTester.RectF expand = expandButton(node);
                int bx = Math.round(expand.x());
                int by = Math.round(expand.y());
                int bw = Math.round(expand.width());
                int bh = Math.round(expand.height());
                canvas.fill(bx, by, bw, bh, withAlpha(palette.cardSurface(), fade));
                canvas.strokeRect(bx, by, bw, bh, withAlpha(tint.accent(), fade * 0.80f), 1);
                ChromeButtons.plus(canvas, bx + (bw - 5) / 2, by + (bh - 5) / 2, 5, withAlpha(tint.accent(), fade));
                Component rounds = Component.translatable(keyIterations, visuals.iterations());
                int maxW = Math.max(8, bx - 4 - afterKind);
                String fitted = ellipsize(canvas.font(), rounds.getString(), maxW);
                canvas.drawString(
                    Component.literal(fitted),
                    bx - 4 - canvas.font().width(fitted),
                    textY,
                    withAlpha(palette.textSecondary(), fade),
                    false
                );
            }
            case provision -> {
                ProvisionMachineStep provision = (ProvisionMachineStep) node.step();
                Component status = provision.craftedByPredecessorSteps()
                        ? Component.translatable(keyCraftedFirst)
                        : Component.translatable(keyPlayerPrepared);
                String fitted = ellipsize(canvas.font(), status.getString(), rightMaxW);
                canvas.drawString(
                    Component.literal(fitted),
                    rightEdge - canvas.font().width(fitted),
                    textY,
                    withAlpha(palette.textSecondary(), fade),
                    false
                );
            }
            case setup -> {
                if (visuals.systemId() != null) {
                    String fitted = ellipsize(
                        canvas.font(),
                        CoPilotPaint.humanizeId(visuals.systemId().toString()),
                        rightMaxW
                    );
                    canvas.drawString(
                        Component.literal(fitted),
                        rightEdge - canvas.font().width(fitted),
                        textY,
                        withAlpha(palette.textSecondary(), fade),
                        false
                    );
                }
            }
            case linePull -> {
                if (visuals.lineId() != null) {
                    String fitted = ellipsize(canvas.font(), CoPilotPaint.humanizeId(visuals.lineId()), rightMaxW);
                    canvas.drawString(
                        Component.literal(fitted),
                        rightEdge - canvas.font().width(fitted),
                        textY,
                        withAlpha(palette.textSecondary(), fade),
                        false
                    );
                }
            }
        }
    }

    private static void drawAcquireCard(
        SceneCanvas canvas,
        DagNodeBox node,
        StepVisuals visuals,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        CirrusPalette.StepTint tint,
        int x,
        int bodyTop,
        int width,
        int bodyHeight,
        float fade
    ) {
        int slotX = x + textInset;
        int slotY = bodyTop + Math.max(2, (bodyHeight - slotSmall) / 2);
        drawSlot(canvas, palette, slotX, slotY, slotSmall, fade);
        drawResource(canvas, visuals.primaryOutput(), resolver, palette, slotX + 1, slotY + 1, slotSmall - 2);
        int textX = slotX + slotSmall + 6;
        int textW = Math.max(8, x + width - textInset - textX);
        Component title = titleOf(node, visuals, resolver);
        canvas.drawString(
            Component.literal(ellipsize(canvas.font(), title.getString(), textW)),
            textX,
            bodyTop + 6,
            withAlpha(palette.textPrimary(), fade),
            false
        );
        Component time = formatStepTime(node);
        int timeW = canvas.font().width(time);
        long amount = Math.max(1L, visuals.count());
        String qty = "×" + WorkbenchFormatters.formatAmount(amount);
        canvas.drawString(qty, textX, bodyTop + 21, withAlpha(tint.accent(), fade), false);
        if (textX + canvas.font().width(qty) + 6 + timeW <= x + width - textInset) {
            canvas.drawString(
                time,
                x + width - textInset - timeW,
                bodyTop + 21,
                withAlpha(palette.textMuted(), fade),
                false
            );
        }
    }

    private static void drawCraftCard(
        SceneCanvas canvas,
        DagNodeBox node,
        StepVisuals visuals,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        CirrusPalette.StepTint tint,
        WorkbenchThemeMode theme,
        int x,
        int bodyTop,
        int width,
        int capBottom,
        int inset,
        float fade
    ) {
        int footerH = 19;
        int footerY = capBottom - footerH;
        int bodyH = Math.max(24, footerY - bodyTop);
        int slotX = x + textInset;
        int slotY = bodyTop + Math.max(3, (bodyH - slotLarge) / 2);
        drawSlot(canvas, palette, slotX, slotY, slotLarge, fade);
        drawResource(canvas, visuals.primaryOutput(), resolver, palette, slotX + 2, slotY + 2, 16);
        int textX = slotX + slotLarge + 6;
        int textW = Math.max(8, x + width - textInset - textX);

        Component title = titleOf(node, visuals, resolver);
        canvas.drawString(
            Component.literal(ellipsize(canvas.font(), title.getString(), textW)),
            textX,
            bodyTop + 3,
            withAlpha(palette.textPrimary(), fade),
            false
        );

        long amount = visuals.outputAmount() > 0L ? visuals.outputAmount() : Math.max(1L, visuals.batchCount());
        String qty = "×" + WorkbenchFormatters.formatAmount(amount);
        canvas.drawString(qty, textX, bodyTop + 16, withAlpha(tint.accent(), fade), false);
        int cursorX = textX + canvas.font().width(qty) + 6;
        if (visuals.batchCount() > 1L && visuals.batchCount() != amount) {
            Component batches = CirrusLangs.Workbench.Flow.batchesSuffix
                    .get(WorkbenchFormatters.formatAmount(visuals.batchCount()));
            String fittedBatches = ellipsize(
                canvas.font(),
                batches.getString(),
                Math.max(0, x + width - textInset - cursorX)
            );
            if (!fittedBatches.isEmpty()) {
                canvas.drawString(fittedBatches, cursorX, bodyTop + 16, withAlpha(palette.textMuted(), fade), false);
                cursorX += canvas.font().width(fittedBatches) + 4;
            }
        }
        if (visuals.secondaryOutputs() > 0) {
            Component byp = Component.translatable(keyByproducts, visuals.secondaryOutputs());
            int bypW = canvas.font().width(byp);
            if (cursorX + bypW <= x + width - textInset) {
                canvas.drawString(
                    byp,
                    x + width - textInset - bypW,
                    bodyTop + 16,
                    withAlpha(palette.costS(), fade),
                    false
                );
            }
        }

        int catReserve = 0;
        if (!visuals.catalysts().isEmpty()) {
            int dots = Math.min(3, visuals.catalysts().size());
            catReserve = dots * 7 + 4;
            int catColor = withAlpha(palette.edgeCatalyst(), fade);
            int catX = x + width - textInset - dots * 7;
            for (int index = 0; index < dots; index++) {
                float cx = catX + index * 7 + 2.5f;
                float cy = bodyTop + 31.5f;
                canvas.coloredQuad(cx, cy - 2.4f, cx + 2.4f, cy, cx, cy + 2.4f, cx - 2.4f, cy, catColor, catColor, catColor, catColor);
            }
        }
        int telemetryW = Math.max(8, textW - catReserve);
        if (!visuals.recipeValues().isEmpty()) {
            drawDagValueStrip(
                canvas,
                visuals.recipeValues(),
                textX,
                bodyTop + 26,
                telemetryW,
                ValueTypeGaugeRenderer.dagPillHeight,
                palette,
                theme,
                fade
            );
        } else {
            var timeIcon = CirrusThemeIcons.resolve(CirrusTextures.Icon.Sign.Energy.time, theme);
            canvas.color(withAlpha(0xFFFFFFFF, fade)).texture(timeIcon, textX, bodyTop + 28, 9, 9);
            canvas.resetColor();
            Component time = formatStepTime(node);
            String timeFitted = ellipsize(canvas.font(), time.getString(), Math.max(8, telemetryW - 12));
            canvas.drawString(timeFitted, textX + 12, bodyTop + 29, withAlpha(palette.textSecondary(), fade), false);
        }

        drawBand(canvas, x + inset, footerY, Math.max(0, width - inset * 2), footerH, palette, fade, false);
        drawVisualInputsFooter(
            canvas,
            visuals.inputAmounts(),
            resolver,
            palette,
            x + textInset,
            footerY + 1,
            Math.max(8, width - textInset * 2),
            footerH - 1,
            fade
        );
    }

    private static void drawDagValueStrip(
        SceneCanvas canvas,
        List<ValueTelemetry> values,
        int x,
        int y,
        int maxWidth,
        int height,
        CirrusPalette palette,
        WorkbenchThemeMode theme,
        float fade
    ) {
        if (values.isEmpty() || maxWidth < 24) {
            return;
        }
        int cursor = x;
        int limitX = x + maxWidth;
        int shown = 0;
        for (int i = 0; i < values.size(); i++) {
            ValueTelemetry telemetry = values.get(i);
            ValueTypeGaugeRenderer.ValueEntry entry = new ValueTypeGaugeRenderer.ValueEntry(
                telemetry.typeId().toString(),
                telemetry.totalAmount(),
                ValueTypeGaugeRenderer.Direction.neutral,
                1.0f
            );
            int remaining = values.size() - i - 1;
            int overflowReserve = remaining > 0 ? 22 : 0;
            int naturalW = ValueTypeGaugeRenderer.measureDagPillWidth(canvas.font(), entry);
            int avail = limitX - cursor - overflowReserve;
            if (avail < 28) {
                break;
            }
            int pillW = Math.min(naturalW, avail);
            ValueTypeGaugeRenderer.drawDagPill(canvas, entry, cursor, y, pillW, height, palette, theme, fade);
            cursor += pillW + ValueTypeGaugeRenderer.dagPillGap;
            shown++;
        }
        int hidden = values.size() - shown;
        if (hidden > 0 && limitX - cursor >= 16) {
            String more = "+" + hidden;
            int bw = Math.min(limitX - cursor, Math.max(16, canvas.font().width(more) + 6));
            canvas.fill(cursor, y, bw, height, withAlpha(palette.recessedSurface(), fade));
            canvas.strokeRect(cursor, y, bw, height, withAlpha(palette.hairlineBorder(), fade), 1);
            ChromeText.draw(
                canvas,
                Component.literal(more),
                cursor + Math.max(2, (bw - canvas.font().width(more)) / 2),
                ChromeText.centeredY(y, height, canvas),
                withAlpha(palette.focusRing(), fade)
            );
        }
    }

    private static void drawVisualInputsFooter(
        SceneCanvas canvas,
        List<InputAmount> inputs,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        int x,
        int y,
        int width,
        int height,
        float fade
    ) {
        int textY = ChromeText.centeredY(y, height, canvas);
        if (inputs == null || inputs.isEmpty()) {
            canvas.drawString(
                Component.translatable(keyHandCraft),
                x,
                textY,
                withAlpha(palette.textMuted(), fade),
                false
            );
            return;
        }
        int slotDim = Math.min(15, Math.max(12, height));
        int step = slotDim - 1;
        int slotY = y + Math.max(0, (height - slotDim) / 2);
        int maxSlots = Math.max(1, (width - 1) / step);
        boolean overflow = inputs.size() > maxSlots;
        int shown = overflow ? Math.max(1, maxSlots - 1) : inputs.size();
        int cursor = x;
        for (int index = 0; index < shown; index++) {
            InputAmount entry = inputs.get(index);
            drawSlot(canvas, palette, cursor, slotY, slotDim, fade);
            drawResource(canvas, entry.resource(), resolver, palette, cursor + 1, slotY + 1, slotDim - 2);
            OreUiSlotAmount.drawInSlot(canvas, cursor, slotY, slotDim, entry.amount(), palette);
            cursor += step;
        }
        if (overflow) {
            drawSlot(canvas, palette, cursor, slotY, slotDim, fade);
            String more = "+" + (inputs.size() - shown);
            int mw = canvas.font().width(more);
            canvas.drawString(
                more,
                cursor + Math.max(1, (slotDim - mw) / 2),
                ChromeText.centeredY(slotY, slotDim, canvas),
                withAlpha(palette.focusRing(), fade),
                false
            );
        } else if (inputs.size() == 1 && cursor + 18 < x + width) {
            String singleName = nameOf(inputs.get(0).resource(), resolver).getString();
            String fitted = ellipsize(canvas.font(), singleName, x + width - cursor - 5);
            canvas.drawString(fitted, cursor + 5, textY, withAlpha(palette.textSecondary(), fade), false);
        }
    }

    private static void drawConvertCard(
        SceneCanvas canvas,
        DagNodeBox node,
        StepVisuals visuals,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        CirrusPalette.StepTint tint,
        int x,
        int bodyTop,
        int width,
        int capBottom,
        int inset,
        float fade
    ) {
        int footerH = 16;
        int footerY = capBottom - footerH;
        int bodyH = Math.max(22, footerY - bodyTop);
        int slotX = x + textInset;
        int slotY = bodyTop + Math.max(2, (bodyH - slotLarge) / 2);
        drawSlot(canvas, palette, slotX, slotY, slotLarge, fade);
        drawResource(canvas, visuals.primaryOutput(), resolver, palette, slotX + 1, slotY + 1, slotLarge - 2);
        int textX = slotX + slotLarge + 6;
        int textW = Math.max(8, x + width - textInset - textX);
        Component title = titleOf(node, visuals, resolver);
        canvas.drawString(
            Component.literal(ellipsize(canvas.font(), title.getString(), textW)),
            textX,
            bodyTop + 5,
            withAlpha(palette.textPrimary(), fade),
            false
        );
        long amount = visuals.outputAmount() > 0L ? visuals.outputAmount() : Math.max(1L, visuals.batchCount());
        String qty = "×" + WorkbenchFormatters.formatAmount(amount);
        canvas.drawString(qty, textX, bodyTop + 18, withAlpha(tint.accent(), fade), false);
        Component time = formatStepTime(node);
        int timeW = canvas.font().width(time);
        if (textX + canvas.font().width(qty) + 6 + timeW <= x + width - textInset) {
            canvas.drawString(
                time,
                x + width - textInset - timeW,
                bodyTop + 18,
                withAlpha(palette.textMuted(), fade),
                false
            );
        }

        drawBand(canvas, x + inset, footerY, Math.max(0, width - inset * 2), footerH, palette, fade, false);
        drawVisualInputsFooter(
            canvas,
            visuals.inputAmounts(),
            resolver,
            palette,
            x + textInset,
            footerY + 1,
            Math.max(8, width - textInset * 2),
            footerH - 1,
            fade
        );
    }

    private static void drawCycleCard(
        SceneCanvas canvas,
        DagNodeBox node,
        StepVisuals visuals,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        CirrusPalette.StepTint tint,
        int x,
        int bodyTop,
        int width,
        int capBottom,
        int inset,
        float fade
    ) {
        int footerH = 16;
        int footerY = capBottom - footerH;
        int bodyH = Math.max(24, footerY - bodyTop);
        int slotX = x + textInset;
        int slotY = bodyTop + Math.max(3, (bodyH - slotLarge) / 2);
        drawSlot(canvas, palette, slotX, slotY, slotLarge, fade);
        drawResource(canvas, visuals.primaryOutput(), resolver, palette, slotX + 1, slotY + 1, slotLarge - 2);
        int textX = slotX + slotLarge + 6;
        int textW = Math.max(8, x + width - textInset - textX);

        Component title = titleOf(node, visuals, resolver);
        canvas.drawString(
            Component.literal(ellipsize(canvas.font(), title.getString(), textW)),
            textX,
            bodyTop + 4,
            withAlpha(palette.textPrimary(), fade),
            false
        );

        String seedSummary = formatInputsSummary(visuals.inputAmounts(), resolver);
        Component seedLine = seedSummary.isEmpty()
                ? Component.translatable(keyWorkingSeed)
                : Component.literal(Component.translatable(keyWorkingSeed).getString() + ": " + seedSummary);
        canvas.drawString(
            ellipsize(canvas.font(), seedLine.getString(), textW),
            textX,
            bodyTop + 17,
            withAlpha(palette.textSecondary(), fade),
            false
        );

        long net = 0L;
        for (long delta : visuals.netDelta().values()) {
            net = Math.max(net, delta);
        }
        Component netLabel = Component.translatable(keyNetDelta, WorkbenchFormatters.formatAmount(net));
        canvas.drawString(
            ellipsize(canvas.font(), netLabel.getString(), textW),
            textX,
            bodyTop + 30,
            withAlpha(tint.accent(), fade),
            false
        );

        drawBand(canvas, x + inset, footerY, Math.max(0, width - inset * 2), footerH, palette, fade, false);
        Component time = formatStepTime(node);
        canvas.drawString(
            ellipsize(canvas.font(), time.getString(), Math.max(8, width - textInset * 2)),
            x + textInset,
            ChromeText.centeredY(footerY + 1, footerH - 1, canvas),
            withAlpha(palette.textMuted(), fade),
            false
        );
    }

    private static void drawProvisionCard(
        SceneCanvas canvas,
        DagNodeBox node,
        StepVisuals visuals,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        CirrusPalette.StepTint tint,
        int x,
        int bodyTop,
        int width,
        int capBottom,
        float fade
    ) {
        ProvisionMachineStep provision = (ProvisionMachineStep) node.step();
        DagHitTester.RectF owned = declareOwnedButton(node);
        int ox = Math.round(owned.x());
        int oy = Math.round(owned.y());
        int ow = Math.round(owned.width());
        int oh = Math.round(owned.height());
        int bodyH = Math.max(22, oy - 3 - bodyTop);

        int slotX = x + textInset;
        int slotY = bodyTop + Math.max(2, (bodyH - slotLarge) / 2);
        drawSlot(canvas, palette, slotX, slotY, slotLarge, fade);
        drawResource(
            canvas,
            provision.machine().contentKey().orElse(null),
            resolver,
            palette,
            slotX + 1,
            slotY + 1,
            slotLarge - 2
        );
        int textX = slotX + slotLarge + 6;
        int textW = Math.max(8, x + width - textInset - textX);

        Component title = machineNameOf(provision.machine(), resolver);
        canvas.drawString(
            Component.literal(ellipsize(canvas.font(), title.getString(), textW)),
            textX,
            bodyTop + 4,
            withAlpha(palette.textPrimary(), fade),
            false
        );
        Component buildCount = CirrusLangs.Workbench.Flow.buildCount
                .get(WorkbenchFormatters.formatAmount(provision.countToBuild()));
        canvas.drawString(buildCount, textX, bodyTop + 17, withAlpha(tint.accent(), fade), false);
        Component time = formatStepTime(node);
        int timeW = canvas.font().width(time);
        if (textX + canvas.font().width(buildCount) + 6 + timeW <= x + width - textInset) {
            canvas.drawString(
                time,
                x + width - textInset - timeW,
                bodyTop + 17,
                withAlpha(palette.textMuted(), fade),
                false
            );
        }

        if (fade < 0.99f) {
            canvas.color(withAlpha(0xFFFFFFFF, fade));
        }
        OreUiPaint.raisedButton(canvas, ox, oy, ow, oh, palette, false, false, true, false);
        if (fade < 0.99f) {
            canvas.resetColor();
        }
        Component ownedLabel = Component.translatable(keyDeclareOwned);
        String ownedText = ellipsize(canvas.font(), ownedLabel.getString(), Math.max(4, ow - 8));
        int textWBtn = canvas.font().width(ownedText);
        canvas.drawString(
            Component.literal(ownedText),
            ox + Math.max(3, (ow - textWBtn) / 2),
            ChromeText.centeredY(oy, oh - 1, canvas),
            withAlpha(palette.textOnAccent(), fade),
            false
        );
    }

    private static void drawSetupCard(
        SceneCanvas canvas,
        DagNodeBox node,
        StepVisuals visuals,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        CirrusPalette.StepTint tint,
        int x,
        int bodyTop,
        int width,
        int capBottom,
        int inset,
        float fade
    ) {
        int footerH = 16;
        int footerY = capBottom - footerH;
        int bodyH = Math.max(22, footerY - bodyTop);
        int slotX = x + textInset;
        int slotY = bodyTop + Math.max(2, (bodyH - slotLarge) / 2);
        drawSlot(canvas, palette, slotX, slotY, slotLarge, fade);
        drawResource(canvas, visuals.primaryOutput(), resolver, palette, slotX + 1, slotY + 1, slotLarge - 2);
        int textX = slotX + slotLarge + 6;
        int textW = Math.max(8, x + width - textInset - textX);
        Component title = titleOf(node, visuals, resolver);
        canvas.drawString(
            Component.literal(ellipsize(canvas.font(), title.getString(), textW)),
            textX,
            bodyTop + 4,
            withAlpha(palette.textPrimary(), fade),
            false
        );
        Component label = Component.translatable(keySetupLine);
        canvas.drawString(
            Component.literal(ellipsize(canvas.font(), label.getString(), textW)),
            textX,
            bodyTop + 17,
            withAlpha(tint.accent(), fade),
            false
        );
        drawBand(canvas, x + inset, footerY, Math.max(0, width - inset * 2), footerH, palette, fade, false);
        Component time = formatStepTime(node);
        canvas.drawString(
            ellipsize(canvas.font(), time.getString(), Math.max(8, width - textInset * 2)),
            x + textInset,
            ChromeText.centeredY(footerY + 1, footerH - 1, canvas),
            withAlpha(palette.textMuted(), fade),
            false
        );
    }

    private static void drawLinePullCard(
        SceneCanvas canvas,
        DagNodeBox node,
        StepVisuals visuals,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        CirrusPalette.StepTint tint,
        int x,
        int bodyTop,
        int width,
        int bodyHeight,
        float fade
    ) {
        int slotX = x + textInset;
        int slotY = bodyTop + Math.max(2, (bodyHeight - slotSmall) / 2);
        drawSlot(canvas, palette, slotX, slotY, slotSmall, fade);
        drawResource(canvas, visuals.primaryOutput(), resolver, palette, slotX + 1, slotY + 1, slotSmall - 2);
        int textX = slotX + slotSmall + 6;
        int textW = Math.max(8, x + width - textInset - textX);
        Component title = titleOf(node, visuals, resolver);
        canvas.drawString(
            Component.literal(ellipsize(canvas.font(), title.getString(), textW)),
            textX,
            bodyTop + 6,
            withAlpha(palette.textPrimary(), fade),
            false
        );
        long amount = Math.max(1L, visuals.count());
        String qty = "×" + WorkbenchFormatters.formatAmount(amount);
        canvas.drawString(qty, textX, bodyTop + 21, withAlpha(tint.accent(), fade), false);
        Component time = formatStepTime(node);
        int timeW = canvas.font().width(time);
        if (textX + canvas.font().width(qty) + 6 + timeW <= x + width - textInset) {
            canvas.drawString(
                time,
                x + width - textInset - timeW,
                bodyTop + 21,
                withAlpha(palette.textMuted(), fade),
                false
            );
        }
    }

    private static Component kindLabel(StepKind kind) {
        return switch (kind) {
            case acquire -> CirrusLangs.Workbench.Flow.kindAcquire.get();
            case craft -> CirrusLangs.Workbench.Flow.kindCraft.get();
            case convert -> CirrusLangs.Workbench.Flow.kindConvert.get();
            case cycleLoop -> CirrusLangs.Workbench.Flow.kindCycle.get();
            case provision -> CirrusLangs.Workbench.Flow.kindBuild.get();
            case setup -> CirrusLangs.Workbench.Flow.kindLine.get();
            case linePull -> CirrusLangs.Workbench.Flow.kindPull.get();
        };
    }

    private static Component formatStepTime(DagNodeBox node) {
        double human = node.step().stepCost().humanActiveSeconds();
        double passive = node.step().stepCost().passiveWaitSeconds();
        boolean hasHuman = human > 0.05;
        boolean hasPassive = passive > 0.05;
        if (!hasHuman && !hasPassive) {
            return CirrusLangs.Workbench.Flow.timeInstant.get();
        }
        if (hasHuman && hasPassive) {
            return CirrusLangs.Workbench.Flow.timeBoth
                    .get(WorkbenchFormatters.formatSeconds(human), WorkbenchFormatters.formatSeconds(passive));
        }
        if (hasHuman) {
            return CirrusLangs.Workbench.Flow.timeWork.get(WorkbenchFormatters.formatSeconds(human));
        }
        return CirrusLangs.Workbench.Flow.timeWait.get(WorkbenchFormatters.formatSeconds(passive));
    }

    private static String formatInputsSummary(List<InputAmount> inputs, @Nullable PlanVisualResolver resolver) {
        if (inputs == null || inputs.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(2, inputs.size());
        for (int index = 0; index < limit; index++) {
            InputAmount entry = inputs.get(index);
            if (index > 0) {
                sb.append(" + ");
            }
            sb.append(WorkbenchFormatters.formatAmount(entry.amount())).append("× ")
                    .append(nameOf(entry.resource(), resolver).getString());
        }
        if (inputs.size() > limit) {
            sb.append(" +").append(inputs.size() - limit);
        }
        return sb.toString();
    }

    private static void drawBadge(
        SceneCanvas canvas,
        long count,
        int rightX,
        int y,
        CirrusPalette palette,
        float fade,
        boolean macro
    ) {
        int badgeWidth = WorkbenchCountBadge.plateWidth(canvas.font(), count);
        WorkbenchCountBadge.draw(
            canvas,
            count,
            rightX - badgeWidth,
            y,
            withAlpha(palette.textOnAccent(), fade),
            withAlpha(palette.textOnDark(), fade)
        );
    }

    private static void drawAdvisoryBorder(
        SceneCanvas canvas,
        StepVisuals visuals,
        CirrusPalette palette,
        int x,
        int y,
        int width,
        int height,
        float fade
    ) {
        RecipeAdvisory advisory = visuals.advisory();
        if (advisory == null || advisory == RecipeAdvisory.none) {
            return;
        }
        int color = advisory == RecipeAdvisory.discouraged
                ? palette.advisoryDiscouraged()
                : palette.advisoryLastResort();
        canvas.strokeRect(x - 1, y - 1, width + 2, height + 2, withAlpha(color, fade), 2);
    }

    private static void drawInlineRecipe(
        SceneCanvas canvas,
        DagNodeBox node,
        StepVisuals visuals,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        CirrusPalette.StepTint tint,
        WorkbenchThemeMode theme,
        float fade
    ) {
        DagHitTester.RectF panel = DagHitTester.inlinePanelBounds(node);
        int px = Math.round(panel.x());
        int py = Math.round(panel.y());
        int pw = Math.max(1, Math.round(panel.width()));
        int ph = Math.max(1, Math.round(panel.height()));
        canvas.fill(px, py, pw, ph, withAlpha(palette.recessedSurface(), fade));
        canvas.strokeRect(px, py, pw, ph, withAlpha(tint.borderIdle(), fade), 1);
        List<DagHitTester.InlineSlot> slots = DagHitTester.inlineSlots(node, visuals.inputs(), visuals.outputs());
        for (DagHitTester.InlineSlot slot : slots) {
            int sx = Math.round(slot.bounds().x());
            int sy = Math.round(slot.bounds().y());
            drawSlot(canvas, palette, sx, sy, DagHitTester.slotSize, fade);
            drawResource(
                canvas,
                resourceOf(visuals, slot),
                resolver,
                palette,
                sx + 1,
                sy + 1,
                DagHitTester.slotSize - 2
            );
        }
        DagHitTester.RectF machine = DagHitTester.machineBounds(node);
        int mx = Math.round(machine.x());
        int my = Math.round(machine.y());
        int mw = Math.round(machine.width());
        canvas.fill(mx, my, mw, Math.round(machine.height()), withAlpha(tint.tintBg(), fade));
        if (visuals.machine() != null && !visuals.machine().isEmpty()) {
            drawResource(canvas, visuals.machine().contentKey().orElse(null), resolver, palette, mx, my, mw);
        } else {
            canvas.fill(
                mx + 4,
                my + Math.max(1, Math.round(machine.height()) / 2),
                mw - 8,
                1,
                withAlpha(palette.hairlineBorder(), fade)
            );
        }
        if (!visuals.recipeValues().isEmpty()) {
            int stripY = py + ph - 1;
            int stripH = ValueTypeGaugeRenderer.dagInlineStripHeight;
            canvas.fill(px, stripY, pw, stripH, withAlpha(palette.recessedSurface(), fade));
            canvas.strokeRect(px, stripY, pw, stripH, withAlpha(tint.borderIdle(), fade), 1);
            drawDagValueStrip(
                canvas,
                visuals.recipeValues(),
                px + 4,
                stripY + 1,
                Math.max(12, pw - 8),
                ValueTypeGaugeRenderer.dagPillHeight,
                palette,
                theme,
                fade
            );
        }
    }

    private static @Nullable ComponentStackKey resourceOf(StepVisuals visuals, DagHitTester.InlineSlot slot) {
        List<ComponentStackKey> list = slot.side() == DagHitTester.SlotSide.input ? visuals.inputs() : visuals.outputs();
        return slot.index() >= 0 && slot.index() < list.size() ? list.get(slot.index()) : null;
    }

    private static void drawSlot(SceneCanvas canvas, CirrusPalette palette, int x, int y, int size, float fade) {
        if (fade < 0.99f) {
            canvas.color(withAlpha(0xFFFFFFFF, fade));
        }
        OreUiPaint.slot(canvas, x, y, size, palette);
        if (fade < 0.99f) {
            canvas.resetColor();
        }
    }

    private static void drawResource(
        SceneCanvas canvas,
        @Nullable ComponentStackKey resource,
        @Nullable PlanVisualResolver resolver,
        CirrusPalette palette,
        int x,
        int y,
        int size
    ) {
        ContentStack<?, ?> stack = contentStackOf(resource, resolver);
        if (stack == null || stack.isEmpty()) {
            if (resource != null) {
                if (ValueTypeVisualRegistry.isValueResourceKey(resource)) {
                    ValueTypeGaugeRenderer.drawMiniEmblem(
                        canvas,
                        resource.contentTypeId().toString(),
                        x,
                        y,
                        size,
                        palette,
                        OreUiPaint.themeOf(palette),
                        1.0f
                    );
                } else {
                    canvas.fill(x, y, size, size, contentTypeColor(resource.contentTypeId(), palette));
                }
            }
            return;
        }
        int drawSize = Math.min(16, size);
        int ox = x + Math.max(0, (size - drawSize) / 2);
        int oy = y + Math.max(0, (size - drawSize) / 2);
        CoPilotPaint.renderStack(canvas, stack, ox, oy, drawSize);
    }

    public static @Nullable ContentStack<?, ?> contentStackOf(
        @Nullable ComponentStackKey resource,
        @Nullable PlanVisualResolver resolver
    ) {
        if (resource == null) {
            return null;
        }
        if (resolver != null) {
            ContentStack<?, ?> stack = resolver.stackOf(resource, 1L);
            if (stack != null && stack.notEmpty()) {
                return stack;
            }
        }
        return CoPilotPaint.stackOf(resource, 1L);
    }

    public static Component titleOf(DagNodeBox node, StepVisuals visuals, @Nullable PlanVisualResolver resolver) {
        ComponentStackKey resource = visuals == null ? null : visuals.primaryOutput();
        if (resource != null) {
            return nameOf(resource, resolver);
        }
        if (node.step() instanceof ProvisionMachineStep provision) {
            return machineNameOf(provision.machine(), resolver);
        }
        if (node.step() instanceof SetupLineStep setup) {
            return Component.literal(setup.systemId().toString());
        }
        if (node.step() instanceof CycleLoopStep loop) {
            return Component.translatable(keyIterations, loop.iterations());
        }
        return Component.literal(node.stepId());
    }

    public static Component nameOf(@Nullable ComponentStackKey resource, @Nullable PlanVisualResolver resolver) {
        if (resource == null) {
            return Component.empty();
        }
        if (resolver != null) {
            Component resolved = resolver.nameOf(resource);
            if (resolved != null && !resolved.getString().isBlank()) {
                String clean = CoPilotPaint.stripBrackets(resolved.getString());
                if (!looksLikeRawId(clean)) {
                    return Component.literal(clean);
                }
            }
        }
        ContentStack<?, ?> stack = contentStackOf(resource, resolver);
        if (stack != null && !stack.isEmpty()) {
            return CoPilotPaint.stackName(stack, Component.literal(CoPilotPaint.humanizeId(resource.displayId())));
        }
        return Component.literal(CoPilotPaint.humanizeId(resource.displayId()));
    }

    public static Component machineNameOf(@Nullable MachineEntry machine, @Nullable PlanVisualResolver resolver) {
        if (machine == null || machine.isEmpty()) {
            return Component.empty();
        }
        if (resolver != null) {
            Component resolved = resolver.machineNameOf(machine);
            if (resolved != null && !resolved.getString().isBlank()) {
                String clean = CoPilotPaint.stripBrackets(resolved.getString());
                if (!looksLikeRawId(clean)) {
                    return Component.literal(clean);
                }
            }
        }
        ComponentStackKey key = machine.contentKey().orElse(null);
        if (key != null) {
            ContentStack<?, ?> stack = contentStackOf(key, resolver);
            if (stack != null && !stack.isEmpty()) {
                return CoPilotPaint.stackName(stack, Component.literal(CoPilotPaint.humanizeId(key.displayId())));
            }
            return Component.literal(CoPilotPaint.humanizeId(key.displayId()));
        }
        return Component.literal(CoPilotPaint.humanizeId(machine.canonicalKey()));
    }

    private static boolean looksLikeRawId(String value) {
        return value.indexOf(':') >= 0 && value.indexOf(' ') < 0;
    }

    private static Component acquireLabel(@Nullable AcquisitionKind kind) {
        if (kind == null) {
            return CirrusLangs.Planning.Acquire.unknown.get();
        }
        return switch (kind) {
            case manualGather -> CirrusLangs.Planning.Acquire.natural.get();
            case baseSupply -> CirrusLangs.Planning.Acquire.base.get();
        };
    }

    private static int acquireColor(@Nullable AcquisitionKind kind, CirrusPalette palette) {
        if (kind == null) {
            return palette.textMuted();
        }
        return switch (kind) {
            case manualGather -> palette.textSecondary();
            case baseSupply -> palette.costS();
        };
    }

    private static @Nullable CompiledRecipe recipeOf(
        @Nullable CompiledRecipeGraph graph,
        @Nullable CompiledRecipeKey key
    ) {
        if (graph == null || key == null) {
            return null;
        }
        return graph.recipeByKey(key).orElse(null);
    }

    private static @Nullable ComponentStackKey resolveInput(Map<Integer, SlotBinding> bindings, InputSlot slot) {
        SlotBinding binding = bindings.get(slot.slotIndex());
        if (binding != null) {
            return binding.chosenResource();
        }
        return slot.candidates().isEmpty() ? null : slot.candidates().get(0);
    }

    private static long countOf(PlanStep step) {
        return switch (step) {
            case AcquireStep acquire -> acquire.amount();
            case LinePullStep pull -> pull.amount();
            case ProvisionMachineStep provision -> provision.countToBuild();
            case SetupLineStep ignored -> 0L;
            case CraftStep ignored -> 0L;
            case ConvertStep ignored -> 0L;
            case CycleLoopStep loop -> loop.iterations();
        };
    }

    private static @Nullable Namespace systemOf(PlanStep step) {
        return switch (step) {
            case CraftStep craft -> craft.recipeKey().systemId();
            case ConvertStep convert -> convert.conversionRecipeKey().systemId();
            case SetupLineStep setup -> setup.systemId();
            default -> null;
        };
    }

    private static int contentTypeColor(@Nullable Namespace contentTypeId, CirrusPalette palette) {
        if (contentTypeId == null) {
            return palette.hairlineBorder();
        }
        if (Namespace.ofMc("fluid").equals(contentTypeId)) {
            return palette.costP();
        }
        if (contentTypeId.path().contains("energy")) {
            return palette.costH();
        }
        return palette.hairlineBorder();
    }

    public static String ellipsize(Font font, String text, int maxWidth) {
        if (text == null || text.isEmpty() || maxWidth <= 0) {
            return "";
        }
        if (font.width(text) <= maxWidth) {
            return text;
        }
        int ellipsisWidth = font.width("...");
        if (maxWidth <= ellipsisWidth) {
            return "";
        }
        return font.plainSubstrByWidth(text, maxWidth - ellipsisWidth).stripTrailing() + "...";
    }

    public static int withAlpha(int argb, float alpha) {
        int base = (argb >>> 24) & 0xFF;
        int scaled = Math.max(0, Math.min(255, Math.round(base * clampAlpha(alpha))));
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    private static float clampAlpha(float alpha) {
        if (!Float.isFinite(alpha)) {
            return 1.0f;
        }
        return Math.max(0.0f, Math.min(1.0f, alpha));
    }

    private enum StepKind {
        acquire, craft, convert, cycleLoop, provision, setup, linePull;

        static StepKind of(PlanStep step) {
            return switch (step) {
                case AcquireStep ignored -> acquire;
                case CraftStep ignored -> craft;
                case ConvertStep ignored -> convert;
                case CycleLoopStep ignored -> cycleLoop;
                case ProvisionMachineStep ignored -> provision;
                case SetupLineStep ignored -> setup;
                case LinePullStep ignored -> linePull;
            };
        }

        int slotSize() {
            return this == acquire || this == linePull ? slotSmall : slotLarge;
        }
    }
}
