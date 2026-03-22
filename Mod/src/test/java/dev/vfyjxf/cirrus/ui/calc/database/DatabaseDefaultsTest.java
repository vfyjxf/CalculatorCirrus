package dev.vfyjxf.cirrus.ui.calc.database;

import dev.vfyjxf.cloudlib.api.util.Namespace;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseDefaultsTest {
    @Test
    void generatesSystemAndJeiDefaults() {
        List<Namespace> systems = List.of(
            Namespace.of("core", "crafting"),
            Namespace.of("core", "smelting")
        );
        List<Namespace> jei = List.of(
            Namespace.of("jei", "category/smelting")
        );

        List<DatabaseDefinition> defs = DatabaseDefaults.generate(systems, jei);

        assertEquals(3, defs.size());
        assertEquals(DatabaseKind.system, defs.getFirst().kind());
        assertEquals(DatabaseKind.jei, defs.getLast().kind());
        assertTrue(defs.getLast().sources().containsAll(jei));
    }
}
