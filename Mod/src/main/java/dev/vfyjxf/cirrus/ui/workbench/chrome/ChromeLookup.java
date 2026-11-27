package dev.vfyjxf.cirrus.ui.workbench.chrome;

import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import org.jspecify.annotations.Nullable;

/**
 * Looks up chrome siblings of the same shell layer by type in the scene tree.
 */
final class ChromeLookup {

    private ChromeLookup() {}

    static <T extends Widget> @Nullable T find(Widget from, Class<T> type) {
        if (from == null || type == null || !from.lifecycle().mounted()) {
            return null;
        }
        return walk(from.scene().root(), type);
    }

    private static <T extends Widget> @Nullable T walk(Widget widget, Class<T> type) {
        if (type.isInstance(widget)) {
            return type.cast(widget);
        }
        if (widget instanceof CompositeWidget<?> composite) {
            for (Widget child : composite.children()) {
                T found = walk(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }
}
