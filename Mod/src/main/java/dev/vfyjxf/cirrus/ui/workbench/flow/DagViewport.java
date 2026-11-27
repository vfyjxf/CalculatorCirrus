package dev.vfyjxf.cirrus.ui.workbench.flow;

import dev.vfyjxf.cirrus.ui.workbench.layout.DagNodeBox;

/**
 * Camera and zoom state of the spatial DAG canvas; {@code camX}/{@code camY} hold the world point at the viewport origin.
 */
public final class DagViewport {

    public static final float minZoom = 0.25f;
    public static final float maxZoom = 2.40f;
    public static final float defaultZoom = 1.0f;
    public static final float defaultPadding = 12.0f;
    public static final float wheelStep = 1.12f;

    private float camX;
    private float camY;
    private float zoom = defaultZoom;

    public DagViewport() {}

    public DagViewport(float camX, float camY, float zoom) {
        this.camX = camX;
        this.camY = camY;
        this.zoom = clampZoom(zoom);
    }

    public static float clampZoom(float zoom) {
        if (!Float.isFinite(zoom)) {
            return defaultZoom;
        }
        return Math.max(minZoom, Math.min(maxZoom, zoom));
    }

    public float camX() {
        return camX;
    }

    public float camY() {
        return camY;
    }

    public float zoom() {
        return zoom;
    }

    public void setCamera(float camX, float camY) {
        this.camX = camX;
        this.camY = camY;
    }

    public void setZoom(float zoom) {
        this.zoom = clampZoom(zoom);
    }

    public double worldToScreenX(double worldX) {
        return (worldX - camX) * (double) zoom;
    }

    public double worldToScreenY(double worldY) {
        return (worldY - camY) * (double) zoom;
    }

    public double screenToWorldX(double screenX) {
        return screenX / (double) zoom + camX;
    }

    public double screenToWorldY(double screenY) {
        return screenY / (double) zoom + camY;
    }

    public float[] worldToScreen(float worldX, float worldY) {
        return new float[]{(float) worldToScreenX(worldX), (float) worldToScreenY(worldY)};
    }

    public float[] screenToWorld(float screenX, float screenY) {
        return new float[]{(float) screenToWorldX(screenX), (float) screenToWorldY(screenY)};
    }

    public void panScreen(double deltaX, double deltaY) {
        if (!Double.isFinite(deltaX) || !Double.isFinite(deltaY)) {
            return;
        }
        camX -= (float) (deltaX / zoom);
        camY -= (float) (deltaY / zoom);
    }

    /**
     * Zooms around a screen anchor so the world point under the pointer stays fixed.
     */
    public void zoomAt(double mouseX, double mouseY, double factor) {
        if (!Double.isFinite(factor) || factor <= 0.0) {
            return;
        }
        double worldX = screenToWorldX(mouseX);
        double worldY = screenToWorldY(mouseY);
        setZoom((float) (zoom * factor));
        camX = (float) (worldX - mouseX / zoom);
        camY = (float) (worldY - mouseY / zoom);
    }

    public void zoomStep(double mouseX, double mouseY, boolean zoomIn) {
        zoomAt(mouseX, mouseY, zoomIn ? wheelStep : 1.0 / wheelStep);
    }

    public void fit(
        float contentMinX,
        float contentMinY,
        float contentMaxX,
        float contentMaxY,
        int viewWidth,
        int viewHeight,
        float padding
    ) {
        float width = Math.max(1.0f, contentMaxX - contentMinX);
        float height = Math.max(1.0f, contentMaxY - contentMinY);
        float usableW = Math.max(1.0f, viewWidth - padding * 2.0f);
        float usableH = Math.max(1.0f, viewHeight - padding * 2.0f);
        setZoom(Math.min(usableW / width, usableH / height));
        centerOnWorld((contentMinX + contentMaxX) * 0.5f, (contentMinY + contentMaxY) * 0.5f, viewWidth, viewHeight);
    }

    public void centerOnWorld(double worldX, double worldY, int viewWidth, int viewHeight) {
        camX = (float) (worldX - viewWidth * 0.5 / zoom);
        camY = (float) (worldY - viewHeight * 0.5 / zoom);
    }

    public void focusOn(DagNodeBox node, int viewWidth, int viewHeight) {
        if (node == null) {
            return;
        }
        centerOnWorld(node.centerX(), node.centerY(), viewWidth, viewHeight);
    }

    /**
     * Recenters on a normalized point inside the given content bounds, used by the minimap viewport frame.
     */
    public void centerOnNormalized(
        double u,
        double v,
        float contentMinX,
        float contentMinY,
        float contentMaxX,
        float contentMaxY,
        int viewWidth,
        int viewHeight
    ) {
        double worldX = contentMinX + clamp01(u) * (contentMaxX - contentMinX);
        double worldY = contentMinY + clamp01(v) * (contentMaxY - contentMinY);
        centerOnWorld(worldX, worldY, viewWidth, viewHeight);
    }

    /**
     * Recenters the camera from a minimap press; the minimap maps content bounds onto its padded box.
     */
    public void centerOnMinimapPoint(
        double localX,
        double localY,
        int minimapWidth,
        int minimapHeight,
        float contentMinX,
        float contentMinY,
        float contentMaxX,
        float contentMaxY,
        int viewWidth,
        int viewHeight,
        float padding
    ) {
        double scale = minimapContentScale(
            minimapWidth,
            minimapHeight,
            contentMinX,
            contentMinY,
            contentMaxX,
            contentMaxY,
            padding
        );
        double contentWidth = Math.max(1.0, contentMaxX - contentMinX);
        double contentHeight = Math.max(1.0, contentMaxY - contentMinY);
        double u = (localX - padding) / (contentWidth * scale);
        double v = (localY - padding) / (contentHeight * scale);
        centerOnNormalized(u, v, contentMinX, contentMinY, contentMaxX, contentMaxY, viewWidth, viewHeight);
    }

    public static double minimapContentScale(
        int minimapWidth,
        int minimapHeight,
        float contentMinX,
        float contentMinY,
        float contentMaxX,
        float contentMaxY,
        float padding
    ) {
        double usableW = Math.max(1.0, minimapWidth - padding * 2.0);
        double usableH = Math.max(1.0, minimapHeight - padding * 2.0);
        double contentW = Math.max(1.0, contentMaxX - contentMinX);
        double contentH = Math.max(1.0, contentMaxY - contentMinY);
        return Math.min(usableW / contentW, usableH / contentH);
    }

    public float visibleWorldX0() {
        return camX;
    }

    public float visibleWorldY0() {
        return camY;
    }

    public float visibleWorldX1(int viewWidth) {
        return (float) (camX + viewWidth / (double) zoom);
    }

    public float visibleWorldY1(int viewHeight) {
        return (float) (camY + viewHeight / (double) zoom);
    }

    private static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
