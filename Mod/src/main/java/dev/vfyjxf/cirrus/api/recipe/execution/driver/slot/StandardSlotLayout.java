package dev.vfyjxf.cirrus.api.recipe.execution.driver.slot;

import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

final class StandardSlotLayout implements SlotLayout {
    private final Map<Role, Index> roleIndex;
    private final Index emptyIndex;

    StandardSlotLayout(Map<Role, Index> roleIndex, Index emptyIndex) {
        this.roleIndex = Map.copyOf(roleIndex);
        this.emptyIndex = emptyIndex;
    }

    @Override
    public Index slots(Role role) {
        Index index = roleIndex.get(role);
        return index == null ? emptyIndex : index;
    }

    static final class Builder implements SlotLayout.Builder {
        private final Map<Role, Index> roleIndex = new LinkedHashMap<>();

        @Override
        public Builder put(Role role, Index index) {
            roleIndex.put(role, index);
            return this;
        }

        @Override
        public Builder put(Role role, Map<Key, Reference> mapping) {
            return put(role, new StandardSlotIndex(mapping));
        }

        @Override
        public Builder add(Role role, Key key, Reference ref) {
            Index current = roleIndex.get(role);
            if (current instanceof StandardSlotIndex standard) {
                standard.put(key, ref);
            } else if (current == null) {
                StandardSlotIndex index = new StandardSlotIndex();
                index.put(key, ref);
                roleIndex.put(role, index);
            } else {
                Map<Key, Reference> merged = new LinkedHashMap<>(current.mapping());
                merged.put(key, ref);
                roleIndex.put(role, new StandardSlotIndex(merged));
            }
            return this;
        }

        @Override
        public SlotLayout build() {
            Index empty = new StandardSlotIndex();
            return new StandardSlotLayout(roleIndex, empty);
        }
    }

    static final class StandardSlotIndex implements SlotLayout.Index {
        private final Map<Key, Reference> slots;
        private Set<Key> keys;

        StandardSlotIndex() {
            this(Map.of());
        }

        StandardSlotIndex(Map<Key, Reference> mapping) {
            this.slots = new LinkedHashMap<>(mapping);
            this.keys = immutableKeys(this.slots);
        }

        void put(Key key, Reference ref) {
            slots.put(key, ref);
            keys = immutableKeys(this.slots);
        }

        @Override
        public @Nullable Reference get(Key key) {
            return slots.get(key);
        }

        @Override
        public Set<Key> keys() {
            return keys;
        }

        @Override
        public Map<Key, Reference> mapping() {
            return Map.copyOf(slots);
        }
    }

    private static Set<Key> immutableKeys(Map<Key, Reference> slots) {
        if (slots.isEmpty()) {
            return Set.of();
        }
        return Collections.unmodifiableSet(new LinkedHashSet<>(slots.keySet()));
    }
}
