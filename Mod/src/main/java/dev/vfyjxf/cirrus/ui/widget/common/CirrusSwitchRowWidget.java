package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cirrus.ui.widget.toggle.sw.Slide;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import dev.vfyjxf.taffy.style.Overflow;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.overflow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public final class CirrusSwitchRowWidget {
    private static final int switchWidth = 20;
    private static final int switchHeight = 13;

    private CirrusSwitchRowWidget() {}

    public static Widget create(Component label, boolean value, Consumer<Boolean> setter) {
        AtomicBoolean state = new AtomicBoolean(value);
        return create(label, state::get, enabled -> {
            state.set(enabled);
            if (setter != null) {
                setter.accept(enabled);
            }
        }, null);
    }

    public static Widget create(
        Component label,
        BooleanSupplier value,
        Consumer<Boolean> setter,
        @Nullable Supplier<Tooltip> tooltipSupplier
    ) {
        BooleanSupplier state = value == null ? () -> false : value;
        Consumer<Boolean> stateSetter = setter == null ? ignored -> {} : setter;
        WidgetGroup<Widget> row = CirrusRowWidget.flat(20, 6);
        CirrusFittedTextWidget rowLabel = CirrusFittedTextWidget.primary(label);
        rowLabel.useStyle(flexGrow(1), minWidth(0));
        row.addWidget(rowLabel);
        Supplier<Tooltip> tooltip = tooltipSupplier == null ? () -> switchTooltip(label, state) : tooltipSupplier;
        Slide toggle = new Slide(state, stateSetter, tooltip);
        toggle.useStyle(sizeOf(switchWidth, switchHeight), flexShrink(0));
        row.addWidget(toggle);
        row.onHoverTooltip((mouseX, mouseY) -> tooltip.get());
        row.useStyle(overflow(Overflow.HIDDEN));
        return row;
    }

    public static Widget create(LangEntry label, boolean value, Consumer<Boolean> setter) {
        return create(label == null ? Component.empty() : label.component(), value, setter);
    }

    public static Widget create(
        LangEntry label,
        BooleanSupplier value,
        Consumer<Boolean> setter,
        @Nullable Supplier<Tooltip> tooltipSupplier
    ) {
        return create(label == null ? Component.empty() : label.component(), value, setter, tooltipSupplier);
    }

    static int switchWidth() {
        return switchWidth;
    }

    static int switchHeight() {
        return switchHeight;
    }

    private static Tooltip switchTooltip(Component label, BooleanSupplier enabled) {
        Tooltip tooltip = Tooltip.create();
        tooltip.add(label, Tooltip.title);
        tooltip.add(
            () -> CirrusLangs.Calculator.Settings.switchState.component(
                (enabled.getAsBoolean()
                        ? CirrusLangs.Calculator.Settings.switchOn
                        : CirrusLangs.Calculator.Settings.switchOff).string()
            )
        );
        return tooltip;
    }
}
