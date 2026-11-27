package dev.vfyjxf.cirrus.ui.widget;

import dev.vfyjxf.cirrus.ui.CirrusTextures.Icon;
import dev.vfyjxf.cirrus.ui.widget.common.CirrusButtonWidget;
import dev.vfyjxf.cloudlib.api.ui.base.CompositeWidget;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntConsumer;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.alignItemsCenter;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.flexRow;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.gap;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.heightOf;
import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.sizeOf;

/**
 * A horizontal paged button bar.
 * <p>
 * Buttons have variable widths and are distributed across pages.
 * Left/right arrow buttons allow navigating between pages.
 * Button backgrounds use {@link CirrusButtonWidget}'s standard button chrome.
 * Arrow icons are configurable with defaults from {@link Icon.Small}.
 */
public class PagedButtonBar extends CompositeWidget<Widget> {

    //region Metrics
    private static final int arrowButtonWidth = 13;
    private static final int arrowButtonHeight = 15;
    private static final int buttonSpacing = 1;
    //endregion

    //region State
    private final List<PagedButton> buttons = new ArrayList<>();
    private final ArrowButton leftArrow;
    private final ArrowButton rightArrow;
    private int currentPage = 0;
    private @Nullable IntConsumer onPageChanged;
    private int lastLayoutWidth = -1;
    //endregion

    //region Init
    public PagedButtonBar(int barHeight) {
        this(
            barHeight,
            Icon.Small.arrowLeft,
            Icon.Small.arrowLeftSelected,
            Icon.Small.arrowRight,
            Icon.Small.arrowRightSelected
        );
    }

    public PagedButtonBar(
        int barHeight,
        SizedTexture leftIcon,
        SizedTexture leftIconHover,
        SizedTexture rightIcon,
        SizedTexture rightIconHover
    ) {
        useStyle(flexRow(), alignItemsCenter(), heightOf(barHeight), gap(buttonSpacing));

        leftArrow = new ArrowButton(leftIcon, leftIconHover, true);
        rightArrow = new ArrowButton(rightIcon, rightIconHover, false);

        onRender((canvas, mouseX, mouseY, partialTicks, self, ctx) -> {
            if (width() != lastLayoutWidth) {
                lastLayoutWidth = width();
                rebuildBar();
            }
        });
    }
    //endregion

    //region Public API

    public PagedButtonBar addButton(PagedButton button) {
        buttons.add(button);
        return this;
    }

    public PagedButtonBar setOnPageChanged(@Nullable IntConsumer onPageChanged) {
        this.onPageChanged = onPageChanged;
        return this;
    }

    public int currentPage() {
        return currentPage;
    }

    public int pageCount() {
        return computePages().size();
    }

    public void setPage(int page) {
        var pages = computePages();
        if (pages.isEmpty()) return;
        currentPage = Math.clamp(page, 0, pages.size() - 1);
        rebuildBar();
        if (onPageChanged != null) onPageChanged.accept(currentPage);
    }

    public void rebuild() {
        var pages = computePages();
        if (!pages.isEmpty() && currentPage >= pages.size()) {
            currentPage = pages.size() - 1;
        }
        rebuildBar();
    }

    //endregion

    //region Layout

    private List<List<PagedButton>> computePages() {
        int available = width();
        // First pass: check if all buttons fit on one page
        int totalWidth = 0;
        for (var btn : buttons) {
            totalWidth += btn.buttonWidth;
        }
        totalWidth += Math.max(0, buttons.size() - 1) * buttonSpacing;

        if (totalWidth <= available) {
            // Everything fits on one page
            return buttons.isEmpty() ? List.of() : List.of(new ArrayList<>(buttons));
        }

        // Need arrows: available space for buttons is reduced
        int arrowSpace = (arrowButtonWidth + buttonSpacing) * 2;
        int pageWidth = available - arrowSpace;

        List<List<PagedButton>> pages = new ArrayList<>();
        List<PagedButton> currentPageButtons = new ArrayList<>();
        int usedWidth = 0;

        for (var btn : buttons) {
            int needed = btn.buttonWidth + (currentPageButtons.isEmpty() ? 0 : buttonSpacing);
            if (!currentPageButtons.isEmpty() && usedWidth + needed > pageWidth) {
                pages.add(currentPageButtons);
                currentPageButtons = new ArrayList<>();
                usedWidth = 0;
                needed = btn.buttonWidth;
            }
            currentPageButtons.add(btn);
            usedWidth += needed;
        }
        if (!currentPageButtons.isEmpty()) {
            pages.add(currentPageButtons);
        }

        return pages;
    }

    private void rebuildBar() {
        clear();
        var pages = computePages();
        if (pages.isEmpty()) return;

        boolean multiPage = pages.size() > 1;
        int page = Math.clamp(currentPage, 0, pages.size() - 1);
        List<PagedButton> visible = pages.get(page);

        if (multiPage) {
            leftArrow.setEnabled(page > 0);
            addWidget(leftArrow);
        }

        for (var btn : visible) {
            addWidget(btn);
        }

        if (multiPage) {
            rightArrow.setEnabled(page < pages.size() - 1);
            addWidget(rightArrow);
        }
    }

    //endregion

    //region ArrowButton

    private class ArrowButton extends CirrusButtonWidget {

        private final boolean isLeft;

        ArrowButton(SizedTexture icon, SizedTexture iconHover, boolean isLeft) {
            super(arrowButtonWidth, arrowButtonHeight);
            this.isLeft = isLeft;
            icon(icon, iconHover).clickSound(true).onPress(() -> {
                if (this.isLeft) {
                    setPage(currentPage - 1);
                } else {
                    setPage(currentPage + 1);
                }
            });
            useStyle(sizeOf(arrowButtonWidth, arrowButtonHeight));
        }

        void setEnabled(boolean enabled) {
            enabled(enabled);
        }
    }

    //endregion

    //region PagedButton

    public static class PagedButton extends CirrusButtonWidget {

        private final int buttonWidth;
        private boolean selected = false;

        public PagedButton(int width, int height) {
            super(width, height);
            this.buttonWidth = width;
            useStyle(sizeOf(width, height));
            selected(this::selected);
        }

        public PagedButton setIcon(@Nullable SizedTexture icon, @Nullable SizedTexture iconHover) {
            icon(icon, iconHover);
            return this;
        }

        public PagedButton setLabel(@Nullable String label, int color) {
            label(label == null ? null : Component.literal(label));
            textColor(color);
            return this;
        }

        public PagedButton setSelected(boolean selected) {
            this.selected = selected;
            return this;
        }

        public boolean selected() {
            return selected;
        }

        public PagedButton setOnClick(@Nullable Runnable onClick) {
            onPress(onClick);
            clickSound(onClick != null);
            return this;
        }
    }

    //endregion
}
