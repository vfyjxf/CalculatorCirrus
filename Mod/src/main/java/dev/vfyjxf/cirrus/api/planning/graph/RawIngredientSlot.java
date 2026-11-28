package dev.vfyjxf.cirrus.api.planning.graph;

import dev.vfyjxf.cirrus.api.content.value.ComponentStackKey;
import dev.vfyjxf.cirrus.api.planning.model.SlotMatchMode;
import net.minecraft.tags.TagKey;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record RawIngredientSlot(
    Optional<TagKey<?>> tagKey,
    List<ComponentStackKey> candidates,
    long amount,
    SlotMatchMode matchMode,
    int durabilityCostPerBatch,
    int maxDurability
) {

    public RawIngredientSlot(
        Optional<TagKey<?>> tagKey,
        List<ComponentStackKey> candidates,
        long amount,
        SlotMatchMode matchMode
    ) {
        this(tagKey, candidates, amount, matchMode, 0, 0);
    }

    public RawIngredientSlot {
        Objects.requireNonNull(tagKey, "tagKey");
        Objects.requireNonNull(candidates, "candidates");
        Objects.requireNonNull(matchMode, "matchMode");
        candidates = List.copyOf(candidates);
        if (durabilityCostPerBatch < 0) {
            throw new IllegalArgumentException("durabilityCostPerBatch must be >= 0");
        }
        if (maxDurability < 0) {
            throw new IllegalArgumentException("maxDurability must be >= 0");
        }
    }

    public boolean catalyst() {
        return amount == 0L || (durabilityCostPerBatch > 0 && maxDurability > 0);
    }
}
