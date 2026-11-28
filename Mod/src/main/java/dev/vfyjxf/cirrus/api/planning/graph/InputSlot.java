package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record InputSlot(
    int slotIndex,
    long amountPerBatch,
    Optional<TagKey<?>> tagKey,
    List<ComponentStackKey> candidates,
    SlotMatchMode matchMode
) {

    public InputSlot {
        if (slotIndex < 0) {
            throw new IllegalArgumentException("slotIndex must be >= 0, got " + slotIndex);
        }
        if (amountPerBatch <= 0L) {
            throw new IllegalArgumentException("amountPerBatch must be > 0, got " + amountPerBatch);
        }
        Objects.requireNonNull(tagKey, "tagKey");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(matchMode, "matchMode");
        candidates = List.copyOf(candidates);
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("InputSlot candidates must not be empty");
        }
    }
}
