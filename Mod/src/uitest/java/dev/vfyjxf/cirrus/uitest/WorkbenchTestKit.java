package dev.vfyjxf.cirrus.uitest;

import dev.vfyjxf.cirrus.ui.widget.calculator.CalculatorScreen;
import dev.vfyjxf.cirrus.ui.workbench.WorkbenchShellWidget;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.uitest.api.Scenes;
import dev.vfyjxf.cloudlib.uitest.api.Selectors;
import dev.vfyjxf.cloudlib.uitest.api.TestContext;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Live widget-tree reads shared by the workbench scenarios.
 *
 * <p>Everything here reads the running UI — bounds, visibility, lifecycle — and never keeps a copy of production state,
 * so a scenario cannot pass on a stale snapshot of the shell it opened.
 */
final class WorkbenchTestKit {

    private WorkbenchTestKit() {}

    static CalculatorScreen screen(TestContext context) {
        if (!(context.screen() instanceof CalculatorScreen screen)) {
            throw new IllegalStateException("the workbench screen is not open, current=" + context.screen());
        }
        return screen;
    }

    static WorkbenchShellWidget shell(TestContext context) {
        return screen(context).shell();
    }

    static Scene scene() {
        return Scenes.sceneOrThrow(Minecraft.getInstance().screen);
    }

    static <W extends Widget> List<W> all(Class<W> type) {
        List<W> found = new ArrayList<>();
        for (Widget widget : Selectors.walk(scene())) {
            if (type.isInstance(widget)) {
                found.add(type.cast(widget));
            }
        }
        return found;
    }

    static <W extends Widget> @Nullable W first(Class<W> type) {
        List<W> found = all(type);
        return found.isEmpty() ? null : found.get(0);
    }

    /** Whether a widget of the given type is mounted, visible and has a non-empty rectangle. */
    static boolean shown(Class<? extends Widget> type) {
        for (Widget widget : all(type)) {
            var bounds = widget.absoluteBounds();
            if (widget.lifecycle().mounted() && widget.visible() && bounds.width() > 0 && bounds.height() > 0) {
                return true;
            }
        }
        return false;
    }

    static Rect boundsOf(Widget widget) {
        return widget.absoluteBounds();
    }

    static String describe(Rect rect) {
        return rect.x() + "," + rect.y() + " " + rect.width() + "x" + rect.height();
    }

    static String describe(Widget widget) {
        return widget.inspectionTypeName() + "[" + describe(boundsOf(widget)) + "]";
    }

    /** Every widget reachable from the scene root that the mount walk never reached, as readable names. */
    static List<String> unmountedWidgets() {
        List<String> found = new ArrayList<>();
        collectUnmounted(scene().root(), found);
        return found;
    }

    private static void collectUnmounted(Widget widget, List<String> into) {
        if (!widget.lifecycle().mounted()) {
            into.add(widget.inspectionTypeName());
        }
        if (widget instanceof CompositeWidget<?> composite) {
            for (Widget child : composite.children()) {
                collectUnmounted(child, into);
            }
        }
    }

    /** One complete left press-and-release at GUI coordinates, through the scene's own input path. */
    static void click(TestContext context, double x, double y) {
        var target = context.screenOrThrow();
        context.driver().placeCursor((float) x, (float) y);
        context.driver().moveTo(target, (float) x, (float) y);
        context.driver().mouseDown(target, (float) x, (float) y, 0);
        context.driver().mouseUp(target, (float) x, (float) y, 0);
    }
}
