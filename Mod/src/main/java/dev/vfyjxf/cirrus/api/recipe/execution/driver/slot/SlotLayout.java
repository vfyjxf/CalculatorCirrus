package dev.vfyjxf.cirrus.api.recipe.execution.driver.slot;

import dev.vfyjxf.cirrus.util.CirrusNamespaces;
import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;
import java.util.Map;
import java.util.Set;

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
        public static final Role input = new Role(CirrusNamespaces.ofInternal("input"));
        public static final Role output = new Role(CirrusNamespaces.ofInternal("output"));
        public static final Role inventory = new Role(CirrusNamespaces.ofInternal("inventory"));
        public static final Role catalyst = new Role(CirrusNamespaces.ofInternal("catalyst"));
        public static final Role interaction = new Role(CirrusNamespaces.ofInternal("interaction"));
    }

    /**
     * Semantic slot key with stable ordering for deterministic iteration.
     */
    record Key(Namespace id, int order) {}

    /**
     * Lightweight reference to a slot in the underlying container.
     */
    sealed interface Reference permits Reference.Index, Reference.Key {
        record Index(int index) implements Reference {}

        record Key(String key) implements Reference {}
    }

    /**
     * Index of slots for a role. Keys are semantic identities and may be sparse.
     */
    interface Index {
        @Nullable
        Reference get(Key key);

        Set<Key> keys();

        default Comparator<Key> order() {
            return Comparator.comparingInt(Key::order).thenComparing(slotKey -> slotKey.id().toString());
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
