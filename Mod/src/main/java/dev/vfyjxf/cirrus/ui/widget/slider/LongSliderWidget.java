package dev.vfyjxf.cirrus.ui.widget.slider;

import dev.vfyjxf.cloudlib.api.event.EventDispatch;
import dev.vfyjxf.cloudlib.api.ui.base.Widget;
import dev.vfyjxf.cloudlib.api.ui.canvas.SceneCanvas;
import dev.vfyjxf.cloudlib.api.ui.texture.SizedTexture;
import dev.vfyjxf.cloudlib.api.ui.texture.VisualTexture;
import org.jspecify.annotations.Nullable;

import java.util.Objects;
import java.util.function.LongConsumer;

public class LongSliderWidget extends Widget {

    public enum Orientation {
        horizontal, vertical
    }

    public record Metrics(
        VisualTexture trackTexture,
        @Nullable VisualTexture fillTexture,
        SizedTexture thumbTexture,
        @Nullable SizedTexture thumbHoverTexture,
        int trackInset,
        int trackThickness,
        int fillThickness,
        int fillMainInset
    ) {

        public Metrics {
            Objects.requireNonNull(trackTexture, "trackTexture");
            Objects.requireNonNull(thumbTexture, "thumbTexture");
        }

        public SizedTexture activeThumbTexture(boolean active) {
            if (active && thumbHoverTexture != null) {
                return thumbHoverTexture;
            }
            return thumbTexture;
        }
    }

    public record Skin(Metrics horizontal, Metrics vertical) {

        public Skin {
            Objects.requireNonNull(horizontal, "horizontal");
            Objects.requireNonNull(vertical, "vertical");
        }

        public Metrics metrics(Orientation orientation) {
            return orientation == Orientation.horizontal ? horizontal : vertical;
        }
    }

    private long value;
    private long minValue;
    private long maxValue = 1;
    private long step = 1;
    private Orientation orientation = Orientation.horizontal;
    private Skin skin = CirrusSliderSkins.scroll();
    private boolean dragging;
    private long soundMinIntervalMs;
    private long lastSoundAtMs = Long.MIN_VALUE;

    @Nullable
    private LongConsumer onValueChanged;

    @Nullable
    private LongConsumer soundPlayer;

    public LongSliderWidget() {
        onMouseClicked((input, context) -> {
            if (!input.isLeftClick()) {
                return EventDispatch.pass;
            }
            dragging = true;
            var relative = input.mouseRelative(this);
            updateFromMouse(relative.x(), relative.y());
            return EventDispatch.consumed;
        });
        onMouseDragged((input, deltaX, deltaY, context) -> {
            if (!dragging) {
                return EventDispatch.pass;
            }
            var relative = input.mouseRelative(this);
            updateFromMouse(relative.x(), relative.y());
            return EventDispatch.consumed;
        });
        onMouseReleased((input, context) -> {
            dragging = false;
            return EventDispatch.pass;
        });
        onMouseLeave((mouseX, mouseY, context) -> dragging = false);
        onHoverTooltip((mouseX, mouseY) -> tooltip().notEmpty() ? tooltip() : null);
    }

    public long value() {
        return value;
    }

    public LongSliderWidget setValue(long value) {
        this.value = normalizeValue(value);
        return this;
    }

    public LongSliderWidget setRange(long minValue, long maxValue) {
        this.minValue = Math.min(minValue, maxValue);
        this.maxValue = Math.max(minValue, maxValue);
        this.value = normalizeValue(this.value);
        return this;
    }

    public LongSliderWidget setStep(long step) {
        this.step = Math.max(1L, step);
        this.value = normalizeValue(this.value);
        return this;
    }

    public LongSliderWidget setOrientation(Orientation orientation) {
        this.orientation = Objects.requireNonNull(orientation, "orientation");
        return this;
    }

    public LongSliderWidget setSkin(Skin skin) {
        this.skin = Objects.requireNonNull(skin, "skin");
        return this;
    }

    public LongSliderWidget onValueChanged(@Nullable LongConsumer onValueChanged) {
        this.onValueChanged = onValueChanged;
        return this;
    }

    public LongSliderWidget setSoundPlayer(@Nullable LongConsumer soundPlayer) {
        this.soundPlayer = soundPlayer;
        return this;
    }

    public LongSliderWidget setSoundMinIntervalMs(long soundMinIntervalMs) {
        this.soundMinIntervalMs = Math.max(0L, soundMinIntervalMs);
        return this;
    }

    @Override
    public boolean dragging() {
        return dragging;
    }

    private void updateFromMouse(double mouseX, double mouseY) {
        Metrics metrics = metrics();
        int thumbSpan = orientation == Orientation.horizontal
                ? metrics.thumbTexture().width()
                : metrics.thumbTexture().height();
        int widgetSpan = orientation == Orientation.horizontal ? width() : height();
        int travel = Math.max(1, widgetSpan - metrics.trackInset() * 2 - thumbSpan);

        double ratio;
        if (orientation == Orientation.horizontal) {
            ratio = (mouseX - metrics.trackInset() - thumbSpan / 2.0d) / travel;
        } else {
            ratio = ((height() - mouseY) - metrics.trackInset() - thumbSpan / 2.0d) / travel;
        }
        ratio = Math.clamp(ratio, 0.0d, 1.0d);

        long nextValue = minValue + Math.round(ratio * (maxValue - minValue));
        long previous = value;
        setValue(nextValue);
        if (value == previous) {
            return;
        }
        playInteractionSound(value);
        if (onValueChanged != null) {
            onValueChanged.accept(value);
        }
    }

    private void playInteractionSound(long value) {
        if (soundPlayer == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (lastSoundAtMs != Long.MIN_VALUE && now - lastSoundAtMs < soundMinIntervalMs) {
            return;
        }
        lastSoundAtMs = now;
        soundPlayer.accept(value);
    }

    private long normalizeValue(long candidate) {
        long clamped = Math.max(minValue, Math.min(maxValue, candidate));
        if (step <= 1L || maxValue <= minValue) {
            return clamped;
        }
        long offset = clamped - minValue;
        long snapped = minValue + Math.round((double) offset / step) * step;
        return Math.max(minValue, Math.min(maxValue, snapped));
    }

    private Metrics metrics() {
        return skin.metrics(orientation);
    }

    private double ratio() {
        if (maxValue <= minValue) {
            return 0.0d;
        }
        return (double) (value - minValue) / (double) (maxValue - minValue);
    }

    private int centeredOffset(int available, int content) {
        return Math.max(0, (available - content) / 2);
    }

    @Override
    protected void renderInternal(SceneCanvas canvas, int mouseX, int mouseY, float partialTicks) {
        super.renderInternal(canvas, mouseX, mouseY, partialTicks);
        if (orientation == Orientation.horizontal) {
            renderHorizontal(canvas);
            return;
        }
        renderVertical(canvas);
    }

    private void renderHorizontal(SceneCanvas canvas) {
        Metrics metrics = metrics();
        SizedTexture thumbTexture = metrics.activeThumbTexture(hovered() || dragging);
        int thumbWidth = thumbTexture.width();
        int thumbHeight = thumbTexture.height();
        int trackY = centeredOffset(height(), metrics.trackThickness());
        int fillY = centeredOffset(height(), metrics.fillThickness());
        int thumbY = centeredOffset(height(), thumbHeight);
        int travel = Math.max(1, width() - metrics.trackInset() * 2 - thumbWidth);
        int fillWidth = Math.max(0, Math.min(travel, (int) Math.round(travel * ratio())));
        int thumbX = metrics.trackInset() + fillWidth;

        canvas.texture(metrics.trackTexture(), 0, trackY, width(), metrics.trackThickness());
        if (metrics.fillTexture() != null && fillWidth > 0) {
            canvas.texture(
                metrics.fillTexture(),
                metrics.trackInset() + metrics.fillMainInset(),
                fillY,
                fillWidth,
                metrics.fillThickness()
            );
        }
        canvas.texture(thumbTexture, thumbX, thumbY, thumbWidth, thumbHeight);
    }

    private void renderVertical(SceneCanvas canvas) {
        Metrics metrics = metrics();
        SizedTexture thumbTexture = metrics.activeThumbTexture(hovered() || dragging);
        int thumbWidth = thumbTexture.width();
        int thumbHeight = thumbTexture.height();
        int trackX = centeredOffset(width(), metrics.trackThickness());
        int fillX = centeredOffset(width(), metrics.fillThickness());
        int thumbX = centeredOffset(width(), thumbWidth);
        int travel = Math.max(1, height() - metrics.trackInset() * 2 - thumbHeight);
        int fillHeight = Math.max(0, Math.min(travel, (int) Math.round(travel * ratio())));
        int fillY = height() - metrics.trackInset() - fillHeight - metrics.fillMainInset();
        int thumbY = height() - metrics.trackInset() - thumbHeight - fillHeight;

        canvas.texture(metrics.trackTexture(), trackX, 0, metrics.trackThickness(), height());
        if (metrics.fillTexture() != null && fillHeight > 0) {
            canvas.texture(metrics.fillTexture(), fillX, fillY, metrics.fillThickness(), fillHeight);
        }
        canvas.texture(thumbTexture, thumbX, thumbY, thumbWidth, thumbHeight);
    }
}
