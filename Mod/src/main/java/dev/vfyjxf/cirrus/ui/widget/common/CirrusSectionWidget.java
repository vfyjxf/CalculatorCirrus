package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusThemeTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.style.UIStyle;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;

public class CirrusSectionWidget extends CompositeWidget<Widget> {
    public enum Chrome {
        outlinedInset, outlinedFlat
    }

    private final Component title;
    private final CirrusFittedTextWidget titleWidget;
    private final WidgetGroup<Widget> body;
    private Chrome chrome = Chrome.outlinedInset;
    private boolean collapsed;
    private @Nullable WorkbenchThemeMode appliedTheme;

    public CirrusSectionWidget(Component title) {
        this.title = title == null ? Component.empty() : title;
        addStyleClass("cirrus-section");
        useStyle(flexColumn(), minWidth(0), minHeight(0), gap(3), padding(4), boxSizingBorderBox());
        chrome(Chrome.outlinedInset);
        onMount(
            (scene, context, handle) -> CirrusUiTheme
                    .bindThemeListener(this, handle, () -> syncDefaultBackground(false))
        );
        titleWidget = addWidget(CirrusFittedTextWidget.primary(this.title));
        titleWidget.useStyle(heightOf(10), minWidth(0));
        body = addWidget(new WidgetGroup<>());
        body.useStyle(flexColumn(), minWidth(0), minHeight(0), gap(3));
    }

    public CirrusSectionWidget(LangEntry title, Object... args) {
        this(title == null ? Component.empty() : title.component(args));
    }

    public Component title() {
        return title;
    }

    public WidgetGroup<Widget> body() {
        return body;
    }

    public CirrusFittedTextWidget titleWidget() {
        return titleWidget;
    }

    public Chrome chrome() {
        return chrome;
    }

    public CirrusSectionWidget chrome(Chrome chrome) {
        this.chrome = chrome == null ? Chrome.outlinedInset : chrome;
        if (this.chrome == Chrome.outlinedInset) {
            addStyleClass("cirrus-section-inset");
        } else {
            removeStyleClass("cirrus-section-inset");
        }
        syncDefaultBackground(true);
        return this;
    }

    private void syncDefaultBackground(boolean force) {
        WorkbenchThemeMode mode = CirrusUiTheme.mode(this);
        if (!force && mode == appliedTheme) {
            return;
        }
        appliedTheme = mode;
        CirrusThemeTextures textures = CirrusUiTheme.textures(this);
        defaultStyle(UIStyle.of(background(switch (this.chrome) {
            case outlinedFlat -> textures.cardSurface();
            case outlinedInset -> textures.recessed();
        })));
    }

    public boolean collapsed() {
        return collapsed;
    }

    public CirrusSectionWidget collapsed(boolean collapsed) {
        this.collapsed = collapsed;
        body.setVisible(!collapsed);
        return this;
    }

    public <W extends Widget> W addContent(W widget) {
        return body.addWidget(widget);
    }

    public CirrusSectionWidget description(Component text) {
        if (text != null && !text.getString().isBlank()) {
            body.addWidget(CirrusFittedTextWidget.secondary(text)).useStyle(heightOf(10), minWidth(0));
        }
        return this;
    }

    public CirrusSectionWidget description(LangEntry text, Object... args) {
        return description(text == null ? Component.empty() : text.component(args));
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        CirrusThemeTextures textures = CirrusUiTheme.textures(this);
        canvas.texture(
            chrome == Chrome.outlinedFlat ? textures.cardSurface() : textures.flat(),
            0,
            0,
            width(),
            height()
        );
    }
}
