package dev.vfyjxf.calculatorcirrus.api.ui.widget;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.vfyjxf.calculatorcirrus.api.data.lang.LangEntry;
import dev.vfyjxf.calculatorcirrus.api.event.EventChannel;
import dev.vfyjxf.calculatorcirrus.api.event.EventDefinition;
import dev.vfyjxf.calculatorcirrus.api.event.EventHandler;
import dev.vfyjxf.calculatorcirrus.api.math.Dimension;
import dev.vfyjxf.calculatorcirrus.api.math.Point;
import dev.vfyjxf.calculatorcirrus.api.math.Rectangle;
import dev.vfyjxf.calculatorcirrus.api.ui.InputContext;
import dev.vfyjxf.calculatorcirrus.api.ui.Renderable;
import dev.vfyjxf.calculatorcirrus.api.ui.RenderableTexture;
import dev.vfyjxf.calculatorcirrus.api.ui.RichTooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.event.InputEvent;
import dev.vfyjxf.calculatorcirrus.api.ui.event.WidgetEvent;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public abstract class Widget implements Renderable, EventHandler<WidgetEvent> {

    protected final EventChannel<WidgetEvent> channel = EventChannel.create(this);

    protected WidgetGroup<?> root;
    @Nullable
    protected WidgetGroup<?> parent;
    protected boolean initialized = false;
    protected String id = UUID.randomUUID().toString();
    protected Point position = Point.ZERO;
    protected Point absolute = calculateAbsolute();
    protected Dimension size = Dimension.ZERO;
    protected RenderableTexture background;
    protected RenderableTexture icon;
    protected boolean active = true;
    protected Visibility visibility = Visibility.VISIBLE;
    protected boolean moving = false;
    protected RichTooltip tooltip = RichTooltip.create();

    protected Point calculateAbsolute() {
        if (parent == null) return position;
        WidgetGroup<?> parent = this.parent;
        Point absolute = position.copy();
        while (parent != null && parent != this) {
            Point parentPos = parent.getPos();
            absolute.translate(parentPos.x, parentPos.y);
            parent = parent.parent();
        }
        return absolute;
    }

    protected void onPositionUpdate() {
        absolute = calculateAbsolute();
    }


    public WidgetGroup<? extends Widget> root() {
        return root;
    }

    @ApiStatus.Internal

    public void setRoot(WidgetGroup<? super Widget> root) {
        this.root = root;
    }


    public @Nullable WidgetGroup<? extends Widget> parent() {
        return parent;
    }

    @Contract("_ -> this")
    @CanIgnoreReturnValue
    public Widget setParent(@Nullable WidgetGroup<? super Widget> parent) {
        this.parent = parent;
        return this;
    }


    @MustBeInvokedByOverriders
    public final void init() {
        listeners(WidgetEvent.onInit).onInit(this);
        onInit();
        initialized = true;
    }

    protected void onInit() {

    }

    public final Widget onInit(WidgetEvent.OnInit listener) {
        events().register(WidgetEvent.onInit, listener);
        return this;
    }


    /**
     * Call when the widget needs to be updated.
     * <p>
     * Such as:
     * 1.Screen size changed.
     * 2.Parent resize.
     */

    public void update() {
        if (initialized) listeners(WidgetEvent.onUpdate).onUpdate(this);
    }

    public final Widget onUpdate(WidgetEvent.OnUpdate listener) {
        events().register(WidgetEvent.onUpdate, listener);
        return this;
    }


    public void tick() {
        listeners(WidgetEvent.onTick).onTick();
    }

    public final Widget onTick(WidgetEvent.OnTick listener) {
        events().register(WidgetEvent.onTick, listener);
        return this;
    }


    public boolean initialized() {
        return initialized;
    }


    public String getId() {
        return id;
    }


    public Widget setId(String id) {
        this.id = id;
        return this;
    }


    public Point getPos() {
        return position;
    }

    public int getX() {
        return getPos().x;
    }

    public int getY() {
        return getPos().y;
    }


    public Point getAbsolute() {
        return absolute;
    }


    public Widget setPos(Point position) {
        var context = common();
        listeners(WidgetEvent.onPositionChanged).onPositionChanged(context, position);
        if (context.cancelled()) return this;
        this.position = position;
        return this;
    }


    public Widget setPos(int x, int y) {
        return setPos(new Point(x, y));
    }


    public Widget setX(int x) {
        return setPos(x, getY());
    }


    public Widget setY(int y) {
        return setPos(getX(), y);
    }


    public Widget translate(int dx, int dy) {
        return setPos(getPos().x + dx, getPos().y + dy);
    }


    public Dimension getSize() {
        return size;
    }

    public int getWidth() {
        return size.width;
    }

    public int getHeight() {
        return size.height;
    }


    public Widget setSize(Dimension size) {
        this.size = size;
        return this;
    }

    public Widget setSize(int width, int height) {
        return setSize(new Dimension(width, height));
    }

    public Rectangle getBounds() {
        return new Rectangle(getPos().x, getPos().y, getSize().width, getSize().height);
    }

    public Widget setWidth(int width) {
        return setSize(width, getSize().height);
    }

    public Widget setHeight(int height) {
        return setSize(getSize().width, height);
    }


    public Widget setBackground(RenderableTexture background) {
        this.background = background;
        return this;
    }


    public Widget setIcon(RenderableTexture icon) {
        this.icon = icon;
        return this;
    }


    public boolean active() {
        return active;
    }


    public void setActive(boolean active) {
        this.active = active;
    }


    public Visibility visibility() {
        return visibility;
    }


    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    public boolean visible() {
        return visibility == Visibility.VISIBLE;
    }

    public boolean invisible() {
        return visibility != Visibility.VISIBLE;
    }

    public void setVisible(boolean visible) {
        setVisibility(visible ? Visibility.VISIBLE : Visibility.INVISIBLE);
    }

    public void hide() {
        setVisible(false);
    }

    public void show() {
        setVisible(true);
    }

    //////////////////////////////////////
    //********       Tools     *********//
    //////////////////////////////////////

    /**
     * @param mouseX the absolute x coordinate of the mouse
     * @param mouseY the absolute y coordinate of the mouse
     * @return true if the mouse is over this widget
     */
    public boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getAbsolute().x &&
                mouseX <= getAbsolute().x + getSize().width &&
                mouseY >= getAbsolute().y &&
                mouseY <= getAbsolute().y + getSize().height;
    }

    public boolean isMouseOver(InputContext input) {
        return isMouseOver(input.mouseX(), input.mouseY());
    }


    /**
     * Top-down search
     */
    @Nullable
    public <T extends Widget> T getWidgetOfType(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        return null;
    }

    public <T extends Widget> void getWidgetsOfType(Class<T> type, List<T> result) {
        if (type.isInstance(this)) {
            result.add(type.cast(this));
        }
    }

    /**
     * down to top search.
     */
    @Nullable
    public <T extends Widget> T findWidgetsOfType(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        var parent = parent();
        if (parent != null) {
            return parent.findWidgetsOfType(type);
        }
        return null;
    }

    public <T extends Widget> void findWidgetsOfType(Class<T> type, List<T> result) {
        if (type.isInstance(this)) {
            result.add(type.cast(this));
        }
        var parent = parent();
        if (parent != null) {
            parent.findWidgetsOfType(type, result);
        }
    }

    @Nullable
    public Widget getHoveredWidget(double mouseX, double mouseY) {
        if (isMouseOver(mouseX, mouseY)) {
            return this;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    public <O extends Widget> O cast() {
        return (O) this;
    }


    public @Nullable RichTooltip getTooltip() {
        return tooltip;
    }


    public Widget setTooltip(@Nullable RichTooltip richTooltip) {
        this.tooltip = richTooltip;
        return this;
    }


    public Widget tooltip(String text) {
        this.tooltip.add(Component.literal(text));
        return this;
    }


    public Widget tooltip(LangEntry entry) {
        this.tooltip.add(Component.translatable(entry.key()));
        return this;
    }


    public Widget tooltip(Component component) {
        this.tooltip.add(component);
        return this;
    }


    public Widget tooltip(Supplier<Component> supplier) {
        this.tooltip.add(supplier);
        return this;
    }


    public Widget tooltip(RichTooltip richTooltip) {
        this.tooltip.ofAll(richTooltip);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T extends Widget> Widget asChild(WidgetGroup<T> parent) {
        if (parent.add(parent.size(), (T) this)) return this;
        this.parent = parent;
        this.onPositionUpdate();
        return this;
    }


    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (invisible()) return;
        graphics.pose().pushPose();
        {
            graphics.pose().translate(position.x, position.y, 0);
            var context = common();
            listeners(WidgetEvent.onRender).onRender(graphics, mouseX, mouseY, partialTicks, context);
            if (context.cancelled()) return;
            renderInternal(graphics, mouseX, mouseY, partialTicks);
            listeners(WidgetEvent.onRenderPost).onRender(graphics, mouseX, mouseY, partialTicks, common());
        }
        graphics.pose().popPose();
    }

    protected void renderInternal(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        if (background != null) background.render(graphics);
        if (icon != null) icon.render(graphics);
    }


    public Widget self() {
        return this;
    }


    public boolean isMoving() {
        return moving;
    }


    public void setMoving(boolean moving) {
        this.moving = moving;
    }

    //////////////////////////////////////
    //********   User Input    *********//
    //////////////////////////////////////

    public boolean mouseClicked(InputContext input) {
        if (!visible() || !active() || !isMouseOver(input)) return false;
        return listeners(InputEvent.onMouseClicked).onClicked(common(), input);
    }

    public boolean mouseReleased(InputContext input) {
        if (!visible() || !active() || !isMouseOver(input)) return false;
        return listeners(InputEvent.onKeyReleased).onKeyReleased(common(), input);
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    public boolean mouseMoved(double mouseX, double mouseY) {
        return false;
    }

    public boolean keyPressed(InputContext input) {
        if (!visible() || !active()) return false;
        return listeners(InputEvent.onKeyPressed).onKeyPressed(common(), input);
    }

    public boolean keyReleased(InputContext input) {
        if (!visible() || !active()) return false;
        return listeners(InputEvent.onKeyReleased).onKeyReleased(common(), input);
    }

    //////////////////////////////////////
    //********     Events      *********//
    //////////////////////////////////////


    public EventChannel<WidgetEvent> events() {
        return channel;
    }

    public final <T extends WidgetEvent> Widget onEvent(EventDefinition<T> definition, T listener) {
        events().register(definition, listener);
        return this;
    }

    @ApiStatus.NonExtendable
    public final Widget onRender(WidgetEvent.OnRender listener) {
        return onEvent(WidgetEvent.onRender, listener);
    }

    @ApiStatus.NonExtendable
    public final Widget onMouseClicked(InputEvent.OnMouseClicked listener) {
        return onEvent(InputEvent.onMouseClicked, listener);
    }

    @ApiStatus.NonExtendable
    public final Widget onMouseReleased(InputEvent.OnMouseReleased listener) {
        return onEvent(InputEvent.onMouseReleased, listener);
    }

    @ApiStatus.NonExtendable
    public final Widget onKeyReleased(InputEvent.OnKeyReleased listener) {
        return onEvent(InputEvent.onKeyReleased, listener);
    }

    @ApiStatus.NonExtendable
    public final Widget onKeyPressed(InputEvent.OnKeyPressed listener) {
        return onEvent(InputEvent.onKeyPressed, listener);
    }

    public void onDelete() {
        listeners(WidgetEvent.onDelete).onDeleted(this);
    }
}
