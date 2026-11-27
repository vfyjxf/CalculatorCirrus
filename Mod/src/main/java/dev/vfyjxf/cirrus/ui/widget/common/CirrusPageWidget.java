package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.ui.widget.LabelWidget;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeFull;

public class CirrusPageWidget extends CompositeWidget<Widget> {
    private @Nullable WorkbenchThemeMode appliedTheme;

    public CirrusPageWidget() {
        this(6, 5);
    }

    public CirrusPageWidget(int padding, int gap) {
        addStyleClass("cirrus-page");
        useStyle(flexColumn(), sizeFull(), padding(padding), gap(gap));
        syncDefaultBackground(true);
        onMount(
            (scene, context, handle) -> CirrusUiTheme
                    .bindThemeListener(this, handle, () -> syncDefaultBackground(false))
        );
    }

    private void syncDefaultBackground(boolean force) {
        WorkbenchThemeMode mode = CirrusUiTheme.mode(this);
        if (!force && mode == appliedTheme) {
            return;
        }
        appliedTheme = mode;
        defaultStyle(UIStyle.of(background(CirrusUiTheme.textures(this).inset())));
    }

    protected LabelWidget addTitle(Component text) {
        LabelWidget label = CirrusText.primary(text);
        addWidget(label);
        return label;
    }

    protected WidgetGroup<Widget> addVerticalList(int rowGap) {
        WidgetGroup<Widget> list = new WidgetGroup<>();
        list.useStyle(flexColumn(), flexGrow(1), minHeight(0), minWidth(0), gap(rowGap));
        addWidget(list);
        return list;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        canvas.texture(CirrusUiTheme.textures(this).inset(), 0, 0, width(), height());
    }
}
