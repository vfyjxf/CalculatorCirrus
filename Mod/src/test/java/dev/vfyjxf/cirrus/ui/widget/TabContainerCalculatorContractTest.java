package dev.vfyjxf.cirrus.ui.widget;

import dev.vfyjxf.cirrus.api.recipe.RecipeApi;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.CalculatorSettingsPage;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.RulesPage;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator.OmniCalculatorView;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.SceneContext;
import dev.vfyjxf.cloudlib.api.ui.base.SceneHost;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import net.minecraft.client.gui.Font;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class TabContainerCalculatorContractTest {

    @Test
    void calculatorTabBuildsOmniCalcView() throws Exception {
        TabContainer container = new TabContainer();
        Widget calculatorContent = createContent(container, "calculatorTab");
        assertTrue(calculatorContent instanceof OmniCalculatorView);
    }

    @Test
    void calculatorTabIsSelectedByDefault() throws Exception {
        TabContainer container = new TabContainer();
        assertTrue(currentContent(container) instanceof OmniCalculatorView);
    }

    @Test
    void nonCalculatorTabsBuildConcretePages() throws Exception {
        assumeTrue(recipeApiAvailable(), "JEI/RecipeApi not loaded");
        TabContainer container = new TabContainer();
        assertTrue(createContent(container, "databaseTab") instanceof DatabasePage);
        assertTrue(createContent(container, "rulesTab") instanceof RulesPage);
        assertTrue(createContent(container, "settingsTab") instanceof CalculatorSettingsPage);
    }

    @Test
    void tabSwitchPreservesDatabasePageInstanceAndScrollUntilScreenCloses() throws Exception {
        assumeTrue(recipeApiAvailable(), "JEI/RecipeApi not loaded");
        WidgetGroup<Widget> root = new WidgetGroup<>();
        TabContainer container = new TabContainer();
        root.addWidget(container);
        Scene scene = new Scene(root);
        scene.init();
        scene.mount(SceneContext.create(testSceneHost()));
        scene.stabilize();

        selectTab(container, "databaseTab");
        scene.stabilize();
        DatabasePage databasePage = (DatabasePage) currentContent(container);
        databasePage.scrollState().contentSize(100, 300);
        databasePage.scrollState().jumpTo(0, 73);

        selectTab(container, "settingsTab");
        scene.stabilize();
        assertTrue(databasePage.lifecycle().unmounted());

        selectTab(container, "databaseTab");
        scene.stabilize();

        assertSame(databasePage, currentContent(container));
        assertEquals(73f, databasePage.scrollState().targetScrollY());

        selectTab(container, "settingsTab");
        scene.stabilize();
        scene.destroy();

        assertTrue(databasePage.lifecycle().destroyed());
    }

    private static boolean recipeApiAvailable() {
        try {
            RecipeApi.instance();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static SceneHost testSceneHost() {
        return new SceneHost() {
            @Override public Font font() { return null; }
            @Override public int width() { return 501; }
            @Override public int height() { return 360; }
        };
    }

    private static Widget currentContent(TabContainer container) throws Exception {
        var field = TabContainer.class.getDeclaredField("content");
        field.setAccessible(true);
        var content = (WidgetGroup<?>) field.get(container);
        return content.children().isEmpty() ? null : content.children().getFirst();
    }

    private static Widget createContent(TabContainer container, String tabName) throws Exception {
        var field = TabContainer.class.getDeclaredField(tabName);
        field.setAccessible(true);
        Object tab = field.get(container);
        var cfField = tab.getClass().getDeclaredField("contentFactory");
        cfField.setAccessible(true);
        var factory = (java.util.function.Supplier<Widget>) cfField.get(tab);
        return factory.get();
    }

    private static void selectTab(TabContainer container, String tabName) throws Exception {
        var field = TabContainer.class.getDeclaredField(tabName);
        field.setAccessible(true);
        Object tab = field.get(container);
        for (var m : TabContainer.class.getDeclaredMethods()) {
            if (m.getName().equals("selectTab") && m.getParameterCount() == 1) {
                m.setAccessible(true);
                m.invoke(container, tab);
                return;
            }
        }
        throw new NoSuchMethodException("selectTab not found");
    }
}
