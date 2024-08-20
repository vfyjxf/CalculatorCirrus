package dev.vfyjxf.calculatorcirrus.api.ui.widget;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import dev.vfyjxf.calculatorcirrus.api.data.lang.LangEntry;
import dev.vfyjxf.calculatorcirrus.api.event.EventChannel;
import dev.vfyjxf.calculatorcirrus.api.event.EventDefinition;
import dev.vfyjxf.calculatorcirrus.api.event.EventHandler;
import dev.vfyjxf.calculatorcirrus.api.math.Dimension;
import dev.vfyjxf.calculatorcirrus.api.math.Point;
import dev.vfyjxf.calculatorcirrus.api.math.Rectangle;
import dev.vfyjxf.calculatorcirrus.api.ui.IRenderable;
import dev.vfyjxf.calculatorcirrus.api.ui.IRenderableTexture;
import dev.vfyjxf.calculatorcirrus.api.ui.InputContext;
import dev.vfyjxf.calculatorcirrus.api.ui.Tooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.drag.Draggable;
import dev.vfyjxf.calculatorcirrus.api.ui.event.InputEvent;
import dev.vfyjxf.calculatorcirrus.api.ui.event.WidgetEvent;
import dev.vfyjxf.calculatorcirrus.utils.ScreenUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

public interface Widget extends IRenderable, Draggable, EventHandler<WidgetEvent> {

    //////////////////////////////////////
    //********       Basic     *********//
    //////////////////////////////////////

    /**
     * maybe itself.
     */
    WidgetGroup<? extends Widget> root();

    @ApiStatus.Internal
    void setRoot(WidgetGroup<? super Widget> root);

    @Nullable
    WidgetGroup<? extends Widget> parent();

    @Contract("_ -> this")
    @CanIgnoreReturnValue
    Widget setParent(@Nullable WidgetGroup<? super Widget> parent);

    /**
     * Construct the widget
     * include layout and addTrait.
     */
    @MustBeInvokedByOverriders
    default void init() {

    }

    @ApiStatus.NonExtendable
    default Widget onInit(WidgetEvent.OnInit listener) {
        channel().register(WidgetEvent.onInit, listener);
        return this;
    }

    boolean initialized();

    /**
     * Call when the widget needs to be updated.
     * <p>
     * Such as:
     * 1.Screen size changed.
     * 2.Parent resize.
     */
    default void update() {

    }

    @ApiStatus.NonExtendable
    default Widget onUpdate(WidgetEvent.OnUpdate listener) {
        channel().register(WidgetEvent.onUpdate, listener);
        return this;
    }

    /**
     * Only for debugging.
     */
    @ApiStatus.Internal
    void rebuild();

    /**
     * Call every tick.
     */
    default void tick() {

    }

    @ApiStatus.NonExtendable
    default Widget onTick(WidgetEvent.OnTick listener) {
        channel().register(WidgetEvent.onTick, listener);
        return this;
    }

    String getId();

    Widget setId(String id);

    /**
     * @return Relative coordinates relative to the upper left corner of the parent.
     */
    Point getPos();

    default int getX() {
        return getPos().x;
    }

    default int getY() {
        return getPos().y;
    }

    /**
     * @return Absolute coordinates relative to the upper left corner of the screen.
     */
    Point getAbsolute();

    Widget setPos(Point position);


    default Widget setPos(int x, int y) {
        return setPos(new Point(x, y));
    }


    default Widget setX(int x) {
        return setPos(x, getY());
    }


    default Widget setY(int y) {
        return setPos(getX(), y);
    }


    default Widget translate(int dx, int dy) {
        return setPos(getPos().x + dx, getPos().y + dy);
    }

    Dimension getSize();

    default int getWidth() {
        return getSize().width;
    }

    default int getHeight() {
        return getSize().height;
    }

    Widget setSize(Dimension size);

    default Widget setSize(int width, int height) {
        return setSize(new Dimension(width, height));
    }

    default Rectangle getBounds() {
        return new Rectangle(getPos().x, getPos().y, getSize().width, getSize().height);
    }

    default Widget setWidth(int width) {
        return setSize(width, getSize().height);
    }

    default Widget setHeight(int height) {
        return setSize(getSize().width, height);
    }

    Widget setBackground(IRenderableTexture background);

    Widget setIcon(IRenderableTexture icon);

    boolean active();

    void setActive(boolean active);

    Visibility visibility();

    void setVisibility(Visibility visibility);

    default boolean visible() {
        return visibility() == Visibility.VISIBLE;
    }

    default boolean invisible() {
        return visibility() != Visibility.VISIBLE;
    }

    default void setVisible(boolean visible) {
        setVisibility(visible ? Visibility.VISIBLE : Visibility.INVISIBLE);
    }

    default void hide() {
        setVisible(false);
    }

    default void show() {
        setVisible(true);
    }

    //////////////////////////////////////
    //********       layout     *********//
    //////////////////////////////////////

    default void measure(int width, int height) {

    }

    default Widget onMeasure(WidgetEvent.OnMeasure listener) {
        return onEvent(WidgetEvent.onMeasure, listener);
    }

    default void layout(int x, int y, int width, int height) {

    }

    default Widget onLayout(WidgetEvent.OnLayout listener) {
        return onEvent(WidgetEvent.onLayout, listener);
    }

    //////////////////////////////////////
    //********       Tools     *********//
    //////////////////////////////////////

    /**
     * @param mouseX the absolute x coordinate of the mouse
     * @param mouseY the absolute y coordinate of the mouse
     * @return true if the mouse is over this widget
     */
    default boolean isMouseOver(double mouseX, double mouseY) {
        return mouseX >= getAbsolute().x &&
                mouseX <= getAbsolute().x + getSize().width &&
                mouseY >= getAbsolute().y &&
                mouseY <= getAbsolute().y + getSize().height;
    }

    default boolean isMouseOver(InputContext input) {
        return isMouseOver(input.mouseX(), input.mouseY());
    }


    /**
     * Top-down search
     */
    @Nullable
    default <T extends Widget> T getWidgetOfType(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        return null;
    }

    default <T extends Widget> void getWidgetsOfType(Class<T> type, List<T> result) {
        if (type.isInstance(this)) {
            result.add(type.cast(this));
        }
    }

    /**
     * down to top search.
     */
    @Nullable
    default <T extends Widget> T findWidgetsOfType(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        var parent = parent();
        if (parent != null) {
            return parent.findWidgetsOfType(type);
        }
        return null;
    }

    default <T extends Widget> void findWidgetsOfType(Class<T> type, List<T> result) {
        if (type.isInstance(this)) {
            result.add(type.cast(this));
        }
        var parent = parent();
        if (parent != null) {
            parent.findWidgetsOfType(type, result);
        }
    }

    @Nullable
    default Widget getHoveredWidget(double mouseX, double mouseY) {
        if (isMouseOver(mouseX, mouseY)) {
            return this;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    default <O extends Widget> O cast() {
        return (O) this;
    }

    //////////////////////////////////////
    //********     Tooltips    *********//
    //////////////////////////////////////

    @Nullable
    Tooltip getTooltip();

    @Contract("_ -> this")
    Widget setTooltip(@Nullable Tooltip tooltip);

    /**
     * Add single line tooltip
     */
    Widget tooltip(String text);

    Widget tooltip(LangEntry key);

    Widget tooltip(Component component);

    Widget tooltip(Supplier<Component> supplier);

    default Widget tooltips(LangEntry... keys) {
        for (LangEntry key : keys) {
            tooltip(key);
        }
        return this;
    }

    default Widget tooltips(Component... components) {
        for (Component component : components) {
            tooltip(component);
        }
        return this;
    }

    Widget tooltip(Tooltip tooltip);

    default void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        Tooltip tooltip = getTooltip();
        if (tooltip != null)
            ScreenUtil.renderTooltip(Objects.requireNonNull(Minecraft.getInstance().screen), graphics, tooltip, mouseX, mouseY);
    }

    //////////////////////////////////////
    //********        Group    *********//
    //////////////////////////////////////

    /**
     * Note that this method should only be used when the WidgetGroup doesn't care about the consistency of children types,
     * <p>
     * it's only for WidgetGroup<Widget>, if you need to add a widget to the group, use {@link WidgetGroup#widget(Widget)}.
     */
    <T extends Widget> Widget asChild(WidgetGroup<T> parent);

    //////////////////////////////////////
    //********     Events      *********//
    //////////////////////////////////////

    @Override
    EventChannel<WidgetEvent> channel();

    default <T extends WidgetEvent> Widget onEvent(EventDefinition<T> definition, T listener) {
        channel().register(definition, listener);
        return this;
    }

    @ApiStatus.NonExtendable
    default Widget onRender(WidgetEvent.OnRender listener) {
        return onEvent(WidgetEvent.onRender, listener);
    }

    @ApiStatus.NonExtendable
    default Widget onMouseClicked(InputEvent.OnMouseClicked listener) {
        return onEvent(InputEvent.onMouseClicked, listener);
    }

    @ApiStatus.NonExtendable
    default Widget onMouseReleased(InputEvent.OnMouseReleased listener) {
        return onEvent(InputEvent.onMouseReleased, listener);
    }

    @ApiStatus.NonExtendable
    default Widget onKeyReleased(InputEvent.OnKeyReleased listener) {
        return onEvent(InputEvent.onKeyReleased, listener);
    }

    @ApiStatus.NonExtendable
    default Widget onKeyPressed(InputEvent.OnKeyPressed listener) {
        return onEvent(InputEvent.onKeyPressed, listener);
    }

    default void onDelete() {
        listeners(WidgetEvent.onDelete).onDeleted(this);
    }

    //////////////////////////////////////
    //********   User Input    *********//
    //////////////////////////////////////

    default boolean mouseClicked(InputContext input) {
        if (!visible() || !active() || !isMouseOver(input)) return false;
        return listeners(InputEvent.onMouseClicked).onClicked(common(), input);
    }

    default boolean mouseReleased(InputContext input) {
        if (!visible() || !active() || !isMouseOver(input)) return false;
        return listeners(InputEvent.onKeyReleased).onKeyReleased(common(), input);
    }

    default boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        return false;
    }

    default boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return false;
    }

    default boolean mouseMoved(double mouseX, double mouseY) {
        return false;
    }

    default boolean keyPressed(InputContext input) {
        if (!visible() || !active()) return false;
        return listeners(InputEvent.onKeyPressed).onKeyPressed(common(), input);
    }

    default boolean keyReleased(InputContext input) {
        if (!visible() || !active()) return false;
        return listeners(InputEvent.onKeyReleased).onKeyReleased(common(), input);
    }

    //TODO:scissor


}
