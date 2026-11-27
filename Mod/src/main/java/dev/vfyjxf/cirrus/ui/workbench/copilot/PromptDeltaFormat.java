package dev.vfyjxf.cirrus.ui.workbench.copilot;

import dev.vfyjxf.cirrus.api.planning.cost.CostVector;
import dev.vfyjxf.cirrus.api.planning.human.GuidanceScope;
import dev.vfyjxf.cirrus.ui.CirrusLangs;
import dev.vfyjxf.cloudlib.data.lang.LangEntry;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Pure text formats for decision cards and plan comparison: cost delta tags, signature diffs, scope labels and leverage tiers.
 */
public final class PromptDeltaFormat {

    public static final int maxChips = 3;
    public static final double chipEpsilon = 0.05;
    public static final float highLeverage = 0.75f;
    public static final int leverageCells = 10;

    private PromptDeltaFormat() {}

    public record Chip(ParetoGeometry.Axis axis, double signedValue, String styleClass, Component label) {
        public Chip {
            Objects.requireNonNull(axis, "axis");
            Objects.requireNonNull(styleClass, "styleClass");
            Objects.requireNonNull(label, "label");
        }
    }

    public static List<Chip> deltaChips(CostVector delta) {
        Objects.requireNonNull(delta, "delta");
        List<Chip> chips = new ArrayList<>(5);
        for (ParetoGeometry.Axis axis : ParetoGeometry.Axis.values()) {
            double magnitude = axis.valueOf(delta);
            if (magnitude < chipEpsilon) {
                continue;
            }
            double signed = axis == ParetoGeometry.Axis.capital ? magnitude : -magnitude;
            chips.add(new Chip(axis, signed, styleClass(axis), chipLabel(axis, signed)));
        }
        chips.sort(Comparator.comparingDouble((Chip chip) -> Math.abs(chip.signedValue())).reversed());
        return List.copyOf(chips.subList(0, Math.min(maxChips, chips.size())));
    }

    public static Component chipLabel(ParetoGeometry.Axis axis, double signedValue) {
        String amount = formatSigned(signedValue, axis.inSeconds() ? 1 : 0);
        return Component.literal(axis.shortLabel().getString() + " " + amount + (axis.inSeconds() ? "s" : ""));
    }

    public static double[] signedDelta(CostVector from, CostVector to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        return new double[]{to.humanActiveSeconds() - from.humanActiveSeconds(),
                to.passiveWaitSeconds() - from.passiveWaitSeconds(),
                to.machineSetupCapital() - from.machineSetupCapital(), to.materialScarcity() - from.materialScarcity(),
                to.cognitiveFriction() - from.cognitiveFriction()};
    }

    public static String formatSigned(double value, int decimals) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        BigDecimal rounded = BigDecimal.valueOf(value).setScale(decimals, RoundingMode.HALF_UP);
        if (rounded.signum() == 0) {
            return "0";
        }
        String plain = rounded.stripTrailingZeros().toPlainString();
        return rounded.signum() > 0 ? "+" + plain : plain;
    }

    public static String signedDeltaText(double value, boolean inSeconds) {
        String formatted = formatSigned(value, inSeconds ? 1 : 0);
        return inSeconds ? formatted + "s" : formatted;
    }

    public static String styleClass(ParetoGeometry.Axis axis) {
        Objects.requireNonNull(axis, "axis");
        return switch (axis) {
            case human -> "h";
            case passive -> "p";
            case capital -> "k";
            case scarcity -> "s";
            case cognitive -> "c";
        };
    }

    public static float leverageFraction(double leverageScore) {
        if (!Double.isFinite(leverageScore)) {
            return 0f;
        }
        return (float) Math.max(0.0, Math.min(1.0, leverageScore));
    }

    public static int leverageCells(double leverageScore) {
        return Math.round(leverageFraction(leverageScore) * leverageCells);
    }

    public static boolean isHighLeverage(double leverageScore) {
        return leverageFraction(leverageScore) >= highLeverage;
    }

    public static @Nullable LangEntry scopeLabel(GuidanceScope scope) {
        if (scope == null) {
            return null;
        }
        return switch (scope) {
            case session -> CirrusLangs.Workbench.Copilot.scopeSession;
            case worldSave -> CirrusLangs.Workbench.Copilot.scopeWorld;
            case modpackDefault, queryLocal -> null;
        };
    }

    public static Component scopeTag(GuidanceScope scope) {
        LangEntry label = scopeLabel(scope);
        if (label != null) {
            return label.get();
        }
        return Component.literal(scope == null ? "-" : scope.name());
    }
}
