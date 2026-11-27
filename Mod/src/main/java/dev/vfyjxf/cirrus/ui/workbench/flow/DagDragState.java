package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cloudlib.api.math.FloatPos;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.TreeMap;

/**
 * Explicit canvas drag state machine: press, 3px promotion threshold, preview and cancel restore.
 */
public final class DagDragState {

    public static final double startThresholdPx = 3.0;
    public static final double minInertiaSpeed = 120.0;
    public static final long inertiaWindowMs = 50L;

    public enum Mode {
        none, panning, nodeDrag, marquee, minimapDrag
    }

    private Mode mode = Mode.none;
    private boolean dragging;
    private double pressScreenX;
    private double pressScreenY;
    private double screenX;
    private double screenY;
    private double velocityX;
    private double velocityY;
    private double lastMoveTimeMs = Double.NaN;
    private @Nullable String anchorStepId;
    private final TreeMap<String, FloatPos> originals = new TreeMap<>();
    private final TreeMap<String, FloatPos> preview = new TreeMap<>();

    public void press(Mode mode, double screenX, double screenY) {
        this.mode = mode == null ? Mode.none : mode;
        this.dragging = false;
        this.pressScreenX = screenX;
        this.pressScreenY = screenY;
        this.screenX = screenX;
        this.screenY = screenY;
        this.velocityX = 0.0;
        this.velocityY = 0.0;
        this.lastMoveTimeMs = Double.NaN;
        this.anchorStepId = null;
        this.originals.clear();
        this.preview.clear();
    }

    public boolean move(double screenX, double screenY, double nowMs) {
        double dx = screenX - this.screenX;
        double dy = screenY - this.screenY;
        if (Double.isFinite(nowMs) && Double.isFinite(this.lastMoveTimeMs) && nowMs > this.lastMoveTimeMs) {
            double seconds = (nowMs - this.lastMoveTimeMs) / 1000.0;
            if (seconds > 0.0) {
                velocityX = dx / seconds;
                velocityY = dy / seconds;
            }
        }
        this.lastMoveTimeMs = nowMs;
        this.screenX = screenX;
        this.screenY = screenY;
        if (!dragging && mode != Mode.none) {
            dragging = Math.abs(screenX - pressScreenX) > startThresholdPx
                    || Math.abs(screenY - pressScreenY) > startThresholdPx;
        }
        return dragging;
    }

    public boolean release() {
        boolean wasActive = mode != Mode.none;
        mode = Mode.none;
        dragging = false;
        anchorStepId = null;
        return wasActive;
    }

    public boolean cancel() {
        boolean wasActive = mode != Mode.none || !originals.isEmpty() || !preview.isEmpty() || dragging;
        mode = Mode.none;
        dragging = false;
        velocityX = 0.0;
        velocityY = 0.0;
        anchorStepId = null;
        preview.clear();
        for (Map.Entry<String, FloatPos> entry : originals.entrySet()) {
            preview.put(entry.getKey(), entry.getValue().copy());
        }
        return wasActive;
    }

    public boolean escape() {
        if (mode == Mode.none) {
            return false;
        }
        cancel();
        return true;
    }

    public Mode mode() {
        return mode;
    }

    public boolean active() {
        return mode != Mode.none;
    }

    public boolean dragging() {
        return dragging;
    }

    public boolean isClick() {
        return DagHitTester.isClick(screenX - pressScreenX, screenY - pressScreenY);
    }

    public double pressScreenX() {
        return pressScreenX;
    }

    public double pressScreenY() {
        return pressScreenY;
    }

    public double screenX() {
        return screenX;
    }

    public double screenY() {
        return screenY;
    }

    public double screenDeltaX() {
        return screenX - pressScreenX;
    }

    public double screenDeltaY() {
        return screenY - pressScreenY;
    }

    public float worldDeltaX(float zoom) {
        return (float) (screenDeltaX() / Math.max(DagViewport.minZoom, zoom));
    }

    public float worldDeltaY(float zoom) {
        return (float) (screenDeltaY() / Math.max(DagViewport.minZoom, zoom));
    }

    public double velocityX() {
        return velocityX;
    }

    public double velocityY() {
        return velocityY;
    }

    public boolean hasInertia() {
        return Math.hypot(velocityX, velocityY) >= minInertiaSpeed;
    }

    public void anchor(@Nullable String stepId) {
        this.anchorStepId = stepId;
    }

    public @Nullable String anchorStepId() {
        return anchorStepId;
    }

    public void captureOriginal(String stepId, @Nullable FloatPos offset) {
        if (stepId == null) {
            return;
        }
        originals.put(stepId, offset == null ? new FloatPos(0.0, 0.0) : offset.copy());
        preview.computeIfAbsent(stepId, key -> offset == null ? new FloatPos(0.0, 0.0) : offset.copy());
    }

    public void previewOffset(String stepId, @Nullable FloatPos offset) {
        if (stepId == null) {
            return;
        }
        originals.putIfAbsent(stepId, new FloatPos(0.0, 0.0));
        preview.put(stepId, offset == null ? new FloatPos(0.0, 0.0) : offset.copy());
    }

    public Map<String, FloatPos> originalOffsets() {
        return new TreeMap<>(originals);
    }

    public Map<String, FloatPos> previewOffsets() {
        return new TreeMap<>(preview);
    }

    public @Nullable FloatPos originalOffset(String stepId) {
        return originals.get(stepId);
    }

    public @Nullable FloatPos previewOffset(String stepId) {
        return preview.get(stepId);
    }

    public DagHitTester.RectF marqueeScreenRect() {
        return DagSelectionMath.marqueeRect(pressScreenX, pressScreenY, screenX, screenY);
    }
}
