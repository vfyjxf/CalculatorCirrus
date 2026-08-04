package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.floating.FloatingEffect;
import dev.vfyjxf.cloudlib.api.ui.floating.FloatingPlacement;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textMuted;
import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textPrimary;
import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.flip;
import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.offset;
import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.shift;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayDefault;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.zIndexOf;

public class CirrusChoiceBoxWidget<T> extends Widget {
    private static final int controlHeight = 16;
    private static final int rowHeight = 16;
    private static final int minControlWidth = 48;
    private static final int popupPadding = 1;
    private static final int popupTextPadding = 18;
    private static final int popupScreenPadding = 4;
    private static final int popupMaxWidth = 180;
    private static final int popupMaxRows = 6;
    private static final int popupZIndex = 2400;

    private final int controlWidth;
    private final List<Option<T>> options = new ArrayList<>();
    private T value;
    private Consumer<T> onChanged = ignored -> {};
    private @Nullable Supplier<Tooltip> tooltipSupplier;
    private @Nullable ChoicePopup popup;
    private boolean closePopupScheduled;
    private int popupScrollIndex;
    private boolean pressed;

    public CirrusChoiceBoxWidget(int width) {
        this.controlWidth = Math.max(minControlWidth, width);
        useStyle(sizeOf(this.controlWidth, controlHeight), flexShrink(0));
        setFocusable(true);
        setClickGroup(this);
        onMouseClicked((input, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            pressed = true;
            return EventDispatch.consumed;
        });
        onMouseReleased((input, context) -> {
            pressed = false;
            return EventDispatch.pass;
        });
        onMouseLeave((mouseX, mouseY, context) -> pressed = false);
        onMouseClick((input, clickCount, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            togglePopup();
            CirrusUiSound.playClickSound();
            return EventDispatch.consumed;
        });
        onKeyPressed((input, context) -> {
            int key = input.key().getValue();
            switch (key) {
                case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER, GLFW.GLFW_KEY_SPACE -> {
                    togglePopup();
                    CirrusUiSound.playClickSound();
                    return EventDispatch.consumed;
                }
                case GLFW.GLFW_KEY_ESCAPE -> {
                    scheduleClosePopup();
                    return EventDispatch.consumed;
                }
                case GLFW.GLFW_KEY_UP -> {
                    moveValue(-1);
                    return EventDispatch.consumed;
                }
                case GLFW.GLFW_KEY_DOWN -> {
                    moveValue(1);
                    return EventDispatch.consumed;
                }
            }
            return EventDispatch.pass;
        });
        onClickOutside(context -> scheduleClosePopup());
        onUnmount(this::closePopupNow);
    }

    public CirrusChoiceBoxWidget<T> option(T value, Component label) {
        Option<T> option = new Option<>(value, label == null ? Component.empty() : label);
        options.add(option);
        if (this.value == null) {
            this.value = value;
        }
        if (popup != null) {
            popup.refresh();
        }
        return this;
    }

    public CirrusChoiceBoxWidget<T> clearOptions() {
        options.clear();
        value = null;
        popupScrollIndex = 0;
        closePopupNow();
        return this;
    }

    public CirrusChoiceBoxWidget<T> option(T value, LangEntry label, Object... args) {
        return option(value, label == null ? Component.empty() : label.component(args));
    }

    public CirrusChoiceBoxWidget<T> value(T value) {
        this.value = value;
        revealSelectedOption();
        if (popup != null) {
            popup.refresh();
        }
        return this;
    }

    public T value() {
        return value;
    }

    public CirrusChoiceBoxWidget<T> onChanged(Consumer<T> onChanged) {
        this.onChanged = onChanged == null ? ignored -> {} : onChanged;
        return this;
    }

    public CirrusChoiceBoxWidget<T> tooltip(@Nullable Supplier<Tooltip> tooltipSupplier) {
        this.tooltipSupplier = tooltipSupplier;
        return this;
    }

    public int optionCount() {
        return options.size();
    }

    public int controlWidth() {
        return controlWidth;
    }

    public static int controlHeight() {
        return controlHeight;
    }

    public static int popupZIndex() {
        return popupZIndex;
    }

    public static int popupMaxRows() {
        return popupMaxRows;
    }

    public static int visiblePopupRows(int optionCount) {
        return Math.max(1, Math.min(Math.max(0, optionCount), popupMaxRows));
    }

    public static int popupHeightForOptionCount(int optionCount) {
        return visiblePopupRows(optionCount) * rowHeight + popupPadding * 2;
    }

    public static int preferredPopupWidth(int controlWidth, int widestLabelWidth, int boundaryWidth) {
        int desired = Math.max(Math.max(minControlWidth, controlWidth), Math.max(0, widestLabelWidth) + popupTextPadding);
        int widthLimit = popupMaxWidth;
        if (boundaryWidth > popupScreenPadding * 2 + minControlWidth) {
            widthLimit = Math.min(widthLimit, boundaryWidth - popupScreenPadding * 2);
        }
        return Math.max(minControlWidth, Math.min(desired, widthLimit));
    }

    public boolean debugPopupOpen() {
        return popup != null;
    }

    public int debugPopupRowCapacity() {
        return popup == null ? 0 : popup.rowCapacity();
    }

    public int debugPopupRenderedRowCount() {
        return popup == null ? 0 : popup.rowCount;
    }

    private void togglePopup() {
        if (popup == null) {
            openPopup();
        } else {
            scheduleClosePopup();
        }
    }

    private void openPopup() {
        if (popup != null || options.isEmpty() || !lifecycle().mounted()) {
            return;
        }
        revealSelectedOption();
        ChoicePopup next = new ChoicePopup();
        next.setClickGroup(this);
        next.useEffect(FloatingEffect.create(this, FloatingPlacement.bottomStart, offset(1), flip(2), shift(2)));
        addRootWidget(scene().root(), next);
        popup = next;
    }

    private void scheduleClosePopup() {
        if (popup == null || closePopupScheduled) {
            return;
        }
        if (!lifecycle().mounted()) {
            closePopupNow();
            return;
        }
        closePopupScheduled = true;
        scene().nextTick(() -> {
            closePopupScheduled = false;
            closePopupNow();
        });
    }

    private void closePopupNow() {
        ChoicePopup active = popup;
        popup = null;
        closePopupScheduled = false;
        if (active != null && active.parent() instanceof WidgetGroup<?> group) {
            group.remove(active);
        }
    }

    private void moveValue(int delta) {
        int index = selectedIndex();
        if (index < 0) {
            index = 0;
        }
        select(options.get(Math.floorMod(index + delta, options.size())), false);
        revealSelectedOption();
        if (popup != null) {
            popup.refresh();
        }
    }

    private void select(Option<T> option, boolean close) {
        if (!Objects.equals(value, option.value())) {
            value = option.value();
            onChanged.accept(value);
            CirrusUiSound.playClickSound();
        }
        if (close) {
            scheduleClosePopup();
        }
    }

    private int selectedIndex() {
        for (int i = 0; i < options.size(); i++) {
            if (Objects.equals(value, options.get(i).value())) {
                return i;
            }
        }
        return -1;
    }

    private void revealSelectedOption() {
        int selectedIndex = selectedIndex();
        if (selectedIndex < 0) {
            popupScrollIndex = clampPopupScroll(popupScrollIndex);
            return;
        }
        int rows = visiblePopupRows(options.size());
        if (selectedIndex < popupScrollIndex) {
            popupScrollIndex = selectedIndex;
        } else if (selectedIndex >= popupScrollIndex + rows) {
            popupScrollIndex = selectedIndex - rows + 1;
        }
        popupScrollIndex = clampPopupScroll(popupScrollIndex);
    }

    private int clampPopupScroll(int scrollIndex) {
        return Math.max(0, Math.min(scrollIndex, Math.max(0, options.size() - visiblePopupRows(options.size()))));
    }

    private Option<T> selectedOption() {
        int index = selectedIndex();
        if (index >= 0) {
            return options.get(index);
        }
        return options.isEmpty() ? new Option<>(value, Component.empty()) : options.getFirst();
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        boolean down = pressed || popup != null;
        CirrusControlChrome.renderButtonBackground(canvas, 0, 0, width(), height(), down, hovered(), true);

        var arrow = down || hovered() ? CirrusTextures.Icon.arrowDownSelected : CirrusTextures.Icon.arrowDown;
        int arrowX = Math.max(0, width() - arrow.width() - 2);
        int arrowY = CirrusControlChrome.centeredIconY(height(), arrow.height(), down);
        canvas.texture(arrow, arrowX, arrowY, arrow.width(), arrow.height());

        Font font = context().font();
        Component label = selectedOption().label();
        int textWidth = Math.max(0, arrowX - 5);
        Component fitted = fitLabel(font, label, textWidth);
        int textY = Math.max(0, (height() - font.lineHeight) / 2) + (down ? 1 : 0);
        canvas.text(fitted, 4, textY, textPrimary, false);
    }

    @Override
    public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
        Tooltip tooltip = tooltipSupplier == null ? null : tooltipSupplier.get();
        if (tooltip != null) {
            return tooltip;
        }
        if (options.isEmpty()) {
            return super.hoverTooltip(mouseX, mouseY);
        }
        tooltip = Tooltip.create();
        tooltip.add(selectedOption().label(), Tooltip.title);
        for (Option<T> option : options) {
            if (!Objects.equals(value, option.value())) {
                tooltip.add(Component.literal("- ").append(option.label()));
            }
        }
        return tooltip;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addRootWidget(WidgetGroup<?> root, Widget widget) {
        ((WidgetGroup) root).addWidget(widget);
    }

    private static Component fitLabel(Font font, Component label, int maxWidth) {
        if (font.width(label) <= maxWidth) {
            return label;
        }
        int ellipsis = font.width("...");
        if (maxWidth <= ellipsis) {
            return Component.empty();
        }
        String prefix = font.plainSubstrByWidth(label.getString(), maxWidth - ellipsis).stripTrailing();
        return Component.literal(prefix + "...");
    }

    private record Option<T>(T value, Component label) {
    }

    private final class ChoicePopup extends CompositeWidget<Widget> {
        private final ArrayList<OptionRow> rows = new ArrayList<>();
        private int popupWidth = -1;
        private int rowCount = -1;
        private int renderedScrollIndex = -1;

        private ChoicePopup() {
            useStyle(flexColumn(), minWidth(0), padding(popupPadding), boxSizingBorderBox(), zIndexOf(popupZIndex));
            onClickOutside(context -> scheduleClosePopup());
            onMouseScrolled((mouseX, mouseY, scrollX, scrollY, context) -> {
                if (options.size() <= popupMaxRows || scrollY == 0) {
                    return EventDispatch.pass;
                }
                popupScrollIndex = clampPopupScroll(popupScrollIndex + (scrollY > 0 ? -1 : 1));
                refresh();
                return EventDispatch.consumed;
            });
            for (int i = 0; i < popupMaxRows; i++) {
                OptionRow row = new OptionRow();
                rows.add(row);
                addWidget(row);
            }
            refresh();
        }

        private void refresh() {
            popupWidth = popupWidth();
            rowCount = visiblePopupRows(options.size());
            popupScrollIndex = clampPopupScroll(popupScrollIndex);
            renderedScrollIndex = popupScrollIndex;
            useStyle(
                flexColumn(),
                minWidth(0),
                padding(popupPadding),
                boxSizingBorderBox(),
                sizeOf(popupWidth, popupHeightForOptionCount(options.size())),
                zIndexOf(popupZIndex)
            );
            int rowWidth = Math.max(0, popupWidth - popupPadding * 2);
            for (int i = 0; i < rows.size(); i++) {
                OptionRow row = rows.get(i);
                int optionIndex = renderedScrollIndex + i;
                row.bind(i < rowCount && optionIndex < options.size() ? options.get(optionIndex) : null, rowWidth);
            }
        }

        private int rowCapacity() {
            return rows.size();
        }

        private int popupWidth() {
            int widest = 0;
            int boundaryWidth = 0;
            if (CirrusChoiceBoxWidget.this.lifecycle().mounted()) {
                Font font = CirrusChoiceBoxWidget.this.context().font();
                for (Option<T> option : options) {
                    widest = Math.max(widest, font.width(option.label()));
                }
                boundaryWidth = CirrusChoiceBoxWidget.this.scene().root().width();
            }
            return preferredPopupWidth(CirrusChoiceBoxWidget.this.width(), widest, boundaryWidth);
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            canvas.texture(CirrusTextures.Background.Outlined.flat, 0, 0, width(), height());
        }
    }

    private final class OptionRow extends Widget {
        private @Nullable Option<T> option;

        private OptionRow() {
            bind(null, 0);
            onMouseClick((input, clickCount, context) -> {
                if (!input.isLeftClick() || option == null) {
                    return EventDispatch.pass;
                }
                select(option, true);
                return EventDispatch.consumed;
            });
        }

        private void bind(@Nullable Option<T> option, int width) {
            this.option = option;
            useStyle(sizeOf(width, option == null ? 0 : rowHeight), option == null ? displayNone() : displayDefault());
            setVisible(option != null).setActive(option != null).setInteractive(option != null);
        }

        @Override
        protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
            if (option == null || !lifecycle().mounted()) {
                return;
            }
            boolean selected = Objects.equals(value, option.value());
            if (selected || hovered()) {
                CirrusControlChrome.renderButtonBackground(
                    canvas,
                    1,
                    1,
                    Math.max(0, width() - 2),
                    Math.max(0, height() - 2),
                    selected,
                    hovered(),
                    true
                );
            }
            Font font = context().font();
            Component fitted = fitLabel(font, option.label(), Math.max(0, width() - 8));
            int y = Math.max(0, (height() - font.lineHeight) / 2);
            canvas.text(fitted, 4, y, selected || hovered() ? textPrimary : textMuted, false);
        }

        @Override
        public @Nullable Tooltip hoverTooltip(int mouseX, int mouseY) {
            return option == null ? null : Tooltip.create().add(option.label());
        }
    }
}
