package dev.vfyjxf.calculatorcirrus.ui.widget;

import dev.vfyjxf.calculatorcirrus.api.data.lang.LangEntry;
import dev.vfyjxf.calculatorcirrus.api.event.EventChannel;
import dev.vfyjxf.calculatorcirrus.api.math.Dimension;
import dev.vfyjxf.calculatorcirrus.api.math.Point;
import dev.vfyjxf.calculatorcirrus.api.ui.IRenderableTexture;
import dev.vfyjxf.calculatorcirrus.api.ui.Tooltip;
import dev.vfyjxf.calculatorcirrus.api.ui.event.WidgetEvent;
import dev.vfyjxf.calculatorcirrus.api.ui.widget.IWidget;
import dev.vfyjxf.calculatorcirrus.api.ui.widget.IWidgetGroup;
import dev.vfyjxf.calculatorcirrus.api.ui.widget.Visibility;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.MustBeInvokedByOverriders;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Supplier;

public class Widget implements IWidget {

    protected final EventChannel<WidgetEvent> channel = EventChannel.create(this);

    protected IWidgetGroup<?> root;
    @Nullable
    protected IWidgetGroup<?> parent;
    protected boolean initialized = false;
    protected String id = UUID.randomUUID().toString();
    protected Point position = Point.ZERO;
    protected Point absolute = calculateAbsolute();
    protected Dimension size = Dimension.ZERO;
    protected IRenderableTexture background;
    protected IRenderableTexture icon;
    protected boolean active = true;
    protected Visibility visibility = Visibility.VISIBLE;
    protected boolean moving = false;
    protected Tooltip tooltip = Tooltip.create();

    protected Point calculateAbsolute() {
        if (parent == null) return position;
        IWidgetGroup<?> parent = this.parent;
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

    @Override
    public IWidgetGroup<? extends IWidget> root() {
        return root;
    }

    @Override
    public void setRoot(IWidgetGroup<? super IWidget> root) {
        this.root = root;
    }

    @Override
    public @Nullable IWidgetGroup<? extends IWidget> parent() {
        return parent;
    }

    @Override
    public IWidget setParent(@Nullable IWidgetGroup<? super IWidget> parent) {
        this.parent = parent;
        return this;
    }

    @Override
    @MustBeInvokedByOverriders
    public final void init() {
        IWidget.super.init();
        listeners(WidgetEvent.onInit).onInit(this);
        onInit();
        initialized = true;
    }

    protected void onInit() {

    }

    @Override
    public void update() {
        if (initialized) listeners(WidgetEvent.onUpdate).onUpdate(this);
    }

    @Override
    public void tick() {
        listeners(WidgetEvent.onTick).onTick();
    }

    @Override
    public boolean initialized() {
        return initialized;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public IWidget setId(String id) {
        this.id = id;
        return this;
    }

    @Override
    public Point getPos() {
        return position;
    }

    @Override
    public Point getAbsolute() {
        return absolute;
    }

    @Override
    public IWidget setPos(Point position) {
        var context = common();
        listeners(WidgetEvent.onPositionChanged).onPositionChanged(context, position);
        if (context.cancelled()) return this;
        this.position = position;
        return this;
    }

    @Override
    public Dimension getSize() {
        return size;
    }

    @Override
    public IWidget setSize(Dimension size) {
        this.size = size;
        return this;
    }

    @Override
    public IWidget setBackground(IRenderableTexture background) {
        this.background = background;
        return this;
    }

    @Override
    public IWidget setIcon(IRenderableTexture icon) {
        this.icon = icon;
        return this;
    }

    @Override
    public boolean active() {
        return active;
    }

    @Override
    public void setActive(boolean active) {
        this.active = active;
    }

    @Override
    public Visibility visibility() {
        return visibility;
    }

    @Override
    public void setVisibility(Visibility visibility) {
        this.visibility = visibility;
    }

    @Override
    public @Nullable Tooltip getTooltip() {
        return null;
    }

    @Override
    public IWidget setTooltip(@Nullable Tooltip tooltip) {
        this.tooltip = tooltip;
        return this;
    }

    @Override
    public IWidget tooltip(String text) {
        this.tooltip.add(Component.literal(text));
        return this;
    }

    @Override
    public IWidget tooltip(LangEntry entry) {
        this.tooltip.add(Component.translatable(entry.key()));
        return this;
    }

    @Override
    public IWidget tooltip(Component component) {
        this.tooltip.add(component);
        return this;
    }

    @Override
    public IWidget tooltip(Supplier<Component> supplier) {
        this.tooltip.add(supplier);
        return this;
    }

    @Override
    public IWidget tooltip(Tooltip tooltip) {
        this.tooltip.ofAll(tooltip);
        return this;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T extends IWidget> IWidget asChild(IWidgetGroup<T> parent) {
        if (parent.add(parent.size(), (T) this)) return this;
        this.parent = parent;
        this.onPositionUpdate();
        return this;
    }

    @Override
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

    @Override
    public IWidget self() {
        return this;
    }

    @Override
    public boolean isMoving() {
        return moving;
    }

    @Override
    public void setMoving(boolean moving) {
        this.moving = moving;
    }

    @Override
    public EventChannel<WidgetEvent> events() {
        return channel;
    }
}
