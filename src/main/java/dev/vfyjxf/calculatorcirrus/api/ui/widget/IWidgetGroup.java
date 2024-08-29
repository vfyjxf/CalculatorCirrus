package dev.vfyjxf.calculatorcirrus.api.ui.widget;

import dev.vfyjxf.calculatorcirrus.api.ui.drag.DragConsumer;
import dev.vfyjxf.calculatorcirrus.api.ui.event.WidgetEvent;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

public interface IWidgetGroup<T extends IWidget> extends IWidget, DragConsumer<T>{

    int size();

    /**
     * @return an immutable view of the children list.
     */
    MutableList<T> children();

    IWidgetGroup<T> add(T widget);

    boolean add(int index, T widget);

    default IWidgetGroup<T> onChildAdded(WidgetEvent.OnChildAdded onChildAdded) {
        events().register(WidgetEvent.OnChildAdded.onChildAdded, onChildAdded);
        return this;
    }

    default IWidgetGroup<T> onChildRemoved(WidgetEvent.OnChildRemoved onChildRemoved) {
        events().register(WidgetEvent.OnChildRemoved.onChildRemoved, onChildRemoved);
        return this;
    }

    default IWidgetGroup<T> widget(T widget) {
        widget.asChild(this);
        return this;
    }

    @SuppressWarnings("unchecked")
    default IWidgetGroup<T> widgets(T... widgets) {
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
    default IWidget getById(String id) {
        for (T child : children()) {
            if (child.getId().equals(id)) {
                return child;
            }
        }
        return null;
    }

    MutableList<IWidget> getById(Pattern regex);

    @Nullable
    @Override
    default IWidget getHoveredWidget(double mouseX, double mouseY) {
        for (T child : children()) {
            if (child.visible() && child.isMouseOver(mouseX, mouseY)) {
                return child;
            }
        }
        return IWidget.super.getHoveredWidget(mouseX, mouseY);
    }

    @Override
    default <W extends IWidget> @Nullable W getWidgetOfType(Class<W> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        for (T child : children()) {
            var result = child.getWidgetOfType(type);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    default <W extends IWidget> void getWidgetsOfType(Class<W> type, List<W> result) {
        if (type.isInstance(this)) {
            result.add(type.cast(this));
        }
        for (T child : children()) {
            child.getWidgetsOfType(type, result);
        }
    }

    @Override
    default <W extends IWidget> @Nullable W findWidgetsOfType(Class<W> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        for (T child : children()) {
            var result = child.findWidgetsOfType(type);
            if (result != null) {
                return result;
            }
        }
        return null;
    }

    @Override
    default <W extends IWidget> void findWidgetsOfType(Class<W> type, List<W> result) {
        if (type.isInstance(this)) {
            result.add(type.cast(this));
        }
        for (T child : children()) {
            child.findWidgetsOfType(type, result);
        }
    }

    default MutableList<IWidget> getContainedWidgets(boolean withInvisible) {
        MutableList<IWidget> result = Lists.mutable.empty();
        for (T child : children()) {
            if (child.visible() || withInvisible) {
                result.add(child);
                if (child instanceof IWidgetGroup<?> group)
                    result.addAll((group).getContainedWidgets(withInvisible));
            }
        }
        return result;
    }

    MutableList<IWidget> getContainedWidgets(boolean withInvisible, int maxDepth);
}
