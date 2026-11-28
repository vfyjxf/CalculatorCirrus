package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.content.complex.DurabilityContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionPlugin;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.eclipse.collections.api.list.ImmutableList;
import org.jspecify.annotations.Nullable;

/**
 * Collapsed net diff for one stable {@link SlotAddress} between original and projected states.
 */
public record SlotDiff(
    SlotAddress address,
    DiffStatus status,
    @Nullable RecipeContent before,
    @Nullable RecipeContent after,
    @Nullable SlotAddress movedFrom,
    ImmutableList<Namespace> touchedByEffects,
    ImmutableList<ProjectionPlugin> touchedByPlugins
) {
    public enum DiffStatus {
        unchanged,
        added,
        removed,
        adjusted,
        transformed,
        moved;

        public boolean isChanged() {
            return this != unchanged;
        }
    }

    public SlotDiff {
        Checks.checkNotNull(address, "address");
        Checks.checkNotNull(status, "status");
        Checks.checkNotNull(touchedByEffects, "touchedByEffects");
        Checks.checkNotNull(touchedByPlugins, "touchedByPlugins");
    }

    public SlotChannel channel() {
        return address.channel();
    }

    public boolean isChanged() {
        return status.isChanged();
    }

    public boolean isGridCell() {
        return address.isGridCell();
    }

    public long beforeAmount() {
        return before == null ? 0L : before.amount();
    }

    public long afterAmount() {
        return after == null ? 0L : after.amount();
    }

    public long amountDelta() {
        return afterAmount() - beforeAmount();
    }

    public @Nullable Double amountFactor() {
        long b = beforeAmount();
        long a = afterAmount();
        if (b <= 0L || a <= 0L) {
            return null;
        }
        return (double) a / (double) b;
    }

    /**
     * Compact UI delta badge string (e.g. {@code ×2}, {@code +3}, {@code -1}, {@code +}, {@code ×}, {@code ⇄}).
     */
    public String deltaBadge() {
        return switch (status) {
            case unchanged -> "";
            case added -> after == null ? "+" : "+" + Math.max(1L, after.amount());
            case removed -> "\u00d7";
            case moved -> "\u2192";
            case transformed -> "\u21c4";
            case adjusted -> {
                long b = beforeAmount();
                long a = afterAmount();
                if (b > 0L && a > 0L && a != b) {
                    if (a > b && a % b == 0L) {
                        yield "\u00d7" + (a / b);
                    }
                    long diff = a - b;
                    yield (diff > 0L ? "+" : "") + diff;
                }
                if (before instanceof DurabilityContent bdc && after instanceof DurabilityContent adc
                        && bdc.durabilityCost() != adc.durabilityCost()) {
                    yield "-" + adc.durabilityCost() + "d";
                }
                if (before != null && after != null && Float.compare(before.chance(), after.chance()) != 0) {
                    yield Math.round(after.chance() * 100f) + "%";
                }
                yield "~";
            }
        };
    }
}
