package dev.vfyjxf.calculatorcirrus.api.ui.drag;

import dev.vfyjxf.calculatorcirrus.api.ui.widget.IWidget;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;

public interface Draggable {

    IWidget self();

    default void onDragStart(int button) {

    }

    default void onDragEnd(boolean successful) {

    }

    default void onDrag(int mouseButton, long timeSinceLastClick) {

    }

    default boolean accepts(int x, int y, @Nullable IWidget IWidget) {
        return true;
    }

    boolean isMoving();

    void setMoving(boolean moving);

    default List<DragConsumer<? super IWidget>> receivers() {
        return Collections.emptyList();
    }

}
