package dev.vfyjxf.cirrus.ui.widget;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Background;
import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.CalculatorSettingsPage;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.DatabasePage;
import dev.vfyjxf.cirrus.ui.widget.calculator.page.RulesPage;
import dev.vfyjxf.cirrus.ui.widget.omnicalc.calculator.OmniCalculatorView;
import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Scene;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.base.WidgetGroup;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import dev.vfyjxf.cloudlib.ui.widget.ColumnWidget;
import dev.vfyjxf.cloudlib.ui.widget.ImageWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;

import java.util.function.Supplier;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.background;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.boxSizingBorderBox;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexColumn;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexGrow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.marginRight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minHeight;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.minWidth;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.padding;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingLeft;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingTop;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.zIndexOf;

public class TabContainer extends CompositeWidget<Widget> {

	private Tab selectedTab;
	private final Tab calculatorTab;
	private final Tab databaseTab;
	private final Tab rulesTab;
	private final Tab settingsTab;
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
		calculatorTab = new Tab(Icon.calculatorSelect, Icon.calculatorHover, Icon.calculatorUnselect, OmniCalculatorView::new);
		tabs.addWidget(calculatorTab);
		databaseTab = new Tab(Icon.databaseSelect, Icon.databaseHover, Icon.databaseUnselect, DatabasePage::new);
		rulesTab = new Tab(Icon.contextSelect, Icon.contextHover, Icon.contextUnselect, RulesPage::new);
		settingsTab = new Tab(Icon.settingsSelect, Icon.settingsHover, Icon.settingsUnselect, CalculatorSettingsPage::new);
		tabs.addWidget(databaseTab);
		tabs.addWidget(rulesTab);
		tabs.addWidget(settingsTab);
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

    public void releaseCachedContent() {
        calculatorTab.releaseContent();
        databaseTab.releaseContent();
        rulesTab.releaseContent();
        settingsTab.releaseContent();
        selectedTab = null;
        content.clear();
    }

	void selectTab(Tab tab) {
        if (selectedTab == tab && (content.children().isEmpty() || content.children().getFirst() == tab.cachedContent)) {
            return;
        }
		if (selectedTab != null) selectedTab.selected = false;
		detachCurrentContentForReuse();
		selectedTab = tab;
		tab.selected = true;
		Widget currentContent = tab.content();
        currentContent.useStyle(flexGrow(1), minWidth(0), minHeight(0));
        content.addWidget(currentContent);
    }

	private void detachCurrentContentForReuse() {
		Widget currentContent = content.children().isEmpty() ? null : content.children().getFirst();
		if (currentContent == null) return;
		Scene currentScene = lifecycle().mounted() && currentContent.lifecycle().mounted() ? scene() : null;
		content.remove(currentContent);
		if (currentScene != null && currentContent.lifecycle().unmounted()) {
			currentScene.reuse(currentContent);
		}
	}

	private class Tab extends CompositeWidget<ImageWidget> {

		boolean selected = false;
		private final Supplier<Widget> contentFactory;
		private Widget cachedContent;

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

		private Widget content() {
			if (cachedContent == null) {
				cachedContent = contentFactory.get();
			}
			return cachedContent;
		}

        private void releaseContent() {
            Widget content = cachedContent;
            if (content instanceof OmniCalculatorView calculatorView) {
                calculatorView.releaseCalculationState();
            }
            cachedContent = null;
        }

	}

}
