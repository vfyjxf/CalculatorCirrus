package dev.vfyjxf.cirrus.api.recipe.execution.driver.slot;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SlotLayoutReferenceTest {

    @Test
    void layoutReferenceUsesSealedTypes() {
        SlotLayout layout = SlotLayout.builder()
                .add(
                    SlotLayout.Role.input,
                    new SlotLayout.Key(Namespace.of("cirrus", "slot_index"), 0),
                    new SlotLayout.Reference.Index(3)
                )
                .add(
                    SlotLayout.Role.input,
                    new SlotLayout.Key(Namespace.of("cirrus", "slot_key"), 1),
                    new SlotLayout.Reference.Key("custom")
                ).build();
        SlotLayout.Index index = layout.slots(SlotLayout.Role.input);
        SlotLayout.Reference indexRef = index.get(new SlotLayout.Key(Namespace.of("cirrus", "slot_index"), 0));
        SlotLayout.Reference keyRef = index.get(new SlotLayout.Key(Namespace.of("cirrus", "slot_key"), 1));
        assertTrue(indexRef instanceof SlotLayout.Reference.Index);
        assertTrue(keyRef instanceof SlotLayout.Reference.Key);
    }
}
