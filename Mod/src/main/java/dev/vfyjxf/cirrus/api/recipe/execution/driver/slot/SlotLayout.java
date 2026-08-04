package dev.vfyjxf.cirrus.api.recipe.execution.driver.slot;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.LinkedHashSet;

/**
 * Slot layout describing semantic roles and addressable slots.
 */
public interface SlotLayout {

    Index slots(Role role);

    static Builder builder() {
        return new StandardSlotLayout.Builder();
    }

    /**
     * Semantic slot role identifier (not necessarily a vanilla Slot type).
     */
    record Role(Namespace id) {
        public static final Role input = new Role(Namespace.of("cirrus", "input"));
        public static final Role output = new Role(Namespace.of("cirrus", "output"));
        public static final Role inventory = new Role(Namespace.of("cirrus", "inventory"));
        public static final Role catalyst = new Role(Namespace.of("cirrus", "catalyst"));
        public static final Role interaction = new Role(Namespace.of("cirrus", "interaction"));
    }

    /**
     * Semantic slot key with stable ordering for deterministic iteration.
     */
    record Key(Namespace id, int order) {}

    /**
     * Lightweight reference to a slot in the underlying container.
     */
    sealed interface Reference permits Reference.Index, Reference.Key {
        record Index(int index) implements Reference { }

        record Key(String key) implements Reference {}
    }

    /**
     * Index of slots for a role. Keys are semantic identities and may be sparse.
     */
    interface Index {
        @Nullable Reference get(Key key);

        Set<Key> keys();

        default Comparator<Key> order() {
            return Comparator.comparingInt(Key::order)
                .thenComparing(slotKey -> slotKey.id().toString());
        }

        /**
         * Returns a stable mapping view for the current snapshot.
         */
        Map<Key, Reference> mapping();
    }

    interface Builder {
        Builder put(Role role, Index index);

        Builder put(Role role, Map<Key, Reference> mapping);

        Builder add(Role role, Key key, Reference ref);

        SlotLayout build();
    }
}

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
