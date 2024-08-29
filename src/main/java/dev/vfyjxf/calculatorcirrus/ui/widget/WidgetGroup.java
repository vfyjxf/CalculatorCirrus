package dev.vfyjxf.calculatorcirrus.ui.widget;

import dev.vfyjxf.calculatorcirrus.api.ui.InputContext;
import dev.vfyjxf.calculatorcirrus.api.ui.drag.DragConsumer;
import dev.vfyjxf.calculatorcirrus.api.ui.event.InputEvent;
import dev.vfyjxf.calculatorcirrus.api.ui.event.WidgetEvent;
import dev.vfyjxf.calculatorcirrus.api.ui.widget.IWidget;
import dev.vfyjxf.calculatorcirrus.api.ui.widget.IWidgetGroup;
import net.minecraft.client.gui.GuiGraphics;
import org.eclipse.collections.api.factory.Lists;
import org.eclipse.collections.api.list.MutableList;

import java.util.List;
import java.util.regex.Pattern;

public class WidgetGroup<T extends IWidget> extends Widget implements IWidgetGroup<T> {

    private final MutableList<T> children = Lists.mutable.empty();
    private final MutableList<T> childrenView = children.asUnmodifiable();

    @Override
    protected void onInit() {
        for (T child : children) {
            child.init();
        }
    }

    @Override
    public int size() {
        return children.size();
    }

    @Override
    public MutableList<T> children() {
        return childrenView;
    }

    @Override
    public IWidgetGroup<T> add(T widget) {
        return this;
    }

    @Override
    public IWidgetGroup<T> self() {
        return this;
    }

    @Override
    public DragConsumer<T> add(T draggable, Runnable onEnter) {
        if (!children.contains(draggable)) {
            draggable.asChild(this);
            onEnter.run();
        }
        return this;
    }

    @Override
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


    @Override
    public void add(T draggable, int index, Runnable onEnter) {
        this.add(index, draggable);
        onEnter.run();
    }

    @Override
    public boolean accept(T draggable) {
        return false;
    }

    @Override
    public boolean remove(T widget) {
        int index = children.indexOf(widget);
        if (index < 0) return false;
        return remove(index);
    }

    @Override
    public boolean remove(int index) {
        if (index < 0 || index >= children.size()) return false;
        IWidget child = children.get(index);
        child.onDelete();
        child.setParent(null);
        return children.remove(index) != null;
    }

    @Override
    public void clear() {
        for (int i = 0; i < children.size(); i++) {
            remove(i);
        }
        initialized = false;
    }

    @Override
    public boolean contains(T widget) {
        return children.contains(widget);
    }

    @Override
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

    @Override
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

    @Override
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

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return super.mouseScrolled(mouseX, mouseY, amount);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseMoved(double mouseX, double mouseY) {
        return super.mouseMoved(mouseX, mouseY);
    }

    @Override
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

    @Override
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

    @Override
    public MutableList<IWidget> getById(Pattern regex) {
        MutableList<IWidget> widgets = Lists.mutable.empty();
        for (IWidget child : children) {
            if (regex.matcher(child.getId()).matches()) {
                widgets.add(child);
            }
            if (child instanceof IWidgetGroup<?> group) {
                List<IWidget> result = group.getById(regex);
                widgets.addAll(result);
            }
        }
        return widgets;
    }

    @Override
    public MutableList<IWidget> getContainedWidgets(boolean withInvisible, int maxDepth) {
        MutableList<IWidget> widgets = Lists.mutable.empty();
        collectHelper(this, widgets, withInvisible, maxDepth, 0);
        return widgets;
    }

    private static void collectHelper(IWidgetGroup<?> group, List<IWidget> widgets, boolean withInvisible, int maxDepth, int depth) {
        if (depth > maxDepth) return;
        for (IWidget child : group.children()) {
            if (child instanceof IWidgetGroup) {
                collectHelper((IWidgetGroup<?>) child, widgets, withInvisible, maxDepth, depth + 1);
            } else {
                if (withInvisible || child.visible())
                    widgets.add(child);
            }
        }
    }
}
