package dev.vfyjxf.cirrus.ui.widget;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator.OmniCalculatorView;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import dev.vfyjxf.cloudlib.ui.widget.ColumnWidget;
import dev.vfyjxf.cloudlib.ui.widget.ImageWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.*;

public class TabContainer extends CompositeWidget<Widget> {

    private Tab selectedTab;
    private final Tab calculatorTab;
    private final WidgetGroup<Widget> content;

    public TabContainer() {
        useStyle(
            flexRow(),
            sizeOf(501, 360)
        );
        var tabs = ColumnWidget.create();
        tabs.useStyle(
            zIndexOf(10),
            flexColumn(),
            paddingTop(15),
            marginRight(-2),
            boxSizingBorderBox()
        );
        calculatorTab = new Tab(Icon.calculatorSelect, Icon.calculatorHover, Icon.calculatorUnselect, this::createCalculatorContent);
        tabs.addWidget(calculatorTab);
        tabs.addWidget(new Tab(Icon.databaseSelect, Icon.databaseHover, Icon.databaseUnselect, () -> null));
        tabs.addWidget(new Tab(Icon.contextSelect, Icon.contextHover, Icon.contextUnselect, () -> null));
        tabs.addWidget(new Tab(Icon.settingsSelect, Icon.settingsHover, Icon.settingsUnselect, () -> null));
        addWidget(tabs);
        content = new WidgetGroup<>();
        content.useStyle(
            flexColumn(),
            flexGrow(1),
            minWidth(0),
            minHeight(0),
            background(Background.frame),
            padding(8, 8, 10, 8),
            sizeOf(480, 360)
        );
        addWidget(content);
        selectTab(calculatorTab);
    }

    Widget debugCreateRegisteredCalculatorContent() {
        return calculatorTab.contentFactory.get();
    }

    Widget debugCurrentContent() {
        return content.children().isEmpty() ? null : content.children().getFirst();
    }

    private Widget createCalculatorContent() {
        return new OmniCalculatorView();
    }

    void selectTab(Tab tab) {
        if (selectedTab != null) selectedTab.selected = false;
        selectedTab = tab;
        tab.selected = true;
        content.clear();
        Widget currentContent = tab.contentFactory.get();
        if (currentContent != null) {
            currentContent.useStyle(flexGrow(1), minWidth(0), minHeight(0));
            content.addWidget(currentContent);
        }
    }

    private class Tab extends CompositeWidget<ImageWidget> {

        boolean selected = false;
        private final Supplier<Widget> contentFactory;

        private final ImageWidget imageWidget;

        public Tab(SizedTexture select, SizedTexture hover, SizedTexture unselect, Supplier<Widget> contentFactory) {
            this.contentFactory = contentFactory;

            useStyle(
                sizeOf(21, 22),
                paddingLeft(2),
                paddingTop(2)
            );

            imageWidget = addWidget(ImageWidget.of(select));

            imageWidget.onRender(((canvas, mouseX, mouseY, partialTicks, self, context) -> {
                if (!hovered()) canvas.translate(1, 0);
            }));

            onRender(((canvas, mouseX, mouseY, partialTicks, self, context) -> {
                int dx = hovered() ? 0 : (selected ? 0 : 1);
                if (selected) {
                    canvas.texture(Background.Tab.select, dx, 0);
                } else {
                    if (hovered()) {
                        canvas.texture(Background.Tab.hover, dx, 0);
                    } else {
                        canvas.texture(Background.Tab.unselect, dx, 0);
                    }
                }

            }));

            onMouseEnter(((mouseX, mouseY, context) -> {
                if (!selected) imageWidget.setTexture(hover);
            }));

            onMouseLeave(((mouseX, mouseY, context) -> {
                imageWidget.setTexture(selected ? select : unselect);
            }));

            onMouseClick((input, clickCount, context) -> {
                selectTab(this);
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1f));
                return EventDispatch.consumed;
            });
        }

    }

}
