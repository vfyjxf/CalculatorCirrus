package dev.vfyjxf.cirrus.api.recipe.projection.pipeline;

import dev.vfyjxf.cirrus.api.recipe.content.RecipeContent;
import dev.vfyjxf.cirrus.api.recipe.projection.ProjectionAttributeKey;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotAddress;
import dev.vfyjxf.cirrus.api.recipe.projection.SlotChannel;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import dev.vfyjxf.cloudlib.util.Checks;
import org.jspecify.annotations.Nullable;

/**
 * Atomic, address-aware mutation recorded during a {@link ProjectionPipeline} trace.
 */
public sealed interface ProjectionChange
        permits ProjectionChange.SlotChange, ProjectionChange.AttributeChange, ProjectionChange.TagChange {

    ChangeReason reason();

    /**
     * Slot-level content mutation pinned to a stable {@link SlotAddress} (including grid cells).
     */
    record SlotChange(
        SlotAddress address,
        @Nullable SlotAddress movedFrom,
        @Nullable RecipeContent before,
        @Nullable RecipeContent after,
        ChangeReason reason
    ) implements ProjectionChange {

        public SlotChange {
            Checks.checkNotNull(address, "address");
            Checks.checkNotNull(reason, "reason");
        }

        public SlotChannel channel() {
            return address.channel();
        }

        public boolean isGridCell() {
            return address.isGridCell();
        }

        public SlotAddress.@Nullable GridCell gridCell() {
            return address instanceof SlotAddress.GridCell cell ? cell : null;
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
    }

    /**
     * Typed attribute mutation on either the recipe ({@code targetSlot == null}) or a specific slot.
     */
    record AttributeChange(
        @Nullable SlotAddress targetSlot,
        ProjectionAttributeKey<?> key,
        @Nullable Object before,
        @Nullable Object after
    ) implements ProjectionChange {

        public AttributeChange {
            Checks.checkNotNull(key, "key");
        }

        @Override
        public ChangeReason reason() {
            return ChangeReason.attributeChanged;
        }
    }

    /**
     * Tag mutation on either the recipe ({@code targetSlot == null}) or a specific slot.
     */
    record TagChange(
        @Nullable SlotAddress targetSlot,
        Namespace tag,
        boolean added
    ) implements ProjectionChange {

        public TagChange {
            Checks.checkNotNull(tag, "tag");
        }

        @Override
        public ChangeReason reason() {
            return ChangeReason.tagChanged;
        }
    }
}
