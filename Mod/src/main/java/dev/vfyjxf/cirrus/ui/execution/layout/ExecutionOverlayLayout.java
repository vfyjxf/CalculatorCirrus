package dev.vfyjxf.cirrus.ui.execution.layout;

import dev.vfyjxf.cirrus.ui.execution.MainPanel;
import dev.vfyjxf.cloudlib.api.ui.overlay.OverlayContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import org.jetbrains.annotations.Nullable;

public final class ExecutionOverlayLayout {
	//region constants

	private static final int panelLeftInset = 2;
	private static final int panelRightInset = 2;
	private static final int panelTopInset = 2;
	private static final int panelBottomInset = 2;
	private static final int jeiLeftInset = 8;
	private static final int jeiTopInset = 18;
	private static final int jeiBottomInset = 28;
	private static final int minimumVisibleHeight = 190;
	//endregion

	private ExecutionOverlayLayout() {
	}

	//region bounds

	public static Rect2i panelBounds(OverlayContext context) {
		return panelBounds(context.screen(), context.width(), context.height());
	}

	public static Rect2i panelBounds(@Nullable Screen screen, int screenWidth, int screenHeight) {
		boolean compactInsets = usesCompactInsets(screen);
		int preferredWidth = MainPanel.preferredPanelWidth(preferredWidth(screen, screenWidth, compactInsets));
		return panelBounds(
				screenWidth,
				screenHeight,
				compactInsets,
				preferredWidth,
				MainPanel.minPanelWidth,
				MainPanel.maxPanelWidth
		);
	}

	public static Rect2i panelBounds(
			int screenWidth,
			int screenHeight,
			boolean compactInsets,
			int preferredWidth,
			int minWidth,
			int maxWidth
	) {
		int leftInset = compactInsets ? jeiLeftInset : panelLeftInset;
		int rightInset = panelRightInset;
		int topInset = compactInsets ? jeiTopInset : panelTopInset;
		int bottomInset = compactInsets ? jeiBottomInset : panelBottomInset;

		int availableWidth = Math.max(0, screenWidth - leftInset - rightInset);
		int clampedMinWidth = Math.max(1, Math.min(minWidth, availableWidth));
		int clampedMaxWidth = Math.max(clampedMinWidth, maxWidth);
		int resolvedWidth = clamp(preferredWidth, clampedMinWidth, clampedMaxWidth);

		int width = Math.min(availableWidth, resolvedWidth);
		if (width <= 0 && availableWidth > 0) {
			width = availableWidth;
		}

		int reservedBottomInset = bottomInset;
		int maxHeightAfterTopInset = Math.max(0, screenHeight - topInset);
		if (maxHeightAfterTopInset <= minimumVisibleHeight) {
			reservedBottomInset = 0;
		} else {
			reservedBottomInset = Math.min(bottomInset, Math.max(0, maxHeightAfterTopInset - minimumVisibleHeight));
		}

		int height = Math.max(0, screenHeight - topInset - reservedBottomInset);
		if (height <= 0 && screenHeight > 0) {
			topInset = Math.min(topInset, Math.max(0, screenHeight - minimumVisibleHeight));
			bottomInset = 0;
			height = Math.max(0, screenHeight - topInset);
		}

		int x = clamp(leftInset, 0, Math.max(0, screenWidth - width));
		int y = clamp(topInset, 0, Math.max(0, screenHeight - height));
		return new Rect2i(x, y, width, height);
	}
	//endregion

	//region screen heuristics

	static boolean usesCompactInsets(Screen screen) {
		return usesCompactInsets(screen == null ? null : screen.getClass());
	}

	static boolean usesCompactInsets(Class<? extends Screen> screenClass) {
		if (screenClass == null) {
			return false;
		}
		if ("mezz.jei.gui.recipes.RecipesGui".equals(screenClass.getName())) {
			return true;
		}
		return AbstractContainerScreen.class.isAssignableFrom(screenClass);
	}

	static int preferredWidth(@Nullable Screen screen, int screenWidth, boolean compactInsets) {
		if (screen instanceof AbstractContainerScreen<?> containerScreen) {
			int guiLeft = containerScreen.getGuiLeft();
			int leftInset = compactInsets ? jeiLeftInset : panelLeftInset;
			return Math.max(0, guiLeft - leftInset);
		}
		int leftInset = compactInsets ? jeiLeftInset : panelLeftInset;
		int rightInset = panelRightInset;
		return Math.max(0, screenWidth - leftInset - rightInset);
	}
	//endregion

	//region internal

	private static int clamp(int value, int min, int max) {
		if (max < min) {
			return min;
		}
		if (value < min) {
			return min;
		}
		return Math.min(value, max);
	}
	//endregion
}
