package dev.vfyjxf.cirrus.ui.execution.layout;

import dev.vfyjxf.cirrus.ui.execution.MainPanel;
import dev.vfyjxf.cloudlib.api.ui.overlay.OverlayContext;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import org.jspecify.annotations.Nullable;

public final class ExecutionOverlayLayout {
    //region constants

    private static final int panelLeftInset = 2;
    private static final int panelRightInset = 2;
    private static final int panelTopInset = 2;
    private static final int panelBottomInset = 2;
    // Mirrors JEI BookmarkOverlay: BORDER_MARGIN=6, BUTTON_SIZE=20, INNER_PADDING=2.
    public static final int jeiLeftInset = 6;
    public static final int jeiRightGap = 6;
    public static final int jeiTopInset = 6;
    public static final int jeiBottomInset = 28;
    private static final int minimumVisibleHeight = 160;
    //endregion

    private ExecutionOverlayLayout() {}

    //region bounds

    public static Rect2i panelBounds(OverlayContext context) {
        return panelBounds(context.screen(), context.width(), context.height());
    }

    public static Rect2i panelBounds(@Nullable Screen screen, int screenWidth, int screenHeight) {
        boolean compactInsets = usesCompactInsets(screen);
        int stripLimit = leftStripLimit(screen, screenWidth, compactInsets);
        int preferredWidth = MainPanel.preferredPanelWidth(preferredWidth(screen, screenWidth, compactInsets));
        return panelBoundsForStrip(
            screenWidth,
            screenHeight,
            stripLimit,
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
        return panelBoundsForStrip(
            screenWidth,
            screenHeight,
            screenWidth,
            compactInsets,
            preferredWidth,
            minWidth,
            maxWidth
        );
    }

    public static Rect2i panelBoundsForStrip(
        int screenWidth,
        int screenHeight,
        int stripLimitWidth,
        boolean compactInsets,
        int preferredWidth,
        int minWidth,
        int maxWidth
    ) {
        int leftInset = compactInsets ? jeiLeftInset : panelLeftInset;
        int rightInset = compactInsets ? jeiRightGap : panelRightInset;
        int topInset = compactInsets ? jeiTopInset : panelTopInset;
        int bottomInset = compactInsets ? jeiBottomInset : panelBottomInset;

        int boundedStrip = Math.max(0, Math.min(screenWidth, stripLimitWidth));
        int availableWidth = Math.max(0, boundedStrip - leftInset - rightInset);
        int clampedMinWidth = Math.max(0, Math.min(minWidth, availableWidth));
        int clampedMaxWidth = Math.max(clampedMinWidth, Math.min(maxWidth, availableWidth));
        int resolvedWidth = clamp(preferredWidth, clampedMinWidth, clampedMaxWidth);

        int width = Math.min(availableWidth, resolvedWidth);
        if (width <= 0 && availableWidth > 0) {
            width = availableWidth;
        }

        int reservedBottomInset = bottomInset;
        int maxHeightAfterTopInset = Math.max(0, screenHeight - topInset);
        if (maxHeightAfterTopInset <= minimumVisibleHeight) {
            reservedBottomInset = Math.min(bottomInset, Math.max(0, maxHeightAfterTopInset - 80));
        } else {
            reservedBottomInset = Math.min(bottomInset, Math.max(0, maxHeightAfterTopInset - minimumVisibleHeight));
        }

        int height = Math.max(0, screenHeight - topInset - reservedBottomInset);
        if (height <= 0 && screenHeight > 0) {
            topInset = Math.min(topInset, Math.max(0, screenHeight - minimumVisibleHeight));
            height = Math.max(0, screenHeight - topInset);
        }

        int x = clamp(leftInset, 0, Math.max(0, screenWidth - width));
        int y = clamp(topInset, 0, Math.max(0, screenHeight - height));
        return new Rect2i(x, y, width, height);
    }

    /**
     * Returns the JEI exclusion area covering the occupied left bookmark content strip above the bottom button bar.
     */
    public static Rect2i jeiExclusionBounds(@Nullable Screen screen, Rect2i panelBounds) {
        if (panelBounds.getWidth() <= 0 || panelBounds.getHeight() <= 0) {
            return panelBounds;
        }
        int exclusionRight = panelBounds.getX() + panelBounds.getWidth() + jeiRightGap;
        if (screen instanceof AbstractContainerScreen<?> containerScreen && containerScreen.getGuiLeft() > 0) {
            exclusionRight = Math.max(exclusionRight, containerScreen.getGuiLeft());
        }
        int exclusionBottom = panelBounds.getY() + panelBounds.getHeight();
        return new Rect2i(0, 0, exclusionRight, exclusionBottom);
    }
    //endregion

    //region screen heuristics

    static boolean usesCompactInsets(@Nullable Screen screen) {
        return usesCompactInsets(screen == null ? null : screen.getClass());
    }

    static boolean usesCompactInsets(@Nullable Class<? extends Screen> screenClass) {
        if (screenClass == null) {
            return false;
        }
        if ("mezz.jei.gui.recipes.RecipesGui".equals(screenClass.getName())) {
            return true;
        }
        return AbstractContainerScreen.class.isAssignableFrom(screenClass);
    }

    static int leftStripLimit(@Nullable Screen screen, int screenWidth, boolean compactInsets) {
        if (screen instanceof AbstractContainerScreen<?> containerScreen) {
            int guiLeft = containerScreen.getGuiLeft();
            if (guiLeft > 0) {
                return Math.min(screenWidth, guiLeft);
            }
        }
        return screenWidth;
    }

    static int preferredWidth(@Nullable Screen screen, int screenWidth, boolean compactInsets) {
        int stripLimit = leftStripLimit(screen, screenWidth, compactInsets);
        int leftInset = compactInsets ? jeiLeftInset : panelLeftInset;
        int rightInset = compactInsets ? jeiRightGap : panelRightInset;
        return Math.max(0, stripLimit - leftInset - rightInset);
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
