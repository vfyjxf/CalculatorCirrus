package dev.vfyjxf.cirrus.ui.workbench.facilities;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.content.value.ContentStack;
import dev.vfyjxf.cirrus.api.recipe.machine.MachineEntry;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.CirrusPalette;
import dev.vfyjxf.cirrus.ui.CirrusThemeTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusButtonWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusFittedTextWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusScrollablePageWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusTextInputWidget;
import dev.vfyjxf.cirrus.ui.widget.toggle.sw.Slide;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.chrome.ChromeText;
import dev.vfyjxf.cirrus.ui.workbench.copilot.CoPilotPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.OreUiPaint;
import dev.vfyjxf.cirrus.ui.workbench.paint.WorkbenchPaint;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiRichTooltip;
import dev.vfyjxf.cirrus.ui.workbench.tooltip.OreUiTooltipProvider;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.function.ToIntFunction;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.widthOf;

/**
 * Facilities and eight-rule studio: category rail on the left, card grid on the right, global search and JSON import/export on top.
 */
public class FacilitiesAndRulesWorkspace extends CompositeWidget<Widget> {

    public static final int categoryRailWidth = 180;
    public static final int toolbarHeight = 18;

    public enum Section {
        machines(CirrusLangs.Workbench.Facilities.floorMachines), lines(
            CirrusLangs.Workbench.Facilities.lines
        ), recipes(
            CirrusLangs.Workbench.Facilities.autoRecipes
        ), optIn(CirrusLangs.Workbench.Facilities.optIn), rules(CirrusLangs.Workbench.Facilities.rules);

        private final LangEntry label;

        Section(LangEntry label) {
            this.label = label;
        }

        public LangEntry label() {
            return label;
        }
    }

    private final FacilitiesHost host;
    private final @Nullable WorkbenchShellContext explicitShell;
    private WorkbenchThemeMode fallbackTheme;
    private final Supplier<CirrusPalette> palette;
    private final WidgetGroup<Widget> contentArea = new WidgetGroup<>();
    private final Map<Section, Panel> panels = new EnumMap<>(Section.class);
    private Section section = Section.machines;
    private @Nullable WorkbenchThemeMode appliedTheme;

    public FacilitiesAndRulesWorkspace(FacilitiesHost host, WorkbenchThemeMode theme) {
        this(host, null, theme);
    }

    public FacilitiesAndRulesWorkspace(FacilitiesHost host, WorkbenchShellContext shellContext) {
        this(host, Objects.requireNonNull(shellContext, "shellContext"), shellContext.theme());
    }

    private FacilitiesAndRulesWorkspace(
        FacilitiesHost host,
        @Nullable WorkbenchShellContext explicitShell,
        @Nullable WorkbenchThemeMode initialTheme
    ) {
        this.host = Objects.requireNonNull(host, "host");
        this.explicitShell = explicitShell;
        this.fallbackTheme = initialTheme == null ? WorkbenchThemeMode.defaultMode() : initialTheme;
        this.palette = this::livePalette;
        addStyleClass("workbench-dock");
        useStyle(sizeFull(), flexColumn(), gap(4), padding(4), boxSizingBorderBox());
        syncTheme(theme());
        onMount((scene, context, handle) -> CirrusUiTheme.bindThemeListener(this, handle, () -> syncTheme(theme())));
        addWidget(topBar());
        addWidget(body());
    }

    public FacilitiesHost host() {
        return host;
    }

    public WorkbenchThemeMode theme() {
        WorkbenchShellContext shell = resolvedShell();
        return shell != null ? shell.theme() : fallbackTheme;
    }

    public Supplier<CirrusPalette> palette() {
        return palette;
    }

    public void syncTheme(@Nullable WorkbenchThemeMode next) {
        if (next != null) {
            this.fallbackTheme = next;
        }
        WorkbenchThemeMode activeMode = theme();
        if (activeMode == appliedTheme) {
            return;
        }
        appliedTheme = activeMode;
        defaultStyle(UIStyle.of(background(CirrusThemeTextures.of(activeMode).dockIsland())));
    }

    private @Nullable WorkbenchShellContext resolvedShell() {
        if (explicitShell != null) {
            return explicitShell;
        }
        return CirrusUiTheme.shellContext(this);
    }

    private CirrusPalette livePalette() {
        WorkbenchShellContext shell = resolvedShell();
        if (shell != null) {
            return shell.palette();
        }
        return lifecycle().mounted()
                ? CirrusPalette.of(scene().theme(), fallbackTheme)
                : CirrusPalette.of(fallbackTheme);
    }

    public Section section() {
        return section;
    }

    public Panel panel(Section key) {
        Section target = Objects.requireNonNull(key, "key");
        Panel existing = panels.get(target);
        if (existing != null && !existing.lifecycle().destroyed()) {
            return existing;
        }
        Panel created = createPanel(target);
        panels.put(target, created);
        return created;
    }

    public Panel activePanel() {
        return panel(section);
    }

    public void selectSection(Section next) {
        Section target = next == null ? Section.machines : next;
        Panel current = panels.get(section);
        if (target == section) {
            activePanel().refresh();
            return;
        }
        if (current != null && contentArea.contains(current)) {
            Scene currentScene = lifecycle().mounted() ? scene() : null;
            contentArea.remove(current);
            if (currentScene != null && current.lifecycle().unmounted()) {
                currentScene.reuse(current);
            }
        }
        section = target;
        contentArea.addWidget(activePanel());
    }

    public void search(String query) {
        activePanel().search(query);
    }

    public void importConfig() {
        applyTransfer(host.importConfig());
    }

    public void exportConfig() {
        applyTransfer(host.exportConfig());
    }

    private void applyTransfer(FacilitiesHost.Transfer transfer) {
        Objects.requireNonNull(transfer, "transfer");
        host.toast(transfer.message());
        for (Panel panel : panels.values()) {
            panel.refresh();
        }
    }

    private Panel createPanel(Section key) {
        return switch (key) {
            case machines -> new OwnedMachinesFloorPanel(host, palette);
            case lines -> new AutomatedLinesPanel(host, palette);
            case recipes -> new AutomatedRecipesPanel(host, palette);
            case optIn -> new OptInSystemsPanel(host, palette);
            case rules -> new GuidanceRulesStudioPanel(host, palette);
        };
    }

    private Widget topBar() {
        WidgetGroup<Widget> bar = new WidgetGroup<>();
        bar.useStyle(flexRow(), alignItemsCenter(), heightOf(toolbarHeight), minWidth(0), gap(6), boxSizingBorderBox());
        CirrusTextInputWidget search = new CirrusTextInputWidget().setPlaceholder(le("search_placeholder"))
                .onTextChanged(this::search);
        search.useStyle(flexGrow(1), minWidth(0), heightOf(toolbarHeight), boxSizingBorderBox());
        bar.addWidget(search);
        bar.addWidget(new StatusBadge(host, palette));
        CirrusButtonWidget exportBtn = new CirrusButtonWidget(76, toolbarHeight)
                .label(CirrusLangs.Workbench.Facilities.exportJson).onPress(this::exportConfig);
        exportBtn.useStyle(flexShrink(0));
        bar.addWidget(exportBtn);
        CirrusButtonWidget importBtn = new CirrusButtonWidget(84, toolbarHeight)
                .label(CirrusLangs.Workbench.Facilities.importJson).onPress(this::importConfig);
        importBtn.useStyle(flexShrink(0));
        bar.addWidget(importBtn);
        return bar;
    }

    private Widget body() {
        WidgetGroup<Widget> rail = new WidgetGroup<>();
        rail.useStyle(flexColumn(), widthOf(categoryRailWidth), minHeight(0), gap(3), boxSizingBorderBox());
        for (Section key : Section.values()) {
            rail.addWidget(
                new CirrusButtonWidget(categoryRailWidth - 8, 18).label(key.label()).selected(() -> section == key)
                        .onPress(() -> selectSection(key))
            );
        }
        contentArea.useStyle(flexColumn(), flexGrow(1), minWidth(0), minHeight(0), gap(3), boxSizingBorderBox());
        contentArea.addWidget(activePanel());
        WidgetGroup<Widget> body = new WidgetGroup<>();
        body.useStyle(flexRow(), flexGrow(1), minHeight(0), minWidth(0), gap(4), boxSizingBorderBox());
        body.addWidget(rail);
        body.addWidget(contentArea);
        return body;
    }

    static LangEntry le(String suffix) {
        return LangEntry.of("cirrus.ui.workbench.facilities." + suffix);
    }

    static Component tr(String suffix, Object... args) {
        return Component.translatable("cirrus.ui.workbench.facilities." + suffix, args);
    }

    /**
     * Shared panel shell: a fixed scroll container plus a rebuild hook, reused by the five sub panels.
     */
    public abstract static class Panel extends CirrusScrollablePageWidget {

        public static final int rowHeight = 22;
        public static final int glyphSize = 20;

        private final FacilitiesHost host;
        private final Supplier<CirrusPalette> palette;
        private final WidgetGroup<Widget> content = new WidgetGroup<>();
        private String filter = "";
        private boolean pendingRebuild;

        protected Panel(FacilitiesHost host, Supplier<CirrusPalette> palette) {
            super(8, 6);
            this.host = Objects.requireNonNull(host, "host");
            this.palette = Objects.requireNonNull(palette, "palette");
            content.useStyle(flexColumn(), minWidth(0), minHeight(0), gap(6));
            addWidget(content);
        }

        public final FacilitiesHost host() {
            return host;
        }

        public final CirrusPalette palette() {
            return palette.get();
        }

        public final Supplier<CirrusPalette> paletteSupplier() {
            return palette;
        }

        public final String filter() {
            return filter;
        }

        public void search(@Nullable String query) {
            String next = query == null ? "" : query.trim();
            if (!next.equals(filter)) {
                filter = next;
                refresh();
            }
        }

        public final void refresh() {
            if (lifecycle().unmounted()) {
                pendingRebuild = true;
                return;
            }
            content.clear();
            buildContent();
            pendingRebuild = false;
        }

        {
            onMount((scene, context, handle) -> {
                if (pendingRebuild) {
                    scene.nextTick(this::refresh, handle::onCleanup);
                }
            });
        }

        public final int debugEntryCount() {
            return content.children().size();
        }

        protected abstract void buildContent();

        protected final WidgetGroup<Widget> content() {
            return content;
        }

        protected static LangEntry le(String suffix) {
            return FacilitiesAndRulesWorkspace.le(suffix);
        }

        protected static Component tr(String suffix, Object... args) {
            return FacilitiesAndRulesWorkspace.tr(suffix, args);
        }

        protected final boolean matches(String... haystacks) {
            return matchesAny(filter, haystacks);
        }

        protected static boolean matchesAny(String needle, String... haystacks) {
            String normalized = needle == null ? "" : needle.trim().toLowerCase(Locale.ROOT);
            if (normalized.isEmpty()) {
                return true;
            }
            for (String haystack : haystacks) {
                if (haystack != null && haystack.toLowerCase(Locale.ROOT).contains(normalized)) {
                    return true;
                }
            }
            return false;
        }

        protected final PanelCard card() {
            return new PanelCard(palette);
        }

        protected final WidgetGroup<Widget> row(Widget... children) {
            WidgetGroup<Widget> row = new WidgetGroup<>();
            row.useStyle(flexRow(), alignItemsCenter(), heightOf(rowHeight), minWidth(0), gap(4), boxSizingBorderBox());
            for (Widget child : children) {
                row.addWidget(child);
            }
            return row;
        }

        protected final CirrusFittedTextWidget caption(Component text) {
            CirrusFittedTextWidget label = new CirrusFittedTextWidget(text, p -> palette().textSecondary());
            label.useStyle(heightOf(12), minWidth(0));
            return label;
        }

        protected final CirrusFittedTextWidget value(LangEntry text, Object... args) {
            CirrusFittedTextWidget label = new CirrusFittedTextWidget(text, p -> palette().textPrimary(), args);
            label.useStyle(flexGrow(1), minWidth(0));
            return label;
        }

        protected final CirrusFittedTextWidget value(Component text) {
            CirrusFittedTextWidget label = new CirrusFittedTextWidget(text, p -> palette().textPrimary());
            label.useStyle(flexGrow(1), minWidth(0));
            return label;
        }

        protected final CirrusFittedTextWidget muted(Component text) {
            CirrusFittedTextWidget label = new CirrusFittedTextWidget(text, p -> palette().textMuted());
            label.useStyle(flexGrow(1), minWidth(0));
            return label;
        }

        protected final CirrusFittedTextWidget fixed(Component text, int width, int color) {
            CirrusFittedTextWidget label = new CirrusFittedTextWidget(text, color);
            label.useStyle(widthOf(width), minWidth(0));
            return label;
        }

        protected final CirrusFittedTextWidget fixed(Component text, int width, ToIntFunction<CirrusPalette> color) {
            CirrusFittedTextWidget label = new CirrusFittedTextWidget(text, p -> color.applyAsInt(palette()));
            label.useStyle(widthOf(width), minWidth(0));
            return label;
        }

        protected final CirrusButtonWidget button(LangEntry label, Runnable action) {
            int autoWidth = Math.max(56, CirrusButtonWidget.preferredLabelButtonWidth(label.component()));
            CirrusButtonWidget button = new CirrusButtonWidget(autoWidth, rowHeight).label(label).onPress(action);
            button.useStyle(flexShrink(0));
            return button;
        }

        protected final CirrusButtonWidget button(LangEntry label, int width, Runnable action) {
            int resolved = Math.max(width, CirrusButtonWidget.preferredLabelButtonWidth(label.component()));
            CirrusButtonWidget button = new CirrusButtonWidget(resolved, rowHeight).label(label).onPress(action);
            button.useStyle(flexShrink(0));
            return button;
        }

        protected final CirrusButtonWidget button(Component label, int width, Runnable action) {
            CirrusButtonWidget button = new CirrusButtonWidget(width, rowHeight).label(label).onPress(action);
            button.useStyle(flexShrink(0));
            return button;
        }

        protected final CirrusButtonWidget microButton(String glyph, int width, Runnable action) {
            CirrusButtonWidget button = new CirrusButtonWidget(width, rowHeight).label(Component.literal(glyph))
                    .onPress(action);
            button.useStyle(flexShrink(0));
            return button;
        }

        protected final Slide toggle(BooleanSupplier getter, Consumer<Boolean> setter) {
            Slide slide = new Slide(getter, setter, () -> null);
            slide.useStyle(sizeOf(20, 13), flexShrink(0));
            return slide;
        }

        protected final CirrusTextInputWidget searchInput(LangEntry placeholder, Consumer<String> onChanged) {
            CirrusTextInputWidget input = new CirrusTextInputWidget().setPlaceholder(placeholder)
                    .onTextChanged(onChanged);
            input.useStyle(flexGrow(1), minWidth(0), heightOf(rowHeight), boxSizingBorderBox());
            return input;
        }

        /**
         * Resource, machine and recipe icon: resolves its item stack lazily at render time.
         */
        protected static final class Glyph extends Widget {

            private final @Nullable ComponentStackKey resourceKey;
            private final String fallbackLabel;
            private final Supplier<CirrusPalette> palette;
            private @Nullable ContentStack<?, ?> resolved;
            private boolean resolveAttempted;

            protected Glyph(@Nullable ComponentStackKey resourceKey, Supplier<CirrusPalette> palette, int size) {
                this.resourceKey = resourceKey;
                this.fallbackLabel = resourceKey == null ? "" : resourceKey.displayId();
                this.palette = palette;
                useStyle(sizeOf(size, size));
            }

            protected Glyph(@Nullable MachineEntry machine, Supplier<CirrusPalette> palette, int size) {
                this.resourceKey = machine == null ? null : machine.contentKey().orElse(null);
                this.fallbackLabel = machine == null ? "" : machine.canonicalKey();
                this.palette = palette;
                useStyle(sizeOf(size, size));
            }

            @Override
            protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
                CirrusPalette theme = palette.get();
                OreUiPaint.slot(canvas, 0, 0, width(), theme);
                ContentStack<?, ?> stack = stack();
                if (stack == null || stack.isEmpty()) {
                    Component glyph = Component.literal(initial());
                    int glyphX = Math.max(1, (width() - ChromeText.width(canvas, glyph)) / 2);
                    ChromeText
                            .draw(canvas, glyph, glyphX, ChromeText.centeredY(0, height(), canvas), theme.textMuted());
                    return;
                }
                int drawSize = Math.min(16, Math.max(8, width() - 4));
                int ox = Math.max(2, (width() - drawSize) / 2);
                int oy = Math.max(2, (height() - drawSize) / 2);
                CoPilotPaint.renderStack(canvas, stack, ox, oy, drawSize);
            }

            private String initial() {
                int slash = Math.max(fallbackLabel.lastIndexOf(':'), fallbackLabel.lastIndexOf('/'));
                String tail = slash < 0 ? fallbackLabel : fallbackLabel.substring(slash + 1);
                return tail.isEmpty() ? "?" : tail.substring(0, 1).toUpperCase(Locale.ROOT);
            }

            private @Nullable ContentStack<?, ?> stack() {
                if (!resolveAttempted) {
                    resolveAttempted = true;
                    if (resourceKey != null && Minecraft.getInstance().level != null) {
                        resolved = CoPilotPaint.stackOf(resourceKey, 1L);
                    }
                }
                return resolved;
            }

            @Override
            public String toString() {
                return "Glyph[" + fallbackLabel + "]";
            }
        }

        /**
         * Panel card plate: takes its colour from the current theme at render time.
         */
        protected static class PanelCard extends WidgetGroup<Widget> implements OreUiTooltipProvider {

            private final Supplier<CirrusPalette> palette;
            private @Nullable Supplier<OreUiRichTooltip> richTooltip;

            protected PanelCard(Supplier<CirrusPalette> palette) {
                this.palette = Objects.requireNonNull(palette, "palette");
                useStyle(flexColumn(), minWidth(0), gap(5), padding(7, 8), boxSizingBorderBox());
                setInteractive(true);
            }

            public PanelCard richTooltip(@Nullable Supplier<OreUiRichTooltip> supplier) {
                this.richTooltip = supplier;
                return this;
            }

            @Override
            public @Nullable OreUiRichTooltip richTooltipAt(double localX, double localY) {
                return richTooltip == null ? null : richTooltip.get();
            }

            @Override
            protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
                WorkbenchPaint.card(canvas, 0, 0, width(), height(), palette.get(), hovered(), false);
            }
        }
    }

    /**
     * Persistence status badge: reads the host binding state at render time and sizes to its localized label.
     */
    private static final class StatusBadge extends Widget {

        private static final int horizontalPadding = 16;

        private final FacilitiesHost host;
        private final Supplier<CirrusPalette> palette;
        private int measuredWidth;

        private StatusBadge(FacilitiesHost host, Supplier<CirrusPalette> palette) {
            this.host = host;
            this.palette = palette;
            this.measuredWidth = estimateWidth(host.statusBadge(), null);
            useStyle(widthOf(measuredWidth), heightOf(toolbarHeight), flexShrink(0));
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            CirrusPalette theme = palette.get();
            Component badge = host.statusBadge();
            int required = estimateWidth(badge, ChromeText.font(canvas));
            if (required > measuredWidth) {
                measuredWidth = required;
                useStyle(widthOf(measuredWidth), heightOf(toolbarHeight), flexShrink(0));
            }
            int accent = host.worldBound() ? theme.costS() : theme.costK();
            canvas.fill(0, 1, width(), Math.max(1, height() - 2), theme.recessedSurface());
            canvas.strokeRect(0, 1, width(), Math.max(1, height() - 2), theme.hairlineBorder(), 1);
            canvas.fill(5, height() / 2 - 2, 4, 4, accent);
            ChromeText.drawFitted(
                canvas,
                badge,
                12,
                ChromeText.centeredY(0, height(), canvas),
                Math.max(0, width() - 16),
                accent
            );
        }

        private static int estimateWidth(Component badge, @Nullable Font font) {
            String text = badge == null ? "" : badge.getString();
            int textWidth = font != null ? font.width(text) : CirrusButtonWidget.preferredLabelButtonWidth(badge);
            return Math.max(64, textWidth + horizontalPadding);
        }
    }
}
