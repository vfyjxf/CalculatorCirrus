package dev.vfyjxf.cirrus.ui.execution.page;

import dev.vfyjxf.cloudlib.api.ui.InputContext;
import dev.vfyjxf.cloudlib.api.ui.texture.ImageTexture;
import dev.vfyjxf.cloudlib.api.ui.tooltip.Tooltip;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusButtonWidget;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class RecipeIconButtonWidget extends CirrusButtonWidget {
    RecipeIconButtonWidget(
        Supplier<ImageTexture> iconSupplier,
        BooleanSupplier activeSupplier,
        Consumer<InputContext> action,
        Supplier<Tooltip> tooltipSupplier
    ) {
        super(16, 16);
        iconSupplier(iconSupplier)
            .selected(activeSupplier)
            .onPress(action)
            .tooltip(tooltipSupplier);
    }
}
