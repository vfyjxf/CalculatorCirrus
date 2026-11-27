package dev.vfyjxf.cirrus.ui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.vfyjxf.cloudlib.api.css.CssError;
import dev.vfyjxf.cloudlib.api.css.CssParser;
import dev.vfyjxf.cloudlib.api.css.Rule;
import dev.vfyjxf.cloudlib.api.css.Stylesheet;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.style.StyleVar;
import dev.vfyjxf.cloudlib.api.ui.style.Styles;
import dev.vfyjxf.cloudlib.api.ui.style.Theme;
import dev.vfyjxf.cloudlib.api.ui.style.Themes;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.api.ui.texture.ColorTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.TintedTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import dev.vfyjxf.cloudlib.ui.widget.CellRowWidget;
import dev.vfyjxf.cloudlib.ui.widget.InfoBarWidget;
import dev.vfyjxf.cloudlib.ui.widget.RingWidget;
import dev.vfyjxf.cloudlib.ui.widget.TextFieldWidget;
import dev.vfyjxf.cloudlib.ui.widget.TextWidget;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CirrusCssThemeContractTest {

    private static final String standardCssPath = "assets/cloudlib/ui/themes/standard/base.css";
    private static final String baseDir = "assets/calculator_cirrus/ui/themes/base/";
    private static final String atelierDir = "assets/calculator_cirrus/ui/themes/atelier_stone/";
    private static final String obsidianDir = "assets/calculator_cirrus/ui/themes/obsidian_blueprint/";

    private static final List<StyleVar<Integer>> allColorVars = List.of(
        CirrusPaletteVars.backdropScrim,
        CirrusPaletteVars.canvasVoid,
        CirrusPaletteVars.canvasGridMinor,
        CirrusPaletteVars.canvasGridMajor,
        CirrusPaletteVars.dockSurface,
        CirrusPaletteVars.cardSurface,
        CirrusPaletteVars.cardSurfaceHover,
        CirrusPaletteVars.cardSurfaceSelected,
        CirrusPaletteVars.recessedSurface,
        CirrusPaletteVars.popoverSurface,
        CirrusPaletteVars.hairlineBorder,
        CirrusPaletteVars.hairlineHighlight,
        CirrusPaletteVars.focusRing,
        CirrusPaletteVars.dropShadow,
        CirrusPaletteVars.textPrimary,
        CirrusPaletteVars.textSecondary,
        CirrusPaletteVars.textMuted,
        CirrusPaletteVars.textDisabled,
        CirrusPaletteVars.textOnAccent,
        CirrusPaletteVars.textOnDark,
        CirrusPaletteVars.costH,
        CirrusPaletteVars.costP,
        CirrusPaletteVars.costK,
        CirrusPaletteVars.costS,
        CirrusPaletteVars.costC,
        CirrusPaletteVars.ruleHardBan,
        CirrusPaletteVars.ruleHardPin,
        CirrusPaletteVars.ruleSoftPrefer,
        CirrusPaletteVars.ruleSoftPenalize,
        CirrusPaletteVars.advisoryDiscouraged,
        CirrusPaletteVars.advisoryLastResort,
        CirrusPaletteVars.edgeMaterial,
        CirrusPaletteVars.edgeCatalyst,
        CirrusPaletteVars.edgeMachine,
        CirrusPaletteVars.edgeCriticalPath,
        CirrusPaletteVars.edgeUpstreamTrace,
        CirrusPaletteVars.edgeDownstreamTrace,
        CirrusPaletteVars.diffAdded,
        CirrusPaletteVars.diffRemoved,
        CirrusPaletteVars.acquireAccent,
        CirrusPaletteVars.acquireTintBg,
        CirrusPaletteVars.acquireBorderIdle,
        CirrusPaletteVars.acquireBorderSelected,
        CirrusPaletteVars.craftAccent,
        CirrusPaletteVars.craftTintBg,
        CirrusPaletteVars.craftBorderIdle,
        CirrusPaletteVars.craftBorderSelected,
        CirrusPaletteVars.convertAccent,
        CirrusPaletteVars.convertTintBg,
        CirrusPaletteVars.convertBorderIdle,
        CirrusPaletteVars.convertBorderSelected,
        CirrusPaletteVars.cycleLoopAccent,
        CirrusPaletteVars.cycleLoopTintBg,
        CirrusPaletteVars.cycleLoopBorderIdle,
        CirrusPaletteVars.cycleLoopBorderSelected,
        CirrusPaletteVars.provisionMachineAccent,
        CirrusPaletteVars.provisionMachineTintBg,
        CirrusPaletteVars.provisionMachineBorderIdle,
        CirrusPaletteVars.provisionMachineBorderSelected,
        CirrusPaletteVars.setupLineAccent,
        CirrusPaletteVars.setupLineTintBg,
        CirrusPaletteVars.setupLineBorderIdle,
        CirrusPaletteVars.setupLineBorderSelected,
        CirrusPaletteVars.linePullAccent,
        CirrusPaletteVars.linePullTintBg,
        CirrusPaletteVars.linePullBorderIdle,
        CirrusPaletteVars.linePullBorderSelected
    );

    private static final List<StyleVar<VisualTexture>> allTextureVars = List.of(
        CirrusPaletteVars.texDock,
        CirrusPaletteVars.texCard,
        CirrusPaletteVars.texCardHover,
        CirrusPaletteVars.texCardSelected,
        CirrusPaletteVars.texFlat,
        CirrusPaletteVars.texInset,
        CirrusPaletteVars.texRecessed,
        CirrusPaletteVars.texSlot,
        CirrusPaletteVars.texSlotDark,
        CirrusPaletteVars.texBtnUp,
        CirrusPaletteVars.texBtnHover,
        CirrusPaletteVars.texBtnDown,
        CirrusPaletteVars.texCtaPrimary,
        CirrusPaletteVars.texCtaDanger,
        CirrusPaletteVars.texSwitchOn,
        CirrusPaletteVars.texSwitchOnHover,
        CirrusPaletteVars.texSwitchOff,
        CirrusPaletteVars.texSwitchOffHover,
        CirrusPaletteVars.texScrollTrack,
        CirrusPaletteVars.texScrollThumb,
        CirrusPaletteVars.texScrollThumbHorizontal,
        CirrusPaletteVars.texScrollTrackFill,
        CirrusPaletteVars.texMinimapFrame,
        CirrusPaletteVars.texMeterTrack,
        CirrusPaletteVars.texTripleLeftUnselected,
        CirrusPaletteVars.texTripleLeftSelected,
        CirrusPaletteVars.texTripleMiddleLeft,
        CirrusPaletteVars.texTripleMiddleMiddle,
        CirrusPaletteVars.texTripleMiddleRight,
        CirrusPaletteVars.texTripleRightSelected,
        CirrusPaletteVars.texTripleRightUnselected
    );

    @Test
    void descriptorsFollowStyleLoaderInheritanceContractAndDeadThemesJsonIsGone() throws IOException {
        assertNull(
            CirrusCssThemeContractTest.class.getClassLoader()
                    .getResource("assets/calculator_cirrus/ui/themes/themes.json"),
            "dead themes.json must be removed"
        );

        JsonObject base = JsonParser.parseString(readResource(baseDir + "theme.json")).getAsJsonObject();
        assertEquals(List.of("workbench_rules.css"), stringArray(base.getAsJsonArray("css")));
        assertEquals(List.of("cloudlib:standard"), stringArray(base.getAsJsonArray("extends")));
        assertFalse(base.get("default").getAsBoolean());

        JsonObject atelier = JsonParser.parseString(readResource(atelierDir + "theme.json")).getAsJsonObject();
        assertEquals(List.of("atelier_stone.css"), stringArray(atelier.getAsJsonArray("css")));
        assertEquals(List.of("calculator_cirrus:base"), stringArray(atelier.getAsJsonArray("extends")));
        assertFalse(atelier.get("default").getAsBoolean());

        JsonObject obsidian = JsonParser.parseString(readResource(obsidianDir + "theme.json")).getAsJsonObject();
        assertEquals(List.of("obsidian_blueprint.css"), stringArray(obsidian.getAsJsonArray("css")));
        assertEquals(List.of("calculator_cirrus:base"), stringArray(obsidian.getAsJsonArray("extends")));
        assertFalse(obsidian.get("default").getAsBoolean());
    }

    @Test
    void stylesheetsParseCleanlyAndResolveWithoutWarningsAcrossAllWorkbenchAndCommonSelectors() throws IOException {
        for (WorkbenchThemeMode mode : WorkbenchThemeMode.values()) {
            Theme theme = loadFullTheme(mode);
            ShowcaseTree showcase = buildShowcaseTree();
            List<String> warnings = new ArrayList<>();
            theme.applyTree(showcase.root(), warnings::add);
            assertEquals(List.of(), warnings, mode + " must resolve without any CSS warnings");

            for (Widget surface : showcase.surfaces()) {
                VisualTexture bg = surface.style().get(Styles.background);
                assertNotNull(bg, mode + " background missing for classes " + surface.styleClasses());
                assertFalse(
                    Objects.requireNonNull(bg).isEmpty(),
                    mode + " background empty for classes " + surface.styleClasses()
                );
            }
        }
    }

    @Test
    void cirrusThemeStyleExpandsVarChainsThroughCascade() throws IOException {
        Theme baseObsidian = loadFullTheme(WorkbenchThemeMode.obsidianBlueprint);
        List<CssError> errors = new ArrayList<>();
        Stylesheet overrideSheet = CssParser.parse(":root { --cirrus-cost-s: #123456FF; }", errors);
        assertEquals(List.of(), errors);
        Theme overrideLayer = new Theme(ResourceLocation.fromNamespaceAndPath("test", "override"), overrideSheet);
        Theme composed = Theme.compose(List.of(baseObsidian, overrideLayer));

        assertEquals(0xFF123456, CirrusThemeStyle.color(composed, CirrusPaletteVars.costS, 0));
        assertEquals(0xFF123456, CirrusThemeStyle.color(composed, CirrusPaletteVars.meterOk, 0));

        VisualTexture barOk = CirrusThemeStyle.texture(composed, StyleVar.texture("--bar-ok"), VisualTexture.empty);
        assertEquals(new ColorTexture(0xFF123456), barOk);
    }

    @Test
    void allColorMeterAndTextureTokensParseAndMatchJavaFallbacksForBothThemes() throws IOException {
        assertEquals(67, allColorVars.size());
        assertEquals(31, allTextureVars.size());

        for (WorkbenchThemeMode mode : WorkbenchThemeMode.values()) {
            Theme theme = loadFullTheme(mode);
            UIStyle rootStyle = CirrusThemeStyle.resolveRootStyle(theme);

            long colorCount = theme.rootVars().keySet().stream()
                    .filter(name -> name.startsWith("--cirrus-") && !name.startsWith("--cirrus-tex-")).count();
            long textureCount = theme.rootVars().keySet().stream().filter(name -> name.startsWith("--cirrus-tex-"))
                    .count();
            assertEquals(67L, colorCount, mode + " must declare 67 --cirrus-* color vars");
            assertEquals(31L, textureCount, mode + " must declare 31 --cirrus-tex-* texture vars");

            for (StyleVar<Integer> colorVar : allColorVars) {
                assertNotNull(rootStyle.var(colorVar), mode + " failed to parse " + colorVar.name());
            }
            CirrusPalette expectedPalette = CirrusPalette.declared(mode);
            CirrusPalette resolvedPalette = CirrusPalette.of(theme, mode);
            assertEquals(expectedPalette, resolvedPalette, mode + " CSS palette must match Java fallback");

            assertEquals(expectedPalette.costP(), CirrusThemeStyle.color(theme, CirrusPaletteVars.meterP, 0));
            assertEquals(expectedPalette.costK(), CirrusThemeStyle.color(theme, CirrusPaletteVars.meterK, 0));
            assertEquals(expectedPalette.costC(), CirrusThemeStyle.color(theme, CirrusPaletteVars.meterC, 0));
            assertEquals(expectedPalette.costS(), CirrusThemeStyle.color(theme, CirrusPaletteVars.meterOk, 0));
            assertEquals(expectedPalette.costH(), CirrusThemeStyle.color(theme, CirrusPaletteVars.meterWarn, 0));
            assertEquals(
                expectedPalette.ruleHardBan(),
                CirrusThemeStyle.color(theme, CirrusPaletteVars.meterDanger, 0)
            );
            assertEquals(expectedPalette.focusRing(), CirrusThemeStyle.color(theme, CirrusPaletteVars.meterFocus, 0));

            for (StyleVar<VisualTexture> textureVar : allTextureVars) {
                VisualTexture parsed = rootStyle.var(textureVar);
                assertNotNull(parsed, mode + " failed to parse " + textureVar.name());
                assertFalse(Objects.requireNonNull(parsed).isEmpty(), mode + " empty texture for " + textureVar.name());
                assertNotEquals(
                    TintedTexture.class,
                    parsed.getClass(),
                    mode + " must not use tinted light-stone PNGs for " + textureVar.name()
                );
            }

            CirrusThemeTextures expectedTextures = CirrusThemeTextures.declared(mode);
            CirrusThemeTextures resolvedTextures = CirrusThemeTextures.of(theme, mode);
            assertEquals(expectedTextures, resolvedTextures, mode + " built-in theme must bind OreUI texture kit");
        }

        // Verify dark icon routing resolves dedicated icon_dark sprites while keeping light originals untouched.
        var lightCalc = CirrusTextures.Icon.calculatorUnselect;
        var resolvedLight = CirrusThemeIcons.resolve(lightCalc, WorkbenchThemeMode.atelierStone);
        var resolvedDark = CirrusThemeIcons.resolve(lightCalc, WorkbenchThemeMode.obsidianBlueprint);
        assertEquals(lightCalc, resolvedLight);
        assertNotEquals(lightCalc, resolvedDark);
        assertTrue(resolvedDark.location().getPath().contains("icon_dark/"));
    }

    @Test
    void primaryAndSecondaryInksMeetWcagAaContrastOnWorkbenchSurfacesInBothThemes() {
        for (WorkbenchThemeMode mode : WorkbenchThemeMode.values()) {
            CirrusPalette palette = CirrusPalette.declared(mode);
            int compositedDock = alphaOver(palette.dockSurface(), palette.canvasVoid());
            assertContrastAtLeast(mode + " textPrimary on dockSurface", palette.textPrimary(), compositedDock, 4.5);
            assertContrastAtLeast(mode + " textSecondary on dockSurface", palette.textSecondary(), compositedDock, 4.5);
            assertContrastAtLeast(
                mode + " textPrimary on cardSurface",
                palette.textPrimary(),
                palette.cardSurface(),
                4.5
            );
            assertContrastAtLeast(
                mode + " textSecondary on cardSurface",
                palette.textSecondary(),
                palette.cardSurface(),
                4.5
            );
            assertContrastAtLeast(
                mode + " textPrimary on canvasVoid",
                palette.textPrimary(),
                palette.canvasVoid(),
                4.5
            );
            assertContrastAtLeast(
                mode + " textSecondary on canvasVoid",
                palette.textSecondary(),
                palette.canvasVoid(),
                4.5
            );
        }
    }

    @Test
    void cirrusUiThemeFallsBackToAtelierStoneOutsideWorkbenchAndResolvesRegisteredThemes() throws IOException {
        Widget detached = new Widget() {};
        assertEquals(WorkbenchThemeMode.atelierStone, CirrusUiTheme.mode(detached));
        assertEquals(WorkbenchThemeMode.atelierStone, CirrusUiTheme.mode(null));
        assertFalse(CirrusUiTheme.isDark(detached));

        List<ResourceLocation> previousActive = Themes.activeIds();
        Theme previousAtelier = Themes.get(WorkbenchThemeMode.atelierStone.themeId());
        Theme previousObsidian = Themes.get(WorkbenchThemeMode.obsidianBlueprint.themeId());
        try {
            Theme atelier = loadFullTheme(WorkbenchThemeMode.atelierStone);
            Theme obsidian = loadFullTheme(WorkbenchThemeMode.obsidianBlueprint);
            Themes.register(atelier);
            Themes.register(obsidian);

            assertEquals(CirrusPalette.atelierStone, CirrusPalette.of(WorkbenchThemeMode.atelierStone));
            assertEquals(CirrusPalette.obsidianBlueprint, CirrusPalette.of(WorkbenchThemeMode.obsidianBlueprint));
            assertNotEquals(
                CirrusThemeTextures.of(WorkbenchThemeMode.atelierStone).dockIsland(),
                CirrusThemeTextures.of(WorkbenchThemeMode.obsidianBlueprint).dockIsland()
            );
        } finally {
            restoreTheme(WorkbenchThemeMode.atelierStone.themeId(), previousAtelier);
            restoreTheme(WorkbenchThemeMode.obsidianBlueprint.themeId(), previousObsidian);
            Themes.setActive(previousActive);
        }
    }

    private static void restoreTheme(ResourceLocation id, @Nullable Theme previous) {
        if (previous != null) {
            Themes.register(previous);
        } else {
            Themes.unregister(id);
        }
    }

    private static Theme loadFullTheme(WorkbenchThemeMode mode) throws IOException {
        List<Rule> rules = new ArrayList<>();
        rules.addAll(parseCss(standardCssPath).rules());
        rules.addAll(parseCss(baseDir + "workbench_rules.css").rules());
        String themeCss = mode == WorkbenchThemeMode.atelierStone
                ? atelierDir + "atelier_stone.css"
                : obsidianDir + "obsidian_blueprint.css";
        rules.addAll(parseCss(themeCss).rules());
        return new Theme(mode.themeId(), new Stylesheet(rules));
    }

    private static Stylesheet parseCss(String resourcePath) throws IOException {
        List<CssError> errors = new ArrayList<>();
        Stylesheet sheet = CssParser.parse(readResource(resourcePath), errors);
        assertEquals(List.of(), errors, "CSS parse errors in " + resourcePath);
        return sheet;
    }

    private static String readResource(String path) throws IOException {
        try (InputStream stream = CirrusCssThemeContractTest.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource: " + path);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static List<String> stringArray(JsonArray array) {
        List<String> out = new ArrayList<>(array.size());
        for (int index = 0; index < array.size(); index++) {
            out.add(array.get(index).getAsString());
        }
        return out;
    }

    private record ShowcaseTree(WidgetGroup<Widget> root, List<Widget> surfaces) {}

    private static ShowcaseTree buildShowcaseTree() {
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.addStyleClass("workbench-shell");
        List<Widget> surfaces = new ArrayList<>();

        for (String styleClass : List.of(
            "workbench-dock",
            "workbench-card",
            "workbench-topbar",
            "workbench-tabbar",
            "workbench-rail",
            "workbench-timeline",
            "workbench-popover",
            "workbench-palette",
            "workbench-toast",
            "cirrus-page",
            "cirrus-card",
            "cirrus-section",
            "cirrus-section-flat",
            "cirrus-section-inset",
            "cirrus-choice",
            "cirrus-row",
            "cirrus-input",
            "cirrus-button",
            "cirrus-slot",
            "cirrus-slot-dark"
        )) {
            Widget node = new Widget() {};
            node.addStyleClass(styleClass);
            root.addWidget(node);
            surfaces.add(node);
        }

        Widget workspace = new Widget() {};
        workspace.addStyleClass("workbench-workspace");
        root.addWidget(workspace);

        Widget primaryBtn = new Widget() {};
        primaryBtn.addStyleClass("cirrus-button").addStyleClass("primary");
        root.addWidget(primaryBtn);
        surfaces.add(primaryBtn);

        Widget dangerBtn = new Widget() {};
        dangerBtn.addStyleClass("cirrus-button").addStyleClass("danger");
        root.addWidget(dangerBtn);
        surfaces.add(dangerBtn);

        root.addWidget(TextWidget.of("Label"));
        root.addWidget(TextFieldWidget.create());

        InfoBarWidget infoBar = InfoBarWidget.of(0.5);
        infoBar.addStyleClass("cirrus-meter");
        infoBar.addSegment(0.2, InfoBarWidget.styleOk);
        infoBar.addSegment(0.2, InfoBarWidget.styleWarn);
        infoBar.addSegment(0.2, InfoBarWidget.styleDanger);
        infoBar.addSegment(0.2, InfoBarWidget.styleInfo);
        infoBar.addSegment(0.2, "cost-h");
        infoBar.addSegment(0.2, "cost-p");
        infoBar.addSegment(0.2, "cost-k");
        infoBar.addSegment(0.2, "cost-s");
        infoBar.addSegment(0.2, "cost-c");
        root.addWidget(infoBar);

        InfoBarWidget thinBar = InfoBarWidget.segmented(InfoBarWidget.Segment.of(0.6, "cost-h"));
        thinBar.addStyleClass("cirrus-meter-thin");
        root.addWidget(thinBar);

        CellRowWidget cells = CellRowWidget.of(10, 0.7).setZones(
            List.of(
                new CellRowWidget.Zone(0.55, InfoBarWidget.styleInfo),
                new CellRowWidget.Zone(0.80, InfoBarWidget.styleWarn),
                new CellRowWidget.Zone(1.0, InfoBarWidget.styleDanger)
            )
        );
        root.addWidget(cells);

        for (String variant : List
                .of("ok", "warn", "danger", "info", "cost-h", "cost-p", "cost-k", "cost-s", "cost-c")) {
            RingWidget ring = RingWidget.of(0.6);
            ring.addStyleClass(variant);
            root.addWidget(ring);
        }
        return new ShowcaseTree(root, List.copyOf(surfaces));
    }

    private static void assertContrastAtLeast(String label, int foreground, int background, double minimumRatio) {
        double ratio = contrastRatio(foreground, background);
        assertTrue(ratio >= minimumRatio, label + " contrast " + ratio + " < " + minimumRatio);
    }

    private static int alphaOver(int top, int bottom) {
        double alpha = ((top >>> 24) & 0xFF) / 255.0;
        int r = (int) Math.round(((top >>> 16) & 0xFF) * alpha + ((bottom >>> 16) & 0xFF) * (1.0 - alpha));
        int g = (int) Math.round(((top >>> 8) & 0xFF) * alpha + ((bottom >>> 8) & 0xFF) * (1.0 - alpha));
        int b = (int) Math.round((top & 0xFF) * alpha + (bottom & 0xFF) * (1.0 - alpha));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static double contrastRatio(int foreground, int background) {
        double l1 = relativeLuminance(foreground);
        double l2 = relativeLuminance(background);
        double lighter = Math.max(l1, l2);
        double darker = Math.min(l1, l2);
        return (lighter + 0.05) / (darker + 0.05);
    }

    private static double relativeLuminance(int argb) {
        double r = linearChannel((argb >>> 16) & 0xFF);
        double g = linearChannel((argb >>> 8) & 0xFF);
        double b = linearChannel(argb & 0xFF);
        return 0.2126 * r + 0.7152 * g + 0.0722 * b;
    }

    private static double linearChannel(int channel) {
        double srgb = channel / 255.0;
        return srgb <= 0.04045 ? srgb / 12.92 : Math.pow((srgb + 0.055) / 1.055, 2.4);
    }
}
