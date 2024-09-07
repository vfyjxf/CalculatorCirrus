package dev.vfyjxf.calculatorcirrus.api.ui.widget;

import dev.vfyjxf.calculatorcirrus.api.ui.InputContext;
import dev.vfyjxf.calculatorcirrus.api.ui.drag.DragConsumer;
import dev.vfyjxf.calculatorcirrus.api.ui.event.InputEvent;
import dev.vfyjxf.calculatorcirrus.api.ui.event.WidgetEvent;
import net.minecraft.client.gui.GuiGraphics;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.regex.Pattern;

public abstract class WidgetGroup<T extends Widget> extends Widget implements DragConsumer<T> {

    private final MutableList<T> children = Lists.mutable.empty();
    private final MutableList<T> childrenView = children.asUnmodifiable();


    protected void onInit() {
        for (T child : children) {
            child.init();
        }
    }

    public int size() {
        return children.size();
    }

    public MutableList<T> children() {
        return childrenView;
    }

    public WidgetGroup<T> add(T widget) {
        return this;
    }

    public WidgetGroup<T> self() {
        return this;
    }

    public DragConsumer<T> add(T draggable, Runnable onEnter) {
        if (!children.contains(draggable)) {
            draggable.asChild(this);
            onEnter.run();
        }
        return this;
    }

    public boolean add(int index, T widget) {
        if (!children.contains(widget)) {
            var context = common();
            listeners(WidgetEvent.onChildAdded).onChildAdded(context, widget);
            if (context.cancelled()) return false;
            children.add(index, widget);
            listeners(WidgetEvent.onChildAddedPost).onChildAdded(context, widget);
            return true;
        }
        return false;
    }


    public void add(T draggable, int index, Runnable onEnter) {
        this.add(index, draggable);
        onEnter.run();
    }


    public boolean accept(T draggable) {
        return false;
    }

    public WidgetGroup<T> onChildAdded(WidgetEvent.OnChildAdded onChildAdded) {
        events().register(WidgetEvent.OnChildAdded.onChildAdded, onChildAdded);
        return this;
    }

    public WidgetGroup<T> onChildRemoved(WidgetEvent.OnChildRemoved onChildRemoved) {
        events().register(WidgetEvent.OnChildRemoved.onChildRemoved, onChildRemoved);
        return this;
    }

    public WidgetGroup<T> widget(T widget) {
        widget.asChild(this);
        return this;
    }

    @SuppressWarnings("unchecked")
    public WidgetGroup<T> widgets(T... widgets) {
        for (T widget : widgets) {
            widget.asChild(this);
        }
        return this;
    }


    public boolean remove(T widget) {
        int index = children.indexOf(widget);
        if (index < 0) return false;
        return remove(index);
    }


    public boolean remove(int index) {
        if (index < 0 || index >= children.size()) return false;
        Widget child = children.get(index);
        child.onDelete();
        child.setParent(null);
        return children.remove(index) != null;
    }


    public void clear() {
        for (int i = 0; i < children.size(); i++) {
            remove(i);
        }
        initialized = false;
    }


    public boolean contains(T widget) {
        return children.contains(widget);
    }

    @Nullable
    public Widget getById(String id) {
        for (T child : children()) {
            if (child.getId().equals(id)) {
                return child;
            }
        }
        return null;
    }


    protected void renderInternal(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(graphics, mouseX, mouseY, partialTicks);
        for (T child : children) {
            graphics.pose().pushPose();
            {
                graphics.pose().translate(position.x, position.y, 0);
                int translatedX = mouseX - this.position.x;
                int translatedY = mouseY - this.position.y;
                child.render(graphics, translatedX, translatedY, partialTicks);
            }
            graphics.pose().popPose();
        }
    }


    public boolean mouseClicked(InputContext input) {
        if (!visible() || !active()) return false;
        var context = common();
        boolean result = listeners(InputEvent.onMouseClicked).onClicked(context, input);
        if (context.cancelled()) return result;
        for (T child : children) {
            if (child.mouseClicked(input)) {
                return true;
            }
        }
        return false;
    }


    public boolean mouseReleased(InputContext input) {
        if (!visible() || !active()) return false;
        var context = common();
        boolean result = listeners(InputEvent.onMouseReleased).onReleased(context, input);
        if (context.cancelled()) return result;
        for (T child : children) {
            if (child.mouseReleased(input)) {
                return true;
            }
        }
        return false;
    }


    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return super.mouseScrolled(mouseX, mouseY, amount);
    }


    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }


    public boolean mouseMoved(double mouseX, double mouseY) {
        return super.mouseMoved(mouseX, mouseY);
    }


    public boolean keyPressed(InputContext input) {
        var context = common();
        boolean result = listeners(InputEvent.onKeyPressed).onKeyPressed(context, input);
        if (context.cancelled()) return result;
        for (T child : children) {
            if (child.keyPressed(input)) {
                return true;
            }
        }
        return false;
    }


    public boolean keyReleased(InputContext input) {
        var context = common();
        boolean result = listeners(InputEvent.onKeyReleased).onKeyReleased(context, input);
        if (context.cancelled()) return result;
        for (T child : children) {
            if (child.keyReleased(input)) {
                return true;
            }
        }
        return false;
    }


    public MutableList<Widget> getById(Pattern regex) {
        MutableList<Widget> widgets = Lists.mutable.empty();
        for (Widget child : children) {
            if (regex.matcher(child.getId()).matches()) {
                widgets.add(child);
            }
            if (child instanceof WidgetGroup<?> group) {
                List<Widget> result = group.getById(regex);
                widgets.addAll(result);
            }
        }
        return widgets;
    }

    @Nullable

    public Widget getHoveredWidget(double mouseX, double mouseY) {
        for (T child : children()) {
            if (child.visible() && child.isMouseOver(mouseX, mouseY)) {
                return child;
            }
        }
        return super.getHoveredWidget(mouseX, mouseY);
    }


    public <W extends Widget> @Nullable W getWidgetOfType(Class<W> type) {
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


    public <W extends Widget> void getWidgetsOfType(Class<W> type, List<W> result) {
        if (type.isInstance(this)) {
            result.add(type.cast(this));
        }
        for (T child : children()) {
            child.getWidgetsOfType(type, result);
        }
    }


    public <W extends Widget> @Nullable W findWidgetsOfType(Class<W> type) {
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


    public <W extends Widget> void findWidgetsOfType(Class<W> type, List<W> result) {
        if (type.isInstance(this)) {
            result.add(type.cast(this));
        }
        for (T child : children()) {
            child.findWidgetsOfType(type, result);
        }
    }

    public MutableList<Widget> getContainedWidgets(boolean withInvisible) {
        MutableList<Widget> result = Lists.mutable.empty();
        for (T child : children()) {
            if (child.visible() || withInvisible) {
                result.add(child);
                if (child instanceof WidgetGroup<?> group)
                    result.addAll((group).getContainedWidgets(withInvisible));
            }
        }
        return result;
    }


    public MutableList<Widget> getContainedWidgets(boolean withInvisible, int maxDepth) {
        MutableList<Widget> widgets = Lists.mutable.empty();
        collectHelper(this, widgets, withInvisible, maxDepth, 0);
        return widgets;
    }

    private static void collectHelper(WidgetGroup<?> group, List<Widget> widgets, boolean withInvisible, int maxDepth, int depth) {
        if (depth > maxDepth) return;
        for (Widget child : group.children()) {
            if (child instanceof WidgetGroup<?> widgetGroup) {
                collectHelper(widgetGroup, widgets, withInvisible, maxDepth, depth + 1);
            } else {
                if (withInvisible || child.visible())
                    widgets.add(child);
            }
        }
    }
}
