package dev.vfyjxf.cirrus.ui;

import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cirrus.planning.bridge.WorldFacilityRegistry;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusButtonWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusCardWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusCheckboxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusChoiceBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusFittedTextWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusPageWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusRowWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusScrollablePageWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSectionWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusSegmentedControlWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusText;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusTextInputWidget;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusWrappedTextWidget;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellContext;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cirrus.ui.workbench.WorkspaceProvider;
import dev.vfyjxf.cirrus.ui.workbench.facilities.FacilitiesAndRulesWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.facilities.FacilitiesHost;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsAndDiagnosticsWorkspace;
import dev.vfyjxf.cirrus.ui.workbench.settings.SettingsHost;
import dev.vfyjxf.cirrus.ui.workbench.state.SessionTabManager;
import dev.vfyjxf.cloudlib.api.css.CssError;
import dev.vfyjxf.cloudlib.api.css.CssParser;
import dev.vfyjxf.cloudlib.api.css.Stylesheet;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.style.Styles;
import dev.vfyjxf.cloudlib.api.ui.style.Theme;
import dev.vfyjxf.cloudlib.api.ui.style.Themes;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CirrusThemeBindingAndClipContractTest {

    private static final Pattern opaqueHexPattern = Pattern.compile("0xFF[0-9A-Fa-f]{6}");

    @Test
    void commonWidgetsFollowWorkbenchThemeLiveAndFallbackToAtelierStoneOutside() {
        CirrusButtonWidget button = new CirrusButtonWidget(60, 16).label(Component.literal("Action"));
        CirrusFittedTextWidget fitted = CirrusFittedTextWidget.primary(Component.literal("Primary"));
        CirrusWrappedTextWidget wrapped = CirrusWrappedTextWidget.primary(Component.literal("Wrapped"));
        LabelWidget unmountedLabel = CirrusText.primary(Component.literal("Label"));
        CirrusCardWidget card = new CirrusCardWidget(Component.literal("Card"));
        CirrusSectionWidget section = new CirrusSectionWidget(Component.literal("Section"));
        CirrusPageWidget page = new CirrusPageWidget() {};
        CirrusScrollablePageWidget scrollPage = new CirrusScrollablePageWidget() {};
        CirrusTextInputWidget textInput = new CirrusTextInputWidget();
        NumberInputWidget numberInput = new NumberInputWidget();
        SearchBoxWidget searchBox = new SearchBoxWidget();
        CirrusChoiceBoxWidget<String> choice = new CirrusChoiceBoxWidget<String>(80)
                .option("a", Component.literal("A"));
        CirrusSegmentedControlWidget<String> segmented = new CirrusSegmentedControlWidget<String>(16)
                .option("a", Component.literal("A"), 24);
        CirrusCheckboxWidget checkbox = new CirrusCheckboxWidget(() -> false, ignored -> {});
        CirrusRowWidget row = CirrusRowWidget.selectable(18, 4);

        assertNotNull(unmountedLabel);
        assertEquals(WorkbenchThemeMode.atelierStone, CirrusUiTheme.mode(unmountedLabel));
        assertTrue(button.styleClasses().contains("cirrus-button"));
        assertTrue(card.styleClasses().contains("cirrus-card"));
        assertTrue(section.styleClasses().contains("cirrus-section"));
        assertTrue(section.styleClasses().contains("cirrus-section-inset"));
        assertTrue(page.styleClasses().contains("cirrus-page"));
        assertTrue(textInput.styleClasses().contains("cirrus-input"));
        assertTrue(numberInput.styleClasses().contains("cirrus-input"));
        assertTrue(searchBox.styleClasses().contains("cirrus-input"));
        assertTrue(choice.styleClasses().contains("cirrus-choice"));
        assertTrue(segmented.styleClasses().contains("cirrus-segmented"));
        assertTrue(checkbox.styleClasses().contains("cirrus-button"));
        assertTrue(row.styleClasses().contains("cirrus-row"));

        assertEquals(WorkbenchThemeMode.atelierStone, CirrusUiTheme.mode(button));
        assertFalse(CirrusUiTheme.isDark(button));
        assertEquals(CirrusPalette.atelierStone.textPrimary(), button.resolvedTextColor());
        assertEquals(CirrusPalette.atelierStone.textPrimary(), fitted.resolvedColor());
        assertEquals(CirrusPalette.atelierStone.textPrimary(), wrapped.resolvedColor());
        assertEquals(OreUiTextures.Light.card, CirrusUiTheme.textures(card).cardSurface());

        WidgetGroup<Widget> container = new WidgetGroup<>();
        container.addWidget(button);
        container.addWidget(fitted);
        container.addWidget(wrapped);
        container.addWidget(card);
        container.addWidget(section);
        container.addWidget(page);
        container.addWidget(scrollPage);
        container.addWidget(textInput);
        container.addWidget(numberInput);
        container.addWidget(searchBox);
        container.addWidget(choice);
        container.addWidget(segmented);
        container.addWidget(checkbox);
        container.addWidget(row);

        WorkbenchShellWidget shell = new WorkbenchShellWidget(
            providerWith(container, new WidgetGroup<>(), new WidgetGroup<>(), new WidgetGroup<>()),
            headlessTabs(),
            WorkbenchMode.planner
        );
        try {
            shell.setTheme(WorkbenchThemeMode.obsidianBlueprint);
            mount(shell);

            assertEquals(WorkbenchThemeMode.obsidianBlueprint, CirrusUiTheme.mode(button));
            assertTrue(CirrusUiTheme.isDark(button));
            assertEquals(CirrusPalette.obsidianBlueprint.textPrimary(), button.resolvedTextColor());
            assertEquals(CirrusPalette.obsidianBlueprint.textPrimary(), fitted.resolvedColor());
            assertEquals(CirrusPalette.obsidianBlueprint.textPrimary(), wrapped.resolvedColor());
            assertEquals(OreUiTextures.Dark.card, CirrusUiTheme.textures(card).cardSurface());
            assertEquals(OreUiTextures.Dark.flat, Objects.requireNonNull(card.style().get(Styles.background)));
            assertEquals(OreUiTextures.Dark.recessed, Objects.requireNonNull(section.style().get(Styles.background)));
            assertEquals(OreUiTextures.Dark.recessed, Objects.requireNonNull(textInput.style().get(Styles.background)));
            assertEquals(
                OreUiTextures.Dark.recessed,
                Objects.requireNonNull(numberInput.style().get(Styles.background))
            );
            assertEquals(OreUiTextures.Dark.recessed, Objects.requireNonNull(searchBox.style().get(Styles.background)));
            assertEquals(OreUiTextures.Dark.scrollThumbTex, scrollPage.debugScrollThumbTexture());

            shell.setTheme(WorkbenchThemeMode.atelierStone);

            assertEquals(WorkbenchThemeMode.atelierStone, CirrusUiTheme.mode(button));
            assertFalse(CirrusUiTheme.isDark(button));
            assertEquals(CirrusPalette.atelierStone.textPrimary(), button.resolvedTextColor());
            assertEquals(CirrusPalette.atelierStone.textPrimary(), fitted.resolvedColor());
            assertEquals(CirrusPalette.atelierStone.textPrimary(), wrapped.resolvedColor());
            assertEquals(OreUiTextures.Light.flat, Objects.requireNonNull(card.style().get(Styles.background)));
            assertEquals(OreUiTextures.Light.recessed, Objects.requireNonNull(section.style().get(Styles.background)));
            assertEquals(
                OreUiTextures.Light.recessed,
                Objects.requireNonNull(textInput.style().get(Styles.background))
            );
            assertEquals(
                OreUiTextures.Light.recessed,
                Objects.requireNonNull(numberInput.style().get(Styles.background))
            );
            assertEquals(
                OreUiTextures.Light.recessed,
                Objects.requireNonNull(searchBox.style().get(Styles.background))
            );
            assertEquals(OreUiTextures.Light.scrollThumbTex, scrollPage.debugScrollThumbTexture());
        } finally {
            shell.shutdown();
        }
    }

    @Test
    void sceneThemeGuardIgnoresMismatchedThemeAndAcceptsNeutralCustomTheme() {
        Widget probe = new Widget() {};
        WidgetGroup<Widget> stage = new WidgetGroup<>();
        stage.addWidget(probe);
        WorkbenchShellWidget shell = new WorkbenchShellWidget(
            providerWith(stage, new WidgetGroup<>(), new WidgetGroup<>(), new WidgetGroup<>()),
            headlessTabs(),
            WorkbenchMode.planner
        );
        ResourceLocation lightId = WorkbenchThemeMode.atelierStone.themeId();
        ResourceLocation customId = ResourceLocation.fromNamespaceAndPath("test", "custom_pack");
        Theme previousLight = Themes.get(lightId);
        Theme previousCustom = Themes.get(customId);
        try {
            shell.setTheme(WorkbenchThemeMode.obsidianBlueprint);
            Scene scene = mount(shell);

            List<CssError> errors = new ArrayList<>();
            Stylesheet lightSheet = CssParser.parse(":root { --cirrus-text-primary: #112233FF; }", errors);
            assertEquals(List.of(), errors);
            Themes.register(new Theme(lightId, lightSheet));
            scene.setTheme(lightId);

            assertEquals(
                CirrusPalette.obsidianBlueprint.textPrimary(),
                CirrusUiTheme.palette(probe).textPrimary(),
                "mismatched atelier_stone theme must be ignored when shell mode is obsidianBlueprint"
            );

            Stylesheet customSheet = CssParser.parse(":root { --cirrus-cost-s: #123456FF; }", errors);
            assertEquals(List.of(), errors);
            Themes.register(new Theme(customId, customSheet));
            scene.setTheme(customId);

            assertEquals(
                0xFF123456,
                CirrusUiTheme.palette(probe).costS(),
                "custom scene theme with neutral id must still override palette tokens"
            );
        } finally {
            restoreTheme(lightId, previousLight);
            restoreTheme(customId, previousCustom);
            shell.shutdown();
        }
    }

    @Test
    void settingsThemeDropdownSwitchesShellAndAllCachedWorkspacesLive() {
        FacilitiesHost facilitiesHost = new FacilitiesHost.Detached(WorldFacilityRegistry.createDetached());
        SettingsHost settingsHost = new SettingsHost.Detached();
        FacilitiesAndRulesWorkspace[] facilitiesHolder = new FacilitiesAndRulesWorkspace[1];
        SettingsAndDiagnosticsWorkspace[] settingsHolder = new SettingsAndDiagnosticsWorkspace[1];
        CirrusScrollablePageWidget[] databaseHostHolder = new CirrusScrollablePageWidget[1];

        WorkspaceProvider provider = new WorkspaceProvider() {
            @Override
            public Widget plannerWorkspace(WorkbenchShellContext ctx) {
                return new WidgetGroup<>();
            }

            @Override
            public Widget facilitiesWorkspace(WorkbenchShellContext ctx) {
                facilitiesHolder[0] = new FacilitiesAndRulesWorkspace(facilitiesHost, ctx);
                return facilitiesHolder[0];
            }

            @Override
            public Widget settingsWorkspace(WorkbenchShellContext ctx) {
                settingsHolder[0] = new SettingsAndDiagnosticsWorkspace(settingsHost, ctx);
                return settingsHolder[0];
            }

            @Override
            public Widget databaseWorkspace(@Nullable WorkbenchShellContext ctx) {
                databaseHostHolder[0] = new CirrusScrollablePageWidget() {};
                return databaseHostHolder[0];
            }
        };

        WorkbenchShellWidget shell = new WorkbenchShellWidget(provider, headlessTabs(), WorkbenchMode.planner);
        try {
            shell.setTheme(WorkbenchThemeMode.obsidianBlueprint);
            Scene scene = mount(shell);

            shell.setMode(WorkbenchMode.facilities);
            scene.stabilize();
            shell.setMode(WorkbenchMode.database);
            scene.stabilize();
            shell.setMode(WorkbenchMode.settings);
            scene.stabilize();

            FacilitiesAndRulesWorkspace facilities = Objects.requireNonNull(facilitiesHolder[0]);
            SettingsAndDiagnosticsWorkspace settings = Objects.requireNonNull(settingsHolder[0]);
            CirrusScrollablePageWidget databaseHost = Objects.requireNonNull(databaseHostHolder[0]);
            CirrusChoiceBoxWidget<WorkbenchThemeMode> dropdown = Objects.requireNonNull(settings.themeChoice());

            assertEquals(WorkbenchThemeMode.obsidianBlueprint, shell.theme());
            assertEquals(WorkbenchThemeMode.obsidianBlueprint, dropdown.value());
            assertEquals(CirrusPalette.obsidianBlueprint, settings.palette().get());
            assertEquals(CirrusPalette.obsidianBlueprint, facilities.palette().get());
            assertEquals(OreUiTextures.Dark.scrollThumbTex, databaseHost.debugScrollThumbTexture());

            dropdown.debugMoveValue(1);

            assertEquals(WorkbenchThemeMode.atelierStone, dropdown.value());
            assertEquals(WorkbenchThemeMode.atelierStone, shell.theme());
            assertEquals(WorkbenchThemeMode.atelierStone, settings.theme());
            assertEquals(WorkbenchThemeMode.atelierStone, facilities.theme());
            assertEquals(CirrusPalette.atelierStone, settings.palette().get());
            assertEquals(CirrusPalette.atelierStone, facilities.palette().get());

            shell.setMode(WorkbenchMode.database);
            scene.stabilize();
            assertEquals(WorkbenchThemeMode.atelierStone, CirrusUiTheme.mode(databaseHost));
            assertEquals(OreUiTextures.Light.scrollThumbTex, databaseHost.debugScrollThumbTexture());

            shell.setMode(WorkbenchMode.settings);
            scene.stabilize();
            dropdown.debugMoveValue(-1);

            assertEquals(WorkbenchThemeMode.obsidianBlueprint, dropdown.value());
            assertEquals(WorkbenchThemeMode.obsidianBlueprint, shell.theme());
            assertEquals(CirrusPalette.obsidianBlueprint, settings.palette().get());
            assertEquals(CirrusPalette.obsidianBlueprint, facilities.palette().get());

            shell.setMode(WorkbenchMode.database);
            scene.stabilize();
            assertEquals(WorkbenchThemeMode.obsidianBlueprint, CirrusUiTheme.mode(databaseHost));
            assertEquals(OreUiTextures.Dark.scrollThumbTex, databaseHost.debugScrollThumbTexture());
        } finally {
            shell.shutdown();
        }

        // DatabasePage itself resolves live WorkbenchShellContext theme without snapshots.
        DatabasePage databasePage = new DatabasePage(RecipeDatabaseIndex.ofRecords(List.of()));
        assertEquals(WorkbenchThemeMode.atelierStone, CirrusUiTheme.mode(databasePage));
        WorkbenchShellWidget databaseShell = new WorkbenchShellWidget(
            providerWith(new WidgetGroup<>(), new WidgetGroup<>(), new WidgetGroup<>(), databasePage),
            headlessTabs(),
            WorkbenchMode.database
        );
        try {
            databaseShell.setTheme(WorkbenchThemeMode.obsidianBlueprint);
            assertEquals(WorkbenchThemeMode.obsidianBlueprint, CirrusUiTheme.mode(databasePage));
            assertEquals(CirrusPalette.obsidianBlueprint, CirrusUiTheme.palette(databasePage));

            databaseShell.setTheme(WorkbenchThemeMode.atelierStone);
            assertEquals(WorkbenchThemeMode.atelierStone, CirrusUiTheme.mode(databasePage));
            assertEquals(CirrusPalette.atelierStone, CirrusUiTheme.palette(databasePage));
        } finally {
            databaseShell.shutdown();
        }
    }

    @Test
    void localClipRectsStayInLocalCoordinatesWhenWidgetsAreOffsetInScene() {
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(sizeFull(), flexColumn(), paddingLeft(120), paddingTop(80));

        NumberInputWidget numberInput = new NumberInputWidget();
        numberInput.useStyle(sizeOf(80, 18));
        CirrusTextInputWidget textInput = new CirrusTextInputWidget();
        textInput.useStyle(sizeOf(100, 18));
        SearchBoxWidget searchBox = new SearchBoxWidget();
        searchBox.useStyle(sizeOf(120, 18));

        DatabasePage databasePage = new DatabasePage(RecipeDatabaseIndex.ofRecords(List.of()));
        DatabasePage.VirtualizedResultSectionsWidget results = databasePage.debugCreateVirtualizedResults();
        results.useStyle(sizeOf(300, 120));

        root.addWidget(numberInput);
        root.addWidget(textInput);
        root.addWidget(searchBox);
        root.addWidget(results);

        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(testSceneHost()));
        scene.setLayoutArea(800, 600);
        scene.layout();
        scene.stabilize();

        assertTrue(numberInput.absolutePos().x() >= 120);
        assertTrue(numberInput.absolutePos().y() >= 80);
        assertEquals(new Rect(4, 0, numberInput.width() - 8, numberInput.height()), numberInput.localTextClipRect());

        assertTrue(textInput.absolutePos().x() >= 120);
        assertEquals(new Rect(4, 0, textInput.width() - 8, textInput.height()), textInput.localTextClipRect());

        assertTrue(searchBox.absolutePos().x() >= 120);
        assertEquals(new Rect(15, 0, searchBox.width() - 19, searchBox.height()), searchBox.localTextClipRect());

        assertTrue(results.absolutePos().x() >= 120);
        assertTrue(results.absolutePos().y() >= 80);
        assertEquals(new Rect(0, 12, results.width(), 68), results.localClipRect(12, 80));
    }

    @Test
    void zeroHardcodedAtelierStoneOpaqueHexesOrDoubleOffsetClipsRemainInMigratedScopes() throws IOException {
        Path commonDir = resolveModSource("src/main/java/dev/vfyjxf/cirrus/ui/widget/common");
        List<Path> migratedWidgets = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(commonDir)) {
            walk.filter(path -> path.toString().endsWith(".java")).forEach(migratedWidgets::add);
        }
        migratedWidgets.add(resolveModSource("src/main/java/dev/vfyjxf/cirrus/ui/widget/NumberInputWidget.java"));
        migratedWidgets.add(resolveModSource("src/main/java/dev/vfyjxf/cirrus/ui/widget/toggle/sw/Slide.java"));
        migratedWidgets.add(resolveModSource("src/main/java/dev/vfyjxf/cirrus/ui/widget/toggle/sw/Rock.java"));
        migratedWidgets.add(
            resolveModSource("src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/search/SearchBoxWidget.java")
        );
        migratedWidgets
                .add(resolveModSource("src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/page/DatabasePage.java"));

        List<String> atelierViolations = new ArrayList<>();
        List<String> clipViolations = new ArrayList<>();
        for (Path file : migratedWidgets) {
            String source = Files.readString(file);
            if (source.contains("CirrusPalette.atelierStone")) {
                atelierViolations.add(file.getFileName().toString());
            }
            if (source.contains("pushClip(absX") || source.contains("sceneClipRect")) {
                clipViolations.add(file.getFileName().toString());
            }
        }
        assertEquals(List.of(), atelierViolations, "Hardcoded CirrusPalette.atelierStone found");
        assertEquals(List.of(), clipViolations, "Double-offset clip helper found");

        Path workbenchDir = resolveModSource("src/main/java/dev/vfyjxf/cirrus/ui/workbench");
        List<String> hexViolations = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(workbenchDir)) {
            List<Path> files = walk.filter(path -> path.toString().endsWith(".java")).toList();
            for (Path file : files) {
                String source = Files.readString(file);
                Matcher matcher = opaqueHexPattern.matcher(source);
                while (matcher.find()) {
                    String token = matcher.group();
                    if (!"0xFFFFFFFF".equalsIgnoreCase(token)) {
                        hexViolations.add(file.getFileName() + ":" + token);
                    }
                }
            }
        }
        assertEquals(List.of(), hexViolations, "Hardcoded opaque theme hex found in ui/workbench");
    }

    private static void restoreTheme(ResourceLocation id, @Nullable Theme previous) {
        if (previous != null) {
            Themes.register(previous);
        } else {
            Themes.unregister(id);
        }
    }

    private static Scene mount(WorkbenchShellWidget shell) {
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(sizeFull());
        root.addWidget(shell);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(testSceneHost()));
        scene.setLayoutArea(1280, 720);
        shell.applyViewport(1280f, 720f);
        scene.layout();
        scene.stabilize();
        return scene;
    }

    private static SessionTabManager headlessTabs() {
        return new SessionTabManager(state -> {
            throw new IllegalStateException("no backend");
        });
    }

    private static WorkspaceProvider providerWith(Widget planner, Widget facilities, Widget settings, Widget database) {
        return new WorkspaceProvider() {
            @Override
            public Widget plannerWorkspace(WorkbenchShellContext ctx) {
                return planner;
            }

            @Override
            public Widget facilitiesWorkspace(WorkbenchShellContext ctx) {
                return facilities;
            }

            @Override
            public Widget settingsWorkspace(WorkbenchShellContext ctx) {
                return settings;
            }

            @Override
            public Widget databaseWorkspace(@Nullable WorkbenchShellContext ctx) {
                return database;
            }
        };
    }

    private static Path resolveModSource(String relativePath) {
        List<Path> candidates = new ArrayList<>();
        String projectRoot = System.getProperty("moddevmcp.project.root");
        if (projectRoot != null) {
            candidates.add(Path.of(projectRoot, "Mod", relativePath));
        }
        candidates.add(Path.of(relativePath));
        candidates.add(Path.of("Mod", relativePath));
        candidates.add(Path.of("CalculatorCirrus", "Mod", relativePath));
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        return candidates.getFirst();
    }

    private static SceneHost testSceneHost() {
        return new SceneHost() {
            @Override
            @SuppressWarnings("NullAway")
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return 1280;
            }

            @Override
            public int height() {
                return 720;
            }
        };
    }
}
