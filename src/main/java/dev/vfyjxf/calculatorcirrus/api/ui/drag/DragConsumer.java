package dev.vfyjxf.calculatorcirrus.api.ui.drag;

import dev.vfyjxf.calculatorcirrus.api.ui.widget.IWidget;
import dev.vfyjxf.calculatorcirrus.api.ui.widget.IWidgetGroup;

public interface DragConsumer<T extends IWidget> {

    IWidgetGroup<T> self();

    DragConsumer<T> add(T draggable, Runnable onEnter);

    boolean add(int index, T draggable);

    void add(T draggable, int index, Runnable onEnter);

    boolean accept(T draggable);

}
