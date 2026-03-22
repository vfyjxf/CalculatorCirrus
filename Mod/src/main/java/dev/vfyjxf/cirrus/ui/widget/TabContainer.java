package dev.vfyjxf.cirrus.ui.widget;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.widget.calculator.calc.CalculatorWidget;
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
    private final WidgetGroup<Widget> content;

    public TabContainer() {
        useStyle(
            flexRow()
        );
        var tabs = ColumnWidget.create();
        tabs.useStyle(
            zIndexOf(10),
            flexColumn(),
            paddingTop(15),
            marginRight(-2),
            boxSizingBorderBox()
        );
        tabs.addWidget(new Tab(Icon.calculatorSelect, Icon.calculatorHover, Icon.calculatorUnselect, CalculatorWidget::new));
        tabs.addWidget(new Tab(Icon.databaseSelect, Icon.databaseHover, Icon.databaseUnselect, () -> null));
        tabs.addWidget(new Tab(Icon.contextSelect, Icon.contextHover, Icon.contextUnselect, () -> null));
        tabs.addWidget(new Tab(Icon.settingsSelect, Icon.settingsHover, Icon.settingsUnselect, () -> null));
        addWidget(tabs);
        content = new WidgetGroup<>();
        content.useStyle(
            background(Background.frame),
            padding(8, 8, 10, 8),
            sizeOf(480, 360)
        );
        addWidget(content);
    }

    void selectTab(Tab tab) {
        if (selectedTab != null) selectedTab.selected = false;
        selectedTab = tab;
        tab.selected = true;
        content.clear();
        content.addWidget(tab.contentFactory.get());
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
