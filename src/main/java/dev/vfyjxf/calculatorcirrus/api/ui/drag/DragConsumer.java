package dev.vfyjxf.calculatorcirrus.api.ui.drag;

import dev.vfyjxf.calculatorcirrus.api.ui.widget.Widget;
import dev.vfyjxf.calculatorcirrus.api.ui.widget.WidgetGroup;

public interface DragConsumer<T extends Widget> {

    WidgetGroup<T> self();

    DragConsumer<T> add(T draggable, Runnable onEnter);

    boolean add(int index, T draggable);

    void add(T draggable, int index, Runnable onEnter);

    boolean accept(T draggable);

}
