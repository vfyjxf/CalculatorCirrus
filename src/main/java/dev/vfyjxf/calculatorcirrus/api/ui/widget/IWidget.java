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

public interface IWidget extends IRenderable, Draggable, EventHandler<WidgetEvent> {

    //////////////////////////////////////
    //********       Basic     *********//
    //////////////////////////////////////

    /**
     * maybe itself.
     */
    IWidgetGroup<? extends IWidget> root();

    @ApiStatus.Internal
    void setRoot(IWidgetGroup<? super IWidget> root);

    @Nullable
    IWidgetGroup<? extends IWidget> parent();

    @Contract("_ -> this")
    @CanIgnoreReturnValue
    IWidget setParent(@Nullable IWidgetGroup<? super IWidget> parent);

    /**
     * Construct the widget
     * include layout and addTrait.
     */
    @MustBeInvokedByOverriders
    default void init() {

    }

    @ApiStatus.NonExtendable
    default IWidget onInit(WidgetEvent.OnInit listener) {
        events().register(WidgetEvent.onInit, listener);
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
    default IWidget onUpdate(WidgetEvent.OnUpdate listener) {
        events().register(WidgetEvent.onUpdate, listener);
        return this;
    }

    /**
     * Call every tick.
     */
    default void tick() {

    }

    @ApiStatus.NonExtendable
    default IWidget onTick(WidgetEvent.OnTick listener) {
        events().register(WidgetEvent.onTick, listener);
        return this;
    }

    String getId();

    IWidget setId(String id);

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

    IWidget setPos(Point position);


    default IWidget setPos(int x, int y) {
        return setPos(new Point(x, y));
    }


    default IWidget setX(int x) {
        return setPos(x, getY());
    }


    default IWidget setY(int y) {
        return setPos(getX(), y);
    }


    default IWidget translate(int dx, int dy) {
        return setPos(getPos().x + dx, getPos().y + dy);
    }

    Dimension getSize();

    default int getWidth() {
        return getSize().width;
    }

    default int getHeight() {
        return getSize().height;
    }

    IWidget setSize(Dimension size);

    default IWidget setSize(int width, int height) {
        return setSize(new Dimension(width, height));
    }

    default Rectangle getBounds() {
        return new Rectangle(getPos().x, getPos().y, getSize().width, getSize().height);
    }

    default IWidget setWidth(int width) {
        return setSize(width, getSize().height);
    }

    default IWidget setHeight(int height) {
        return setSize(getSize().width, height);
    }

    IWidget setBackground(IRenderableTexture background);

    IWidget setIcon(IRenderableTexture icon);

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

    default IWidget onMeasure(WidgetEvent.OnMeasure listener) {
        return onEvent(WidgetEvent.onMeasure, listener);
    }

    default void layout(int x, int y, int width, int height) {

    }

    default IWidget onLayout(WidgetEvent.OnLayout listener) {
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
    default <T extends IWidget> T getWidgetOfType(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        return null;
    }

    default <T extends IWidget> void getWidgetsOfType(Class<T> type, List<T> result) {
        if (type.isInstance(this)) {
            result.add(type.cast(this));
        }
    }

    /**
     * down to top search.
     */
    @Nullable
    default <T extends IWidget> T findWidgetsOfType(Class<T> type) {
        if (type.isInstance(this)) {
            return type.cast(this);
        }
        var parent = parent();
        if (parent != null) {
            return parent.findWidgetsOfType(type);
        }
        return null;
    }

    default <T extends IWidget> void findWidgetsOfType(Class<T> type, List<T> result) {
        if (type.isInstance(this)) {
            result.add(type.cast(this));
        }
        var parent = parent();
        if (parent != null) {
            parent.findWidgetsOfType(type, result);
        }
    }

    @Nullable
    default IWidget getHoveredWidget(double mouseX, double mouseY) {
        if (isMouseOver(mouseX, mouseY)) {
            return this;
        }
        return null;
    }

    @SuppressWarnings("unchecked")
    default <O extends IWidget> O cast() {
        return (O) this;
    }

    //////////////////////////////////////
    //********     Tooltips    *********//
    //////////////////////////////////////

    @Nullable
    Tooltip getTooltip();

    @Contract("_ -> this")
    IWidget setTooltip(@Nullable Tooltip tooltip);

    /**
     * Add single line tooltip
     */
    IWidget tooltip(String text);

    IWidget tooltip(LangEntry entry);

    IWidget tooltip(Component component);

    IWidget tooltip(Supplier<Component> supplier);

    default IWidget tooltips(LangEntry... keys) {
        for (LangEntry key : keys) {
            tooltip(key);
        }
        return this;
    }

    default IWidget tooltips(Component... components) {
        for (Component component : components) {
            tooltip(component);
        }
        return this;
    }

    IWidget tooltip(Tooltip tooltip);

    default void renderOverlay(GuiGraphics graphics, int mouseX, int mouseY, float partialTicks) {
        Tooltip tooltip = getTooltip();
        if (tooltip != null)
            ScreenUtil.renderTooltip(Objects.requireNonNull(Minecraft.getInstance().screen), graphics, tooltip, mouseX, mouseY);
    }

    //////////////////////////////////////
    //********        Group    *********//
    //////////////////////////////////////

    /**
     * Note that this method should only be used when the IWidgetGroup doesn't care about the consistency of children types,
     * <p>
     * it's only for IWidgetGroup<IWidget>, if you need to add a widget to the group, use {@link IWidgetGroup#widget(IWidget)}.
     */
    <T extends IWidget> IWidget asChild(IWidgetGroup<T> parent);

    //////////////////////////////////////
    //********     Events      *********//
    //////////////////////////////////////


    @Override
    EventChannel<WidgetEvent> events();

    default <T extends WidgetEvent> IWidget onEvent(EventDefinition<T> definition, T listener) {
        events().register(definition, listener);
        return this;
    }

    @ApiStatus.NonExtendable
    default IWidget onRender(WidgetEvent.OnRender listener) {
        return onEvent(WidgetEvent.onRender, listener);
    }

    @ApiStatus.NonExtendable
    default IWidget onMouseClicked(InputEvent.OnMouseClicked listener) {
        return onEvent(InputEvent.onMouseClicked, listener);
    }

    @ApiStatus.NonExtendable
    default IWidget onMouseReleased(InputEvent.OnMouseReleased listener) {
        return onEvent(InputEvent.onMouseReleased, listener);
    }

    @ApiStatus.NonExtendable
    default IWidget onKeyReleased(InputEvent.OnKeyReleased listener) {
        return onEvent(InputEvent.onKeyReleased, listener);
    }

    @ApiStatus.NonExtendable
    default IWidget onKeyPressed(InputEvent.OnKeyPressed listener) {
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
