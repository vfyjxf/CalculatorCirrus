package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.api.recipe.database.RecipeDatabaseIndex;
import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.widget.NumberInputCalculatorPopup;
import dev.vfyjxf.cirrus.ui.widget.NumberInputWidget;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.toggle.sw.Slide;
import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.event.InputEvents;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.api.ui.tooltip.TooltipEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CirrusCommonWidgetContractTest {

    @Test
    void commonButtonOwnsStandardButtonChromeAndState() {
        CirrusButtonWidget button = new CirrusButtonWidget(20, 12).label(Component.literal("long label"))
                .enabled(false);

        assertTrue(button.usesStandardButtonTextures());
        assertFalse(button.enabled());
        assertFalse(button.active());
        assertFalse(button.interactive());
        assertEquals(-1, CirrusButtonWidget.iconYOffset());
    }

    @Test
    void textButtonsDefaultToContentMinimumWidth() {
        CirrusButtonWidget shortButton = new CirrusButtonWidget(20, 12).label(Component.literal("Clear"));
        CirrusButtonWidget longButton = new CirrusButtonWidget(20, 12).label(Component.literal("Select filtered"));

        assertTrue(longButton.preferredMinWidth() > shortButton.preferredMinWidth());
    }

    @Test
    void nestedActionWidgetsConsumeClicksBeforeSelectableRows() {
        AtomicInteger rowClicks = new AtomicInteger();
        AtomicInteger buttonClicks = new AtomicInteger();
        boolean[] checked = {false};
        WidgetGroup<Widget> root = new WidgetGroup<>();
        CirrusRowWidget row = CirrusRowWidget.selectable(18, 4).onPress(ignored -> rowClicks.incrementAndGet());
        CirrusButtonWidget button = new CirrusButtonWidget(18, 18).onPress(buttonClicks::incrementAndGet);
        CirrusCheckboxWidget checkbox = new CirrusCheckboxWidget(() -> checked[0], selected -> checked[0] = selected);
        row.addWidget(button);
        row.addWidget(checkbox);
        root.addWidget(row);

        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(testSceneHost()));
        var buttonBubble = button.bubble();
        Scene.handleBubbleEvent(
            button,
            buttonBubble,
            InputEvents.onMouseClick,
            listener -> listener.onClick(InputContext.fromMouse(1, 1, GLFW.GLFW_MOUSE_BUTTON_LEFT), 1, buttonBubble)
        );

        assertEquals(1, buttonClicks.get());
        assertEquals(0, rowClicks.get());

        var checkboxBubble = checkbox.bubble();
        Scene.handleBubbleEvent(
            checkbox,
            checkboxBubble,
            InputEvents.onMouseClick,
            listener -> listener.onClick(InputContext.fromMouse(1, 1, GLFW.GLFW_MOUSE_BUTTON_LEFT), 1, checkboxBubble)
        );

        assertTrue(checked[0]);
        assertEquals(0, rowClicks.get());
    }

    @Test
    void rowsExposeOnlyGenericChromeVariants() {
        assertEquals(CirrusRowWidget.Chrome.flat, CirrusRowWidget.flat(18, 4).chrome());
        assertEquals(CirrusRowWidget.Chrome.selectable, CirrusRowWidget.selectable(18, 4).chrome());
    }

    @Test
    void databasePageExposesViewAndSortState() {
        DatabasePage page = new DatabasePage(RecipeDatabaseIndex.ofRecords(List.of()));

        assertEquals("grid", page.debugViewMode());
        assertEquals("relevance", page.debugSortMode());
        assertEquals("ascending", page.debugSortDirection());
        assertEquals("builtin", page.debugActiveDatabaseId());

        page.debugSetViewMode("list");
        page.debugSetSortMode("name");
        page.debugSetSortDirection("descending");

        assertEquals("list", page.debugViewMode());
        assertEquals("name", page.debugSortMode());
        assertEquals("descending", page.debugSortDirection());
        assertEquals(78, DatabasePage.databaseChoiceWidth());
        assertEquals(20, DatabasePage.createDatabaseButtonWidth());
        assertEquals(20, DatabasePage.createDatabaseButtonHeight());
        assertEquals(20, DatabasePage.deleteDatabaseButtonWidth());
        assertEquals(20, DatabasePage.deleteDatabaseButtonHeight());
        assertEquals(118, DatabasePage.createDatabaseEditorWidth());
        assertEquals(58, DatabasePage.createDatabaseNameFieldMinWidth());
        assertEquals(20, DatabasePage.viewModeButtonWidth());
        assertEquals(20, DatabasePage.viewModeButtonHeight());
        assertEquals(20, DatabasePage.sortModeButtonWidth());
        assertEquals(20, DatabasePage.sortModeButtonHeight());
        assertEquals(20, DatabasePage.sortDirectionButtonWidth());
        assertEquals(20, DatabasePage.sortDirectionButtonHeight());
        assertEquals(20, DatabasePage.sourcesButtonWidth());
        assertEquals(20, DatabasePage.sourcesButtonHeight());
        assertEquals(92, DatabasePage.systemSourceRowMinWidth());
        assertEquals(210, DatabasePage.systemSourceRowMaxWidth());
        assertEquals(3, DatabasePage.systemSourceLabelPaddingLeft());
        assertEquals(2, DatabasePage.systemSourceLabelPaddingRight());
    }

    @Test
    void databaseViewToggleUsesCroppedGridListIcons() {
        assertEquals(10, CirrusTextures.Toggle.Switch.Calc.gridOn.width());
        assertEquals(12, CirrusTextures.Toggle.Switch.Calc.gridOn.height());
        assertEquals(10, CirrusTextures.Toggle.Switch.Calc.listOn.width());
        assertEquals(12, CirrusTextures.Toggle.Switch.Calc.listOn.height());
    }

    @Test
    void databaseGridCardsUseCompactFillingLayout() {
        assertEquals(118, DatabasePage.gridCardPreferredWidth());
        assertEquals(104, DatabasePage.gridCardMinWidth());
        assertEquals(42, DatabasePage.gridCardHeight());
        assertEquals(3, DatabasePage.gridGap());
        assertEquals(0.48f, DatabasePage.gridCardBasisPercent(), 0.0001f);
    }

    @Test
    void databaseToolbarKeepsSearchSuggestionsAboveButtons() {
        DatabasePage page = new DatabasePage(RecipeDatabaseIndex.ofRecords(List.of()));
        WidgetGroup<?> toolbar = (WidgetGroup<?>) page.children().getFirst();
        Widget search = toolbar.children().getFirst();
        Widget database = toolbar.children().get(1);
        Widget create = toolbar.children().get(2);
        Widget editor = toolbar.children().get(3);
        Widget delete = toolbar.children().get(4);
        Widget sources = toolbar.children().get(5);
        Widget view = toolbar.children().get(6);
        Widget sort = toolbar.children().get(7);
        Widget direction = toolbar.children().get(8);

        assertEquals(9, toolbar.children().size());
        assertTrue(search.zIndex() > view.zIndex());
        assertTrue(search.zIndex() > sort.zIndex());
        assertTrue(search.zIndex() > direction.zIndex());
        assertTrue(search.zIndex() > create.zIndex());
        assertTrue(search.zIndex() > database.zIndex());
        assertTrue(search.zIndex() > editor.zIndex());
        assertTrue(search.zIndex() > delete.zIndex());
        assertTrue(search.zIndex() > sources.zIndex());
        assertTrue(SearchBoxWidget.suggestionPopupZIndex() > toolbar.zIndex());

        List<String> createTooltip = page.debugCreateDatabaseTooltipLines();
        assertTrue(database.visible());
        assertTrue(database.active());
        assertTrue(database.interactive());
        assertFalse(editor.visible());

        page.debugOpenCreateDatabaseEditor();

        assertSame(database, toolbar.children().get(1));
        assertSame(create, toolbar.children().get(2));
        assertSame(editor, toolbar.children().get(3));
        assertTrue(database.visible());
        assertTrue(database.active());
        assertTrue(database.interactive());
        assertTrue(create.visible());
        assertTrue(editor.visible());
        assertNotEquals(createTooltip, page.debugCreateDatabaseTooltipLines());

        page.debugCloseCreateDatabaseEditor();

        assertSame(database, toolbar.children().get(1));
        assertSame(create, toolbar.children().get(2));
        assertSame(editor, toolbar.children().get(3));
        assertTrue(database.visible());
        assertFalse(editor.visible());
        assertEquals(createTooltip, page.debugCreateDatabaseTooltipLines());
    }

    @Test
    void databasePageCreatesEmptyCustomDatabasesAfterExplicitNaming() {
        DatabasePage page = new DatabasePage(RecipeDatabaseIndex.ofRecords(List.of()));

        assertFalse(page.debugCreateDatabaseEditorOpen());
        assertFalse(page.debugCreateDatabaseConfirmEnabled());

        page.debugOpenCreateDatabaseEditor();
        assertTrue(page.debugCreateDatabaseEditorOpen());
        assertEquals("", page.debugCreateDatabaseName());
        assertFalse(page.debugCreateDatabaseConfirmEnabled());

        page.debugSetCreateDatabaseName("   ");
        page.debugConfirmCreateDatabaseName();
        assertEquals(1, page.debugCustomDatabaseCount());
        assertTrue(page.debugCreateDatabaseEditorOpen());

        page.debugSetCreateDatabaseName("Machine Routes");
        assertTrue(page.debugCreateDatabaseConfirmEnabled());
        page.debugConfirmCreateDatabaseName();

        assertEquals("machine_routes", page.debugActiveDatabaseId());
        assertEquals(2, page.debugCustomDatabaseCount());
        assertEquals(0, page.debugActiveDatabaseRecipeCount());
        assertFalse(page.debugCreateDatabaseEditorOpen());
        assertTrue(page.debugCreateDatabaseTooltipLines().size() >= 2);
        assertTrue(page.debugDatabaseChoiceTooltipLines().size() >= 3);
    }

    @Test
    void wrappedTextWidgetTracksLineBudgetConfiguration() {
        CirrusWrappedTextWidget text = new CirrusWrappedTextWidget(
            Component.literal("long translated text"),
            0xFFFFFFFF
        ).maxLines(3).padding(2).lineGap(1);

        assertEquals(3, text.maxLines());
        assertEquals(2, text.padding());
        assertEquals(1, text.lineGap());
    }

    @Test
    void textInputTracksPlaceholderAndLengthLimit() {
        CirrusTextInputWidget input = new CirrusTextInputWidget().setPlaceholder(Component.literal("Name").getString())
                .maxLength(5).setText("abcdef");

        assertEquals("Name", input.placeholder());
        assertEquals("abcde", input.text());
        assertEquals(5, input.maxLength());
    }

    @Test
    void textInputCtrlASelectsTextAndTypingReplacesSelection() {
        AtomicInteger changes = new AtomicInteger();
        CirrusTextInputWidget input = new CirrusTextInputWidget().onTextChanged(ignored -> changes.incrementAndGet())
                .setText("original");

        input.debugHandleKeyInput(GLFW.GLFW_KEY_A, true);

        assertEquals(0, input.debugSelectionStart());
        assertEquals(input.text().length(), input.debugCursorPosition());
        assertEquals("original", input.debugSelectedText());

        input.debugInsertText("next");

        assertEquals("next", input.text());
        assertEquals("", input.debugSelectedText());
        assertEquals(input.text().length(), input.debugCursorPosition());
        assertEquals(1, changes.get());
    }

    @Test
    void textInputBackspaceAndDeleteRemoveSelection() {
        CirrusTextInputWidget backspace = new CirrusTextInputWidget().setText("delete me");
        backspace.debugHandleKeyInput(GLFW.GLFW_KEY_A, true);
        backspace.debugHandleKeyInput(GLFW.GLFW_KEY_BACKSPACE, false);
        assertEquals("", backspace.text());

        CirrusTextInputWidget delete = new CirrusTextInputWidget().setText("delete me too");
        delete.debugHandleKeyInput(GLFW.GLFW_KEY_A, true);
        delete.debugHandleKeyInput(GLFW.GLFW_KEY_DELETE, false);
        assertEquals("", delete.text());
    }

    @Test
    void scrollablePageOwnsStandardScrollbarState() {
        CirrusScrollablePageWidget page = new CirrusScrollablePageWidget();

        assertEquals(6, page.scrollbarWidth());
        assertEquals(4, page.scrollViewportInset());
        assertEquals(4, page.scrollViewportVerticalInset());
    }

    @Test
    void sectionsExposeNamedBodyForCategorizedContent() {
        CirrusSectionWidget section = new CirrusSectionWidget(Component.literal("Database"));

        section.addContent(CirrusText.primary(Component.literal("row")));

        assertEquals(1, section.body().children().size());
        assertFalse(section.collapsed());
    }

    @Test
    void cardsExposeBodyAndExtensionSlotsWithoutBusinessSpecificSections() {
        CirrusCardWidget card = new CirrusCardWidget(Component.literal("Preview"));

        card.addContent(CirrusText.primary(Component.literal("main")));

        assertEquals(1, card.body().children().size());
        assertEquals(0, card.extension().children().size());
        assertFalse(card.extension().visible());

        card.addExtension(CirrusText.primary(Component.literal("extra")));

        assertEquals(1, card.extension().children().size());
        assertTrue(card.extension().visible());
        assertTrue(card.extension().active());
        assertTrue(card.extension().interactive());
    }

    @Test
    void keyValueRowsUseReusableFittedTextChildren() {
        CirrusKeyValueRowWidget row = CirrusKeyValueRowWidget
                .create(Component.literal("Key"), Component.literal("A very long value"));

        assertEquals(CirrusRowWidget.Chrome.flat, row.chrome());
        assertEquals(2, row.children().size());
        assertTrue(row.children().getFirst() instanceof CirrusFittedTextWidget);
        assertTrue(row.children().get(1) instanceof CirrusFittedTextWidget);
    }

    @Test
    void segmentedControlTracksOptionsAndValue() {
        CirrusSegmentedControlWidget<String> control = new CirrusSegmentedControlWidget<String>(16)
                .option("grid", Component.literal("Grid"), 28).option("list", Component.literal("List"), 28)
                .value("list");

        assertEquals(2, control.optionCount());
        assertEquals("list", control.value());
    }

    @Test
    void choiceBoxTracksOptionsValueAndCompactMetrics() {
        CirrusChoiceBoxWidget<String> box = new CirrusChoiceBoxWidget<String>(84)
                .option("consume", Component.literal("Consume observed stock"))
                .option("preserve", Component.literal("Preserve planned stock")).value("preserve");

        assertEquals(2, box.optionCount());
        assertEquals("preserve", box.value());
        assertEquals(84, box.controlWidth());
        assertEquals(16, CirrusChoiceBoxWidget.controlHeight());
        assertEquals(2400, CirrusChoiceBoxWidget.popupZIndex());
        assertFalse(box.debugPopupOpen());
    }

    @Test
    void choiceBoxCustomTooltipOwnsFullTooltipContent() {
        CirrusChoiceBoxWidget<String> box = new CirrusChoiceBoxWidget<String>(84)
                .option("consume", Component.literal("Consume observed stock"))
                .option("preserve", Component.literal("Preserve planned stock")).value("preserve")
                .tooltip(() -> Tooltip.create().add(Component.literal("Custom setting")));

        List<String> lines = DatabasePage.debugTooltipLines(Objects.requireNonNull(box.hoverTooltip(0, 0)));

        assertEquals(List.of("Custom setting"), lines);
    }

    @Test
    void numberInputExposesRangeAndSupplierTooltip() {
        NumberInputWidget input = NumberInputWidget.create(80).setRange(10, 500).setScrollStep(10)
                .tooltip(() -> Tooltip.create().add(Component.literal("Range: 10 - 500")));

        input.setValue(800);

        assertEquals(10, input.minValue());
        assertEquals(500, input.maxValue());
        assertEquals(10, input.scrollStep());
        assertEquals(500, input.getValue());
        assertEquals(
            List.of("Range: 10 - 500"),
            DatabasePage.debugTooltipLines(Objects.requireNonNull(input.hoverTooltip(0, 0)))
        );
    }

    @Test
    void numberInputCanUseReusableCalculatorPopup() {
        NumberInputWidget input = NumberInputWidget.create(80).enableCalculatorPopup();

        assertTrue(input.debugCalculatorPopupEnabled());
        assertEquals(2600, NumberInputCalculatorPopup.popupZIndex());

        input.onCalculatorOpen((source, mouseX, mouseY) -> {});

        assertFalse(input.debugCalculatorPopupEnabled());
    }

    @Test
    void numberInputCalculatorPopupTooltipIncludesShortcutUsage() {
        NumberInputWidget input = NumberInputWidget.create(8).enableCalculatorPopup()
                .tooltipWithCalculatorPopup(() -> Tooltip.create().add(Component.literal("Amount")));

        List<String> lines = DatabasePage.debugTooltipLines(Objects.requireNonNull(input.hoverTooltip(0, 0)));

        assertTrue(lines.contains("Amount"));
        assertTrue(
            lines.stream().anyMatch(line -> line.contains("Middle") || line.contains("middle") || line.contains("中键")) // NOPMD - CJK hint alias
        );
    }

    @Test
    void numberInputMiddleClickOpensCalculatorPopupInRootLayer() {
        NumberInputWidget input = NumberInputWidget.create(80).enableCalculatorPopup();
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.addWidget(input);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(testSceneHost(240, 180)));

        var openBubble = input.bubble();
        boolean handled = Scene.handleBubbleEvent(
            input,
            openBubble,
            InputEvents.onMouseClick,
            listener -> listener.onClick(InputContext.fromMouse(1, 1, GLFW.GLFW_MOUSE_BUTTON_MIDDLE), 1, openBubble)
        );

        assertTrue(handled);
        assertTrue(input.debugCalculatorPopupOpen());
        assertEquals(2, root.children().size());

        var closeBubble = input.bubble();
        Scene.handleBubbleEvent(
            input,
            closeBubble,
            InputEvents.onMouseClick,
            listener -> listener.onClick(InputContext.fromMouse(1, 1, GLFW.GLFW_MOUSE_BUTTON_MIDDLE), 1, closeBubble)
        );
        scene.tick();

        assertFalse(input.debugCalculatorPopupOpen());
        assertEquals(1, root.children().size());
    }

    @Test
    void numberInputWheelDoesNotBubbleToScrollableParent() {
        TestScrollablePage page = new TestScrollablePage();
        NumberInputWidget input = NumberInputWidget.create(10).setRange(0, 100).setScrollStep(5);
        page.addTestWidget(input);
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.addWidget(page);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(testSceneHost()));
        page.scrollState().contentSize(100, 320);
        page.scrollState().jumpTo(0, 64);

        var bubble = input.bubble();
        boolean handled = Scene.handleBubbleEvent(
            input,
            bubble,
            InputEvents.onMouseScrolled,
            listener -> listener.onScrolled(1, 1, 0, -1, bubble)
        );

        assertTrue(handled);
        assertEquals(5, input.getValue());
        assertEquals(64f, page.scrollState().targetScrollY(), 0.001f);
    }

    @Test
    void choiceBoxDoesNotSwitchValueOnMouseWheel() {
        CirrusChoiceBoxWidget<String> box = new CirrusChoiceBoxWidget<String>(84)
                .option("consume", Component.literal("Consume observed stock"))
                .option("preserve", Component.literal("Preserve planned stock")).value("preserve");
        WidgetGroup<Widget> root = new WidgetGroup<>();
        root.addWidget(box);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(testSceneHost()));

        var bubble = box.bubble();
        boolean handled = Scene.handleBubbleEvent(
            box,
            bubble,
            InputEvents.onMouseScrolled,
            listener -> listener.onScrolled(1, 1, 0, -1, bubble)
        );

        assertFalse(handled);
        assertEquals("preserve", box.value());
    }

    @Test
    void choiceBoxPopupUsesCappedRootLayerMetrics() {
        assertEquals(6, CirrusChoiceBoxWidget.popupMaxRows());
        assertEquals(18, CirrusChoiceBoxWidget.popupHeightForOptionCount(1));
        assertEquals(98, CirrusChoiceBoxWidget.popupHeightForOptionCount(20));
        assertEquals(84, CirrusChoiceBoxWidget.preferredPopupWidth(84, 24, 320));
        assertEquals(138, CirrusChoiceBoxWidget.preferredPopupWidth(84, 120, 320));
        assertEquals(112, CirrusChoiceBoxWidget.preferredPopupWidth(84, 180, 120));
    }

    @Test
    void choiceBoxOptionsCanBeRebuiltAfterDatabaseDeletion() {
        CirrusChoiceBoxWidget<String> box = new CirrusChoiceBoxWidget<String>(84)
                .option("builtin", Component.literal("Builtin")).option("scratch", Component.literal("Scratch"))
                .value("scratch");

        box.clearOptions().option("builtin", Component.literal("Builtin")).value("builtin");

        assertEquals(1, box.optionCount());
        assertEquals("builtin", box.value());
    }

    @Test
    void switchRowsUseToggleStateCallback() {
        Widget row = CirrusSwitchRowWidget.create(Component.literal("Trace"), true, ignored -> {});

        assertTrue(row instanceof WidgetGroup<?>);
        assertEquals(2, ((WidgetGroup<?>) row).children().size());
        assertTrue(((WidgetGroup<?>) row).children().getFirst() instanceof CirrusFittedTextWidget);
        assertTrue(((WidgetGroup<?>) row).children().get(1) instanceof Slide);
        assertEquals(20, CirrusSwitchRowWidget.switchWidth());
        assertEquals(13, CirrusSwitchRowWidget.switchHeight());

        Tooltip tooltip = row.hoverTooltip(0, 0);
        assertNotNull(tooltip);
        assertTrue(DatabasePage.debugTooltipLines(tooltip).stream().anyMatch("Trace"::equals));
        assertTrue(tooltip.flatEntries().stream().anyMatch(TooltipEntry.DynamicEntry.class::isInstance));
    }

    @Test
    void checkboxRowsUseButtonBackedCheckedTextureControl() {
        Widget row = CirrusCheckboxRowWidget.create(Component.literal("System"), false, ignored -> {});

        assertTrue(row instanceof WidgetGroup<?>);
        assertEquals(2, ((WidgetGroup<?>) row).children().size());
        assertTrue(((WidgetGroup<?>) row).children().getFirst() instanceof CirrusCheckboxWidget);
        assertEquals(18, CirrusCheckboxRowWidget.rowHeight());
        assertEquals(14, CirrusCheckboxRowWidget.checkboxSize());
        assertEquals(14, CirrusCheckboxWidget.defaultSize());
        assertEquals(4, CirrusCheckboxWidget.iconPadding());
        assertEquals(-1, CirrusCheckboxWidget.iconYOffset());
        assertTrue(((CirrusCheckboxWidget) ((WidgetGroup<?>) row).children().getFirst()).usesStandardButtonTextures());
    }

    private static SceneHost testSceneHost() {
        return testSceneHost(100, 100);
    }

    private static SceneHost testSceneHost(int width, int height) {
        return new SceneHost() {
            @Override
            // Headless host: no Minecraft Font exists in unit tests, and the paths under test never read it.
            @SuppressWarnings("NullAway")
            public Font font() {
                return null;
            }

            @Override
            public int width() {
                return width;
            }

            @Override
            public int height() {
                return height;
            }
        };
    }

    private static final class TestScrollablePage extends CirrusScrollablePageWidget {
        <W extends Widget> W addTestWidget(W widget) {
            return addWidget(widget);
        }
    }

}
