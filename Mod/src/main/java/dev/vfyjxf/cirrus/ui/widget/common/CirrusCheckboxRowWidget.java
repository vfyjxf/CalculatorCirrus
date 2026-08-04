package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusUiSound;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static dev.vfyjxf.cirrus.ui.widget.omnicalc.OmniCalcColors.textPrimary;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

public final class CirrusCheckboxRowWidget {
    private static final int rowHeight = 18;
    private static final int checkboxSize = CirrusCheckboxWidget.defaultSize();

    private CirrusCheckboxRowWidget() {
    }

    public static Widget create(Component label, boolean value, Consumer<Boolean> setter) {
        return create(label, value, setter, null);
    }

    public static Widget create(
        Component label,
        boolean value,
        Consumer<Boolean> setter,
        @Nullable Supplier<Tooltip> tooltipSupplier
    ) {
        AtomicBoolean state = new AtomicBoolean(value);
        CirrusRowWidget row = CirrusRowWidget.flat(rowHeight, 5);
        CirrusCheckboxWidget check = new CirrusCheckboxWidget(state::get, selected -> {
            state.set(selected);
            setter.accept(selected);
        });
        check.useStyle(sizeOf(checkboxSize, checkboxSize), flexShrink(0));
        row.addWidget(check);

        CirrusFittedTextWidget text = new CirrusFittedTextWidget(label == null ? Component.empty() : label, textPrimary);
        text.useStyle(heightOf(rowHeight), flexGrow(1), minWidth(0));
        row.addWidget(text);
        row.onPress(ignored -> {
            boolean next = !state.get();
            state.set(next);
            setter.accept(next);
            CirrusUiSound.playClickSound();
        });
        if (tooltipSupplier != null) {
            row.onHoverTooltip((mouseX, mouseY) -> tooltipSupplier.get());
        }
        return row;
    }

    public static int rowHeight() {
        return rowHeight;
    }

    public static int checkboxSize() {
        return checkboxSize;
    }
}
