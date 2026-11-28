package dev.vfyjxf.cirrus.ui.widget.calculator.page;

import dev.vfyjxf.cirrus.api.recipe.content.BatchableRecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.DurabilityContent;
import dev.vfyjxf.cirrus.api.recipe.projection.RecipeProjection;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cirrus.api.recipe.projection.display.ProjectionDisplayModel;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.ProjectionResult;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.SlotDiff;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.RecipeContentSlotWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSlotGroupRenderer;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiSlotAmount;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeGaugeRenderer;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipContext;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.taffy.style.Overflow;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.eclipse.collections.api.list.ImmutableList;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.overflow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * Diff-aware recipe projection viewer backed directly by {@link ProjectionDisplayModel}.
 *
 * <p>Renders 2D input/output grids (preserving exact {@code (col, row)} coordinates), loose/named
 * slots, value gauges, and optional per-cell / per-slot diff overlays (added, removed ghost,
 * adjusted delta badge, transformed swap, moved arrow) with rich before→after hover tooltips.
 */
final class ProjectionRecipeViewWidget extends Widget implements OreUiTooltipProvider {
    private static final int framePadding = 5;
    private static final int slotSize = 18;
    private static final int slotInnerSize = 16;
    private static final int sectionGap = 5;
    private static final int valueSectionGap = 6;
    private static final int maxLooseColumns = 6;
    private static final int arrowGapLeft = 6;
    private static final int arrowGapRight = 8;
    private static final int arrowRenderWidth = 18;
    private static final int arrowRenderHeight = 12;

    private final Layout layout;

    static @Nullable ProjectionRecipeViewWidget create(@Nullable RecipeProjection projection) {
        if (projection == null) {
            return null;
        }
        return create(ProjectionDisplayModel.fromProjection(projection));
    }

    static @Nullable ProjectionRecipeViewWidget create(@Nullable ProjectionResult result) {
        if (result == null) {
            return null;
        }
        return create(result.toDisplayModel());
    }

    static @Nullable ProjectionRecipeViewWidget create(@Nullable ProjectionDisplayModel model) {
        if (model == null) {
            return null;
        }
        Layout layout = Layout.build(model);
        return layout.hasContent() ? new ProjectionRecipeViewWidget(layout) : null;
    }

    private ProjectionRecipeViewWidget(Layout layout) {
        this.layout = layout;
        useStyle(
            sizeOf(layout.width() + framePadding * 2, layout.height() + framePadding * 2),
            boxSizingBorderBox(),
            overflow(Overflow.HIDDEN)
        );
        onHoverTooltip((mouseX, mouseY) -> tooltipAt(mouseX, mouseY));
    }

    int inputCount() {
        return layout.inputCount();
    }

    int outputCount() {
        return layout.outputCount();
    }

    int valueCount() {
        return layout.valueMeters().size();
    }

    boolean usesGrid() {
        return layout.usesGrid();
    }

    int changedSlotCount() {
        int count = 0;
        for (Slot slot : layout.slots()) {
            if (slot.status().isChanged()) {
                count++;
            }
        }
        return count;
    }

    boolean hasDiffOverlays() {
        return changedSlotCount() > 0 || layout.valueMeters().stream().anyMatch(m -> m.status().isChanged());
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        canvas.texture(CirrusUiTheme.textures(this).cardSurface(), 0, 0, width(), height());
        int baseX = Math.max(framePadding, (width() - layout.width()) / 2);
        int baseY = Math.max(framePadding, (height() - layout.height()) / 2);
        CirrusPalette palette = CirrusUiTheme.palette(this);
        WorkbenchThemeMode theme = CirrusUiTheme.mode(this);

        renderSectionBackgrounds(canvas, baseX, baseY);
        renderSlotDiffUnderlays(canvas, baseX, baseY, palette);
        if (layout.showArrow()) {
            int ax0 = baseX + layout.arrowX() + 1;
            int ax1 = baseX + layout.arrowX() + arrowRenderWidth - 1;
            int midY = baseY + layout.arrowY() + arrowRenderHeight / 2;
            OreUiRecipeGrid.drawRecipeArrow(canvas, ax0, ax1, midY, palette, false);
        }
        renderContent(canvas, baseX, baseY, partialTicks, palette);
        renderValueMeters(canvas, baseX, baseY, mouseX, mouseY, palette, theme);
    }

    private void renderValueMeters(
        SceneCanvas canvas,
        int baseX,
        int baseY,
        int mouseX,
        int mouseY,
        CirrusPalette palette,
        WorkbenchThemeMode theme
    ) {
        if (layout.valueMeters().isEmpty()) {
            return;
        }
        if (layout.valueDividerY() >= 0) {
            canvas.fill(baseX, baseY + layout.valueDividerY(), layout.width(), 1, OreUiPaint.cardDivider(palette));
        }
        for (ValueMeter meter : layout.valueMeters()) {
            int mx = baseX + meter.x();
            int my = baseY + meter.y();
            boolean hovered = mouseX >= mx
                    && mouseX < mx + meter.width()
                    && mouseY >= my
                    && mouseY < my + meter.height();
            ValueTypeGaugeRenderer.drawDatabaseMeter(
                canvas,
                meter.entry(),
                mx,
                my,
                meter.width(),
                meter.height(),
                palette,
                theme,
                hovered
            );
            if (meter.status().isChanged()) {
                int accent = statusColor(meter.status(), palette);
                canvas.strokeRect(mx, my, meter.width(), meter.height(), OreUiPaint.withAlpha(accent, 0.72f), 1);
            }
        }
    }

    private void renderSectionBackgrounds(SceneCanvas canvas, int baseX, int baseY) {
        for (Section section : layout.sections()) {
            CirrusSlotGroupRenderer
                    .renderCells(canvas, this, baseX + section.x(), baseY + section.y(), slotSize, section.cells());
        }
    }

    private void renderSlotDiffUnderlays(SceneCanvas canvas, int baseX, int baseY, CirrusPalette palette) {
        boolean dark = OreUiPaint.isDark(palette);
        for (Slot slot : layout.slots()) {
            if (!slot.status().isChanged()) {
                continue;
            }
            int sx = baseX + slot.x();
            int sy = baseY + slot.y();
            int accent = statusColor(slot.status(), palette);
            float tintAlpha = slot.status() == SlotDiff.DiffStatus.removed
                    ? (dark ? 0.28f : 0.20f)
                    : (dark ? 0.20f : 0.14f);
            canvas.fill(sx + 1, sy + 1, slotSize - 2, slotSize - 2, OreUiPaint.withAlpha(accent, tintAlpha));
            canvas.strokeRect(sx, sy, slotSize, slotSize, OreUiPaint.withAlpha(accent, 0.85f), 1);
        }
    }

    private void renderContent(
        SceneCanvas canvas,
        int baseX,
        int baseY,
        float partialTicks,
        CirrusPalette palette
    ) {
        BatchableRecipeContentRenderer.Batch batch = BatchableRecipeContentRenderer.Batch
                .create(canvas.layeredGraphics(), partialTicks);
        for (Slot slot : layout.slots()) {
            RecipeContent content = slot.displayContent();
            if (ExecutionOverlayContentHelper.hasRenderableVisual(content)) {
                batch.add(
                    content,
                    ExecutionOverlayContentHelper.currentVisualIndex(content),
                    baseX + slot.x() + 1,
                    baseY + slot.y() + 1,
                    slotInnerSize,
                    slotInnerSize
                );
            }
        }
        batch.flush();

        Font font = context().font();
        GuiGraphics graphics = canvas.layeredGraphics();
        for (Slot slot : layout.slots()) {
            RecipeContent content = slot.displayContent();
            if (!ExecutionOverlayContentHelper.hasRenderableVisual(content)) {
                renderFallbackMarker(graphics, baseX + slot.x(), baseY + slot.y());
            }
            if (slot.status() == SlotDiff.DiffStatus.removed) {
                renderRemovedScrim(graphics, baseX + slot.x(), baseY + slot.y(), palette);
            }
            renderAmount(font, graphics, baseX, baseY, slot);
            if (slot.status().isChanged() && !slot.deltaBadge().isEmpty()) {
                renderDeltaBadge(font, graphics, baseX + slot.x(), baseY + slot.y(), slot.status(), slot.deltaBadge(), palette);
            }
        }
    }

    private void renderRemovedScrim(GuiGraphics graphics, int sx, int sy, CirrusPalette palette) {
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, 180f);
        graphics.fill(
            sx + 1,
            sy + 1,
            sx + slotSize - 1,
            sy + slotSize - 1,
            OreUiPaint.withAlpha(OreUiPaint.outerInk(palette), 0.44f)
        );
        // Crisp horizontal strike bar across removed ghost slot
        int midY = sy + slotSize / 2;
        graphics.fill(sx + 2, midY, sx + slotSize - 2, midY + 1, palette.diffRemoved());
        graphics.pose().popPose();
    }

    private void renderDeltaBadge(
        Font font,
        GuiGraphics graphics,
        int sx,
        int sy,
        SlotDiff.DiffStatus status,
        String badge,
        CirrusPalette palette
    ) {
        int rawW = Math.max(1, font.width(badge));
        float scale = Math.max(0.45f, Math.min(0.62f, (slotSize - 3.0f) / rawW));
        float scaledW = rawW * scale;
        float scaledH = 8f * scale;
        float tx = sx + 1.5f;
        float ty = sy + 1.2f;
        int bgW = Math.min(slotSize - 2, (int) Math.ceil(scaledW + 2.0f));
        int bgH = Math.min(slotSize - 2, (int) Math.ceil(scaledH + 1.0f));
        int accent = statusColor(status, palette);

        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, 220f);
        if (bgW > 0 && bgH > 0) {
            graphics.fill(
                sx + 1,
                sy + 1,
                sx + 1 + bgW,
                sy + 1 + bgH,
                OreUiPaint.withAlpha(OreUiPaint.outerInk(palette), 0.78f)
            );
        }
        graphics.pose().translate(tx, ty, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, badge, 0, 0, accent, true);
        graphics.pose().popPose();
    }

    private void renderFallbackMarker(GuiGraphics graphics, int x, int y) {
        CirrusPalette palette = CirrusUiTheme.palette(this);
        int cx = x + slotSize / 2;
        int cy = y + slotSize / 2;
        graphics.fill(cx - 2, cy - 2, cx + 2, cy + 2, palette.textMuted());
        graphics.fill(cx - 1, cy - 1, cx + 1, cy + 1, palette.recessedSurface());
    }

    private void renderAmount(Font font, GuiGraphics graphics, int baseX, int baseY, Slot slot) {
        if (slot.displayContent() instanceof DurabilityContent) {
            return;
        }
        long amount = slot.displayContent().amount();
        String text = OreUiSlotAmount.format(amount);
        if (text.isEmpty()) {
            return;
        }
        CirrusPalette palette = CirrusUiTheme.palette(this);
        int rawW = Math.max(1, font.width(text));
        float preferred = text.length() <= 2 ? 0.80f : (text.length() == 3 ? 0.68f : 0.58f);
        float scale = Math.max(0.48f, Math.min(preferred, (slotSize - 2.5f) / rawW));
        float scaledW = rawW * scale;
        float scaledH = 8f * scale;
        int sx = baseX + slot.x();
        int sy = baseY + slot.y();
        float tx = sx + slotSize - 1.5f - scaledW;
        float ty = sy + slotSize - 1.5f - scaledH;
        int bgX = Math.max(sx + 1, (int) Math.floor(tx - 1.0f));
        int bgY = Math.max(sy + 1, (int) Math.floor(ty - 0.5f));
        int bgW = Math.min(sx + slotSize - 1 - bgX, (int) Math.ceil(scaledW + 2.0f));
        int bgH = Math.min(sy + slotSize - 1 - bgY, (int) Math.ceil(scaledH + 1.5f));
        graphics.pose().pushPose();
        graphics.pose().translate(0f, 0f, 200f);
        if (bgW > 0 && bgH > 0) {
            graphics.fill(bgX, bgY, bgX + bgW, bgY + bgH, OreUiPaint.withAlpha(OreUiPaint.outerInk(palette), 0.64f));
        }
        int ink = slot.status() == SlotDiff.DiffStatus.removed
                ? palette.textMuted()
                : amount >= 1_000_000L
                        ? OreUiPaint.lighten(palette.focusRing(), 0.25f)
                        : amount >= 1_000L ? OreUiPaint.lighten(palette.costH(), 0.25f) : palette.textOnAccent();
        graphics.pose().translate(tx, ty, 0f);
        graphics.pose().scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, ink, true);
        graphics.pose().popPose();
    }

    private static int statusColor(SlotDiff.DiffStatus status, CirrusPalette palette) {
        return switch (status) {
            case added -> palette.diffAdded();
            case removed -> palette.diffRemoved();
            case adjusted -> palette.focusRing();
            case transformed -> palette.costH();
            case moved -> palette.costP();
            case unchanged -> palette.hairlineBorder();
        };
    }

    private static OreUiRichTooltip.Tone statusTone(SlotDiff.DiffStatus status) {
        return switch (status) {
            case added -> OreUiRichTooltip.Tone.positive;
            case removed -> OreUiRichTooltip.Tone.danger;
            case adjusted -> OreUiRichTooltip.Tone.accent;
            case transformed -> OreUiRichTooltip.Tone.warning;
            case moved -> OreUiRichTooltip.Tone.costP;
            case unchanged -> OreUiRichTooltip.Tone.secondary;
        };
    }

    private static String statusLabel(SlotDiff.DiffStatus status, String badge) {
        String name = switch (status) {
            case added -> "ADDED";
            case removed -> "REMOVED";
            case adjusted -> "ADJUSTED";
            case transformed -> "TRANSFORMED";
            case moved -> "MOVED";
            case unchanged -> "";
        };
        if (name.isEmpty()) {
            return "";
        }
        return badge.isEmpty() ? name : name + " " + badge;
    }

    @Override
    public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
        if (CirrusUiTheme.shellContext(this) != null) {
            return null;
        }
        return super.hoverTooltip(mouseX, mouseY);
    }

    @Override
    public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
        int baseX = Math.max(framePadding, (width() - layout.width()) / 2);
        int baseY = Math.max(framePadding, (height() - layout.height()) / 2);
        ValueMeter meter = layout.valueMeterAt(localX - baseX, localY - baseY);
        if (meter != null) {
            return ValueTypeGaugeRenderer.richTooltipFor(meter.entry(), CirrusUiTheme.palette(this));
        }
        Slot slot = layout.slotAt(localX - baseX, localY - baseY);
        if (slot == null || slot.displayContent().isEmpty()) {
            return null;
        }
        if (!slot.status().isChanged()) {
            return RecipeContentSlotWidget.richTooltipFor(slot.displayContent(), null);
        }
        return buildDiffRichTooltip(slot);
    }

    private OreUiRichTooltip buildDiffRichTooltip(Slot slot) {
        Component title = contentDisplayName(slot.displayContent());
        OreUiRichTooltip.Builder builder = OreUiRichTooltip.builder(title)
                .badge(statusLabel(slot.status(), slot.deltaBadge()), statusTone(slot.status()))
                .subtitle(slot.address().channel().name().toUpperCase() + " " + slot.address().shortLabel());
        builder.keyValue(
            "Before",
            slot.before() == null ? "(empty)" : contentSummary(slot.before()),
            OreUiRichTooltip.Tone.secondary
        );
        builder.keyValue(
            "After",
            slot.after() == null ? "(removed)" : contentSummary(slot.after()),
            statusTone(slot.status())
        );
        if (slot.after() instanceof DurabilityContent durability) {
            builder.keyValue(
                "Durability Cost",
                "-" + durability.durabilityCost() + " / craft",
                durability.consumesWholeTool() ? OreUiRichTooltip.Tone.danger : OreUiRichTooltip.Tone.warning
            );
            int maxDur = durability.maxDurability();
            if (maxDur > 0) {
                builder.keyValue(
                    "Tool Life",
                    durability.usesPerTool() + " crafts / tool (" + maxDur + " max)",
                    OreUiRichTooltip.Tone.secondary
                );
            }
        }
        if (slot.movedFrom() != null) {
            builder.keyValue("Moved From", slot.movedFrom().shortLabel(), OreUiRichTooltip.Tone.costP);
        }
        if (!slot.sourceEffects().isEmpty()) {
            StringBuilder fx = new StringBuilder();
            for (int i = 0; i < slot.sourceEffects().size(); i++) {
                if (i > 0) {
                    fx.append(", ");
                }
                fx.append(slot.sourceEffects().get(i).path());
            }
            builder.keyValue("Effects", fx.toString(), OreUiRichTooltip.Tone.muted);
        }
        return builder.build();
    }

    private static Component contentDisplayName(RecipeContent content) {
        try {
            return RecipeContentRenderer.getRenderer(content).getName(content);
        } catch (RuntimeException ignored) {
            return Component.literal(content.getClass().getSimpleName());
        }
    }

    private static String contentSummary(RecipeContent content) {
        if (content instanceof DurabilityContent durability) {
            String tool = contentDisplayName(durability.ingredient()).getString();
            String tail = durability.amount() > 1L ? " \u00d7" + durability.amount() : "";
            return tool + " (-" + durability.durabilityCost() + " dur)" + tail;
        }
        String name = contentDisplayName(content).getString();
        long amount = content.amount();
        if (content.chance() < RecipeContent.MAX_CHANCE) {
            return name + " x" + amount + " (" + Math.round(content.chance() * 100f) + "%)";
        }
        return name + " x" + amount;
    }

    private @Nullable Tooltip tooltipAt(double localX, double localY) {
        int baseX = Math.max(framePadding, (width() - layout.width()) / 2);
        int baseY = Math.max(framePadding, (height() - layout.height()) / 2);
        ValueMeter meter = layout.valueMeterAt(localX - baseX, localY - baseY);
        if (meter != null) {
            return ValueTypeGaugeRenderer.tooltipFor(meter.entry());
        }
        Slot slot = layout.slotAt(localX - baseX, localY - baseY);
        if (slot == null || slot.displayContent().isEmpty()) {
            return null;
        }
        if (slot.status().isChanged()) {
            Tooltip tooltip = Tooltip.create();
            tooltip.add(contentDisplayName(slot.displayContent()), Tooltip.title);
            tooltip.add(
                Component.literal(
                    slot.address().shortLabel() + " · " + statusLabel(slot.status(), slot.deltaBadge())
                )
            );
            tooltip.add(
                Component.literal(
                    (slot.before() == null ? "∅" : contentSummary(slot.before())) + " → "
                            + (slot.after() == null ? "∅" : contentSummary(slot.after()))
                )
            );
            return tooltip;
        }
        try {
            @SuppressWarnings("unchecked")
            RecipeContentRenderer<RecipeContent> renderer = (RecipeContentRenderer<RecipeContent>) RecipeContentRenderer
                    .getRenderer(slot.displayContent());
            return renderer.tooltip(
                slot.displayContent(),
                ExecutionOverlayContentHelper.currentVisualIndex(slot.displayContent()),
                TooltipContext.create()
            );
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private record Slot(
        SlotAddress address,
        SlotDiff.DiffStatus status,
        @Nullable RecipeContent before,
        @Nullable RecipeContent after,
        @Nullable SlotAddress movedFrom,
        String deltaBadge,
        ImmutableList<Namespace> sourceEffects,
        int x,
        int y
    ) {
        private RecipeContent displayContent() {
            RecipeContent c = after != null ? after : before;
            return c != null ? c : RecipeContent.empty();
        }
    }

    private record ValueMeter(
        ValueTypeGaugeRenderer.ValueEntry entry,
        SlotDiff.DiffStatus status,
        String deltaBadge,
        int x,
        int y,
        int width,
        int height
    ) {}

    private record Section(int x, int y, List<CirrusSlotGroupRenderer.Cell> cells) {
        private Section {
            cells = List.copyOf(cells);
        }
    }

    private record Side(List<Section> sections, List<Slot> slots, int width, int height, boolean usesGrid) {
        private static final Side empty = new Side(List.of(), List.of(), 0, 0, false);

        private Side offset(int x, int y) {
            if (this == empty || (x == 0 && y == 0)) {
                return this;
            }
            ArrayList<Section> shiftedSections = new ArrayList<>(sections.size());
            for (Section section : sections) {
                shiftedSections.add(new Section(section.x() + x, section.y() + y, section.cells()));
            }
            ArrayList<Slot> shiftedSlots = new ArrayList<>(slots.size());
            for (Slot slot : slots) {
                shiftedSlots.add(
                    new Slot(
                        slot.address(),
                        slot.status(),
                        slot.before(),
                        slot.after(),
                        slot.movedFrom(),
                        slot.deltaBadge(),
                        slot.sourceEffects(),
                        slot.x() + x,
                        slot.y() + y
                    )
                );
            }
            return new Side(List.copyOf(shiftedSections), List.copyOf(shiftedSlots), width, height, usesGrid);
        }
    }

    private record Layout(
        Side inputs,
        Side outputs,
        List<ValueMeter> valueMeters,
        int inputValueCount,
        int outputValueCount,
        int valueDividerY,
        boolean showArrow,
        int arrowX,
        int arrowY,
        int width,
        int height
    ) {
        static Layout build(ProjectionDisplayModel model) {
            ArrayList<ValueEntryWithDiff> valueEntries = new ArrayList<>();
            int inValues = 0;
            int outValues = 0;
            for (ProjectionDisplayModel.ValueView vv : model.values()) {
                ValueContent<?> display = vv.displayValue();
                if (display == null || display.isEmpty()) {
                    continue;
                }
                ValueTypeGaugeRenderer.Direction dir = vv.channel() == SlotChannel.input
                        ? ValueTypeGaugeRenderer.Direction.input
                        : ValueTypeGaugeRenderer.Direction.output;
                valueEntries.add(
                    new ValueEntryWithDiff(
                        ValueTypeGaugeRenderer.ValueEntry.fromContent(display, dir),
                        vv.status(),
                        vv.deltaBadge()
                    )
                );
                if (vv.channel() == SlotChannel.input) {
                    inValues++;
                } else {
                    outValues++;
                }
            }

            Side inputSide = buildSide(model.inputSide());
            Side outputSide = buildSide(model.outputSide());
            boolean hasSlots = !inputSide.slots().isEmpty() || !outputSide.slots().isEmpty()
                    || inputSide.usesGrid() || outputSide.usesGrid();
            boolean arrow = (!inputSide.slots().isEmpty() || inputSide.usesGrid())
                    && (!outputSide.slots().isEmpty() || outputSide.usesGrid());
            int arrowWidth = arrow ? arrowRenderWidth : 0;
            int arrowHeight = arrow ? arrowRenderHeight : 0;
            int middleWidth = arrow ? arrowGapLeft + arrowWidth + arrowGapRight : 0;
            int slotRowWidth = inputSide.width() + middleWidth + outputSide.width();
            int slotRowHeight = hasSlots ? Math.max(Math.max(inputSide.height(), outputSide.height()), arrowHeight) : 0;

            int meterCols = valueEntries.size() <= 1 ? 1 : 2;
            int minValueStripW = valueEntries.isEmpty()
                    ? 0
                    : meterCols * ValueTypeGaugeRenderer.databaseMeterMinWidth
                            + (meterCols - 1) * ValueTypeGaugeRenderer.databaseMeterGap;
            int totalWidth = Math.max(slotRowWidth, minValueStripW);
            int slotRowOffsetX = Math.max(0, (totalWidth - slotRowWidth) / 2);

            Side placedInputs = inputSide.offset(slotRowOffsetX, centered(slotRowHeight, inputSide.height()));
            Side placedOutputs = outputSide.offset(
                slotRowOffsetX + inputSide.width() + middleWidth,
                centered(slotRowHeight, outputSide.height())
            );

            ArrayList<ValueMeter> meters = new ArrayList<>(valueEntries.size());
            int dividerY = -1;
            int totalHeight = slotRowHeight;
            if (!valueEntries.isEmpty()) {
                int meterTop = hasSlots ? slotRowHeight + valueSectionGap : 0;
                if (hasSlots) {
                    dividerY = slotRowHeight + valueSectionGap / 2;
                }
                int gap = ValueTypeGaugeRenderer.databaseMeterGap;
                int meterH = ValueTypeGaugeRenderer.databaseMeterHeight;
                int cellW = meterCols == 1
                        ? totalWidth
                        : Math.max(ValueTypeGaugeRenderer.databaseMeterMinWidth, (totalWidth - gap) / 2);
                for (int i = 0; i < valueEntries.size(); i++) {
                    int col = i % meterCols;
                    int row = i / meterCols;
                    boolean lastOdd = meterCols == 2 && i == valueEntries.size() - 1 && col == 0;
                    int w = lastOdd ? totalWidth : cellW;
                    int x = col * (cellW + gap);
                    int y = meterTop + row * (meterH + gap);
                    ValueEntryWithDiff item = valueEntries.get(i);
                    meters.add(new ValueMeter(item.entry(), item.status(), item.deltaBadge(), x, y, w, meterH));
                }
                int rows = (int) Math.ceil(valueEntries.size() / (double) meterCols);
                totalHeight = meterTop + rows * meterH + Math.max(0, rows - 1) * gap;
            }

            return new Layout(
                placedInputs,
                placedOutputs,
                List.copyOf(meters),
                inValues,
                outValues,
                dividerY,
                arrow,
                slotRowOffsetX + inputSide.width() + arrowGapLeft,
                centered(slotRowHeight, arrowHeight),
                totalWidth,
                totalHeight
            );
        }

        private record ValueEntryWithDiff(
            ValueTypeGaugeRenderer.ValueEntry entry,
            SlotDiff.DiffStatus status,
            String deltaBadge
        ) {}

        private static Side buildSide(ProjectionDisplayModel.SideView sideView) {
            ProjectionDisplayModel.GridView grid = sideView.grid();
            if (grid == null) {
                return looseSide(sideView.looseSlots().castToList());
            }
            if (!grid.hasAnyOccupiedCell() && sideView.looseSlots().isEmpty()) {
                return new Side(List.of(), List.of(), 0, 0, true);
            }

            ArrayList<Section> sections = new ArrayList<>();
            ArrayList<Slot> slots = new ArrayList<>();
            int gridW = grid.spec().width() * slotSize;
            int gridH = grid.spec().height() * slotSize;
            sections.add(new Section(0, 0, CirrusSlotGroupRenderer.rectCells(grid.spec().width(), grid.spec().height())));
            for (ProjectionDisplayModel.CellView cell : grid.cells()) {
                if (!cell.isOccupied()) {
                    continue;
                }
                slots.add(
                    new Slot(
                        cell.address(),
                        cell.status(),
                        cell.before(),
                        cell.after(),
                        cell.movedFrom(),
                        cell.deltaBadge(),
                        cell.sourceEffects(),
                        cell.col() * slotSize,
                        cell.row() * slotSize
                    )
                );
            }

            Side loose = looseSide(sideView.looseSlots().castToList());
            if (!loose.slots().isEmpty()) {
                int y = gridH + sectionGap;
                Side shifted = loose.offset(0, y);
                sections.addAll(shifted.sections());
                slots.addAll(shifted.slots());
                return new Side(
                    List.copyOf(sections),
                    List.copyOf(slots),
                    Math.max(gridW, loose.width()),
                    y + loose.height(),
                    true
                );
            }
            return new Side(List.copyOf(sections), List.copyOf(slots), gridW, gridH, true);
        }

        private static Side looseSide(List<ProjectionDisplayModel.SlotView> views) {
            ArrayList<ProjectionDisplayModel.SlotView> visible = new ArrayList<>();
            for (ProjectionDisplayModel.SlotView sv : views) {
                RecipeContent display = sv.displayContent();
                if (display != null && display.notEmpty() && !(display instanceof ValueContent<?>)) {
                    visible.add(sv);
                }
            }
            if (visible.isEmpty()) {
                return Side.empty;
            }
            int columns = visible.size() <= 4
                    ? visible.size()
                    : visible.size() <= 9 ? 3 : Math.min(maxLooseColumns, (int) Math.ceil(Math.sqrt(visible.size())));
            int rows = (int) Math.ceil(visible.size() / (double) columns);
            int width = columns * slotSize;
            int height = rows * slotSize;
            ArrayList<Slot> slots = new ArrayList<>(visible.size());
            for (int i = 0; i < visible.size(); i++) {
                ProjectionDisplayModel.SlotView sv = visible.get(i);
                slots.add(
                    new Slot(
                        sv.address(),
                        sv.status(),
                        sv.before(),
                        sv.after(),
                        sv.movedFrom(),
                        sv.deltaBadge(),
                        sv.sourceEffects(),
                        (i % columns) * slotSize,
                        (i / columns) * slotSize
                    )
                );
            }
            return new Side(
                List.of(new Section(0, 0, CirrusSlotGroupRenderer.rowMajorCells(visible.size(), columns))),
                List.copyOf(slots),
                width,
                height,
                false
            );
        }

        private static int centered(int container, int content) {
            return Math.max(0, (container - content) / 2);
        }

        private boolean hasContent() {
            return !inputs.slots().isEmpty() || !outputs.slots().isEmpty() || !valueMeters.isEmpty();
        }

        private boolean usesGrid() {
            return inputs.usesGrid() || outputs.usesGrid();
        }

        private int inputCount() {
            return inputs.slots().size() + inputValueCount;
        }

        private int outputCount() {
            return outputs.slots().size() + outputValueCount;
        }

        private List<Slot> slots() {
            ArrayList<Slot> slots = new ArrayList<>(inputs.slots().size() + outputs.slots().size());
            slots.addAll(inputs.slots());
            slots.addAll(outputs.slots());
            return List.copyOf(slots);
        }

        private List<Section> sections() {
            ArrayList<Section> sections = new ArrayList<>(inputs.sections().size() + outputs.sections().size());
            sections.addAll(inputs.sections());
            sections.addAll(outputs.sections());
            return List.copyOf(sections);
        }

        private @Nullable Slot slotAt(double x, double y) {
            for (Slot slot : slots()) {
                if (x >= slot.x() && x < slot.x() + slotSize && y >= slot.y() && y < slot.y() + slotSize) {
                    return slot;
                }
            }
            return null;
        }

        private @Nullable ValueMeter valueMeterAt(double x, double y) {
            for (ValueMeter meter : valueMeters) {
                if (x >= meter.x()
                        && x < meter.x() + meter.width()
                        && y >= meter.y()
                        && y < meter.y() + meter.height()) {
                    return meter;
                }
            }
            return null;
        }
    }
}
