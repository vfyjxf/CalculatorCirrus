package dev.vfyjxf.cirrus.uitest;

import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchBoxWidget;
import dev.vfyjxf.cirrus.ui.widget.calculator.search.SearchSuggestion;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchMode;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.uitest.api.ClientScenario;
import dev.vfyjxf.cloudlib.uitest.api.ScenarioBuilder;
import dev.vfyjxf.cloudlib.uitest.api.Scenes;
import dev.vfyjxf.cloudlib.uitest.api.Selectors;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * The database search box opens its suggestion popup as a floating child added after mount. Hovering in
 * that same frame used to hit-test the child before the scene mounted it, which crashed the client inside
 * the scene's layer registration.
 */
@ClientScenario(
    name = "cirrus.calculator.search",
    group = "calculator",
    tags = {"ui", "calculator", "search"},
    requiresWorld = false,
    hideGui = true,
    timeoutMs = 30_000,
    scenarioTimeoutMs = 120_000
)
public final class CalculatorSearchHoverScenario {

    private @Nullable SearchBoxWidget searchBox;

    private SearchBoxWidget box() {
        SearchBoxWidget held = searchBox;
        if (held == null) {
            throw new IllegalStateException("the search box was not captured by the open step");
        }
        return held;
    }

    public void define(ScenarioBuilder build) {
        build.guiScale(1)
                .log("calculator.search: a floating suggestion popup mounts and takes hits while the pointer hovers")
                .openScreen(() -> new CalculatorScreen(WorkbenchMode.database))
                .awaitScreen(CalculatorScreen.class)
                .checkScreen(CalculatorScreen.class)
                .action("open the popup and hover before the next rebuild", this::openAndHover)
                .frames(2)
                .check("the suggestion popup survives the rebuild", () -> box().debugSuggestionPopupOpen())
                .check("the mounted popup is hit inside its card", CalculatorSearchHoverScenario::popupIsHitInside)
                .screenshot("calculator-search-popup");
    }

    private void openAndHover(TestContext context) {
        Scene scene = Scenes.sceneOrThrow(context.screenOrThrow());
        SearchBoxWidget box = findSearchBox(scene);
        searchBox = box;
        scene.requestFocus(box);
        box.suggestionViewport(
            () -> new Rect(
                0,
                0,
                Minecraft.getInstance().getWindow().getGuiScaledWidth(),
                Minecraft.getInstance().getWindow().getGuiScaledHeight()
            )
        );
        box.suggestionSource(
            (query, cursor, limit) -> List.of(
                new SearchSuggestion("minecraft:cobblestone", "minecraft:cobblestone", 0, query.length())
            )
        );
        box.setText("minecraft:co");
        if (!box.debugSuggestionPopupOpen()) {
            throw new IllegalStateException("the suggestion popup did not open synchronously");
        }
        // Same step, before the scene rebuild mounts the popup: this is the frame the client crashed on.
        context.driver().placeCursor(4, 4);
        context.driver().moveTo(context.screenOrThrow(), 4, 4);
        scene.mouseMoved(4, 4);
    }

    private static boolean popupIsHitInside() {
        Scene scene = Scenes.sceneOrThrow(Minecraft.getInstance().screen);
        Widget popup = findPopup(scene);
        Rect bounds = popup.absoluteBounds();
        Widget hit = scene.hitTest(bounds.x() + bounds.width() / 2.0, bounds.y() + bounds.height() / 2.0);
        for (Widget widget = hit; widget != null; widget = widget.parent()) {
            if (widget == popup) {
                return true;
            }
        }
        return false;
    }

    private static SearchBoxWidget findSearchBox(Scene scene) {
        for (Widget widget : Selectors.walk(scene)) {
            if (widget instanceof SearchBoxWidget box) {
                return box;
            }
        }
        throw new IllegalStateException("the database search box is not in the tree");
    }

    private static Widget findPopup(Scene scene) {
        for (Widget widget : Selectors.walk(scene)) {
            if (widget.getClass().getSimpleName().contains("SuggestionPopup")) {
                return widget;
            }
        }
        throw new IllegalStateException("the suggestion popup is not in the tree");
    }
}
