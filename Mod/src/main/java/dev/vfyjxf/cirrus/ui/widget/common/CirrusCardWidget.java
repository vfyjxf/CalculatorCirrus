package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayFlex;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.displayNone;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexShrink;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;

public final class CirrusCardWidget extends CompositeWidget<Widget> {
    private final Component title;
    private final WidgetGroup<Widget> body;
    private final WidgetGroup<Widget> extension;
    private @Nullable WorkbenchThemeMode appliedTheme;

    public CirrusCardWidget() {
        this(Component.empty());
    }

    public CirrusCardWidget(Component title) {
        this.title = title == null ? Component.empty() : title;
        addStyleClass("cirrus-card");
        useStyle(flexColumn(), gap(3), padding(5), minWidth(0), flexShrink(0), boxSizingBorderBox());
        syncDefaultBackground(true);
        onMount(
            (scene, context, handle) -> CirrusUiTheme
                    .bindThemeListener(this, handle, () -> syncDefaultBackground(false))
        );
        if (!this.title.getString().isBlank()) {
            addWidget(CirrusFittedTextWidget.primary(this.title).textPadding(1, 1)).useStyle(heightOf(12), minWidth(0));
        }
        body = addWidget(new WidgetGroup<>());
        body.useStyle(flexColumn(), gap(3), minWidth(0));

        extension = addWidget(new WidgetGroup<>());
        extension.useStyle(flexColumn(), gap(3), minWidth(0), displayNone());
        extension.setVisible(false).setActive(false).setInteractive(false);
    }

    private void syncDefaultBackground(boolean force) {
        WorkbenchThemeMode mode = CirrusUiTheme.mode(this);
        if (!force && mode == appliedTheme) {
            return;
        }
        appliedTheme = mode;
        defaultStyle(UIStyle.of(background(CirrusUiTheme.textures(this).flat())));
    }

    public Component title() {
        return title;
    }

    public WidgetGroup<Widget> body() {
        return body;
    }

    public WidgetGroup<Widget> extension() {
        return extension;
    }

    public <W extends Widget> W addContent(W widget) {
        return body.addWidget(widget);
    }

    public <W extends Widget> W addExtension(W widget) {
        W added = extension.addWidget(widget);
        extension.useStyle(displayFlex());
        extension.setVisible(true).setActive(true).setInteractive(true);
        return added;
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        canvas.texture(CirrusUiTheme.textures(this).cardSurface(), 0, 0, width(), height());
    }
}
