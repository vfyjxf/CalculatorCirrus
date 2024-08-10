package dev.vfyjxf.calculatorcirrus.api.ui.widget;

import dev.vfyjxf.calculatorcirrus.api.ui.drag.DragConsumer;
import dev.vfyjxf.calculatorcirrus.api.ui.event.WidgetEvent;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public interface WidgetGroup<T extends Widget> extends Widget, DragConsumer<T>, Iterable<T> {

    int size();

    /**
     * @return an immutable view of the children list.
     */
    List<T> children();

    WidgetGroup<T> add(T widget);

    boolean add(int index, T widget);

    default WidgetGroup<T> onChildAdded(WidgetEvent.OnChildAdded onChildAdded) {
        channel().register(WidgetEvent.OnChildAdded.onChildAdded, onChildAdded);
        return this;
    }

    default WidgetGroup<T> onChildRemoved(WidgetEvent.OnChildRemoved onChildRemoved) {
        channel().register(WidgetEvent.OnChildRemoved.onChildRemoved, onChildRemoved);
        return this;
    }

    default WidgetGroup<T> widget(T widget) {
        widget.asChild(this);
        return this;
    }

    @SuppressWarnings("unchecked")
    default WidgetGroup<T> widgets(T... widgets) {
        for (T widget : widgets) {
            widget.asChild(this);
        }
        return this;
    }

    boolean remove(T widget);

    boolean remove(int index);

    void clear();

    boolean contains(T widget);

    @Nullable
    default Widget getById(String id) {
        for (T child : this) {
            if (child.getId().equals(id)) {
                return child;
            }
        }
        return null;
    }

    List<Widget> getById(Pattern regex);

    @Nullable
    @Override
    default Widget getHoveredWidget(double mouseX, double mouseY) {
        for (T child : this) {
            if (child.visible() && child.isMouseOver(mouseX, mouseY)) {
                return child;
            }
        }
        return Widget.super.getHoveredWidget(mouseX, mouseY);
    }

    @Override
    default <W extends Widget> @Nullable W getWidgetOfType(Class<W> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        for (T child : this) {
            var result = child.getWidgetOfType(type);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    default <W extends Widget> void getWidgetsOfType(Class<W> type, List<W> result) {
        if (type.isInstance(this)) {
            result.add(type.cast(this));
        }
        for (T child : this) {
            child.getWidgetsOfType(type, result);
        }
    }

    @Override
    default <W extends Widget> @Nullable W findWidgetsOfType(Class<W> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        for (T child : this) {
            var result = child.findWidgetsOfType(type);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    default <W extends Widget> void findWidgetsOfType(Class<W> type, List<W> result) {
        if (type.isInstance(this)) {
            result.add(type.cast(this));
        }
        for (T child : this) {
            child.findWidgetsOfType(type, result);
        }
    }

    default List<Widget> getContainedWidgets(boolean withInvisible) {
        List<Widget> result = new ArrayList<>();
        for (T child : this) {
            if (child.visible() || withInvisible) {
                result.add(child);
                if (child instanceof WidgetGroup<?> group)
                    result.addAll((group).getContainedWidgets(withInvisible));
            }
        }
        return result;
    }

    List<Widget> getContainedWidgets(boolean withInvisible, int maxDepth);
}
