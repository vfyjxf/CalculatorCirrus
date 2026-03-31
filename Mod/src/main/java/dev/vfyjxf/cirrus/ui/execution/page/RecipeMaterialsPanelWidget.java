package dev.vfyjxf.cirrus.ui.execution.page;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.execution.driver.ExecutionDriverSnapshot;
import dev.vfyjxf.cirrus.api.recipe.execution.overlay.OverlayMaterialRequirement;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.execution.state.ExecutionOverlayViewState;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayContentHelper;
import dev.vfyjxf.cirrus.ui.execution.support.ExecutionOverlayUi;
import dev.vfyjxf.cirrus.ui.tooltip.RichTooltipComponent;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusControlChrome;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipStack;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.Rect2i;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;

final class RecipeMaterialsPanelWidget extends Widget {
    private static final int padding = 5;
    private static final int paddingCompact = 4;
    private static final int paddingDense = 3;
    private static final int toolbarHeight = 18;
    private static final int toolbarHeightDense = 16;
    private static final int buttonSize = 18;
    private static final int buttonSizeDense = 16;
    private static final int gridGap = 3;
    private static final int gridGapDense = 2;
    private static final int cellWidth = 48;
    private static final int cellWidthDense = 42;
    private static final int cellHeight = 22;
    private static final int cellHeightDense = 20;
    private static final int slotVisualSize = 18;
    private static final int metricMarkerWidth = 2;

    private final ExecutionOverlayViewState state;
    private final ArrayList<InteractiveArea> interactiveAreas = new ArrayList<>();
    private DisplayMode displayMode = DisplayMode.missing;
    private RecipePage.LayoutDensity layoutDensity = RecipePage.LayoutDensity.normal;

    private final ScrollState materialsScrollState = CirrusScroll.longVerticalState(10);
    private int scrollY = 0;

    RecipeMaterialsPanelWidget(ExecutionOverlayViewState state) {
        this.state = state;
        useStyle(background(Background.Outlined.flat), minWidth(0), minHeight(0), boxSizingBorderBox());
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            var relative = input.mouseRelative(this);
            int mouseX = (int) Math.round(relative.x());
            int mouseY = (int) Math.round(relative.y());
            for (InteractiveArea area : interactiveAreas) {
                if (ExecutionOverlayUi.contains(area.bounds(), mouseX, mouseY) && area.action() != null) {
                    area.action().run();
                    context.consume();
                    return EventDispatch.consumed;
                }
            }
            return EventDispatch.pass;
        });
        onMouseScrolled((mouseX, mouseY, scrollX, scrollY, context) -> {
            if (scrollY != 0) {
                materialsScrollState.scrollBy(0, (float) scrollY);
                clampScrollY();
                return EventDispatch.consumed;
            }
            return EventDispatch.pass;
        });
    }

    void setLayoutDensity(RecipePage.LayoutDensity density) {
        layoutDensity = density == null ? RecipePage.LayoutDensity.normal : density;
        clampScrollY();
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        interactiveAreas.clear();
        Font font = context().font();
        int pad = resolvePadding(layoutDensity);
        int toolbarH = resolveToolbarHeight(layoutDensity);
        int contentW = Math.max(0, width() - pad * 2);
        int contentH = Math.max(0, height() - pad * 2);
        if (contentW <= 0 || contentH <= 0) {
            return;
        }

        drawToolbar(canvas, font, pad, pad, contentW, toolbarH, mouseX, mouseY);
        int gridY = pad + toolbarH + 3;
        int gridH = Math.max(0, height() - pad - gridY);
        drawMaterials(canvas, font, pad, gridY, contentW, gridH, mouseX, mouseY);
    }

    @Override
    public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
        for (InteractiveArea area : interactiveAreas) {
            if (ExecutionOverlayUi.contains(area.bounds(), mouseX, mouseY)) {
                Tooltip tooltip = area.tooltip().get();
                if (tooltip != null && tooltip.notEmpty()) {
                    return tooltip;
                }
            }
        }
        return super.hoverTooltip(mouseX, mouseY);
    }

    private void drawToolbar(SceneCanvas canvas, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        int button = resolveButtonSize(layoutDensity);
        Rect2i modeButton = new Rect2i(x + width - button, y, button, height);

        int titleWidth = Math.max(0, modeButton.getX() - x - 4);
        String title = titleText(font, titleWidth);
        int titleY = y + Math.max(0, (height - font.lineHeight) / 2);
        canvas.drawString(title, x, titleY, ExecutionOverlayUi.titleColor, false);

        boolean hovered = hover(modeButton, mouseX, mouseY);
        drawIconButton(canvas, modeButton, displayMode.icon(hovered), displayMode.activeColor(), hovered, true);
        interactiveAreas.add(new InteractiveArea(modeButton, this::displayModeTooltip, this::toggleDisplayMode));
    }

    private String titleText(Font font, int width) {
        List<OverlayMaterialRequirement> entries = visibleRequirements();
        String title = displayMode.label() + " " + entries.size();
        return ExecutionOverlayUi.trim(font, title, width);
    }

    private void drawIconButton(
        SceneCanvas canvas,
        Rect2i area,
        SizedTexture icon,
        int accent,
        boolean hovered,
        boolean enabled
    ) {
        CirrusControlChrome.renderButtonBackground(canvas, area.getX(), area.getY(), area.getWidth(), area.getHeight(), false, hovered, enabled);
        int iconX = area.getX() + CirrusControlChrome.centeredIconX(area.getWidth(), icon.width());
        int iconY = area.getY() + CirrusControlChrome.centeredIconY(area.getHeight(), icon.height(), false);
        canvas.texture(icon, iconX, iconY, icon.width(), icon.height());
        if (hovered) {
            canvas.fill(area.getX() + 3, area.getY() + area.getHeight() - 2, Math.max(1, area.getWidth() - 6), 1, accent);
        }
    }

    private void drawMaterials(SceneCanvas canvas, Font font, int x, int y, int width, int height, int mouseX, int mouseY) {
        List<OverlayMaterialRequirement> requirements = visibleRequirements();
        if (requirements.isEmpty()) {
            String text = state.calculating()
                ? ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.calculatingShort)
                : ExecutionOverlayUi.tr(displayMode.emptyLabel());
            canvas.drawString(ExecutionOverlayUi.trim(font, text, width), x, y + 2, ExecutionOverlayUi.textSecondary, false);
            return;
        }

        int gap = resolveGridGap(layoutDensity);
        int cellW = resolveCellWidth(layoutDensity);
        int cellH = resolveCellHeight(layoutDensity);
        int columns = Math.max(1, Math.min(requirements.size(), (width + gap) / Math.max(1, cellW + gap)));
        int usedWidth = columns * cellW + Math.max(0, columns - 1) * gap;
        int startX = x + Math.max(0, (width - usedWidth) / 2);

        clampScrollY();
        int totalRows = requirements.isEmpty() ? 0 : (requirements.size() + columns - 1) / columns;
        int scrolledY = y - scrollY;

        for (int index = 0; index < requirements.size(); index++) {
            int col = index % columns;
            int row = index / columns;
            int cellX = startX + col * (cellW + gap);
            int cellY = scrolledY + row * (cellH + gap);
            if (cellY + cellH < y) continue;
            if (cellY > y + height) break;
            Rect2i cell = new Rect2i(cellX, cellY, cellW, cellH);
            OverlayMaterialRequirement requirement = requirements.get(index);
            boolean hovered = hover(cell, mouseX, mouseY);
            drawMaterialCell(canvas, font, cell, requirement, hovered);
            interactiveAreas.add(new InteractiveArea(cell, () -> materialTooltip(requirement), null));
        }
    }

    private void drawMaterialCell(SceneCanvas canvas, Font font, Rect2i cell, OverlayMaterialRequirement requirement, boolean hovered) {
        canvas.texture(Background.flat, cell.getX(), cell.getY(), cell.getWidth(), cell.getHeight());
        if (hovered) {
            canvas.fill(cell.getX(), cell.getY(), cell.getWidth(), cell.getHeight(), 0x12FFFFFF);
        }

        RecipeContent content = requirement.material();
        int slotX = cell.getX() + 2;
        int slotY = cell.getY() + Math.max(1, (cell.getHeight() - slotVisualSize) / 2);
        if (ExecutionOverlayContentHelper.hasRenderableVisual(content)) {
            ExecutionOverlayUi.drawSlot(canvas, content, slotX, slotY);
        } else {
            canvas.texture(Background.Slot.dark, slotX, slotY, slotVisualSize, slotVisualSize);
            String token = ExecutionOverlayContentHelper.fallbackToken(content);
            canvas.drawString(
                token,
                slotX + Math.max(1, (slotVisualSize - font.width(token)) / 2),
                slotY + 5,
                ExecutionOverlayUi.textSecondary,
                false
            );
        }

        int metricX = slotX + slotVisualSize + 3;
        int metricY = cell.getY() + Math.max(1, (cell.getHeight() - font.lineHeight) / 2);
        int metricW = Math.max(0, cell.getX() + cell.getWidth() - metricX - 3);
        drawMetric(canvas, font, metricX, metricY, metricW, displayMode.activeColor(), displayMode.value(requirement), displayMode.emphasize(requirement));
    }

    private void drawMetric(
        SceneCanvas canvas,
        Font font,
        int x,
        int y,
        int width,
        int color,
        long value,
        boolean emphasize
    ) {
        if (width <= 0) {
            return;
        }
        canvas.fill(x, y + 2, metricMarkerWidth, Math.max(1, font.lineHeight - 4), color);
        String text = ExecutionOverlayUi.compactNumber(value);
        int textX = x + metricMarkerWidth + 2;
        canvas.drawString(
            ExecutionOverlayUi.trim(font, text, Math.max(0, width - metricMarkerWidth - 2)),
            textX,
            y,
            emphasize ? color : ExecutionOverlayUi.textPrimary,
            false
        );
    }

    private void clampScrollY() {
        List<OverlayMaterialRequirement> req = visibleRequirements();
        int gap = resolveGridGap(layoutDensity);
        int cellH = resolveCellHeight(layoutDensity);
        int cols = Math.max(1, Math.min(req.size(), (width() + gap) / Math.max(1, cellH + gap)));
        int rows = req.isEmpty() ? 0 : (req.size() + cols - 1) / cols;
        int contentH = rows * cellH + Math.max(0, rows - 1) * gap;
        int maxScroll = Math.max(0, contentH - height());
        scrollY = Math.max(0, Math.min((int) materialsScrollState.targetScrollY(), maxScroll));
    }

    private List<OverlayMaterialRequirement> visibleRequirements() {
        ExecutionDriverSnapshot snapshot = state.snapshot();
        if (snapshot == null || snapshot.materials().isEmpty()) {
            return List.of();
        }
        ArrayList<OverlayMaterialRequirement> out = new ArrayList<>();
        for (OverlayMaterialRequirement requirement : snapshot.materials()) {
            if (displayMode.accepts(requirement)) {
                out.add(requirement);
            }
        }
        out.sort(displayMode::compare);
        return out;
    }

    private void toggleDisplayMode() {
        displayMode = displayMode == DisplayMode.missing ? DisplayMode.total : DisplayMode.missing;
        materialsScrollState.resetScroll();
        ExecutionOverlayUi.playClickSound();
    }

    private Tooltip displayModeTooltip() {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(ExecutionOverlayUi.tooltipTitleText(displayMode.label()));
        tooltip.add(ExecutionOverlayUi.tooltipBodyText(displayMode.next().label()));
        return tooltip;
    }

    private Tooltip materialTooltip(OverlayMaterialRequirement requirement) {
        Tooltip tooltip = Tooltip.create();
        TooltipStack<?> stack = ExecutionOverlayContentHelper.tooltipStack(requirement.material());
        if (stack != null) {
            tooltip.setStack(stack);
        }
        tooltip.add(ExecutionOverlayUi.tooltipTitleText(label(requirement)));
        RichTooltipComponent.Builder builder = RichTooltipComponent.builder();
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.need),
            ExecutionOverlayUi.tooltipTextSecondary,
            ExecutionOverlayUi.compactNumber(requirement.required()),
            ExecutionOverlayUi.tooltipTextPrimary
        );
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.have),
            ExecutionOverlayUi.tooltipTextSecondary,
            ExecutionOverlayUi.compactNumber(requirement.available()),
            ExecutionOverlayUi.tooltipSuccess
        );
        builder.kv(
            ExecutionOverlayUi.tr(CirrusLangs.Execution.Overlay.Material.miss),
            ExecutionOverlayUi.tooltipTextSecondary,
            ExecutionOverlayUi.compactNumber(requirement.missing()),
            requirement.missing() > 0 ? ExecutionOverlayUi.tooltipWarning : ExecutionOverlayUi.tooltipTextSecondary
        );
        tooltip.add(builder.build());
        return tooltip;
    }

    private static String label(OverlayMaterialRequirement requirement) {
        String label = ExecutionOverlayContentHelper.label(requirement.material());
        return label == null || label.isBlank() ? "-" : label;
    }

    private static boolean hover(Rect2i area, int mouseX, int mouseY) {
        return ExecutionOverlayUi.contains(area, mouseX, mouseY);
    }

    private static int resolvePadding(RecipePage.LayoutDensity density) {
        return switch (density) {
            case normal -> padding;
            case compact -> paddingCompact;
            case dense -> paddingDense;
        };
    }

    private static int resolveToolbarHeight(RecipePage.LayoutDensity density) {
        return density == RecipePage.LayoutDensity.dense ? toolbarHeightDense : toolbarHeight;
    }

    private static int resolveButtonSize(RecipePage.LayoutDensity density) {
        return density == RecipePage.LayoutDensity.dense ? buttonSizeDense : buttonSize;
    }

    private static int resolveGridGap(RecipePage.LayoutDensity density) {
        return density == RecipePage.LayoutDensity.dense ? gridGapDense : gridGap;
    }

    static int resolveCellWidth(RecipePage.LayoutDensity density) {
        return density == RecipePage.LayoutDensity.dense ? cellWidthDense : cellWidth;
    }

    static int resolveCellHeight(RecipePage.LayoutDensity density) {
        return density == RecipePage.LayoutDensity.dense ? cellHeightDense : cellHeight;
    }

    private enum DisplayMode {
        missing(
            CirrusLangs.Execution.Overlay.Material.viewMissing,
            CirrusLangs.Execution.Overlay.Material.noneMissing,
            CirrusTextures.Icon.Calc.Sort.missingFirst,
            CirrusTextures.Icon.Calc.Sort.missingFirstPressed,
            ExecutionOverlayUi.warning
        ),
        total(
            CirrusLangs.Execution.Overlay.Material.viewTotal,
            CirrusLangs.Execution.Overlay.Material.none,
            CirrusTextures.Icon.Calc.Sort.number,
            CirrusTextures.Icon.Calc.Sort.numberPressed,
            ExecutionOverlayUi.accent
        );

        private final LangEntry label;
        private final LangEntry emptyLabel;
        private final SizedTexture icon;
        private final SizedTexture hoverIcon;
        private final int color;

        DisplayMode(LangEntry label, LangEntry emptyLabel, SizedTexture icon, SizedTexture hoverIcon, int color) {
            this.label = label;
            this.emptyLabel = emptyLabel;
            this.icon = icon;
            this.hoverIcon = hoverIcon;
            this.color = color;
        }

        private boolean accepts(OverlayMaterialRequirement requirement) {
            return switch (this) {
                case missing -> requirement.missing() > 0L;
                case total -> true;
            };
        }

        private long value(OverlayMaterialRequirement requirement) {
            return switch (this) {
                case missing -> requirement.missing();
                case total -> requirement.required();
            };
        }

        private boolean emphasize(OverlayMaterialRequirement requirement) {
            return this == missing && requirement.missing() > 0L;
        }

        private int compare(OverlayMaterialRequirement left, OverlayMaterialRequirement right) {
            int byValue = Long.compare(value(right), value(left));
            if (byValue != 0) {
                return byValue;
            }
            int byMissing = Long.compare(right.missing(), left.missing());
            if (byMissing != 0) {
                return byMissing;
            }
            return String.CASE_INSENSITIVE_ORDER.compare(
                RecipeMaterialsPanelWidget.label(left),
                RecipeMaterialsPanelWidget.label(right)
            );
        }

        private DisplayMode next() {
            return switch (this) {
                case missing -> total;
                case total -> missing;
            };
        }

        private LangEntry emptyLabel() {
            return emptyLabel;
        }

        private String label() {
            return ExecutionOverlayUi.tr(label);
        }

        private SizedTexture icon(boolean hovered) {
            return hovered ? hoverIcon : icon;
        }

        private int activeColor() {
            return color;
        }
    }

    private record InteractiveArea(Rect2i bounds, Supplier<Tooltip> tooltip, @Nullable Runnable action) {
    }
}
