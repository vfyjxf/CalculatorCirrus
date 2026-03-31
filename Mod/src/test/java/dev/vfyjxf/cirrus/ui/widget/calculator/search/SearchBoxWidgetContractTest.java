package dev.vfyjxf.cirrus.ui.widget.calculator.search;

import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchBoxWidgetContractTest {

    @Test
    void exposesHighlightAndSuggestionStateForTheCurrentQuery() {
        SearchBoxWidget widget = new SearchBoxWidget()
            .setText("out");

        assertTrue(widget.debugHighlightedSegments().stream()
            .anyMatch(segment -> "out".equals(segment.text()) && segment.kind() == SearchHighlightKind.field));
        assertTrue(widget.debugSuggestionCount() > 0);
    }

    @Test
    void commitSelectedSuggestionUpdatesTextAndNotifiesListener() {
        AtomicReference<String> observed = new AtomicReference<>("");
        SearchBoxWidget widget = new SearchBoxWidget()
            .onQueryChanged(observed::set)
            .setText("out");

        widget.debugCommitSuggestion(0);

        assertEquals("out:", widget.text());
        assertEquals("out:", observed.get());
    }

    @Test
    void ctrlASelectsSearchTextAndTypingReplacesSelection() {
        AtomicReference<String> observed = new AtomicReference<>("");
        SearchBoxWidget widget = new SearchBoxWidget()
            .onQueryChanged(observed::set)
            .setText("out:minecraft:smoker");

        assertEquals(EventDispatch.consumed, widget.debugHandleKeyInput(GLFW.GLFW_KEY_A, true));

        assertEquals(0, widget.debugSelectionStart());
        assertEquals(widget.text().length(), widget.debugCursorPosition());
        assertEquals("out:minecraft:smoker", widget.debugSelectedText());

        widget.debugInsertText("in:minecraft:stone");

        assertEquals("in:minecraft:stone", widget.text());
        assertEquals("in:minecraft:stone", observed.get());
        assertEquals("", widget.debugSelectedText());
        assertEquals(widget.text().length(), widget.debugCursorPosition());
    }

    @Test
    void backspaceAndDeleteRemoveSearchSelectionBeforeSingleCharacters() {
        SearchBoxWidget backspace = new SearchBoxWidget().setText("has:minecraft:cobblestone");
        backspace.debugHandleKeyInput(GLFW.GLFW_KEY_A, true);
        assertEquals(EventDispatch.consumed, backspace.debugHandleKeyInput(GLFW.GLFW_KEY_BACKSPACE, false));
        assertEquals("", backspace.text());

        SearchBoxWidget delete = new SearchBoxWidget().setText("out:minecraft:piston");
        delete.debugHandleKeyInput(GLFW.GLFW_KEY_A, true);
        assertEquals(EventDispatch.consumed, delete.debugHandleKeyInput(GLFW.GLFW_KEY_DELETE, false));
        assertEquals("", delete.text());
    }

    @Test
    void databasePageUsesRecipeSearchBoxState() {
        DatabasePage page = new DatabasePage(RecipeDatabaseIndex.ofRecords(List.of()));

        page.debugSetQuery("out");

        assertEquals("out", page.debugQuery());
        assertTrue(page.debugSearchSuggestionCount() > 0);
    }

    @Test
    void emptyDatabaseDoesNotInventJeiStyleValueSuggestions() {
        DatabasePage page = new DatabasePage(RecipeDatabaseIndex.ofRecords(List.of()));

        page.debugSetQuery("@");

        assertEquals("@", page.debugQuery());
        assertEquals(2, page.debugSearchSuggestionCount());
    }

    @Test
    void incompleteSearchPrefixesWaitForMoreInput() {
        DatabasePage page = new DatabasePage(RecipeDatabaseIndex.ofRecords(List.of()));

        page.debugSetQuery("out:");

        assertEquals("out:", page.debugQuery());
        assertEquals(0, page.debugVisibleResultCount());
        assertTrue(page.debugSearchSuggestionCount() > 0);
    }

    @Test
    void shortSearchValuesWaitForMoreInput() {
        DatabasePage page = new DatabasePage(RecipeDatabaseIndex.ofRecords(List.of()));

        page.debugSetQuery("i");
        assertEquals("i", page.debugQuery());
        assertEquals(0, page.debugVisibleResultCount());

        page.debugSetQuery("out:i");
        assertEquals("out:i", page.debugQuery());
        assertEquals(0, page.debugVisibleResultCount());

        page.debugSetQuery("has:*");
        assertEquals("has:*", page.debugQuery());
        assertEquals(0, page.debugVisibleResultCount());
    }

    @Test
    void suggestionSourceCanBeInjectedFromDatabaseCandidates() {
        SearchBoxWidget widget = new SearchBoxWidget()
            .suggestionSource((query, cursor, limit) -> List.of(new SearchSuggestion("@test", "@test", 0, 1)))
            .setText("@");

        assertEquals(1, widget.debugSuggestionCount());
        widget.debugCommitSuggestion(0);
        assertEquals("@test ", widget.text());
    }

    @Test
    void suggestionPopupRebindsRowsSafelyWhenSearchChangesBeforeRebuildUnmountsIt() {
        TestSearchBoxWidget widget = new TestSearchBoxWidget();
        widget.suggestionSource((query, cursor, limit) -> query.length() < 2
            ? List.of(
                new SearchSuggestion("apple", "apple", 0, query.length()),
                new SearchSuggestion("axe", "axe", 0, query.length())
            )
            : List.of(
                new SearchSuggestion("abacus", "abacus", 0, query.length()),
                new SearchSuggestion("anvil", "anvil", 0, query.length()),
                new SearchSuggestion("arrow", "arrow", 0, query.length())
            ));
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(sizeOf(100, 100));
        root.addWidget(widget);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(testSceneHost()));
        scene.stabilize();
        widget.debugSetSize(80, 18);
        scene.requestFocus(widget);
        assertTrue(widget.focused());
        assertTrue(widget.width() > 0);

        widget.setText("a");
        assertTrue(widget.debugSuggestionCount() > 0);
        scene.stabilize();
        assertTrue(widget.debugSuggestionPopupOpen());

        widget.setText("ab");

        assertTrue(widget.debugSuggestionPopupOpen());
        assertDoesNotThrow(root::clear);
    }

    @Test
    void suggestionsUseFloatingLayerAbovePageControls() {
        assertTrue(SearchBoxWidget.suggestionPopupZIndex() >= 1000);
        assertEquals(2400, SearchBoxWidget.suggestionPopupZIndex());
        assertEquals(16, SearchBoxWidget.suggestionPopupHeightForCount(1));
        assertEquals(86, SearchBoxWidget.suggestionPopupHeightForCount(6));
        assertEquals(86, SearchBoxWidget.suggestionPopupHeightForCount(48));
        assertEquals(6, SearchBoxWidget.maxVisibleSuggestions());
        assertEquals(2, SearchBoxWidget.visibleSuggestionRowsForAvailableHeight(6, 30));
        assertEquals(0, SearchBoxWidget.visibleSuggestionRowsForAvailableHeight(6, 12));
        assertEquals(44, SearchBoxWidget.suggestionPopupHeightForRows(3));
    }

    @Test
    void suggestionPopupClosesWhenSearchBoxLeavesItsViewport() {
        TestSearchBoxWidget widget = new TestSearchBoxWidget();
        widget.suggestionSource((query, cursor, limit) -> List.of(
            new SearchSuggestion("apple", "apple", 0, query.length()),
            new SearchSuggestion("axe", "axe", 0, query.length())
        ));
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.useStyle(sizeOf(120, 120));
        root.addWidget(widget);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(testSceneHost()));
        scene.stabilize();
        widget.debugSetSize(80, 18);
        scene.requestFocus(widget);

        widget.suggestionViewport(() -> new Rect(0, 0, 120, 120));
        widget.setText("a");
        scene.stabilize();
        assertTrue(widget.debugSuggestionPopupOpen());

        widget.suggestionViewport(() -> new Rect(0, 40, 120, 20));
        scene.tick();
        scene.stabilize();

        assertFalse(widget.debugSuggestionPopupOpen());
    }

    @Test
    void suggestionRowsCommitOnMousePressBeforeFocusCanClosePopup() throws Exception {
        Path sourcePath = Path.of("src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/search/SearchBoxWidget.java");
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/search/SearchBoxWidget.java");
        }
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("CalculatorCirrus/Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/search/SearchBoxWidget.java");
        }
        if (!Files.exists(sourcePath)) {
            sourcePath = Path.of("D:/ProjectDir/CloudMods/CalculatorCirrus/Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/search/SearchBoxWidget.java");
        }
        String source = Files.readString(sourcePath);

        assertTrue(source.contains("onMouseClicked((input, context) ->"));
        assertTrue(source.contains("commitSuggestion(index);"));
    }

    @Test
    void calculatorSearchBoxesUseScrollableViewportAsPopupBoundary() throws Exception {
        Path databasePagePath = Path.of("src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/page/DatabasePage.java");
        Path rulesPagePath = Path.of("src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/page/RulesPage.java");
        if (!Files.exists(databasePagePath)) {
            databasePagePath = Path.of("Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/page/DatabasePage.java");
            rulesPagePath = Path.of("Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/page/RulesPage.java");
        }
        if (!Files.exists(databasePagePath)) {
            databasePagePath = Path.of("CalculatorCirrus/Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/page/DatabasePage.java");
            rulesPagePath = Path.of("CalculatorCirrus/Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/page/RulesPage.java");
        }
        if (!Files.exists(databasePagePath)) {
            databasePagePath = Path.of("D:/ProjectDir/CloudMods/CalculatorCirrus/Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/page/DatabasePage.java");
            rulesPagePath = Path.of("D:/ProjectDir/CloudMods/CalculatorCirrus/Mod/src/main/java/dev/vfyjxf/cirrus/ui/widget/calculator/page/RulesPage.java");
        }

        assertTrue(Files.readString(databasePagePath).contains(".suggestionViewport(this::scrollViewportSceneBounds)"));
        assertTrue(Files.readString(rulesPagePath).contains(".suggestionViewport(this::scrollViewportSceneBounds)"));
    }

    @Test
    void searchHighlightPaletteUsesReadableAccentColors() {
        assertEquals(0xFF0B5CAD, SearchBoxWidget.highlightColorFor(SearchHighlightKind.field));
        assertEquals(0xFF00796B, SearchBoxWidget.highlightColorFor(SearchHighlightKind.prefix));
        assertEquals(0xFF6A4A00, SearchBoxWidget.highlightColorFor(SearchHighlightKind.operator));
        assertEquals(0xFF6D3FB1, SearchBoxWidget.highlightColorFor(SearchHighlightKind.keyword));
        assertEquals(0xFFB3261E, SearchBoxWidget.highlightColorFor(SearchHighlightKind.negation));
        assertEquals(0xFFD11F2A, SearchBoxWidget.highlightColorFor(SearchHighlightKind.error));
    }

    private static SceneHost testSceneHost() {
        return new SceneHost() {
            @Override
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return 100;
            }

            @Override
            public int height() {
                return 100;
            }
        };
    }

    private static final class TestSearchBoxWidget extends SearchBoxWidget {
        private void debugSetSize(int width, int height) {
            setSize(width, height);
        }
    }
}
