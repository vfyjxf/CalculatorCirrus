package dev.vfyjxf.cirrus.ui.widget;

import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.floating.FloatingEffect;
import dev.vfyjxf.cloudlib.api.ui.floating.FloatingPlacement;
import org.jetbrains.annotations.Nullable;

import java.util.WeakHashMap;

import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.flip;
import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.offset;
import static dev.vfyjxf.cloudlib.api.ui.floating.FloatingMiddlewares.shift;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.zIndexOf;

/**
 * Reusable popup binding between {@link NumberInputWidget} and {@link SimpleMathCalculatorWidget}.
 */
public final class NumberInputCalculatorPopup {
    private static final int popupZIndex = 2600;
    private static final WeakHashMap<NumberInputWidget, PopupBinding> activePopups = new WeakHashMap<>();

    private NumberInputCalculatorPopup() {
    }

    public static void open(NumberInputWidget source, int mouseX, int mouseY) {
        if (source == null || !source.lifecycle().mounted()) {
            return;
        }

        PopupBinding active = activePopups.get(source);
        if (active != null && active.attached()) {
            active.scheduleClose();
            return;
        }
        if (active != null) {
            activePopups.remove(source);
        }

        SimpleMathCalculatorWidget popup = SimpleMathCalculatorWidget.create()
            .setInitialValue(source.getValue());
        PopupBinding binding = new PopupBinding(source, popup, source.clickGroup());
        popup.onResultConfirmed(result -> {
            source.setValue(result);
            binding.scheduleClose();
        });
        popup.onClose(binding::scheduleClose);
        popup.onUnmount(binding::onPopupUnmounted);
        source.setClickGroup(binding);
        popup.setClickGroup(binding);
        popup.useStyle(zIndexOf(popupZIndex));
        popup.useEffect(FloatingEffect.create(source, FloatingPlacement.bottomStart, offset(2), flip(3), shift(3)));

        addRootWidget(source.scene().root(), popup);
        activePopups.put(source, binding);
        source.scene().nextTick(() -> {
            if (popup.lifecycle().mounted()) {
                popup.scene().requestFocus(popup);
            }
        });
        CirrusUiSound.playClickSound();
    }

    public static boolean isOpen(NumberInputWidget source) {
        PopupBinding active = activePopups.get(source);
        return active != null && active.attached();
    }

    public static int debugActivePopupCount() {
        activePopups.entrySet().removeIf(entry -> !entry.getValue().attached());
        return activePopups.size();
    }

    public static int popupZIndex() {
        return popupZIndex;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void addRootWidget(WidgetGroup<?> root, Widget widget) {
        ((WidgetGroup) root).addWidget(widget);
    }

    private static final class PopupBinding {
        private final NumberInputWidget source;
        private final SimpleMathCalculatorWidget widget;
        private final @Nullable Object previousSourceClickGroup;
        private boolean closeScheduled;

        private PopupBinding(
            NumberInputWidget source,
            SimpleMathCalculatorWidget widget,
            @Nullable Object previousSourceClickGroup
        ) {
            this.source = source;
            this.widget = widget;
            this.previousSourceClickGroup = previousSourceClickGroup;
        }

        private void scheduleClose() {
            if (closeScheduled) {
                return;
            }
            if (!source.lifecycle().mounted()) {
                closeNow();
                return;
            }
            closeScheduled = true;
            source.scene().nextTick(() -> {
                closeScheduled = false;
                closeNow();
            });
        }

        private boolean attached() {
            return widget.parent() != null && !widget.lifecycle().destroyed();
        }

        private void closeNow() {
            activePopups.remove(source);
            restoreSourceClickGroup();
            if (widget.parent() instanceof WidgetGroup<?> group) {
                group.remove(widget);
            }
        }

        private void onPopupUnmounted() {
            activePopups.remove(source);
            restoreSourceClickGroup();
        }

        private void restoreSourceClickGroup() {
            if (source.lifecycle().mounted() && source.clickGroup() == this) {
                source.setClickGroup(previousSourceClickGroup);
            }
        }
    }
}
