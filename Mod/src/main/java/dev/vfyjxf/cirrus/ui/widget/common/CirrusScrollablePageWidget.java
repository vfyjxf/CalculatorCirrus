package dev.vfyjxf.cirrus.ui.widget.common;

import dev.vfyjxf.cirrus.ui.CirrusScroll;
import dev.vfyjxf.cirrus.ui.CirrusThemeTextures;
import dev.vfyjxf.cirrus.ui.CirrusUiTheme;
import dev.vfyjxf.cirrus.ui.WorkbenchThemeMode;
import dev.vfyjxf.cloudlib.api.math.FloatPos;
import dev.vfyjxf.cloudlib.api.math.Rect;
import dev.vfyjxf.cloudlib.api.ui.effect.UIEffects;
import dev.vfyjxf.cloudlib.api.ui.scroll.ScrollState;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import org.jspecify.annotations.Nullable;

import static dev.vfyjxf.cloudlib.api.ui.style.UIStyles.paddingRight;

public class CirrusScrollablePageWidget extends CirrusPageWidget {
    private static final int scrollbarWidth = 6;
    private static final int scrollViewportInset = 4;
    private static final int scrollViewportVerticalInset = 4;

    private final ScrollState scrollState;
    private @Nullable WorkbenchThemeMode appliedScrollTheme;
    private VisualTexture appliedThumbTexture;

    public CirrusScrollablePageWidget() {
        this(6, 5);
    }

    public CirrusScrollablePageWidget(int padding, int gap) {
        super(padding, gap);
        CirrusThemeTextures initialTextures = CirrusUiTheme.textures(this);
        this.appliedThumbTexture = initialTextures.scrollThumb();
        this.scrollState = CirrusScroll.longVerticalState(12).smooth(true).smoothSpeed(0.35f)
                .trackTexture(initialTextures.scrollTrack()).thumbTexture(initialTextures.scrollThumb())
                .scrollbarWidth(scrollbarWidth).minThumbSize(14).viewportInset(
                    scrollViewportVerticalInset,
                    scrollViewportInset,
                    scrollViewportVerticalInset,
                    scrollViewportInset
                );
        useEffect(UIEffects.scrollable(scrollState));
        useStyle(paddingRight(padding + scrollbarWidth + 2));
        onMount(
            (scene, context, handle) -> CirrusUiTheme.bindThemeListener(this, handle, () -> syncScrollTextures(false))
        );
    }

    private void syncScrollTextures(boolean force) {
        WorkbenchThemeMode mode = CirrusUiTheme.mode(this);
        if (!force && mode == appliedScrollTheme) {
            return;
        }
        appliedScrollTheme = mode;
        CirrusThemeTextures textures = CirrusUiTheme.textures(this);
        appliedThumbTexture = textures.scrollThumb();
        scrollState.trackTexture(textures.scrollTrack()).thumbTexture(appliedThumbTexture);
    }

    public VisualTexture debugScrollThumbTexture() {
        return appliedThumbTexture;
    }

    public ScrollState scrollState() {
        return scrollState;
    }

    public int scrollbarWidth() {
        return scrollState.scrollbarWidth();
    }

    public int scrollViewportInset() {
        return scrollState.viewportInsetRight();
    }

    public int scrollViewportVerticalInset() {
        return scrollState.viewportInsetTop();
    }

    public Rect scrollViewportSceneBounds() {
        FloatPos origin = localToScene(0, 0);
        int left = Math.min(scrollState.viewportInsetLeft(), Math.max(0, width()));
        int right = Math.min(scrollState.viewportInsetRight(), Math.max(0, width() - left));
        int top = Math.min(scrollState.viewportInsetTop(), Math.max(0, height()));
        int bottom = Math.min(scrollState.viewportInsetBottom(), Math.max(0, height() - top));
        return new Rect(
            (int) origin.x + left,
            (int) origin.y + top,
            Math.max(0, width() - left - right),
            Math.max(0, height() - top - bottom)
        );
    }

    public void resetScroll() {
        scrollState.resetScroll();
        viewport().setContentOffset(0f, 0f);
    }

    public void pageScrollingEnabled(boolean enabled) {
        scrollState.enabled(enabled);
        if (!enabled) {
            viewport().setContentOffset(0f, 0f);
        }
    }
}
