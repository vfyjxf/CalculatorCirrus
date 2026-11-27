package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.plan.ProductionPlan;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cloudlib.api.math.Rect;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure geometry of the Pareto scatter plot: cost-axis values, coordinate mapping, frontier line order and hit tests.
 */
public final class ParetoGeometry {

    public static final double hitTolerancePx = 6.0;
    public static final int plotMargin = 10;

    private ParetoGeometry() {}

    public enum Axis {
        human, passive, capital, scarcity, cognitive;

        public double valueOf(CostVector cost) {
            Objects.requireNonNull(cost, "cost");
            return switch (this) {
                case human -> cost.humanActiveSeconds();
                case passive -> cost.passiveWaitSeconds();
                case capital -> cost.machineSetupCapital();
                case scarcity -> cost.materialScarcity();
                case cognitive -> cost.cognitiveFriction();
            };
        }

        public String letter() {
            return switch (this) {
                case human -> "H";
                case passive -> "P";
                case capital -> "K";
                case scarcity -> "S";
                case cognitive -> "C";
            };
        }

        public Component shortLabel() {
            return switch (this) {
                case human -> CirrusLangs.Workbench.Axis.work.get();
                case passive -> CirrusLangs.Workbench.Axis.waitTime.get();
                case capital -> CirrusLangs.Workbench.Axis.equip.get();
                case scarcity -> CirrusLangs.Workbench.Axis.stock.get();
                case cognitive -> CirrusLangs.Workbench.Axis.steps.get();
            };
        }

        public boolean inSeconds() {
            return this == human || this == passive;
        }

        public Axis next() {
            Axis[] all = values();
            return all[(ordinal() + 1) % all.length];
        }

        public Axis previous() {
            Axis[] all = values();
            return all[(ordinal() + all.length - 1) % all.length];
        }
    }

    public record Extent(double minX, double maxX, double minY, double maxY) {

        public Extent {
            if (!Double.isFinite(minX) || !Double.isFinite(maxX)) {
                minX = 0.0;
                maxX = 1.0;
            }
            if (!Double.isFinite(minY) || !Double.isFinite(maxY)) {
                minY = 0.0;
                maxY = 1.0;
            }
            if (maxX <= minX) {
                minX -= 1.0;
                maxX += 1.0;
            }
            if (maxY <= minY) {
                minY -= 1.0;
                maxY += 1.0;
            }
        }

        public static Extent of(List<ProductionPlan> plans, Axis xAxis, Axis yAxis) {
            Objects.requireNonNull(plans, "plans");
            Objects.requireNonNull(xAxis, "xAxis");
            Objects.requireNonNull(yAxis, "yAxis");
            double minX = Double.POSITIVE_INFINITY;
            double maxX = Double.NEGATIVE_INFINITY;
            double minY = Double.POSITIVE_INFINITY;
            double maxY = Double.NEGATIVE_INFINITY;
            for (ProductionPlan plan : plans) {
                if (plan == null) {
                    continue;
                }
                CostVector cost = plan.totalCost();
                double x = xAxis.valueOf(cost);
                double y = yAxis.valueOf(cost);
                minX = Math.min(minX, x);
                maxX = Math.max(maxX, x);
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
            if (!Double.isFinite(minX) || !Double.isFinite(minY)) {
                return new Extent(0.0, 1.0, 0.0, 1.0);
            }
            return new Extent(minX, maxX, minY, maxY);
        }

        public double spanX() {
            return maxX - minX;
        }

        public double spanY() {
            return maxY - minY;
        }

        public Extent padded() {
            double padX = spanX() * 0.08;
            double padY = spanY() * 0.08;
            return new Extent(minX - padX, maxX + padX, minY - padY, maxY + padY);
        }
    }

    public record Point(String planId, double x, double y, int px, int py) {
        public Point {
            Objects.requireNonNull(planId, "planId");
        }
    }

    public static Rect innerPlot(Rect frame) {
        Objects.requireNonNull(frame, "frame");
        int inset = Math.min(plotMargin, Math.max(0, Math.min(frame.width(), frame.height()) / 4));
        int width = Math.max(1, frame.width() - inset * 2);
        int height = Math.max(1, frame.height() - inset * 2);
        return new Rect(frame.x() + inset, frame.y() + inset, width, height);
    }

    public static Point project(Rect frame, Extent extent, ProductionPlan plan, Axis xAxis, Axis yAxis) {
        Objects.requireNonNull(plan, "plan");
        return project(frame, extent, plan.planId(), xAxis.valueOf(plan.totalCost()), yAxis.valueOf(plan.totalCost()));
    }

    public static Point project(Rect frame, Extent extent, String planId, double x, double y) {
        Objects.requireNonNull(frame, "frame");
        Objects.requireNonNull(extent, "extent");
        Rect inner = innerPlot(frame);
        double fx = clamp01((x - extent.minX()) / extent.spanX());
        double fy = clamp01((y - extent.minY()) / extent.spanY());
        int px = inner.x() + (int) Math.round(fx * Math.max(0, inner.width() - 1));
        int py = inner.y() + (int) Math.round((1.0 - fy) * Math.max(0, inner.height() - 1));
        return new Point(planId, x, y, px, py);
    }

    public static List<Point> projectAll(
        Rect frame,
        Extent extent,
        List<ProductionPlan> plans,
        Axis xAxis,
        Axis yAxis
    ) {
        Objects.requireNonNull(plans, "plans");
        List<Point> points = new ArrayList<>(plans.size());
        for (ProductionPlan plan : plans) {
            if (plan != null) {
                points.add(project(frame, extent, plan, xAxis, yAxis));
            }
        }
        return List.copyOf(points);
    }

    public static List<Point> frontierOrder(List<Point> points) {
        Objects.requireNonNull(points, "points");
        List<Point> ordered = new ArrayList<>(points);
        ordered.sort((a, b) -> {
            int byX = Double.compare(a.x(), b.x());
            if (byX != 0) {
                return byX;
            }
            int byY = Double.compare(a.y(), b.y());
            if (byY != 0) {
                return byY;
            }
            return a.planId().compareTo(b.planId());
        });
        return List.copyOf(ordered);
    }

    public static double distance(Point point, double mouseX, double mouseY) {
        Objects.requireNonNull(point, "point");
        double dx = point.px() - mouseX;
        double dy = point.py() - mouseY;
        return Math.sqrt(dx * dx + dy * dy);
    }

    public static @Nullable Point nearest(List<Point> points, double mouseX, double mouseY) {
        return nearest(points, mouseX, mouseY, hitTolerancePx);
    }

    public static @Nullable Point nearest(List<Point> points, double mouseX, double mouseY, double tolerance) {
        Objects.requireNonNull(points, "points");
        Point best = null;
        double bestDistance = tolerance;
        for (Point point : points) {
            double distance = distance(point, mouseX, mouseY);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = point;
            }
        }
        return best;
    }

    public static double clamp01(double value) {
        if (!Double.isFinite(value)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, value));
    }
}
