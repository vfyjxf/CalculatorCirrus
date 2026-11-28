package dev.vfyjxf.cirrus.ui.workbench.projection;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.RecipeContentRenderer;
import dev.vfyjxf.cirrus.api.recipe.content.StackContent;
import dev.vfyjxf.cirrus.api.recipe.content.StackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.TagStackContent;
import dev.vfyjxf.cirrus.api.recipe.content.TypedStackListContent;
import dev.vfyjxf.cirrus.api.recipe.content.ValueContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.DurabilityContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.FluidContainerContent;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cirrus.api.recipe.projection.pipeline.SlotDiff;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiRecipeGrid;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeGaugeRenderer;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeVisual;
import dev.vfyjxf.cirrus.ui.workbench.value.ValueTypeVisualRegistry;
import dev.vfyjxf.cirrus.util.NumberUtils;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Registry and built-in display templates for rendering heterogeneous {@link RecipeContent} diffs
 * across {@code ProjectionPipelineWidget} and {@code DatabasePage}.
 */
public final class ProjectionDiffTemplates {

    private static final int slotDim = 18;
    private static final int standardVisualW = 54;
    private static final int valueVisualW = 36;
    private static final int movedVisualW = 34;

    private static final CopyOnWriteArrayList<ProjectionDiffTemplate> customTemplates = new CopyOnWriteArrayList<>();

    public static final ProjectionDiffTemplate valueMeter = new ValueMeterTemplate();
    public static final ProjectionDiffTemplate movedSlot = new MovedSlotTemplate();
    public static final ProjectionDiffTemplate durability = new DurabilityDiffTemplate();
    public static final ProjectionDiffTemplate fluid = new FluidTransitionTemplate();
    public static final ProjectionDiffTemplate tagOrAlternatives = new TagOrAlternativesTemplate();
    public static final ProjectionDiffTemplate stackSlot = new StackSlotTemplate();
    public static final ProjectionDiffTemplate generic = new GenericContentTemplate();

    private static final List<ProjectionDiffTemplate> builtins = List.of(
        valueMeter,
        movedSlot,
        durability,
        fluid,
        tagOrAlternatives,
        stackSlot,
        generic
    );

    private ProjectionDiffTemplates() {}

    /**
     * Registers an additional display template (higher {@link ProjectionDiffTemplate#priority()}
     * takes precedence over lower-priority and built-in templates).
     */
    public static void register(ProjectionDiffTemplate template) {
        Objects.requireNonNull(template, "template");
        customTemplates.removeIf(existing -> existing.id().equals(template.id()));
        customTemplates.add(template);
    }

    public static ProjectionDiffTemplate resolve(ProjectionDiffTemplate.DiffInput input) {
        Objects.requireNonNull(input, "input");
        if (!customTemplates.isEmpty()) {
            List<ProjectionDiffTemplate> sorted = new ArrayList<>(customTemplates);
            sorted.sort(Comparator.comparingInt(ProjectionDiffTemplate::priority).reversed());
            for (ProjectionDiffTemplate candidate : sorted) {
                if (candidate.matches(input)) {
                    return candidate;
                }
            }
        }
        for (ProjectionDiffTemplate candidate : builtins) {
            if (candidate.matches(input)) {
                return candidate;
            }
        }
        return generic;
    }

    public static ProjectionDiffTemplate resolve(
        SlotAddress address,
        SlotDiff.DiffStatus status,
        @Nullable RecipeContent before,
        @Nullable RecipeContent after,
        @Nullable SlotAddress movedFrom,
        String defaultBadge
    ) {
        return resolve(new ProjectionDiffTemplate.DiffInput(address, status, before, after, movedFrom, defaultBadge));
    }

    /**
     * Produces a clean, content-kind-aware summary string for any {@link RecipeContent}
     * (e.g. {@code Energy: 16K FE}, {@code Time: 50t (2.5s)}, {@code Water 1000 mB},
     * {@code #c:ingots/iron ×2}, {@code Copper Ingot ×4 (25%)}).
     */
    public static String describeContent(@Nullable RecipeContent content) {
        if (content == null || content.isEmpty()) {
            return "(empty)";
        }
        if (content instanceof ValueContent<?> val) {
            return valueMeter.describeSingle(val);
        }
        if (content instanceof DurabilityContent) {
            return durability.describeSingle(content);
        }
        if (isFluidOrContainer(content)) {
            return fluid.describeSingle(content);
        }
        if (isTagOrAlternatives(content)) {
            return tagOrAlternatives.describeSingle(content);
        }
        return stackSlot.describeSingle(content);
    }

    /**
     * Returns a clean human-readable display name for any {@link RecipeContent}, never leaking
     * raw {@code calculator_cirrus:energy=16000} strings for {@link ValueContent}.
     */
    public static Component contentTitle(@Nullable RecipeContent content) {
        if (content == null || content.isEmpty()) {
            return Component.literal("(empty)");
        }
        if (content instanceof ValueContent<?> val) {
            return ValueTypeVisualRegistry.resolve(val.type()).displayName();
        }
        if (content instanceof DurabilityContent dc) {
            return contentTitle(dc.ingredient());
        }
        if (content instanceof FluidContainerContent fc) {
            return Component.literal(fluidContainerName(fc));
        }
        if (content instanceof TagStackContent<?> tag) {
            return Component.literal("#" + tag.tagKey().location());
        }
        try {
            Component name = RecipeContentRenderer.getRenderer(content).getName(content);
            if (name != null && !name.getString().isBlank()) {
                return name;
            }
        } catch (RuntimeException ignored) {
            // Headless unit test fallback
        }
        return Component.literal(content.getClass().getSimpleName());
    }

    //region shared helpers

    private static boolean isFluid(RecipeContent content) {
        return content instanceof StackContent<?, ?> sc && sc.isFluid();
    }

    private static boolean isFluidOrContainer(@Nullable RecipeContent content) {
        return content != null && (isFluid(content) || content instanceof FluidContainerContent);
    }

    private static boolean isTagOrAlternatives(@Nullable RecipeContent content) {
        if (content == null) {
            return false;
        }
        if (content instanceof TagStackContent<?>) {
            return true;
        }
        if (content instanceof StackListContent list && list.stacks().size() > 1) {
            return true;
        }
        return content instanceof TypedStackListContent<?, ?> typed && typed.stacks().size() > 1;
    }

    private static String sideTag(SlotAddress address) {
        return address.channel() == SlotChannel.input ? "IN" : "OUT";
    }

    private static String statusVerb(SlotDiff.DiffStatus status, @Nullable SlotAddress movedFrom) {
        return switch (status) {
            case added -> "added";
            case removed -> "removed";
            case transformed -> "transformed";
            case adjusted -> "adjusted";
            case moved -> movedFrom == null ? "moved" : "moved from " + movedFrom.shortLabel();
            case unchanged -> "unchanged";
        };
    }

    private static int defaultStatusAccent(SlotDiff.DiffStatus status, CirrusPalette palette) {
        return switch (status) {
            case added -> palette.diffAdded();
            case removed -> palette.diffRemoved();
            case transformed -> palette.costH();
            case moved -> palette.costP();
            case adjusted -> palette.focusRing();
            case unchanged -> palette.textMuted();
        };
    }

    private static String formatChanceSuffix(float chance) {
        if (chance >= 0.999f) {
            return "";
        }
        if (chance <= RecipeContent.MIN_CHANCE + 0.0001f) {
            return " (catalyst)";
        }
        return " (" + Math.max(1, Math.round(chance * 100f)) + "%)";
    }

    private static String formatFluidAmount(long mb) {
        if (mb < 10_000L) {
            return mb + " mB";
        }
        return NumberUtils.toSuffix(mb) + " mB";
    }

    private static String fluidContainerName(FluidContainerContent fc) {
        try {
            String itemName = fc.sampleContainer().value().getHoverName().getString();
            if (!itemName.isBlank()) {
                return itemName;
            }
        } catch (RuntimeException ignored) {
            // Headless fallback
        }
        return "Fluid Container";
    }

    private static String formatScaledBadge(long before, long after, String unit) {
        if (before <= 0L) {
            return "+" + NumberUtils.toSuffix(after) + (unit.isEmpty() ? "" : " " + unit);
        }
        if (after > before) {
            double ratio = (double) after / (double) before;
            if (ratio >= 1.5D) {
                return "\u00d7" + trimNumber(ratio);
            }
            long delta = after - before;
            return "+" + NumberUtils.toSuffix(delta) + (unit.isEmpty() ? "" : " " + unit);
        }
        if (after < before) {
            int pct = Math.max(1, Math.min(99, (int) Math.round((1.0D - ((double) after / (double) before)) * 100.0D)));
            return "-" + pct + "%";
        }
        return "";
    }

    private static String trimNumber(double value) {
        if (Math.abs(value - Math.rint(value)) < 0.0001D) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static void paintSlotOrFallback(
        SceneCanvas canvas,
        RecipeContent content,
        int x,
        int y,
        int size,
        CirrusPalette palette,
        WorkbenchThemeMode theme,
        ProjectionDiffTemplate.SlotSink slotSink,
        boolean fluidTankChrome
    ) {
        if (content instanceof ValueContent<?> val) {
            ValueTypeGaugeRenderer.drawMiniEmblem(
                canvas,
                val.type().typeId().toString(),
                x,
                y,
                size,
                palette,
                theme,
                1.0f
            );
            return;
        }
        OreUiPaint.slot(canvas, x, y, size, palette);
        if (content instanceof DurabilityContent dc) {
            int durAccent = dc.consumesWholeTool() ? palette.diffRemoved() : palette.costH();
            canvas.strokeRect(x, y, size, size, OreUiPaint.withAlpha(durAccent, 0.78f), 1);
        } else if (fluidTankChrome) {
            int tankAccent = palette.costP();
            canvas.strokeRect(x, y, size, size, OreUiPaint.withAlpha(tankAccent, 0.65f), 1);
            canvas.fill(x + 1, y + size - 2, Math.max(1, size - 2), 1, tankAccent);
        }
        if (ExecutionOverlayContentHelper.hasRenderableVisual(content)) {
            slotSink.enqueue(content, x, y, size);
        } else {
            paintUnknownEmblem(canvas, content, x, y, size, palette);
        }
    }

    private static void paintUnknownEmblem(
        SceneCanvas canvas,
        RecipeContent content,
        int x,
        int y,
        int size,
        CirrusPalette palette
    ) {
        int accent = palette.focusRing();
        canvas.fill(x + 1, y + 1, Math.max(1, size - 2), Math.max(1, size - 2), OreUiPaint.withAlpha(accent, 0.16f));
        String token = deriveContentToken(content);
        Component comp = Component.literal(token);
        int tw = ChromeText.width(canvas, comp);
        ChromeText.draw(
            canvas,
            comp,
            x + Math.max(1, (size - tw) / 2),
            ChromeText.centeredY(y, size, canvas),
            accent
        );
    }

    private static String deriveContentToken(RecipeContent content) {
        try {
            String path = content.recipeType().typeId().path();
            int slash = path.lastIndexOf('/');
            String tail = slash >= 0 && slash + 1 < path.length() ? path.substring(slash + 1) : path;
            if (!tail.isEmpty()) {
                return tail.substring(0, Math.min(2, tail.length())).toUpperCase(Locale.ROOT);
            }
        } catch (RuntimeException ignored) {
            // Fallback below
        }
        String simple = content.getClass().getSimpleName();
        return simple.isEmpty() ? "?" : simple.substring(0, Math.min(2, simple.length())).toUpperCase(Locale.ROOT);
    }

    private static void paintStandardSlotPair(
        SceneCanvas canvas,
        ProjectionDiffTemplate.DiffInput input,
        int x,
        int y,
        int rowHeight,
        CirrusPalette palette,
        WorkbenchThemeMode theme,
        ProjectionDiffTemplate.SlotSink slotSink,
        boolean highlightFluids
    ) {
        int slotY = y + Math.max(1, (rowHeight - slotDim) / 2);
        int accent = defaultStatusAccent(input.status(), palette);
        RecipeContent before = input.before();
        RecipeContent after = input.after();

        if (before != null && after != null) {
            paintSlotOrFallback(
                canvas,
                before,
                x,
                slotY,
                slotDim,
                palette,
                theme,
                slotSink,
                highlightFluids && isFluid(before)
            );
            OreUiRecipeGrid.drawRecipeArrow(
                canvas,
                x + slotDim + 2,
                x + slotDim + 12,
                slotY + slotDim / 2,
                palette,
                true
            );
            paintSlotOrFallback(
                canvas,
                after,
                x + slotDim + 14,
                slotY,
                slotDim,
                palette,
                theme,
                slotSink,
                highlightFluids && isFluid(after)
            );
        } else if (after != null) {
            drawUnaryActionGlyph(canvas, "+", x + 2, y, rowHeight, palette.diffAdded(), palette);
            paintSlotOrFallback(
                canvas,
                after,
                x + 18,
                slotY,
                slotDim,
                palette,
                theme,
                slotSink,
                highlightFluids && isFluid(after)
            );
        } else if (before != null) {
            drawUnaryActionGlyph(canvas, "\u2212", x + 2, y, rowHeight, palette.diffRemoved(), palette);
            paintSlotOrFallback(
                canvas,
                before,
                x + 18,
                slotY,
                slotDim,
                palette,
                theme,
                slotSink,
                highlightFluids && isFluid(before)
            );
            canvas.strokeRect(x + 18, slotY, slotDim, slotDim, OreUiPaint.withAlpha(palette.diffRemoved(), 0.65f), 1);
        }
    }

    /**
     * Draws a uniform, left-aligned action sign chip ({@code +}, {@code −}, {@code →}) using
     * pixel-exact symmetric geometry inside a {@code 13×13} OreUI frame so every unary diff row
     * aligns cleanly along the exact same axis regardless of font metrics or glyph baselines.
     */
    public static void drawUnaryActionGlyph(
        SceneCanvas canvas,
        String symbol,
        int x,
        int rowY,
        int rowHeight,
        int accent,
        CirrusPalette palette
    ) {
        int chipW = 13;
        int chipH = 13;
        int cy = rowY + Math.max(0, (rowHeight - chipH) / 2);
        boolean dark = OreUiPaint.isDark(palette);
        canvas.fill(x, cy, chipW, chipH, OreUiPaint.withAlpha(accent, dark ? 0.18f : 0.12f));
        canvas.strokeRect(x, cy, chipW, chipH, OreUiPaint.withAlpha(accent, 0.58f), 1);

        int mx = x + chipW / 2;
        int my = cy + chipH / 2;
        if ("+".equals(symbol)) {
            canvas.fill(mx - 3, my, 7, 1, accent);
            canvas.fill(mx, my - 3, 1, 7, accent);
        } else if ("-".equals(symbol) || "\u2212".equals(symbol)) {
            canvas.fill(mx - 3, my, 7, 1, accent);
        } else if ("\u2192".equals(symbol) || "->".equals(symbol)) {
            canvas.fill(mx - 3, my, 5, 1, accent);
            canvas.fill(mx + 1, my - 2, 1, 5, accent);
            canvas.fill(mx + 2, my - 1, 1, 3, accent);
            canvas.fill(mx + 3, my, 1, 1, accent);
        } else {
            Component comp = Component.literal(symbol);
            int tw = ChromeText.width(canvas, comp);
            ChromeText.draw(
                canvas,
                comp,
                x + Math.max(1, (chipW - tw) / 2),
                ChromeText.centeredY(cy, chipH, canvas),
                accent
            );
        }
    }

    private static boolean hasDurabilityContent(@Nullable RecipeContent before, @Nullable RecipeContent after) {
        return before instanceof DurabilityContent || after instanceof DurabilityContent;
    }

    //endregion

    //region built-in templates

    /**
     * Telemetry meter template for {@link ValueContent} (Energy FE, Time ticks, EU, AE, Mana,
     * Heat, Stress, and any custom {@code ValueType} registered in {@link ValueTypeVisualRegistry}).
     *
     * <p>Renders an instrument icon + unit badge on the left, clean localized value transition text
     * in the center, and a dual-track comparative gauge bar beneath the text.
     */
    private static final class ValueMeterTemplate implements ProjectionDiffTemplate {
        @Override
        public String id() {
            return "cirrus:diff_template/value_meter";
        }

        @Override
        public Kind kind() {
            return Kind.valueMeter;
        }

        @Override
        public int priority() {
            return 100;
        }

        @Override
        public boolean matches(DiffInput input) {
            return input.before() instanceof ValueContent<?> || input.after() instanceof ValueContent<?>;
        }

        private static @Nullable ValueContent<?> primaryValue(DiffInput input) {
            if (input.after() instanceof ValueContent<?> v) {
                return v;
            }
            if (input.before() instanceof ValueContent<?> v) {
                return v;
            }
            return null;
        }

        @Override
        public int accentColor(DiffInput input, CirrusPalette palette) {
            if (input.status() == SlotDiff.DiffStatus.removed) {
                return palette.diffRemoved();
            }
            if (input.status() == SlotDiff.DiffStatus.added) {
                return palette.diffAdded();
            }
            ValueContent<?> val = primaryValue(input);
            return val == null ? palette.focusRing() : ValueTypeVisualRegistry.resolve(val.type()).accentColor(palette);
        }

        @Override
        public String formatHeader(DiffInput input) {
            ValueContent<?> val = primaryValue(input);
            String typeLabel = val == null
                    ? "Value"
                    : ValueTypeVisualRegistry.resolve(val.type()).displayName().getString();
            return sideTag(input.address()) + " " + input.address().shortLabel()
                    + " \u00b7 " + typeLabel + " \u00b7 " + statusVerb(input.status(), input.movedFrom());
        }

        @Override
        public String formatDetail(DiffInput input) {
            RecipeContent before = input.before();
            RecipeContent after = input.after();
            if (before instanceof ValueContent<?> vb && after instanceof ValueContent<?> va) {
                ValueTypeVisual visB = ValueTypeVisualRegistry.resolve(vb.type());
                ValueTypeVisual visA = ValueTypeVisualRegistry.resolve(va.type());
                return visB.formatDetailed(vb.amount()) + " \u2192 " + visA.formatDetailed(va.amount());
            }
            if (before == null && after instanceof ValueContent<?> va) {
                ValueTypeVisual vis = ValueTypeVisualRegistry.resolve(va.type());
                return "+ " + vis.formatDetailed(va.amount()) + formatChanceSuffix(va.chance());
            }
            if (before instanceof ValueContent<?> vb && after == null) {
                ValueTypeVisual vis = ValueTypeVisualRegistry.resolve(vb.type());
                return vis.formatDetailed(vb.amount()) + " (removed)";
            }
            return describeContent(before) + " \u2192 " + describeContent(after);
        }

        @Override
        public String formatBadge(DiffInput input) {
            RecipeContent before = input.before();
            RecipeContent after = input.after();
            if (before instanceof ValueContent<?> vb && after instanceof ValueContent<?> va
                    && vb.type().typeId().equals(va.type().typeId())) {
                ValueTypeVisual vis = ValueTypeVisualRegistry.resolve(va.type());
                String badge = formatScaledBadge(vb.amount(), va.amount(), vis.unit());
                return badge.isEmpty() ? input.defaultBadge() : badge;
            }
            if (before == null && after instanceof ValueContent<?> va) {
                ValueTypeVisual vis = ValueTypeVisualRegistry.resolve(va.type());
                return "+" + vis.formatCompact(va.amount());
            }
            if (before instanceof ValueContent<?> vb && after == null) {
                ValueTypeVisual vis = ValueTypeVisualRegistry.resolve(vb.type());
                return "-" + vis.formatCompact(vb.amount());
            }
            return input.defaultBadge();
        }

        @Override
        public String describeSingle(RecipeContent content) {
            if (content instanceof ValueContent<?> val) {
                ValueTypeVisual vis = ValueTypeVisualRegistry.resolve(val.type());
                return vis.displayName().getString() + " " + vis.formatDetailed(val.amount())
                        + formatChanceSuffix(val.chance());
            }
            return contentTitle(content).getString();
        }

        @Override
        public int visualColumnWidth(DiffInput input) {
            return valueVisualW;
        }

        @Override
        public int minVisualHeight(DiffInput input) {
            return 26;
        }

        @Override
        public int extraBodyHeight(DiffInput input) {
            return 11;
        }

        @Override
        public void paintVisual(
            SceneCanvas canvas,
            DiffInput input,
            int x,
            int y,
            int colWidth,
            int rowHeight,
            CirrusPalette palette,
            WorkbenchThemeMode theme,
            SlotSink slotSink
        ) {
            ValueContent<?> val = primaryValue(input);
            if (val == null) {
                return;
            }
            ValueTypeVisual visual = ValueTypeVisualRegistry.resolve(val.type());
            int accent = visual.accentColor(palette);
            boolean dark = OreUiPaint.isDark(palette);
            int tileW = Math.min(colWidth - 4, 32);
            int tileH = Math.min(Math.max(24, rowHeight - 6), 28);
            int tx = x + Math.max(0, (colWidth - 2 - tileW) / 2);
            int ty = y + Math.max(1, (rowHeight - tileH) / 2);

            canvas.fill(tx, ty, tileW, tileH, OreUiPaint.withAlpha(accent, dark ? 0.16f : 0.11f));
            canvas.strokeRect(tx, ty, tileW, tileH, OreUiPaint.withAlpha(accent, 0.72f), 1);
            canvas.fill(tx + 1, ty + 1, Math.max(1, tileW - 2), 1, accent);

            ValueTypeGaugeRenderer.drawIcon(
                canvas,
                visual,
                tx + (tileW - 9) / 2,
                ty + 4,
                9,
                accent,
                theme,
                1.0f
            );

            Component unitComp = Component.literal(visual.unit());
            int unitW = ChromeText.width(canvas, unitComp);
            int ink = dark ? OreUiPaint.lighten(accent, 0.28f) : accent;
            ChromeText.draw(
                canvas,
                ChromeText.fit(canvas, unitComp, Math.max(8, tileW - 4)),
                tx + Math.max(2, (tileW - Math.min(unitW, tileW - 4)) / 2),
                ty + tileH - 10,
                ink
            );
        }

        @Override
        public void paintBodyExtra(
            SceneCanvas canvas,
            DiffInput input,
            int x,
            int y,
            int width,
            int height,
            CirrusPalette palette,
            WorkbenchThemeMode theme
        ) {
            if (width < 28 || height < 8) {
                return;
            }
            ValueContent<?> primary = primaryValue(input);
            if (primary == null) {
                return;
            }
            ValueTypeVisual visual = ValueTypeVisualRegistry.resolve(primary.type());
            int accent = accentColor(input, palette);
            int barW = Math.min(width, 210);
            int topY = y + 2;

            if (input.before() instanceof ValueContent<?> vb && input.after() instanceof ValueContent<?> va) {
                long beforeAmt = Math.max(0L, vb.amount());
                long afterAmt = Math.max(0L, va.amount());
                long maxAmt = Math.max(1L, Math.max(beforeAmt, afterAmt));
                float beforeFrac = Math.max(0.06f, Math.min(1.0f, (float) beforeAmt / (float) maxAmt));
                float afterFrac = Math.max(0.06f, Math.min(1.0f, (float) afterAmt / (float) maxAmt));

                // Top baseline track (before)
                canvas.fill(x, topY, barW, 3, OreUiPaint.meterTrack(palette));
                int bw = Math.max(2, Math.round(barW * beforeFrac));
                canvas.fill(x, topY, Math.min(barW, bw), 3, OreUiPaint.withAlpha(palette.textMuted(), 0.78f));

                // Bottom projected track (after)
                ValueTypeGaugeRenderer.drawSegmentedBar(
                    canvas,
                    x,
                    topY + 4,
                    barW,
                    4,
                    afterFrac,
                    accent,
                    palette,
                    1.0f
                );
            } else {
                long amt = Math.max(0L, primary.amount());
                float frac = visual.gaugeFraction(amt);
                ValueTypeGaugeRenderer.drawSegmentedBar(
                    canvas,
                    x,
                    topY + 2,
                    barW,
                    5,
                    frac,
                    accent,
                    palette,
                    1.0f
                );
            }
        }
    }

    /**
     * Grid-move template for {@link SlotDiff.DiffStatus#moved}: renders one slot icon + directional
     * chevron instead of duplicating the exact same icon on both sides of an arrow.
     */
    private static final class MovedSlotTemplate implements ProjectionDiffTemplate {
        @Override
        public String id() {
            return "cirrus:diff_template/moved_slot";
        }

        @Override
        public Kind kind() {
            return Kind.moved;
        }

        @Override
        public int priority() {
            return 85;
        }

        @Override
        public boolean matches(DiffInput input) {
            return input.status() == SlotDiff.DiffStatus.moved
                    && !(input.before() instanceof ValueContent<?>)
                    && !(input.after() instanceof ValueContent<?>);
        }

        @Override
        public int accentColor(DiffInput input, CirrusPalette palette) {
            return palette.costP();
        }

        @Override
        public String formatHeader(DiffInput input) {
            return sideTag(input.address()) + " " + input.address().shortLabel()
                    + " \u00b7 " + statusVerb(input.status(), input.movedFrom());
        }

        @Override
        public String formatDetail(DiffInput input) {
            RecipeContent item = input.primaryContent();
            String base = describeContent(item);
            if (input.movedFrom() != null) {
                return base + " (" + input.movedFrom().shortLabel() + " \u2192 " + input.address().shortLabel() + ")";
            }
            return base;
        }

        @Override
        public String formatBadge(DiffInput input) {
            if (input.movedFrom() != null) {
                return input.movedFrom().shortLabel() + "\u2192" + input.address().shortLabel();
            }
            return input.defaultBadge().isEmpty() ? "MOVED" : input.defaultBadge();
        }

        @Override
        public String describeSingle(RecipeContent content) {
            return describeContent(content);
        }

        @Override
        public int visualColumnWidth(DiffInput input) {
            return movedVisualW + 4;
        }

        @Override
        public void paintVisual(
            SceneCanvas canvas,
            DiffInput input,
            int x,
            int y,
            int colWidth,
            int rowHeight,
            CirrusPalette palette,
            WorkbenchThemeMode theme,
            SlotSink slotSink
        ) {
            RecipeContent content = input.primaryContent();
            if (content == null) {
                return;
            }
            int slotY = y + Math.max(1, (rowHeight - slotDim) / 2);
            drawUnaryActionGlyph(canvas, "\u2192", x + 2, y, rowHeight, palette.costP(), palette);
            paintSlotOrFallback(canvas, content, x + 18, slotY, slotDim, palette, theme, slotSink, isFluid(content));
            canvas.strokeRect(x + 18, slotY, slotDim, slotDim, OreUiPaint.withAlpha(palette.costP(), 0.75f), 1);
        }
    }

    /**
     * Durability requirement template for {@link DurabilityContent}: shows the conversion from a
     * tool ingredient into a durability-consuming tool requirement ({@code Iron Pickaxe → Iron Pickaxe (-4 dur · 62 uses/tool)})
     * together with a per-craft tool life bar.
     */
    private static final class DurabilityDiffTemplate implements ProjectionDiffTemplate {
        @Override
        public String id() {
            return "cirrus:diff_template/durability";
        }

        @Override
        public Kind kind() {
            return Kind.durability;
        }

        @Override
        public int priority() {
            return 80;
        }

        @Override
        public boolean matches(DiffInput input) {
            return hasDurabilityContent(input.before(), input.after());
        }

        private static @Nullable DurabilityContent activeDurability(DiffInput input) {
            if (input.after() instanceof DurabilityContent dc) {
                return dc;
            }
            if (input.before() instanceof DurabilityContent dc) {
                return dc;
            }
            return null;
        }

        @Override
        public int accentColor(DiffInput input, CirrusPalette palette) {
            DurabilityContent dc = activeDurability(input);
            return dc != null && dc.consumesWholeTool() ? palette.diffRemoved() : palette.costH();
        }

        @Override
        public String formatHeader(DiffInput input) {
            return sideTag(input.address()) + " " + input.address().shortLabel()
                    + " \u00b7 Durability \u00b7 " + statusVerb(input.status(), input.movedFrom());
        }

        @Override
        public String formatDetail(DiffInput input) {
            RecipeContent before = input.before();
            RecipeContent after = input.after();
            if (before instanceof DurabilityContent bdc && after instanceof DurabilityContent adc) {
                String toolName = contentTitle(adc.ingredient()).getString();
                String life = adc.maxDurability() > 0 ? " \u00b7 " + adc.usesPerTool() + " uses/tool" : "";
                return toolName + " (-" + bdc.durabilityCost() + " dur \u2192 -" + adc.durabilityCost() + " dur" + life + ")";
            }
            if (after instanceof DurabilityContent adc) {
                String toolName = contentTitle(adc.ingredient()).getString();
                String life = adc.maxDurability() > 0 ? " \u00b7 " + adc.usesPerTool() + " uses/tool" : "";
                if (before != null) {
                    return toolName + " \u2192 " + toolName + " (-" + adc.durabilityCost() + " dur" + life + ")";
                }
                return toolName + " (-" + adc.durabilityCost() + " dur" + life + ")";
            }
            if (before instanceof DurabilityContent bdc) {
                String toolName = contentTitle(bdc.ingredient()).getString();
                return toolName + " (-" + bdc.durabilityCost() + " dur)";
            }
            return stackSlot.formatDetail(input);
        }

        @Override
        public String formatBadge(DiffInput input) {
            DurabilityContent dc = activeDurability(input);
            if (dc != null) {
                return "-" + dc.durabilityCost() + " dur";
            }
            return input.defaultBadge();
        }

        @Override
        public String describeSingle(RecipeContent content) {
            if (content instanceof DurabilityContent dc) {
                String toolName = contentTitle(dc.ingredient()).getString();
                String countTail = dc.amount() > 1L ? " \u00d7" + dc.amount() : "";
                return toolName + " (-" + dc.durabilityCost() + " dur)" + countTail;
            }
            return stackSlot.describeSingle(content);
        }

        @Override
        public int visualColumnWidth(DiffInput input) {
            return standardVisualW;
        }

        @Override
        public int extraBodyHeight(DiffInput input) {
            DurabilityContent dc = activeDurability(input);
            return dc != null && dc.maxDurability() > 0 ? 10 : 0;
        }

        @Override
        public void paintVisual(
            SceneCanvas canvas,
            DiffInput input,
            int x,
            int y,
            int colWidth,
            int rowHeight,
            CirrusPalette palette,
            WorkbenchThemeMode theme,
            SlotSink slotSink
        ) {
            paintStandardSlotPair(canvas, input, x, y, rowHeight, palette, theme, slotSink, false);
        }

        @Override
        public void paintBodyExtra(
            SceneCanvas canvas,
            DiffInput input,
            int x,
            int y,
            int width,
            int height,
            CirrusPalette palette,
            WorkbenchThemeMode theme
        ) {
            DurabilityContent dc = activeDurability(input);
            if (dc == null || width < 28 || height < 8) {
                return;
            }
            int max = dc.maxDurability();
            if (max <= 0) {
                return;
            }
            int cost = dc.durabilityCost();
            float costFrac = Math.max(0.0f, Math.min(1.0f, (float) cost / (float) max));
            float remainingFrac = Math.max(0.0f, 1.0f - costFrac);
            int barW = Math.min(width, 200);
            int topY = y + 2;

            // Top track: full tool capacity with highlighted per-craft cost slice at the right end.
            canvas.fill(x, topY, barW, 2, OreUiPaint.meterTrack(palette));
            int costPx = Math.min(barW, Math.max(3, Math.round(barW * costFrac)));
            int costAccent = dc.consumesWholeTool() ? palette.diffRemoved() : palette.costH();
            canvas.fill(x + barW - costPx, topY, costPx, 2, costAccent);

            // Bottom segmented bar: remaining life after 1 craft.
            int barColor = dc.consumesWholeTool()
                    ? palette.diffRemoved()
                    : remainingFrac > 0.5f ? palette.diffAdded() : palette.costH();
            ValueTypeGaugeRenderer
                    .drawSegmentedBar(canvas, x, topY + 3, barW, 4, remainingFrac, barColor, palette, 1.0f);
        }
    }

    /**
     * Fluid & fluid-container template: formats fluid quantities in {@code mB}, names unpacked
     * container catalysts cleanly, and styles fluid slots with subtle tank chrome.
     */
    private static final class FluidTransitionTemplate implements ProjectionDiffTemplate {
        @Override
        public String id() {
            return "cirrus:diff_template/fluid";
        }

        @Override
        public Kind kind() {
            return Kind.fluid;
        }

        @Override
        public int priority() {
            return 70;
        }

        @Override
        public boolean matches(DiffInput input) {
            return isFluidOrContainer(input.before()) || isFluidOrContainer(input.after());
        }

        @Override
        public int accentColor(DiffInput input, CirrusPalette palette) {
            return defaultStatusAccent(input.status(), palette);
        }

        @Override
        public String formatHeader(DiffInput input) {
            return sideTag(input.address()) + " " + input.address().shortLabel()
                    + " \u00b7 " + statusVerb(input.status(), input.movedFrom());
        }

        @Override
        public String formatDetail(DiffInput input) {
            RecipeContent before = input.before();
            RecipeContent after = input.after();
            if (before == null && after != null) {
                return "+ " + describeSingle(after);
            }
            if (before != null && after == null) {
                return describeSingle(before) + " (removed)";
            }
            if (before != null && after != null) {
                if (input.status() == SlotDiff.DiffStatus.adjusted && isFluid(before) && isFluid(after)) {
                    return contentTitle(after).getString() + " "
                            + formatFluidAmount(before.amount()) + " \u2192 " + formatFluidAmount(after.amount());
                }
                return describeSingle(before) + " \u2192 " + describeSingle(after);
            }
            return "";
        }

        @Override
        public String formatBadge(DiffInput input) {
            RecipeContent before = input.before();
            RecipeContent after = input.after();
            if (input.status() == SlotDiff.DiffStatus.adjusted && before != null && after != null && isFluid(after)) {
                String scaled = formatScaledBadge(before.amount(), after.amount(), "mB");
                if (!scaled.isEmpty()) {
                    return scaled;
                }
            }
            if (before == null && after != null && isFluid(after)) {
                return "+" + formatFluidAmount(after.amount());
            }
            if (before != null && after == null && isFluid(before)) {
                return "-" + formatFluidAmount(before.amount());
            }
            return input.defaultBadge();
        }

        @Override
        public String describeSingle(RecipeContent content) {
            if (isFluid(content)) {
                return contentTitle(content).getString() + " " + formatFluidAmount(content.amount())
                        + formatChanceSuffix(content.chance());
            }
            if (content instanceof FluidContainerContent fc) {
                return fluidContainerName(fc) + " \u00d7" + fc.amount() + formatChanceSuffix(fc.chance());
            }
            return stackSlot.describeSingle(content);
        }

        @Override
        public int visualColumnWidth(DiffInput input) {
            return standardVisualW;
        }

        @Override
        public void paintVisual(
            SceneCanvas canvas,
            DiffInput input,
            int x,
            int y,
            int colWidth,
            int rowHeight,
            CirrusPalette palette,
            WorkbenchThemeMode theme,
            SlotSink slotSink
        ) {
            paintStandardSlotPair(canvas, input, x, y, rowHeight, palette, theme, slotSink, true);
        }
    }

    /**
     * Tag & multi-alternative ingredient template: marks slots with a corner {@code #} or variant
     * count indicator and renders tag IDs / alternative counts clearly.
     */
    private static final class TagOrAlternativesTemplate implements ProjectionDiffTemplate {
        @Override
        public String id() {
            return "cirrus:diff_template/tag_or_alternatives";
        }

        @Override
        public Kind kind() {
            return Kind.tagOrAlternatives;
        }

        @Override
        public int priority() {
            return 60;
        }

        @Override
        public boolean matches(DiffInput input) {
            return isTagOrAlternatives(input.before()) || isTagOrAlternatives(input.after());
        }

        @Override
        public int accentColor(DiffInput input, CirrusPalette palette) {
            return defaultStatusAccent(input.status(), palette);
        }

        @Override
        public String formatHeader(DiffInput input) {
            String kindTag = (input.primaryContent() instanceof TagStackContent<?>) ? "Tag" : "Variants";
            return sideTag(input.address()) + " " + input.address().shortLabel()
                    + " \u00b7 " + kindTag + " \u00b7 " + statusVerb(input.status(), input.movedFrom());
        }

        @Override
        public String formatDetail(DiffInput input) {
            RecipeContent before = input.before();
            RecipeContent after = input.after();
            if (before == null && after != null) {
                return "+ " + describeSingle(after);
            }
            if (before != null && after == null) {
                return describeSingle(before) + " (removed)";
            }
            if (before != null && after != null) {
                if (input.status() == SlotDiff.DiffStatus.adjusted) {
                    return contentTitle(after).getString() + " " + before.amount() + " \u2192 " + after.amount();
                }
                return describeSingle(before) + " \u2192 " + describeSingle(after);
            }
            return "";
        }

        @Override
        public String formatBadge(DiffInput input) {
            return input.defaultBadge();
        }

        @Override
        public String describeSingle(RecipeContent content) {
            if (content instanceof TagStackContent<?> tag) {
                return "#" + tag.tagKey().location() + " \u00d7" + tag.amount() + formatChanceSuffix(tag.chance());
            }
            if (content instanceof StackListContent list) {
                return contentTitle(content).getString() + " (" + list.stacks().size() + " variants) \u00d7"
                        + list.amount() + formatChanceSuffix(list.chance());
            }
            if (content instanceof TypedStackListContent<?, ?> typed) {
                return contentTitle(content).getString() + " (" + typed.stacks().size() + " variants) \u00d7"
                        + typed.amount() + formatChanceSuffix(typed.chance());
            }
            return stackSlot.describeSingle(content);
        }

        @Override
        public int visualColumnWidth(DiffInput input) {
            return standardVisualW;
        }

        @Override
        public void paintVisual(
            SceneCanvas canvas,
            DiffInput input,
            int x,
            int y,
            int colWidth,
            int rowHeight,
            CirrusPalette palette,
            WorkbenchThemeMode theme,
            SlotSink slotSink
        ) {
            paintStandardSlotPair(canvas, input, x, y, rowHeight, palette, theme, slotSink, false);
            int slotY = y + Math.max(1, (rowHeight - slotDim) / 2);
            int pipX = input.before() == null ? x + 14 : x;
            canvas.fill(pipX + 1, slotY + 1, 4, 4, palette.costC());
        }
    }

    /**
     * Standard item/entity stack slot diff template.
     */
    private static final class StackSlotTemplate implements ProjectionDiffTemplate {
        @Override
        public String id() {
            return "cirrus:diff_template/stack_slot";
        }

        @Override
        public Kind kind() {
            return Kind.stackSlot;
        }

        @Override
        public int priority() {
            return 20;
        }

        @Override
        public boolean matches(DiffInput input) {
            return input.before() instanceof StackContent<?, ?> || input.after() instanceof StackContent<?, ?>;
        }

        @Override
        public int accentColor(DiffInput input, CirrusPalette palette) {
            return defaultStatusAccent(input.status(), palette);
        }

        @Override
        public String formatHeader(DiffInput input) {
            return sideTag(input.address()) + " " + input.address().shortLabel()
                    + " \u00b7 " + statusVerb(input.status(), input.movedFrom());
        }

        @Override
        public String formatDetail(DiffInput input) {
            RecipeContent before = input.before();
            RecipeContent after = input.after();
            if (before == null && after != null) {
                return "+ " + describeSingle(after);
            }
            if (before != null && after == null) {
                return describeSingle(before) + " (removed)";
            }
            if (before != null && after != null) {
                if (input.status() == SlotDiff.DiffStatus.adjusted) {
                    return contentTitle(after).getString() + " " + before.amount() + " \u2192 " + after.amount();
                }
                return describeSingle(before) + " \u2192 " + describeSingle(after);
            }
            return "";
        }

        @Override
        public String formatBadge(DiffInput input) {
            return input.defaultBadge();
        }

        @Override
        public String describeSingle(RecipeContent content) {
            String name = contentTitle(content).getString();
            return name + " \u00d7" + content.amount() + formatChanceSuffix(content.chance());
        }

        @Override
        public int visualColumnWidth(DiffInput input) {
            return standardVisualW;
        }

        @Override
        public void paintVisual(
            SceneCanvas canvas,
            DiffInput input,
            int x,
            int y,
            int colWidth,
            int rowHeight,
            CirrusPalette palette,
            WorkbenchThemeMode theme,
            SlotSink slotSink
        ) {
            paintStandardSlotPair(canvas, input, x, y, rowHeight, palette, theme, slotSink, false);
        }
    }

    /**
     * Safe fallback template for any custom/third-party {@link RecipeContent}; renders a typed
     * emblem pill when no sprite visual exists so no diff entry ever displays empty slot boxes.
     */
    private static final class GenericContentTemplate implements ProjectionDiffTemplate {
        @Override
        public String id() {
            return "cirrus:diff_template/generic";
        }

        @Override
        public Kind kind() {
            return Kind.generic;
        }

        @Override
        public int priority() {
            return Integer.MIN_VALUE;
        }

        @Override
        public boolean matches(DiffInput input) {
            return true;
        }

        @Override
        public int accentColor(DiffInput input, CirrusPalette palette) {
            return defaultStatusAccent(input.status(), palette);
        }

        @Override
        public String formatHeader(DiffInput input) {
            return sideTag(input.address()) + " " + input.address().shortLabel()
                    + " \u00b7 " + statusVerb(input.status(), input.movedFrom());
        }

        @Override
        public String formatDetail(DiffInput input) {
            RecipeContent before = input.before();
            RecipeContent after = input.after();
            if (before == null && after != null) {
                return "+ " + describeSingle(after);
            }
            if (before != null && after == null) {
                return describeSingle(before) + " (removed)";
            }
            if (before != null && after != null) {
                return describeSingle(before) + " \u2192 " + describeSingle(after);
            }
            return "";
        }

        @Override
        public String formatBadge(DiffInput input) {
            return input.defaultBadge();
        }

        @Override
        public String describeSingle(RecipeContent content) {
            return contentTitle(content).getString() + " \u00d7" + content.amount()
                    + formatChanceSuffix(content.chance());
        }

        @Override
        public int visualColumnWidth(DiffInput input) {
            return standardVisualW;
        }

        @Override
        public void paintVisual(
            SceneCanvas canvas,
            DiffInput input,
            int x,
            int y,
            int colWidth,
            int rowHeight,
            CirrusPalette palette,
            WorkbenchThemeMode theme,
            SlotSink slotSink
        ) {
            paintStandardSlotPair(canvas, input, x, y, rowHeight, palette, theme, slotSink, false);
        }
    }

    //endregion
}
